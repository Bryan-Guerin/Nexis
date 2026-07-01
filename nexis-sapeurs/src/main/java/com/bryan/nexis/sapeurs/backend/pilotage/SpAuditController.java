package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.sapeurs.datamodel.SpCri;
import com.bryan.nexis.sapeurs.datarepository.SpCriRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Audit opérationnel SP (dispatch / officiers) : affectations forcées (bypass qualification)
 * et validations de CRI. Les données existent déjà en base — cet écran les rend consultables.
 */
@Controller("/api/sp/audit")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP_DISPATCH")
public class SpAuditController {

    private static final int MAX = 200;

    private final SpVehiculeAffectationRepository affectationRepo;
    private final SpCriRepository criRepo;

    public SpAuditController(SpVehiculeAffectationRepository affectationRepo, SpCriRepository criRepo) {
        this.affectationRepo = affectationRepo;
        this.criRepo         = criRepo;
    }

    @Get
    @Transactional
    public SpAuditDto audit() {
        var forcages = affectationRepo.findByForceeTrueOrderByForceLeDesc().stream()
                .limit(MAX)
                .map(a -> new ForcageDto(
                        a.getMembre().getMatricule(),
                        a.getMembre().getNomComplet(),
                        a.getVehicule().getLibelle(),
                        a.getPoste() != null ? a.getPoste().getFonction().getCode() : null,
                        a.getForcePar(), a.getForceLe(), a.getFin()))
                .toList();
        var validations = criRepo.findByStatutOrderByValideLeDesc(SpCri.VALIDE).stream()
                .limit(MAX)
                .map(c -> new ValidationCriDto(
                        c.getIntervention().getCode(),
                        c.getVehicule().getLibelle(),
                        c.getSoumisPar(), c.getSoumisLe(),
                        c.getValidePar(), c.getValideLe()))
                .toList();
        return new SpAuditDto(forcages, validations);
    }

    @Serdeable
    public record SpAuditDto(List<ForcageDto> forcages, List<ValidationCriDto> validations) {}

    /** Affectation forcée : qui a été placé où, par qui, quand (fin = null si encore active). */
    @Serdeable
    public record ForcageDto(String matricule, String membre, String vehicule, String fonction,
                             String forcePar, Instant forceLe, Instant fin) {}

    @Serdeable
    public record ValidationCriDto(String interventionCode, String vehicule,
                                   String soumisPar, Instant soumisLe,
                                   String validePar, Instant valideLe) {}
}
