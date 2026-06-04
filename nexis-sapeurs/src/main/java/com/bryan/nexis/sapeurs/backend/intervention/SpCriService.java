package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpCriDto;
import com.bryan.nexis.sapeurs.datamodel.SpCri;
import com.bryan.nexis.sapeurs.datarepository.SpCriRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpCriService {

    private final SpCriRepository                 criRepo;
    private final SpInterventionRepository         interventionRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SecurityService                  securityService;

    public SpCriService(SpCriRepository criRepo, SpInterventionRepository interventionRepo,
                        SpVehiculeAffectationRepository affectationRepo, SecurityService securityService) {
        this.criRepo          = criRepo;
        this.interventionRepo = interventionRepo;
        this.affectationRepo  = affectationRepo;
        this.securityService  = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    /** Liste les CRI d'une intervention, en créant ceux manquants (1 par engin courant). */
    @Transactional
    public List<SpCriDto> listForIntervention(UUID interventionId) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        for (var engin : inter.getEngins()) {
            if (!criRepo.existsByInterventionIdAndVehiculeId(interventionId, engin.getId())) {
                criRepo.save(new SpCri(inter, engin));
            }
        }
        return criRepo.findByInterventionId(interventionId).stream()
                .map(SpCriDto::from)
                .sorted(Comparator.comparing(SpCriDto::vehiculeLibelle))
                .toList();
    }

    @Transactional
    public SpCriDto update(UUID criId, String contenu) {
        var cri = exigerModifiable(criId);
        cri.setContenu(contenu);
        return SpCriDto.from(criRepo.update(cri));
    }

    @Transactional
    public SpCriDto soumettre(UUID criId) {
        var cri = exigerModifiable(criId);
        cri.setStatut(SpCri.SOUMIS);
        cri.setSoumisPar(actor());
        cri.setSoumisLe(Instant.now());
        return SpCriDto.from(criRepo.update(cri));
    }

    /** Validation (admin SP — sécurisé au niveau du controller). */
    @Transactional
    public SpCriDto valider(UUID criId) {
        var cri = criRepo.findById(criId)
                .orElseThrow(() -> new NoSuchElementException("CRI introuvable : " + criId));
        if (!SpCri.SOUMIS.equals(cri.getStatut())) {
            throw new IllegalStateException("Le CRI doit être soumis avant d'être validé.");
        }
        cri.setStatut(SpCri.VALIDE);
        cri.setValidePar(actor());
        cri.setValideLe(Instant.now());
        return SpCriDto.from(criRepo.update(cri));
    }

    /** Récupère un CRI modifiable par l'appelant (équipier du véhicule ou admin), non encore validé. */
    private SpCri exigerModifiable(UUID criId) {
        var cri = criRepo.findById(criId)
                .orElseThrow(() -> new NoSuchElementException("CRI introuvable : " + criId));
        if (SpCri.VALIDE.equals(cri.getStatut())) {
            throw new IllegalStateException("CRI déjà validé : non modifiable.");
        }
        if (!securityService.hasRole("ROLE_ADMIN_SP") && !estEquipier(cri.getVehicule().getId(), actor())) {
            throw new IllegalStateException("Réservé à l'équipage du véhicule.");
        }
        return cri;
    }

    private boolean estEquipier(UUID vehiculeId, String username) {
        return username != null && affectationRepo.findByVehiculeIdAndFinIsNull(vehiculeId).stream()
                .anyMatch(a -> username.equals(a.getMembre().getUser().getUsername()));
    }
}
