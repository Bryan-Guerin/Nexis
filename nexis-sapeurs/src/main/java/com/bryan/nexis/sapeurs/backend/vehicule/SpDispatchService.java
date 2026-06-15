package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto;
import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto.SpDispatchMembreDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeEtatDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeTypeDto;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.backend.intervention.SpInterventionService;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVerificationRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Singleton
public class SpDispatchService {

    private final SpVehiculeRepository             vehiculeRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SpVehiculeTypePosteRepository    posteRepo;
    private final SpVerificationRepository         verifRepo;
    private final SpInterventionService            interventionService;

    public SpDispatchService(SpVehiculeRepository vehiculeRepo, SpVehiculeAffectationRepository affectationRepo,
                             SpVehiculeTypePosteRepository posteRepo, SpVerificationRepository verifRepo,
                             SpInterventionService interventionService) {
        this.vehiculeRepo    = vehiculeRepo;
        this.affectationRepo = affectationRepo;
        this.posteRepo       = posteRepo;
        this.verifRepo       = verifRepo;
        this.interventionService = interventionService;
    }

    @Transactional
    public List<SpDispatchDto> listDispatch() {
        return vehiculeRepo.findAll().stream().map(v -> {
            var affectations = affectationRepo.findByVehiculeIdAndFinIsNull(v.getId());
            var equipe = affectations.stream()
                    // Ordre d'affichage = catégorie de la FONCTION (Chef d'agrès → Conducteur →
                    // Chef d'équipe → Équipier), puis ordre du poste dans le type pour départager.
                    .sorted(java.util.Comparator
                            .comparingInt((SpVehiculeAffectation a) -> a.getPoste() != null ? a.getPoste().getFonction().getTypeFonction().ordinal() : Integer.MAX_VALUE)
                            .thenComparingInt(a -> a.getPoste() != null ? a.getPoste().getOrdre() : Integer.MAX_VALUE))
                    .map(a -> new SpDispatchMembreDto(
                            a.getMembre().getId(),
                            a.getMembre().getMatricule(),
                            a.getMembre().getUser().getUsername(),
                            a.getMembre().getGrade().getCode(),
                            a.getMembre().getGrade().getLabel(),
                            a.getMembre().getNomComplet(),
                            a.getPoste() != null ? a.getPoste().getId() : null,
                            a.getPoste() != null ? a.getPoste().getFonction().getLabel() : null
                    ))
                    .toList();
            var manquants = postesManquants(v, affectations);
            boolean arme = estArme(v, affectations, manquants);
            return new SpDispatchDto(
                    v.getId(),
                    v.getLibelle(),
                    v.getImmatriculation(),
                    SpVehiculeTypeDto.from(v.getType()),
                    SpVehiculeEtatDto.from(v.getEtat()),
                    SpVehiculeStatutDto.from(v.getStatut()),
                    arme,
                    equipe,
                    manquants,
                    verifRepo.findDerniereVerif(v.getId()).map(java.time.Instant::toString).orElse(null)
            );
        }).toList();
    }

    /** Postes obligatoires non couverts (par un équipier non occupé ailleurs), dans l'ordre du type. */
    private List<String> postesManquants(SpVehicule v, List<SpVehiculeAffectation> crew) {
        Set<UUID> occupes = interventionService.membresOccupesSurAutreIntervention(v.getId());
        return posteRepo.findByVehiculeTypeIdOrderByOrdreAsc(v.getType().getId()).stream()
                .filter(SpVehiculeTypePoste::isObligatoire)
                .filter(p -> crew.stream().noneMatch(a ->
                        a.getPoste() != null && a.getPoste().getId().equals(p.getId())
                                && !occupes.contains(a.getMembre().getId())))
                .map(p -> p.getFonction().getLabel())
                .toList();
    }

    private boolean estArme(SpVehicule v, List<SpVehiculeAffectation> crew, List<String> manquants) {
        boolean aOblig = posteRepo.findByVehiculeTypeId(v.getType().getId()).stream()
                .anyMatch(SpVehiculeTypePoste::isObligatoire);
        if (!aOblig) {
            Set<UUID> occupes = interventionService.membresOccupesSurAutreIntervention(v.getId());
            return crew.stream().anyMatch(a -> !occupes.contains(a.getMembre().getId()));
        }
        return manquants.isEmpty();
    }
}
