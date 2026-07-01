package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.datarepository.RefUserRepository;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpCriDto;
import com.bryan.nexis.sapeurs.datamodel.SpCri;
import com.bryan.nexis.sapeurs.datarepository.SpCriRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Map;

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
    private final SpMembreRepository               membreRepo;
    private final RefUserRepository                userRepo;
    private final SecurityService                  securityService;
    private final ApplicationEventPublisher<RealtimeEvent> events;

    public SpCriService(SpCriRepository criRepo, SpInterventionRepository interventionRepo,
                        SpVehiculeAffectationRepository affectationRepo, SpMembreRepository membreRepo,
                        RefUserRepository userRepo, SecurityService securityService,
                        ApplicationEventPublisher<RealtimeEvent> events) {
        this.criRepo          = criRepo;
        this.interventionRepo = interventionRepo;
        this.affectationRepo  = affectationRepo;
        this.membreRepo       = membreRepo;
        this.userRepo         = userRepo;
        this.securityService  = securityService;
        this.events           = events;
    }

    private String actor() { return securityService.username().orElse(null); }

    /** Liste les CRI d'une intervention, en créant ceux manquants (1 par engin courant). */
    @Transactional
    public List<SpCriDto> listForIntervention(UUID interventionId) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        creerCrisManquants(inter);
        return criRepo.findByInterventionId(interventionId).stream()
                .map(SpCriDto::from)
                .sorted(Comparator.comparing(SpCriDto::vehiculeLibelle))
                .toList();
    }

    /** Crée 1 CRI par engin courant si pas encore présent. À appeler au moment de la clôture
     *  (avant détachement des engins) pour figer les CRIs requis par équipage. */
    @Transactional
    public void creerCrisManquants(com.bryan.nexis.sapeurs.datamodel.SpIntervention inter) {
        for (var engin : inter.getEngins()) {
            if (!criRepo.existsByInterventionIdAndVehiculeId(inter.getId(), engin.getId())) {
                criRepo.save(new SpCri(inter, engin));
            }
        }
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
        var dto = SpCriDto.from(criRepo.update(cri));
        notifierMaj(cri);
        return dto;
    }

    /** Validation : admin SP, ou porteur d'un grade configuré comme « peut valider CRI » (sergent et +).
     *  Séparation des rôles : on ne valide pas son propre CRI (sauf admin SP). Quand le dernier CRI
     *  de l'intervention passe VALIDE, la clôture définitive est notée en main courante. */
    @Transactional
    public SpCriDto valider(UUID criId) {
        var cri = criRepo.findById(criId)
                .orElseThrow(() -> new NoSuchElementException("CRI introuvable : " + criId));
        if (!SpCri.SOUMIS.equals(cri.getStatut())) {
            throw new IllegalStateException("Le CRI doit être soumis avant d'être validé.");
        }
        if (!utilisateurPeutValider(actor())) {
            throw new IllegalStateException("Validation réservée aux grades autorisés (sergent et +) ou à un admin SP.");
        }
        if (actor() != null && actor().equals(cri.getSoumisPar()) && !securityService.hasRole("ROLE_ADMIN_SP")) {
            throw new IllegalStateException("Un CRI ne peut pas être validé par celui qui l'a soumis.");
        }
        cri.setStatut(SpCri.VALIDE);
        cri.setValidePar(actor());
        cri.setValideLe(Instant.now());
        var dto = SpCriDto.from(criRepo.update(cri));
        notifierMaj(cri);
        var inter = cri.getIntervention();
        boolean tousValides = criRepo.findByInterventionId(inter.getId()).stream()
                .allMatch(c -> SpCri.VALIDE.equals(c.getStatut()));
        if (tousValides) {
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.MAIN_COURANTE, "SP",
                    "Intervention close — tous les CRI validés",
                    Map.of("interventionId", inter.getId().toString()), actor()).withReference(inter.getCode()));
        }
        return dto;
    }

    /** Broadcast SP éphémère : un CRI a changé de statut → front recompte le badge "à valider". */
    private void notifierMaj(SpCri cri) {
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.CRI_MAJ, "SP", "CRI mis à jour",
                Map.of("interventionId", cri.getIntervention().getId().toString(),
                       "statut", cri.getStatut()),
                actor()).ephemere());
    }

    /** L'utilisateur courant peut-il valider des CRI ? (admin SP, ou grade autorisé). */
    @Transactional
    public boolean peutValiderCri() {
        return utilisateurPeutValider(actor());
    }

    /** Nombre de CRI en attente de validation, pour celui qui peut valider (0 sinon). */
    @Transactional
    public long countAValider() {
        if (!utilisateurPeutValider(actor())) return 0;
        return criRepo.countByStatut(SpCri.SOUMIS);
    }

    private boolean utilisateurPeutValider(String username) {
        if (securityService.hasRole("ROLE_ADMIN_SP")) return true;
        if (username == null) return false;
        return userRepo.findByUsername(username)
                .flatMap(u -> membreRepo.findByUserId(u.getId()))
                .map(m -> m.getGrade() != null && m.getGrade().isPeutValiderCri())
                .orElse(false);
    }

    /** Récupère un CRI modifiable par l'appelant (équipier du véhicule ou admin), non encore validé. */
    private SpCri exigerModifiable(UUID criId) {
        var cri = criRepo.findById(criId)
                .orElseThrow(() -> new NoSuchElementException("CRI introuvable : " + criId));
        if (SpCri.VALIDE.equals(cri.getStatut())) {
            throw new IllegalStateException("CRI déjà validé : non modifiable.");
        }
        if (!securityService.hasRole("ROLE_ADMIN_SP")
                && !estEquipier(cri.getVehicule().getId(), actor())
                && !estEquipierHistorise(cri.getIntervention(), actor())) {
            throw new IllegalStateException("Réservé à l'équipage du véhicule.");
        }
        return cri;
    }

    private boolean estEquipier(UUID vehiculeId, String username) {
        return username != null && affectationRepo.findByVehiculeIdAndFinIsNull(vehiculeId).stream()
                .anyMatch(a -> username.equals(a.getMembre().getUser().getUsername()));
    }

    /**
     * Équipier présent dans l'équipage FIGÉ à la clôture (snapshot). Permet de saisir / soumettre
     * son CRI après la fin de garde ou une désaffectation : l'affectation véhicule active n'existe
     * plus, mais la participation à l'intervention est historisée.
     */
    private boolean estEquipierHistorise(com.bryan.nexis.sapeurs.datamodel.SpIntervention inter, String username) {
        if (username == null || inter == null) return false;
        var membreId = userRepo.findByUsername(username)
                .flatMap(u -> membreRepo.findByUserId(u.getId()))
                .map(m -> m.getId()).orElse(null);
        if (membreId == null) return false;
        return inter.getEnginsHisto().stream()
                .flatMap(e -> e.getEquipage().stream())
                .anyMatch(eq -> membreId.equals(eq.getMembreId()));
    }
}
