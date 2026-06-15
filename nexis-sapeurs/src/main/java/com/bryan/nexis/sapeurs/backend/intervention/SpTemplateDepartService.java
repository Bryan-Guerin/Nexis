package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpTemplateDepartDto;
import com.bryan.nexis.sapeurs.datamodel.SpTemplateDepart;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpTemplateDepartRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Lots de départ : types de véhicule à engager par nature d'intervention. */
@Singleton
public class SpTemplateDepartService {

    private final SpTemplateDepartRepository     repo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpVehiculeTypeRepository        typeRepo;

    public SpTemplateDepartService(SpTemplateDepartRepository repo, SpNatureInterventionRepository natureRepo,
                                   SpVehiculeTypeRepository typeRepo) {
        this.repo       = repo;
        this.natureRepo = natureRepo;
        this.typeRepo   = typeRepo;
    }

    @Transactional
    public List<SpTemplateDepartDto> listByNature(UUID natureId) {
        return repo.findByNatureIdOrderByPosition(natureId).stream().map(SpTemplateDepartDto::from).toList();
    }

    @Transactional
    public SpTemplateDepartDto add(UUID natureId, UUID typeId, int quantite) {
        var nature = natureRepo.findById(natureId)
                .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + natureId));
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule introuvable : " + typeId));
        var ligne = new SpTemplateDepart(nature, type, Math.max(1, quantite));
        ligne.setPosition(repo.findByNatureIdOrderByPosition(natureId).size());
        return SpTemplateDepartDto.from(repo.save(ligne));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }
}
