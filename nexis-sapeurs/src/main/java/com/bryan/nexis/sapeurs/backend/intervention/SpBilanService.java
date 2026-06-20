package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.bilan.BilanSapContenu;
import com.bryan.nexis.sapeurs.backend.dto.SpBilanDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVictimeDto;
import com.bryan.nexis.sapeurs.datamodel.FamilleBilan;
import com.bryan.nexis.sapeurs.datamodel.SpBilan;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVictime;
import com.bryan.nexis.sapeurs.datarepository.SpBilanRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVictimeRepository;
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

    private final SpInterventionRepository        interventionRepo;
    private final SpVictimeRepository             victimeRepo;
    private final SpBilanRepository               bilanRepo;
    private final SpVehiculeAffectationRepository affectationRepo;
    private final SecurityService                 security;
    private final JsonMapper                      json;

    public SpBilanService(SpInterventionRepository interventionRepo, SpVictimeRepository victimeRepo,
                          SpBilanRepository bilanRepo, SpVehiculeAffectationRepository affectationRepo,
                          SecurityService security, JsonMapper json) {
        this.interventionRepo = interventionRepo;
        this.victimeRepo      = victimeRepo;
        this.bilanRepo        = bilanRepo;
        this.affectationRepo  = affectationRepo;
        this.security         = security;
        this.json             = json;
    }

    @Transactional
    public List<SpVictimeDto> listVictimes(UUID interventionId) {
        return victimeRepo.findByInterventionId(interventionId).stream().map(SpVictimeDto::from).toList();
    }

    @Transactional
    public SpVictimeDto ajouterVictime(UUID interventionId, String libelle) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        assertPeutSaisir(inter);
        var victime = new SpVictime(inter, (int) victimeRepo.countByInterventionId(interventionId) + 1);
        if (libelle != null && !libelle.isBlank()) victime.setLibelle(libelle.trim());
        return SpVictimeDto.from(victimeRepo.save(victime));
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
        return toDto(bilanRepo.save(bilan));
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
