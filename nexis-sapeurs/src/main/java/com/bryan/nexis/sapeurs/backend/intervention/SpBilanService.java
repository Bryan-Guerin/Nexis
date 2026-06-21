package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.bilan.BilanSapContenu;
import com.bryan.nexis.sapeurs.backend.bilan.BilanSrContenu;
import com.bryan.nexis.sapeurs.backend.dto.SpBilanDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVictimeDto;
import com.bryan.nexis.sapeurs.datamodel.FamilleBilan;
import com.bryan.nexis.sapeurs.datamodel.Sexe;
import com.bryan.nexis.sapeurs.datamodel.SpBilan;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVictime;
import com.bryan.nexis.sapeurs.datarepository.SpBilanRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
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
    private final SecurityService                 security;
    private final JsonMapper                      json;
    private final ApplicationEventPublisher<RealtimeEvent> events;

    public SpBilanService(SpInterventionRepository interventionRepo, SpVictimeRepository victimeRepo,
                          SpBilanRepository bilanRepo, SpVehiculeAffectationRepository affectationRepo,
                          SecurityService security, JsonMapper json,
                          ApplicationEventPublisher<RealtimeEvent> events) {
        this.interventionRepo = interventionRepo;
        this.victimeRepo      = victimeRepo;
        this.bilanRepo        = bilanRepo;
        this.affectationRepo  = affectationRepo;
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

    /** Édite l'identité d'une victime — refusé si l'intervention est close. */
    @Transactional
    public SpVictimeDto modifierVictime(UUID victimeId, String libelle, String nom, String prenom, String sexe) {
        var victime = victimeRepo.findById(victimeId)
                .orElseThrow(() -> new NoSuchElementException("Victime introuvable : " + victimeId));
        var inter = victime.getIntervention();
        assertPeutSaisir(inter);
        if (inter.getFin() != null) throw new IllegalStateException("Intervention close : victime non modifiable.");
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

    private void assertPeutSaisir(SpIntervention inter) {
        if (!security.hasRole("ROLE_ADMIN_SP") && !estEquipier(inter, actor())) {
            throw new IllegalStateException("Seul un équipier de l'intervention peut saisir un bilan.");
        }
    }

    private boolean estEquipier(SpIntervention inter, String username) {
        if (username == null) return false;
        return inter.getEngins().stream().anyMatch(engin ->
                affectationRepo.findByVehiculeIdAndFinIsNull(engin.getId()).stream()
                        .anyMatch(a -> username.equals(a.getMembre().getUser().getUsername())));
    }

    private String actor() {
        return security.username().orElse("system");
    }
}
