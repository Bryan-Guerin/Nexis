package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpBadgeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpMembreBadgeDto;
import com.bryan.nexis.sapeurs.datamodel.BadgeCondition;
import com.bryan.nexis.sapeurs.datamodel.SpBadge;
import com.bryan.nexis.sapeurs.datamodel.TypeFonction;
import com.bryan.nexis.sapeurs.datarepository.SpBadgeRepository;
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

    private final SpBadgeRepository              badgeRepo;
    private final SpMembreBadgeRepository        membreBadgeRepo;
    private final SpNatureInterventionRepository natureRepo;

    public SpBadgeService(SpBadgeRepository badgeRepo,
                          SpMembreBadgeRepository membreBadgeRepo,
                          SpNatureInterventionRepository natureRepo) {
        this.badgeRepo       = badgeRepo;
        this.membreBadgeRepo = membreBadgeRepo;
        this.natureRepo      = natureRepo;
    }

    // ── Catalogue ────────────────────────────────────────────────────────────

    @Transactional
    public List<SpBadgeDto> listCatalog() {
        return badgeRepo.findAll(BY_POSITION).stream().map(SpBadgeDto::from).toList();
    }

    @Transactional
    public SpBadgeDto create(String code, String label, String icone, String description,
                             String typeCondition, UUID natureId, String typeFonction, int seuil, int xpReward) {
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
        if (type == BadgeCondition.QUALIF_TYPE_COUNT) {
            if (typeFonction == null) throw new IllegalArgumentException("typeFonction requis pour QUALIF_TYPE_COUNT");
            b.setTypeFonction(TypeFonction.valueOf(typeFonction));
        }
        return SpBadgeDto.from(badgeRepo.save(b));
    }

    @Transactional
    public SpBadgeDto update(UUID id, String label, String icone, String description,
                             String typeCondition, UUID natureId, String typeFonction, Integer seuil, Integer xpReward) {
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
        if (b.getTypeCondition() == BadgeCondition.QUALIF_TYPE_COUNT) {
            if (typeFonction == null) throw new IllegalArgumentException("typeFonction requis pour QUALIF_TYPE_COUNT");
            b.setTypeFonction(TypeFonction.valueOf(typeFonction));
        } else {
            b.setTypeFonction(null);
        }
        return SpBadgeDto.from(badgeRepo.update(b));
    }

    @Transactional
    public void delete(UUID id) {
        var b = badgeRepo.findById(id).orElseThrow(
                () -> new NoSuchElementException("Badge introuvable : " + id));
        badgeRepo.delete(b);   // membre_badge cascade
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
