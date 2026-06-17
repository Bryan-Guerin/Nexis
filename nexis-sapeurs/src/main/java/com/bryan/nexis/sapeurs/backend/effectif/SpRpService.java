package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpMembreBadgeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpProfilRpDto;
import com.bryan.nexis.sapeurs.datamodel.*;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Profil RP : XP, niveau et badges des membres.
 *
 * <p>XP = (interventions × 10) + heures_garde + somme des XP des badges obtenus.</p>
 * <p>Niveaux à paliers (cumulatifs) — au-delà du dernier palier, on continue par incréments
 * fixes (+2500 par niveau).</p>
 *
 * <p>L'évaluation des badges est manuelle (endpoint admin) ou déclenchée par la clôture
 * d'une intervention (à brancher dans SpInterventionService).</p>
 */
@Singleton
public class SpRpService {

    private static final Logger LOG = LoggerFactory.getLogger(SpRpService.class);

    /** Seuils XP cumulatifs. L'index = niveau − 1 (niveau 1 = 0 XP). */
    private static final int[] PALIERS = { 0, 100, 250, 500, 1000, 1750, 2750, 4000, 5500, 7500 };
    private static final int INCREMENT_AU_DELA = 2500;

    private final SpMembreRepository              membreRepo;
    private final SpBadgeRepository               badgeRepo;
    private final SpMembreBadgeRepository         membreBadgeRepo;
    private final SpVehiculeAffectationRepository affRepo;
    private final SpInterventionRepository        interRepo;
    private final SpInterventionEquipierRepository equipierRepo;
    private final SpPlanningRepository            planningRepo;
    private final ApplicationEventPublisher<RealtimeEvent> events;

    public SpRpService(SpMembreRepository membreRepo,
                       SpBadgeRepository badgeRepo,
                       SpMembreBadgeRepository membreBadgeRepo,
                       SpVehiculeAffectationRepository affRepo,
                       SpInterventionRepository interRepo,
                       SpInterventionEquipierRepository equipierRepo,
                       SpPlanningRepository planningRepo,
                       ApplicationEventPublisher<RealtimeEvent> events) {
        this.membreRepo      = membreRepo;
        this.badgeRepo       = badgeRepo;
        this.membreBadgeRepo = membreBadgeRepo;
        this.affRepo         = affRepo;
        this.equipierRepo    = equipierRepo;
        this.interRepo       = interRepo;
        this.planningRepo    = planningRepo;
        this.events          = events;
    }

    // ── Profil ───────────────────────────────────────────────────────────────

    @Transactional
    public SpProfilRpDto getProfil(UUID membreId) {
        var membre = membreRepo.findById(membreId).orElseThrow(
                () -> new NoSuchElementException("Membre SP introuvable : " + membreId));

        var compteurs = computeCompteurs(membre);
        var badges    = membreBadgeRepo.findByMembreId(membreId).stream()
                .map(SpMembreBadgeDto::from)
                .toList();
        int xpBadges = badges.stream().mapToInt(SpMembreBadgeDto::xpReward).sum();
        int xp = compteurs.interventions() * 10 + compteurs.heuresGarde() + xpBadges;

        int niveau         = niveauFor(xp);
        int xpNiveauActuel = seuilFor(niveau);
        int xpSuivant      = seuilFor(niveau + 1);
        int delta          = xpSuivant - xpNiveauActuel;
        int dansNiveau     = xp - xpNiveauActuel;
        int progression    = delta > 0 ? Math.min(100, dansNiveau * 100 / delta) : 100;

        return new SpProfilRpDto(xp, niveau, xpNiveauActuel, xpSuivant, progression, compteurs, badges);
    }

    // ── Évaluation badges ─────────────────────────────────────────────────────

    /** Attribue les badges éligibles à tous les membres actifs. Idempotent. */
    @Transactional
    public int evalAll() {
        int attribues = 0;
        for (var m : membreRepo.findByActif(true)) {
            attribues += evalForMembre(m);
        }
        return attribues;
    }

    /** Attribue les badges éligibles au membre. Renvoie le nombre de nouveaux badges. */
    @Transactional
    public int evalForMembre(SpMembre membre) {
        var compteurs = computeCompteurs(membre);
        int nouveaux = 0;
        for (var b : badgeRepo.findAll()) {
            if (membreBadgeRepo.existsByMembreIdAndBadgeId(membre.getId(), b.getId())) continue;
            if (atteint(b, compteurs, membre)) {
                membreBadgeRepo.save(new SpMembreBadge(membre, b));
                nouveaux++;
                LOG.info("Badge attribué : {} → {}", b.getCode(), membre.getMatricule());
            }
        }
        // Notif nominale au porteur (sans dévoiler quel badge — il découvre sur sa fiche).
        if (nouveaux > 0) {
            String message = nouveaux == 1
                    ? "🎁 Vous avez débloqué un nouveau badge ! Découvrez-le sur votre fiche."
                    : "🎁 Vous avez débloqué " + nouveaux + " nouveaux badges ! Découvrez-les sur votre fiche.";
            events.publishEvent(RealtimeEvent.users(
                    "BADGE_OBTENU", "SP",
                    java.util.Set.of(membre.getUser().getUsername()),
                    message,
                    java.util.Map.of("count", String.valueOf(nouveaux)),
                    null).ephemere());   // non journalisé, juste push
        }
        return nouveaux;
    }

    /** Marque un badge comme découvert par son porteur. */
    @Transactional
    public SpMembreBadgeDto markDecouvert(UUID membreId, UUID badgeId) {
        var mb = membreBadgeRepo.findByMembreId(membreId).stream()
                .filter(x -> x.getBadge().getId().equals(badgeId))
                .findFirst().orElseThrow(
                        () -> new NoSuchElementException("Badge non obtenu par ce membre"));
        if (!mb.isDecouvert()) {
            mb.setDecouvert(true);
            membreBadgeRepo.update(mb);
        }
        return SpMembreBadgeDto.from(mb);
    }

    private boolean atteint(SpBadge b, SpProfilRpDto.Compteurs c, SpMembre membre) {
        return switch (b.getTypeCondition()) {
            case INTER_COUNT         -> c.interventions() >= b.getSeuil();
            case INTER_NATURE_COUNT  -> {
                if (b.getNature() == null) yield false;
                yield countInterventionsByNature(membre.getId(), b.getNature().getId()) >= b.getSeuil();
            }
            case GARDE_HEURES        -> c.heuresGarde() >= b.getSeuil();
            case SERVICE_JOURS       -> c.joursService() >= b.getSeuil();
            case GRADE_JOURS         -> c.joursGrade()   >= b.getSeuil();
            case QUALIF_COUNT        -> membre.getQualifications().size() >= b.getSeuil();
            case QUALIF_TYPE_COUNT   -> {
                if (b.getTypeFonction() == null) yield false;
                long n = membre.getQualifications().stream()
                        .filter(q -> q.getFonction() != null
                                && q.getFonction().getTypeFonction() == b.getTypeFonction())
                        .count();
                yield n >= b.getSeuil();
            }
            case FONCTION_ORGA       -> b.getFonctionOrga() != null
                    && membre.getFonctionsOrga().stream()
                            .anyMatch(f -> f.getId().equals(b.getFonctionOrga().getId()));
        };
    }

    // ── Calcul des compteurs ─────────────────────────────────────────────────

    private SpProfilRpDto.Compteurs computeCompteurs(SpMembre m) {
        int inter = countInterventions(m.getId(), null);
        int heuresGarde = sumHeuresParCategorie(m.getId(), TypeService.GARDE);
        int joursService = (int) ChronoUnit.DAYS.between(m.getDateIntegration(), Instant.now());
        int joursGrade   = (int) ChronoUnit.DAYS.between(
                m.getDateDernierePromotion() != null ? m.getDateDernierePromotion() : m.getDateIntegration(),
                Instant.now());
        int qualifs = m.getQualifications().size();
        return new SpProfilRpDto.Compteurs(inter, heuresGarde, joursService, joursGrade, qualifs);
    }

    private int countInterventionsByNature(UUID membreId, UUID natureId) {
        return countInterventions(membreId, natureId);
    }

    /**
     * Compte les interventions du membre. Deux sources, car l'engin live est détaché à la
     * clôture (historisation) :
     * <ul>
     *   <li><b>clôturées</b> : depuis le snapshot d'équipage figé (membre_id) ;</li>
     *   <li><b>ouvertes</b> : depuis les affectations actives sur les engins encore rattachés.</li>
     * </ul>
     * Filtre optionnel par nature.
     */
    private int countInterventions(UUID membreId, UUID natureIdFilter) {
        var matched = new HashSet<UUID>(natureIdFilter == null
                ? equipierRepo.distinctInterventionIds(membreId)
                : equipierRepo.distinctInterventionIdsByNature(membreId, natureIdFilter));

        // Interventions ouvertes : engins encore en base, affectation active du membre.
        var vehIds = affRepo.findByMembreIdAndFinIsNull(membreId).stream()
                .map(a -> a.getVehicule().getId()).collect(Collectors.toSet());
        if (!vehIds.isEmpty()) {
            for (var inter : interRepo.findByFinIsNull()) {
                if (natureIdFilter != null && !inter.getNature().getId().equals(natureIdFilter)) continue;
                if (inter.getEngins().stream().anyMatch(v -> vehIds.contains(v.getId()))) matched.add(inter.getId());
            }
        }
        return matched.size();
    }

    private int sumHeuresParCategorie(UUID membreId, TypeService cat) {
        long minutes = planningRepo.findByMembreId(membreId).stream()
                .filter(p -> p.getStatut() != null && p.getStatut().getCategorie() == cat)
                .filter(p -> p.getFin() != null && p.getDebut() != null)
                .mapToLong(p -> Duration.between(p.getDebut(), p.getFin()).toMinutes())
                .sum();
        return (int) (minutes / 60);
    }

    // ── Niveaux ──────────────────────────────────────────────────────────────

    private int niveauFor(int xp) {
        for (int i = PALIERS.length - 1; i >= 0; i--) {
            if (xp >= PALIERS[i]) return i + 1;
        }
        return 1;
    }

    private int seuilFor(int niveau) {
        if (niveau <= 0) return 0;
        if (niveau <= PALIERS.length) return PALIERS[niveau - 1];
        return PALIERS[PALIERS.length - 1] + (niveau - PALIERS.length) * INCREMENT_AU_DELA;
    }
}
