package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpBadgeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpMembreBadgeDto;
import com.bryan.nexis.sapeurs.datamodel.BadgeCondition;
import com.bryan.nexis.sapeurs.datamodel.SpBadge;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.TypeFonction;
import com.bryan.nexis.sapeurs.datarepository.SpBadgeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpFonctionOrgaRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreBadgeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Catalogue des badges + lecture des badges d'un membre.
 * L'attribution automatique (évaluation des conditions) est faite par
 * {@code SpRpService} (étape suivante : XP + déclencheurs).
 */
@Singleton
public class SpBadgeService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    /** Paliers générés pour une nature : { seuil, XP }. Cohérent avec le seed V52. */
    private static final int[][] NATURE_PALIERS = { {1, 25}, {10, 50}, {50, 100}, {100, 200} };

    private final SpBadgeRepository              badgeRepo;
    private final SpMembreBadgeRepository        membreBadgeRepo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpFonctionOrgaRepository       fonctionOrgaRepo;

    public SpBadgeService(SpBadgeRepository badgeRepo,
                          SpMembreBadgeRepository membreBadgeRepo,
                          SpNatureInterventionRepository natureRepo,
                          SpFonctionOrgaRepository fonctionOrgaRepo) {
        this.badgeRepo        = badgeRepo;
        this.membreBadgeRepo  = membreBadgeRepo;
        this.natureRepo       = natureRepo;
        this.fonctionOrgaRepo = fonctionOrgaRepo;
    }

    // ── Catalogue ────────────────────────────────────────────────────────────

    @Transactional
    public List<SpBadgeDto> listCatalog() {
        return badgeRepo.findAll(BY_POSITION).stream().map(SpBadgeDto::from).toList();
    }

    @Transactional
    public SpBadgeDto create(String code, String label, String icone, String description,
                             String typeCondition, UUID natureId, String typeFonction, UUID fonctionOrgaId,
                             int seuil, int xpReward) {
        var type = BadgeCondition.valueOf(typeCondition);
        var b = new SpBadge(code, label, type, seuil);
        b.setIcone(icone);
        b.setDescription(description);
        b.setXpReward(xpReward);
        b.setPosition((int) badgeRepo.count());
        if (type == BadgeCondition.INTER_NATURE_COUNT) {
            if (natureId == null) throw new IllegalArgumentException("natureId requis pour INTER_NATURE_COUNT");
            b.setNature(natureRepo.findById(natureId).orElseThrow(
                    () -> new NoSuchElementException("Nature introuvable : " + natureId)));
        }
        if (type == BadgeCondition.QUALIF_TYPE_COUNT || type == BadgeCondition.INTER_TYPE_FONCTION_COUNT) {
            if (typeFonction == null) throw new IllegalArgumentException("typeFonction requis pour " + type);
            b.setTypeFonction(TypeFonction.valueOf(typeFonction));
        }
        if (type == BadgeCondition.FONCTION_ORGA) {
            if (fonctionOrgaId == null) throw new IllegalArgumentException("fonctionOrgaId requis pour FONCTION_ORGA");
            b.setFonctionOrga(fonctionOrgaRepo.findById(fonctionOrgaId).orElseThrow(
                    () -> new NoSuchElementException("Fonction d'organigramme introuvable : " + fonctionOrgaId)));
        }
        return SpBadgeDto.from(badgeRepo.save(b));
    }

    @Transactional
    public SpBadgeDto update(UUID id, String label, String icone, String description,
                             String typeCondition, UUID natureId, String typeFonction, UUID fonctionOrgaId,
                             Integer seuil, Integer xpReward) {
        var b = badgeRepo.findById(id).orElseThrow(
                () -> new NoSuchElementException("Badge introuvable : " + id));
        if (label != null)         b.setLabel(label);
        b.setIcone(icone);
        b.setDescription(description);
        if (typeCondition != null) b.setTypeCondition(BadgeCondition.valueOf(typeCondition));
        if (seuil != null)         b.setSeuil(seuil);
        if (xpReward != null)      b.setXpReward(xpReward);
        if (natureId != null) {
            b.setNature(natureRepo.findById(natureId).orElseThrow(
                    () -> new NoSuchElementException("Nature introuvable : " + natureId)));
        } else if (b.getTypeCondition() != BadgeCondition.INTER_NATURE_COUNT) {
            b.setNature(null);
        }
        if (b.getTypeCondition() == BadgeCondition.QUALIF_TYPE_COUNT
                || b.getTypeCondition() == BadgeCondition.INTER_TYPE_FONCTION_COUNT) {
            if (typeFonction == null) throw new IllegalArgumentException("typeFonction requis pour " + b.getTypeCondition());
            b.setTypeFonction(TypeFonction.valueOf(typeFonction));
        } else {
            b.setTypeFonction(null);
        }
        if (b.getTypeCondition() == BadgeCondition.FONCTION_ORGA) {
            if (fonctionOrgaId == null) throw new IllegalArgumentException("fonctionOrgaId requis pour FONCTION_ORGA");
            b.setFonctionOrga(fonctionOrgaRepo.findById(fonctionOrgaId).orElseThrow(
                    () -> new NoSuchElementException("Fonction d'organigramme introuvable : " + fonctionOrgaId)));
        } else {
            b.setFonctionOrga(null);
        }
        return SpBadgeDto.from(badgeRepo.update(b));
    }

    @Transactional
    public void delete(UUID id) {
        var b = badgeRepo.findById(id).orElseThrow(
                () -> new NoSuchElementException("Badge introuvable : " + id));
        badgeRepo.delete(b);   // membre_badge cascade
    }

    // ── Badges « par nature » (générés/supprimés avec la nature) ──────────────

    /**
     * Crée les badges de paliers (1/10/50/100) pour une nature. Idempotent : un palier
     * dont le code existe déjà est ignoré. Code = {@code NAT_<code>_<seuil>} (= le seed V52).
     */
    @Transactional
    public void createForNature(SpNatureIntervention nature) {
        String icone = (nature.getIcone() != null && !nature.getIcone().isBlank()) ? nature.getIcone() : "🔥";
        for (int[] palier : NATURE_PALIERS) {
            int seuil = palier[0], xp = palier[1];
            String code = "NAT_" + nature.getCode() + "_" + seuil;
            if (badgeRepo.existsByCode(code)) continue;
            var b = new SpBadge(code, nature.getLabel() + " ×" + seuil, BadgeCondition.INTER_NATURE_COUNT, seuil);
            b.setIcone(icone);
            b.setNature(nature);
            b.setXpReward(xp);
            b.setPosition((int) badgeRepo.count());
            badgeRepo.save(b);
        }
    }

    /** Supprime tous les badges rattachés à une nature (avant de supprimer la nature). */
    @Transactional
    public void deleteForNature(UUID natureId) {
        for (var b : badgeRepo.findByNatureId(natureId)) {
            badgeRepo.delete(b);   // membre_badge cascade
        }
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var b = badgeRepo.findById(id).orElseThrow(
                    () -> new NoSuchElementException("Badge introuvable : " + id));
            b.setPosition(i);
            badgeRepo.update(b);
        }
    }

    // ── Badges d'un membre ──────────────────────────────────────────────────

    @Transactional
    public List<SpMembreBadgeDto> listForMembre(UUID membreId) {
        return membreBadgeRepo.findByMembreId(membreId).stream()
                .map(SpMembreBadgeDto::from)
                .toList();
    }
}
