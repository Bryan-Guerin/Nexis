package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.bilan.BilanIncContenu;
import com.bryan.nexis.sapeurs.backend.bilan.BilanSapContenu;
import com.bryan.nexis.sapeurs.backend.bilan.BilanSrContenu;
import com.bryan.nexis.sapeurs.backend.dto.SpBilanDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVictimeDto;
import com.bryan.nexis.core.datarepository.RefUserRepository;
import com.bryan.nexis.sapeurs.datamodel.FamilleBilan;
import com.bryan.nexis.sapeurs.datamodel.Sexe;
import com.bryan.nexis.sapeurs.datamodel.SpBilan;
import com.bryan.nexis.sapeurs.datamodel.SpCri;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVictime;
import com.bryan.nexis.sapeurs.datarepository.SpBilanRepository;
import com.bryan.nexis.sapeurs.datarepository.SpCriRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVictimeRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Bilans d'intervention (SAP / SR / INC) + victimes. Le contenu typé (records par famille) est
 * sérialisé en JSON via le {@link JsonMapper}. Écriture réservée à un équipier d'un engin de
 * l'intervention (ou admin SP), comme la main courante / le CRI.
 */
@Singleton
public class SpBilanService {

    private static final String BILAN_MAJ = "BILAN_MAJ";

    private final SpInterventionRepository        interventionRepo;
    private final SpVictimeRepository             victimeRepo;
    private final SpBilanRepository               bilanRepo;
    private final SpVehiculeAffectationRepository affectationRepo;
    private final SpCriRepository                 criRepo;      // statut des CRI → intervention close ou non
    private final SpMembreRepository              membreRepo;   // username → membre (équipage historisé)
    private final RefUserRepository               userRepo;
    private final SecurityService                 security;
    private final JsonMapper                      json;
    private final ApplicationEventPublisher<RealtimeEvent> events;

    public SpBilanService(SpInterventionRepository interventionRepo, SpVictimeRepository victimeRepo,
                          SpBilanRepository bilanRepo, SpVehiculeAffectationRepository affectationRepo,
                          SpCriRepository criRepo, SpMembreRepository membreRepo, RefUserRepository userRepo,
                          SecurityService security, JsonMapper json,
                          ApplicationEventPublisher<RealtimeEvent> events) {
        this.interventionRepo = interventionRepo;
        this.victimeRepo      = victimeRepo;
        this.bilanRepo        = bilanRepo;
        this.affectationRepo  = affectationRepo;
        this.criRepo          = criRepo;
        this.membreRepo       = membreRepo;
        this.userRepo         = userRepo;
        this.security         = security;
        this.json             = json;
        this.events           = events;
    }

    @Transactional
    public List<SpVictimeDto> listVictimes(UUID interventionId) {
        return victimeRepo.findByInterventionId(interventionId).stream().map(SpVictimeDto::from).toList();
    }

    @Transactional
    public SpVictimeDto ajouterVictime(UUID interventionId, String libelle, String nom, String prenom, String sexe) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        assertPeutSaisir(inter);
        var victime = new SpVictime(inter, (int) victimeRepo.countByInterventionId(interventionId) + 1);
        appliquerIdentite(victime, libelle, nom, prenom, sexe);
        var dto = SpVictimeDto.from(victimeRepo.save(victime));
        notifierMaj(inter);
        return dto;
    }

    /** Édite l'identité d'une victime — refusé une fois l'intervention close (tous les CRI validés). */
    @Transactional
    public SpVictimeDto modifierVictime(UUID victimeId, String libelle, String nom, String prenom, String sexe) {
        var victime = victimeRepo.findById(victimeId)
                .orElseThrow(() -> new NoSuchElementException("Victime introuvable : " + victimeId));
        var inter = victime.getIntervention();
        assertPeutSaisir(inter);
        appliquerIdentite(victime, libelle, nom, prenom, sexe);
        var dto = SpVictimeDto.from(victimeRepo.update(victime));
        notifierMaj(inter);
        return dto;
    }

    @Transactional
    public List<SpBilanDto> listBilans(UUID interventionId) {
        return bilanRepo.findByInterventionId(interventionId).stream().map(this::toDto).toList();
    }

    /** Enregistre (crée ou écrase) le bilan SAP d'une victime. */
    @Transactional
    public SpBilanDto enregistrerBilanSap(UUID victimeId, BilanSapContenu contenu) {
        var victime = victimeRepo.findById(victimeId)
                .orElseThrow(() -> new NoSuchElementException("Victime introuvable : " + victimeId));
        var inter = victime.getIntervention();
        assertPeutSaisir(inter);
        var bilan = bilanRepo.findByVictimeId(victimeId)
                .orElseGet(() -> new SpBilan(inter, FamilleBilan.SAP, victime));
        bilan.setContenu(ecrire(contenu));
        bilan.setAuteur(actor());
        bilan.setMajLe(Instant.now());
        var dto = toDto(bilanRepo.save(bilan));
        notifierMaj(inter);
        return dto;
    }

    /** Enregistre (crée ou écrase) le bilan SR (scène + véhicules) de l'intervention. */
    @Transactional
    public SpBilanDto enregistrerBilanSr(UUID interventionId, BilanSrContenu contenu) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        assertPeutSaisir(inter);
        var bilan = bilanRepo.findByInterventionAndFamille(interventionId, FamilleBilan.SR)
                .orElseGet(() -> new SpBilan(inter, FamilleBilan.SR, null));
        bilan.setContenu(ecrire(contenu));
        bilan.setAuteur(actor());
        bilan.setMajLe(Instant.now());
        var dto = toDto(bilanRepo.save(bilan));
        notifierMaj(inter);
        return dto;
    }

    /** Enregistre (crée ou écrase) le bilan INC (feu de forêt) de l'intervention.
     *  Sur transition d'état du feu (EN_COURS/MAITRISE/ETEINT/SOUS_SURVEILLANCE), tamponne automatiquement
     *  l'heure correspondante dans le contenu et publie une note de main courante.
     */
    @Transactional
    public SpBilanDto enregistrerBilanInc(UUID interventionId, BilanIncContenu contenu) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        assertPeutSaisir(inter);
        var bilan = bilanRepo.findByInterventionAndFamille(interventionId, FamilleBilan.INC)
                .orElseGet(() -> new SpBilan(inter, FamilleBilan.INC, null));
        var ancien = lireInc(bilan.getContenu());
        var contenuFinal = appliquerTransitionEtat(inter, ancien, contenu);
        bilan.setContenu(ecrire(contenuFinal));
        bilan.setAuteur(actor());
        bilan.setMajLe(Instant.now());
        var dto = toDto(bilanRepo.save(bilan));
        notifierMaj(inter);
        return dto;
    }

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId TZ_PARIS = ZoneId.of("Europe/Paris");

    private BilanIncContenu appliquerTransitionEtat(SpIntervention inter, BilanIncContenu ancien, BilanIncContenu nouveau) {
        if (nouveau == null || nouveau.sinistre() == null || nouveau.sinistre().etat() == null) return nouveau;
        var ancienEtat = ancien != null && ancien.sinistre() != null ? ancien.sinistre().etat() : null;
        var nouveauEtat = nouveau.sinistre().etat();
        if (nouveauEtat == ancienEtat) return nouveau;
        var maintenant = LocalTime.now(TZ_PARIS).format(HHMM);
        var s = nouveau.sinistre();
        String hd = s.heureDebut(), hm = s.heureMaitrise(), he = s.heureExtinction(), note = null;
        switch (nouveauEtat) {
            case EN_COURS:
                if (hd == null || hd.isBlank()) hd = maintenant;
                note = "Feu en cours (" + maintenant + ")"; break;
            case MAITRISE:
                if (hm == null || hm.isBlank()) hm = maintenant;
                note = "Feu maîtrisé (" + maintenant + ")"; break;
            case ETEINT:
                if (he == null || he.isBlank()) he = maintenant;
                note = "Feu éteint (" + maintenant + ")"; break;
            case SOUS_SURVEILLANCE:
                note = "Feu sous surveillance (" + maintenant + ")"; break;
        }
        if (note != null) {
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.MAIN_COURANTE, "SP", note,
                    Map.of("interventionId", inter.getId().toString()), actor()).withReference(inter.getCode()));
        }
        var s2 = new BilanIncContenu.Sinistre(s.surfaceBrulee(), s.surfaceBruleeSource(), s.surfaceMenacee(),
                s.couvert(), s.etat(), hd, hm, he);
        return new BilanIncContenu(nouveau.typeFeu(), s2, nouveau.propagation(), nouveau.enjeux(),
                nouveau.hydraulique(), nouveau.aeriens(), nouveau.technique(),
                nouveau.polygone(), nouveau.enginsPositions());
    }

    private BilanIncContenu lireInc(String contenu) {
        if (contenu == null || contenu.isBlank()) return null;
        try { return json.readValue(contenu, Argument.of(BilanIncContenu.class)); }
        catch (IOException e) { return null; }
    }

    private void appliquerIdentite(SpVictime v, String libelle, String nom, String prenom, String sexe) {
        v.setLibelle(vide(libelle));
        v.setNom(vide(nom));
        v.setPrenom(vide(prenom));
        v.setSexe(sexe != null && !sexe.isBlank() ? Sexe.valueOf(sexe.trim()) : null);
    }
    private String vide(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /**
     * Signale (temps réel éphémère, non journalisé) qu'un bilan / une victime a changé. Diffusé à
     * toute la faction SP : le front filtre par interventionId → tout SP affichant ce dossier se
     * rafraîchit (pas restreint à l'équipage : un dispatcher peut suivre un dossier sans y être affecté).
     */
    private void notifierMaj(SpIntervention inter) {
        events.publishEvent(RealtimeEvent.faction(BILAN_MAJ, "SP", "Bilan mis à jour",
                Map.of("interventionId", inter.getId().toString()), actor()).ephemere());
    }

    private SpBilanDto toDto(SpBilan b) {
        return new SpBilanDto(b.getId(), b.getFamille().name(),
                b.getVictime() != null ? b.getVictime().getId() : null,
                lire(b.getContenu()), b.getAuteur(), b.getCreeLe(), b.getMajLe());
    }

    private String ecrire(Object contenu) {
        try { return json.writeValueAsString(contenu); }
        catch (IOException e) { throw new IllegalStateException("Sérialisation du bilan impossible", e); }
    }

    private Object lire(String contenu) {
        try { return json.readValue(contenu, Argument.mapOf(String.class, Object.class)); }
        catch (IOException e) { return Map.of(); }
    }

    /**
     * Saisie autorisée : admin SP, équipier actif d'un engin, OU équipier historisé (après la
     * clôture, engins détachés). Refusée une fois l'intervention CLOSE — alignée sur le CRI :
     * les bilans restent complétables pendant « en attente CRI / validation », figés à la
     * validation du dernier CRI.
     */
    private void assertPeutSaisir(SpIntervention inter) {
        if (estClose(inter)) {
            throw new IllegalStateException("Intervention close (CRI validés) : bilans figés.");
        }
        if (!security.hasRole("ROLE_ADMIN_SP") && !estEquipier(inter, actor()) && !estEquipierHistorise(inter, actor())) {
            throw new IllegalStateException("Seul un équipier de l'intervention peut saisir un bilan.");
        }
    }

    /** Close définitive = terminée ET aucun CRI restant à valider (aligné sur le statut dérivé front/liste). */
    private boolean estClose(SpIntervention inter) {
        if (inter.getFin() == null) return false;
        var cris = criRepo.findByInterventionId(inter.getId());
        return cris.isEmpty() || cris.stream().allMatch(c -> SpCri.VALIDE.equals(c.getStatut()));
    }

    private boolean estEquipier(SpIntervention inter, String username) {
        if (username == null) return false;
        return inter.getEngins().stream().anyMatch(engin ->
                affectationRepo.findByVehiculeIdAndFinIsNull(engin.getId()).stream()
                        .anyMatch(a -> username.equals(a.getMembre().getUser().getUsername())));
    }

    /** Présent dans l'équipage figé à la clôture (les engins actifs sont détachés à ce moment-là). */
    private boolean estEquipierHistorise(SpIntervention inter, String username) {
        if (username == null) return false;
        var membreId = userRepo.findByUsername(username)
                .flatMap(u -> membreRepo.findByUserId(u.getId()))
                .map(m -> m.getId()).orElse(null);
        if (membreId == null) return false;
        return inter.getEnginsHisto().stream()
                .flatMap(e -> e.getEquipage().stream())
                .anyMatch(eq -> membreId.equals(eq.getMembreId()));
    }

    private String actor() {
        return security.username().orElse("system");
    }
}
