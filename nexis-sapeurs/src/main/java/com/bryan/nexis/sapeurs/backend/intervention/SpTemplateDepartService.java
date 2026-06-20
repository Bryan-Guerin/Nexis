package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpLotProposeLigneDto;
import com.bryan.nexis.sapeurs.backend.dto.SpTemplateDepartDto;
import com.bryan.nexis.sapeurs.datamodel.CibleQuestion;
import com.bryan.nexis.sapeurs.datamodel.DeclencheurFlag;
import com.bryan.nexis.sapeurs.datamodel.SpIcone;
import com.bryan.nexis.sapeurs.datamodel.SpTemplateDepart;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import com.bryan.nexis.sapeurs.datarepository.SpIconeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpQuestionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpTemplateDepartRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

/** Lots de départ : types de véhicule à engager par nature d'intervention. */
@Singleton
public class SpTemplateDepartService {

    private final SpTemplateDepartRepository     repo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpVehiculeTypeRepository        typeRepo;
    private final SpIconeRepository               iconeRepo;
    private final SpQuestionRepository            questionRepo;

    public SpTemplateDepartService(SpTemplateDepartRepository repo, SpNatureInterventionRepository natureRepo,
                                   SpVehiculeTypeRepository typeRepo, SpIconeRepository iconeRepo,
                                   SpQuestionRepository questionRepo) {
        this.repo         = repo;
        this.natureRepo   = natureRepo;
        this.typeRepo     = typeRepo;
        this.iconeRepo    = iconeRepo;
        this.questionRepo = questionRepo;
    }

    /**
     * Lot recommandé à la création : union des lots des déclencheurs actifs (nature choisie + flags),
     * fusionnés <b>au max par type</b> (un besoin, pas un cumul), plus les recos <b>par unité</b>
     * (ex. question NB_VICTIMES → 1 VSAV par victime). Indicatif : le dispatcher édite.
     */
    @Transactional
    public List<SpLotProposeLigneDto> proposerLot(UUID natureId, List<DeclencheurFlag> flags, Integer nbVictimes) {
        Map<UUID, SpVehiculeType> types = new HashMap<>();
        Map<UUID, Integer> quantites = new HashMap<>();
        // Lots par déclencheur (nature + flags) → max par type.
        Stream.concat(
                natureId != null ? repo.findByNatureIdOrderByPosition(natureId).stream() : Stream.empty(),
                flags == null ? Stream.<SpTemplateDepart>empty()
                        : flags.stream().distinct().flatMap(f -> repo.findByDeclencheurFlagOrderByPosition(f).stream())
        ).forEach(t -> {
            types.putIfAbsent(t.getVehiculeType().getId(), t.getVehiculeType());
            quantites.merge(t.getVehiculeType().getId(), t.getQuantite(), Integer::max);
        });
        // Recos par unité (NB_VICTIMES → N véhicules), fusionnées au max elles aussi.
        int n = nbVictimes != null ? nbVictimes : 0;
        if (n > 0) {
            for (var q : questionRepo.findAll()) {
                if (q.isRecoParUnite() && q.getRecoVehiculeType() != null && q.getCible() == CibleQuestion.NB_VICTIMES) {
                    var t = q.getRecoVehiculeType();
                    types.putIfAbsent(t.getId(), t);
                    quantites.merge(t.getId(), n, Integer::max);
                }
            }
        }
        return quantites.entrySet().stream()
                .map(e -> { var t = types.get(e.getKey());
                            return new SpLotProposeLigneDto(t.getId(), t.getCode(), t.getLabel(), e.getValue()); })
                .sorted(Comparator.comparing(SpLotProposeLigneDto::typeCode))
                .toList();
    }

    private SpIcone resolveIcone(UUID iconeImageId) {
        return iconeImageId != null ? iconeRepo.findById(iconeImageId).orElse(null) : null;
    }

    @Transactional
    public List<SpTemplateDepartDto> listByNature(UUID natureId) {
        return repo.findByNatureIdOrderByPosition(natureId).stream().map(SpTemplateDepartDto::from).toList();
    }

    @Transactional
    public List<SpTemplateDepartDto> listByFlag(DeclencheurFlag flag) {
        return repo.findByDeclencheurFlagOrderByPosition(flag).stream().map(SpTemplateDepartDto::from).toList();
    }

    @Transactional
    public SpTemplateDepartDto addFlag(DeclencheurFlag flag, UUID typeId, int quantite, String description, UUID iconeImageId) {
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule introuvable : " + typeId));
        var ligne = new SpTemplateDepart(flag, type, Math.max(1, quantite));
        ligne.setDescription(description == null || description.isBlank() ? null : description.trim());
        ligne.setIconeImage(resolveIcone(iconeImageId));
        ligne.setPosition(repo.findByDeclencheurFlagOrderByPosition(flag).size());
        return SpTemplateDepartDto.from(repo.save(ligne));
    }

    @Transactional
    public SpTemplateDepartDto add(UUID natureId, UUID typeId, int quantite, String description, UUID iconeImageId) {
        var nature = natureRepo.findById(natureId)
                .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + natureId));
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule introuvable : " + typeId));
        var ligne = new SpTemplateDepart(nature, type, Math.max(1, quantite));
        ligne.setDescription(description == null || description.isBlank() ? null : description.trim());
        ligne.setIconeImage(resolveIcone(iconeImageId));
        ligne.setPosition(repo.findByNatureIdOrderByPosition(natureId).size());
        return SpTemplateDepartDto.from(repo.save(ligne));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }
}
