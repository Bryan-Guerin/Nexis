package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeAffectationDto;
import com.bryan.nexis.sapeurs.backend.intervention.SpEngagementService;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Affectation automatique de l'équipage de garde aux postes d'un engin.
 *
 * <p>Remplissage <b>de bas en haut</b> (Équipier → CA), <b>postes obligatoires d'abord</b>,
 * en choisissant à chaque place le candidat <b>le moins sur-qualifié</b> puis <b>le moins gradé</b>
 * puis <b>le plus récemment formé</b> sur la fonction — afin de réserver les CA aux postes CA.</p>
 */
@Singleton
public class SpAffectationAutoService {

    private final SpVehiculeRepository             vehiculeRepo;
    private final SpVehiculeTypePosteRepository    posteRepo;
    private final SpMembreRepository               membreRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SpPlanningService                planningService;
    private final SpEngagementService              engagement;
    private final SpVehiculeAffectationService     affectationService;

    public SpAffectationAutoService(SpVehiculeRepository vehiculeRepo, SpVehiculeTypePosteRepository posteRepo,
                                    SpMembreRepository membreRepo, SpVehiculeAffectationRepository affectationRepo,
                                    SpPlanningService planningService, SpEngagementService engagement,
                                    SpVehiculeAffectationService affectationService) {
        this.vehiculeRepo        = vehiculeRepo;
        this.posteRepo           = posteRepo;
        this.membreRepo          = membreRepo;
        this.affectationRepo     = affectationRepo;
        this.planningService     = planningService;
        this.engagement          = engagement;
        this.affectationService  = affectationService;
    }

    /** Affecte automatiquement l'équipage de garde aux postes encore libres de l'engin. */
    @Transactional
    public List<SpVehiculeAffectationDto> affecterAuto(UUID vehiculeId) {
        var vehicule = vehiculeRepo.findById(vehiculeId)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + vehiculeId));
        var postes = posteRepo.findByVehiculeTypeIdOrderByOrdreAsc(vehicule.getType().getId());

        var actives = affectationRepo.findByVehiculeIdAndFinIsNull(vehiculeId);
        Map<UUID, Long> occupeParPoste = actives.stream().filter(a -> a.getPoste() != null)
                .collect(Collectors.groupingBy(a -> a.getPoste().getId(), Collectors.counting()));
        Set<UUID> dejaSurVehicule  = actives.stream().map(a -> a.getMembre().getId()).collect(Collectors.toSet());
        Set<UUID> occupesAilleurs  = engagement.membresOccupesSurAutreIntervention(vehiculeId);
        Set<UUID> enService        = new HashSet<>(planningService.membresEnService());

        List<SpMembre> pool = membreRepo.findByActif(true).stream()
                .filter(m -> enService.contains(m.getId()))
                .filter(m -> !occupesAilleurs.contains(m.getId()))
                .filter(m -> !dejaSurVehicule.contains(m.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        var resultat = new ArrayList<SpVehiculeAffectationDto>();
        // Passe 1 : postes obligatoires, puis passe 2 : optionnels. Chaque passe : niveau croissant.
        for (boolean obligatoire : new boolean[]{true, false}) {
            var postesPasse = postes.stream()
                    .filter(p -> p.isObligatoire() == obligatoire)
                    .sorted(Comparator.comparingInt(p -> p.getFonction().getTypeFonction().getNiveau()))
                    .toList();
            for (var poste : postesPasse) {
                long restantes = poste.getNbPlaces() - occupeParPoste.getOrDefault(poste.getId(), 0L);
                UUID fonctionId = poste.getFonction().getId();
                for (long k = 0; k < restantes; k++) {
                    var choix = pool.stream()
                            .filter(m -> qualifiePour(m, fonctionId))
                            .min(comparateur(fonctionId));
                    if (choix.isEmpty()) break;   // plus personne pour ce poste
                    var membre = choix.get();
                    pool.remove(membre);
                    resultat.add(affectationService.affecter(vehiculeId, membre.getId(), poste.getId(), Instant.now()));
                }
            }
        }
        return resultat;
    }

    /** Le moins sur-qualifié, puis le moins gradé, puis le plus récemment formé sur la fonction. */
    private Comparator<SpMembre> comparateur(UUID fonctionId) {
        return Comparator.<SpMembre>comparingInt(this::niveauMax)
                .thenComparingInt(m -> m.getGrade().getPosition())
                .thenComparing(m -> dateQualif(m, fonctionId), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SpMembre::getMatricule);
    }

    private int niveauMax(SpMembre m) {
        return m.getQualifications().stream()
                .mapToInt(q -> q.getFonction().getTypeFonction().getNiveau()).max().orElse(0);
    }

    private boolean qualifiePour(SpMembre m, UUID fonctionId) {
        return m.getQualifications().stream().anyMatch(q -> q.getFonction().getId().equals(fonctionId));
    }

    private Instant dateQualif(SpMembre m, UUID fonctionId) {
        return m.getQualifications().stream()
                .filter(q -> q.getFonction().getId().equals(fonctionId))
                .map(q -> q.getDateDelivrance()).findFirst().orElse(null);
    }
}
