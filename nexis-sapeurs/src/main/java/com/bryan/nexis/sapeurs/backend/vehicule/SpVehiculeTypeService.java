package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeTypeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeTypePosteDto;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.*;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpVehiculeTypeService {

    private final SpVehiculeTypeRepository       typeRepo;
    private final SpVehiculeTypePosteRepository  posteRepo;
    private final SpFonctionRepository           fonctionRepo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpVehiculeAffectationRepository affectationRepo;

    public SpVehiculeTypeService(SpVehiculeTypeRepository typeRepo,
                                  SpVehiculeTypePosteRepository posteRepo,
                                  SpFonctionRepository fonctionRepo,
                                  SpNatureInterventionRepository natureRepo,
                                  SpVehiculeAffectationRepository affectationRepo) {
        this.typeRepo     = typeRepo;
        this.posteRepo    = posteRepo;
        this.fonctionRepo = fonctionRepo;
        this.natureRepo   = natureRepo;
        this.affectationRepo = affectationRepo;
    }

    @Transactional
    public List<SpVehiculeTypeDto> listAll() {
        return typeRepo.findAll().stream().map(SpVehiculeTypeDto::from).toList();
    }

    @Transactional
    public SpVehiculeTypeDto create(String code, String label) {
        return SpVehiculeTypeDto.from(typeRepo.save(new SpVehiculeType(code, label)));
    }

    @Transactional
    public List<SpVehiculeTypePosteDto> listPostes(UUID typeId) {
        return posteRepo.findByVehiculeTypeIdOrderByOrdreAsc(typeId).stream().map(SpVehiculeTypePosteDto::from).toList();
    }

    @Transactional
    public SpVehiculeTypePosteDto addPoste(UUID typeId, UUID fonctionId, short nbPlaces, boolean obligatoire) {
        var type     = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule SP introuvable : " + typeId));
        var fonction = fonctionRepo.findById(fonctionId)
                .orElseThrow(() -> new NoSuchElementException("Fonction SP introuvable : " + fonctionId));
        var poste = new SpVehiculeTypePoste(type, fonction, nbPlaces, obligatoire);
        poste.setOrdre(posteRepo.findByVehiculeTypeId(typeId).size());   // ajouté en fin de liste
        return SpVehiculeTypePosteDto.from(posteRepo.save(poste));
    }

    /** Réordonne les postes d'un type selon la liste d'identifiants fournie. */
    @Transactional
    public void setPostesOrder(UUID typeId, List<UUID> posteIds) {
        if (posteIds == null) return;
        var postes = posteRepo.findByVehiculeTypeId(typeId).stream()
                .collect(java.util.stream.Collectors.toMap(SpVehiculeTypePoste::getId, p -> p));
        int ordre = 0;
        for (var id : posteIds) {
            var p = postes.get(id);
            if (p == null) throw new IllegalArgumentException("Poste " + id + " absent du type " + typeId);
            p.setOrdre(ordre++);
            posteRepo.update(p);
        }
    }

    /** Bascule le caractère obligatoire d'un poste. */
    @Transactional
    public SpVehiculeTypePosteDto toggleObligatoire(UUID posteId) {
        var poste = posteRepo.findById(posteId)
                .orElseThrow(() -> new NoSuchElementException("Poste introuvable : " + posteId));
        poste.setObligatoire(!poste.isObligatoire());
        return SpVehiculeTypePosteDto.from(posteRepo.update(poste));
    }

    /**
     * Supprime un poste. Bloqué si le poste est occupé par une affectation active
     * (un équipier est actuellement à ce poste). Les affectations historiques (closes)
     * sont, elles, « détachées » (poste_id → NULL) par la base via la FK ON DELETE SET NULL :
     * l'historique des affectations est conservé.
     *
     * @throws IllegalStateException si une affectation active référence le poste — le message
     *         indique la fonction et les engins concernés.
     */
    @Transactional
    public void deletePoste(UUID posteId) {
        var actives = affectationRepo.findByPosteIdAndFinIsNull(posteId);
        if (!actives.isEmpty()) {
            var fonction = posteRepo.findById(posteId)
                    .map(p -> p.getFonction().getLabel())
                    .orElse("?");
            var engins = actives.stream()
                    .map(a -> a.getVehicule().getLibelle())
                    .distinct()
                    .sorted()
                    .toList();
            throw new IllegalStateException(
                    "Impossible de supprimer le poste « " + fonction + " » : il est occupé sur "
                    + String.join(", ", engins) + ". Libérez l'équipage avant de supprimer le poste.");
        }
        posteRepo.deleteById(posteId);
    }

    /** Définit les natures d'intervention associées à un type (tags). */
    @Transactional
    public SpVehiculeTypeDto setNatures(UUID typeId, List<UUID> natureIds) {
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule SP introuvable : " + typeId));
        var natures = new HashSet<SpNatureIntervention>();
        if (natureIds != null) {
            for (var nid : natureIds) {
                natures.add(natureRepo.findById(nid)
                        .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + nid)));
            }
        }
        type.getNatures().clear();
        type.getNatures().addAll(natures);
        reconcilierPrincipale(type);
        return SpVehiculeTypeDto.from(typeRepo.update(type));
    }

    /** Étoile une nature comme principale (catégorie dispatch). {@code natureId} null = retire l'étoile. */
    @Transactional
    public SpVehiculeTypeDto setNaturePrincipale(UUID typeId, UUID natureId) {
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule SP introuvable : " + typeId));
        if (natureId == null) {
            type.setNaturePrincipale(null);
        } else {
            var nature = type.getNatures().stream().filter(n -> n.getId().equals(natureId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La nature principale doit faire partie des natures du type."));
            type.setNaturePrincipale(nature);
        }
        return SpVehiculeTypeDto.from(typeRepo.update(type));
    }

    /**
     * Maintient la cohérence de la nature principale après modification des natures :
     * une seule nature → promue automatiquement ; principale retirée des natures → effacée.
     */
    private void reconcilierPrincipale(SpVehiculeType type) {
        var natures = type.getNatures();
        if (natures.size() == 1) {
            type.setNaturePrincipale(natures.iterator().next());
        } else if (type.getNaturePrincipale() != null
                && natures.stream().noneMatch(n -> n.getId().equals(type.getNaturePrincipale().getId()))) {
            type.setNaturePrincipale(null);
        }
    }
}
