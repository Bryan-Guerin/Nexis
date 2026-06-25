package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.*;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.bryan.nexis.sapeurs.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Règles d'affectation du dispatch : double affectation, garde, qualification, capacité,
 * sweep de fin de garde (avec garde-fou intervention) et désaffectation globale.
 */
class SpVehiculeAffectationServiceTest {

    private SpVehiculeAffectationRepository affectationRepo;
    private SpVehiculeRepository vehiculeRepo;
    private SpMembreRepository membreRepo;
    private SpVehiculeTypePosteRepository posteRepo;
    private SpInterventionRepository interventionRepo;
    private SpPlanningService planningService;
    private ApplicationEventPublisher<RealtimeEvent> events;
    private SecurityService securityService;

    private SpVehiculeAffectationService service;

    // Fixtures de base : un FPT avec un poste CA obligatoire, un membre qualifié de garde.
    private SpVehiculeType typeFpt;
    private SpVehiculeTypePoste posteCa;
    private SpVehicule fpt;
    private SpVehicule vtu;
    private SpMembre jean;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        affectationRepo  = mock(SpVehiculeAffectationRepository.class);
        vehiculeRepo     = mock(SpVehiculeRepository.class);
        membreRepo       = mock(SpMembreRepository.class);
        posteRepo        = mock(SpVehiculeTypePosteRepository.class);
        interventionRepo = mock(SpInterventionRepository.class);
        planningService  = mock(SpPlanningService.class);
        events           = mock(ApplicationEventPublisher.class);
        securityService = mock(SecurityService.class);
        when(securityService.username()).thenReturn(Optional.of("codis"));

        service = new SpVehiculeAffectationService(affectationRepo, vehiculeRepo, membreRepo,
                posteRepo, interventionRepo, planningService, events, securityService);

        typeFpt = type("FPT");
        var etatDispo   = etat("DISPONIBLE");
        var statutDispo = statut("DISPONIBLE", etatDispo);
        fpt = vehicule(typeFpt, "FPT 1", etatDispo, statutDispo);
        vtu = vehicule(type("VTU"), "VTU 1", etatDispo, statutDispo);

        var fonctionCa = fonction("CATE");
        posteCa = poste(typeFpt, fonctionCa, 1, true);
        jean = membre("jean", 352);
        qualifier(jean, fonctionCa);

        when(vehiculeRepo.findById(fpt.getId())).thenReturn(Optional.of(fpt));
        when(membreRepo.findById(jean.getId())).thenReturn(Optional.of(jean));
        when(posteRepo.findById(posteCa.getId())).thenReturn(Optional.of(posteCa));
        when(planningService.estDeGarde(jean.getId())).thenReturn(true);
        when(affectationRepo.findByMembreIdAndFinIsNull(jean.getId())).thenReturn(List.of());
        when(affectationRepo.countByVehiculeIdAndPosteIdAndFinIsNull(fpt.getId(), posteCa.getId())).thenReturn(0L);
        when(affectationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(affectationRepo.update(any(SpVehiculeAffectation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of());
    }

    // ── affecter ──────────────────────────────────────────────────────────────

    @Test
    void affectationNominale_reussit() {
        var dto = service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now());

        assertThat(dto.vehiculeId()).isEqualTo(fpt.getId());
        assertThat(dto.membreId()).isEqualTo(jean.getId());
        verify(affectationRepo).save(any());
    }

    @Test
    void affectationRefusee_posteDunAutreType() {
        var posteAutreType = poste(type("VSAV"), fonction("EQ SAP"), 1, true);
        when(posteRepo.findById(posteAutreType.getId())).thenReturn(Optional.of(posteAutreType));

        assertThatThrownBy(() -> service.affecter(fpt.getId(), jean.getId(), posteAutreType.getId(), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("n'appartient pas au type");
    }

    @Test
    void affectationRefusee_dejaSurCeVehicule() {
        when(affectationRepo.findByMembreIdAndFinIsNull(jean.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteCa)));

        assertThatThrownBy(() -> service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà engagé sur ce véhicule");
    }

    @Test
    void affectationAutorisee_surUnSecondVehicule() {
        // Jean est déjà sur le VTU : la double affectation FPT + VTU doit passer.
        when(affectationRepo.findByMembreIdAndFinIsNull(jean.getId()))
                .thenReturn(List.of(affectation(vtu, jean, null)));

        var dto = service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now());

        assertThat(dto.vehiculeId()).isEqualTo(fpt.getId());
    }

    @Test
    void affectationRefusee_membrePasDeGarde() {
        when(planningService.estDeGarde(jean.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("n'est pas de garde");
    }

    @Test
    void affectationRefusee_membreNonQualifie() {
        var paul = membre("paul", 353);   // aucune qualification
        when(membreRepo.findById(paul.getId())).thenReturn(Optional.of(paul));
        when(planningService.estDeGarde(paul.getId())).thenReturn(true);
        when(affectationRepo.findByMembreIdAndFinIsNull(paul.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.affecter(fpt.getId(), paul.getId(), posteCa.getId(), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("n'est pas qualifié");
    }

    @Test
    void affectationForcee_bypassQualif_siDispatch() {
        var paul = membre("paul", 353);   // aucune qualification
        when(membreRepo.findById(paul.getId())).thenReturn(Optional.of(paul));
        when(planningService.estDeGarde(paul.getId())).thenReturn(true);
        when(affectationRepo.findByMembreIdAndFinIsNull(paul.getId())).thenReturn(List.of());
        when(securityService.hasRole("ROLE_SP_DISPATCH")).thenReturn(true);
        when(affectationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.affecter(fpt.getId(), paul.getId(), posteCa.getId(), Instant.now(), true);
        assertThat(dto.forcee()).isTrue();
        assertThat(dto.forcePar()).isEqualTo("codis");
    }

    @Test
    void affectationForcee_refusee_siPasDispatch() {
        var paul = membre("paul", 353);
        when(membreRepo.findById(paul.getId())).thenReturn(Optional.of(paul));
        when(planningService.estDeGarde(paul.getId())).thenReturn(true);
        when(affectationRepo.findByMembreIdAndFinIsNull(paul.getId())).thenReturn(List.of());
        when(securityService.hasRole("ROLE_SP_DISPATCH")).thenReturn(false);
        when(securityService.hasRole("ROLE_ADMIN_SP")).thenReturn(false);

        assertThatThrownBy(() -> service.affecter(fpt.getId(), paul.getId(), posteCa.getId(), Instant.now(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forcée réservée au dispatch");
    }

    @Test
    void affectationForcee_normale_siDejaQualifie_pasDeForcage() {
        // jean est qualifié → forcer=true ne doit PAS marquer l'affectation comme forcée.
        when(securityService.hasRole("ROLE_SP_DISPATCH")).thenReturn(true);
        when(affectationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var dto = service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now(), true);
        assertThat(dto.forcee()).isFalse();
    }

    @Test
    void affectationRefusee_capaciteDuPosteAtteinte() {
        when(affectationRepo.countByVehiculeIdAndPosteIdAndFinIsNull(fpt.getId(), posteCa.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Capacité");
    }

    @Test
    void affecter_bipeLeNouvelEquipier_siVehiculeDejaEngageSurUneInterventionOuverte() {
        // FPT déjà engagé sur une intervention ouverte → l'équipier affecté APRÈS le départ
        // (armement auto / retardataire) reçoit le bip de départ.
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(intervention("Feu", fpt)));

        service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now());

        var captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(events, atLeastOnce()).publishEvent(captor.capture());
        var bip = captor.getAllValues().stream().filter(e -> "BIP".equals(e.getType())).findFirst();
        assertThat(bip).isPresent();
        assertThat(bip.get().getRecipients()).containsExactly("jean");
    }

    @Test
    void affecter_neBipePas_siVehiculeNonEngage() {
        // Pré-armement (véhicule disponible) : pas de bip, seulement l'event d'affectation.
        service.affecter(fpt.getId(), jean.getId(), posteCa.getId(), Instant.now());

        var captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(events, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(e -> "BIP".equals(e.getType()));
    }

    // ── Sweep de fin de garde ─────────────────────────────────────────────────

    @Test
    void sweepIgnoreLesMembresEncoreDeGarde() {
        var aff = affectation(fpt, jean, posteCa);
        when(affectationRepo.findByFinIsNull()).thenReturn(List.of(aff));
        when(planningService.estDeGarde(jean.getId())).thenReturn(true);

        int closed = service.cloturerExpirees();

        assertThat(closed).isZero();
        assertThat(aff.getFin()).isNull();
    }

    @Test
    void sweepCloture_finDeGarde_horsIntervention() {
        var aff = affectation(fpt, jean, posteCa);
        when(affectationRepo.findByFinIsNull()).thenReturn(List.of(aff));
        when(planningService.estDeGarde(jean.getId())).thenReturn(false);

        int closed = service.cloturerExpirees();

        assertThat(closed).isEqualTo(1);
        assertThat(aff.getFin()).isNotNull();
    }

    @Test
    void sweepConserve_finDeGarde_enIntervention_etNotifieUneSeuleFois() {
        var aff = affectation(fpt, jean, posteCa);
        when(affectationRepo.findByFinIsNull()).thenReturn(List.of(aff));
        when(planningService.estDeGarde(jean.getId())).thenReturn(false);
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(intervention("Feu", fpt)));

        int closed = service.cloturerExpirees();
        service.cloturerExpirees();   // second tick : ne doit pas re-notifier

        assertThat(closed).isZero();
        assertThat(aff.getFin()).isNull();
        var captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(events, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("GARDE_FIN_INTERVENTION");
        assertThat(captor.getValue().getRecipients()).containsExactly("jean");
    }

    // ── Désaffectation globale ────────────────────────────────────────────────

    @Test
    void desaffectationGlobaleEpargneLesEquipagesEnIntervention() {
        var paul = membre("paul", 353);
        var affFpt = affectation(fpt, jean, posteCa);   // FPT engagé sur une intervention
        var affVtu = affectation(vtu, paul, null);      // VTU libre
        when(affectationRepo.findByFinIsNull()).thenReturn(List.of(affFpt, affVtu));
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(intervention("Feu", fpt)));

        int closed = service.cloturerToutes(Instant.now());

        assertThat(closed).isEqualTo(1);
        assertThat(affVtu.getFin()).isNotNull();
        assertThat(affFpt.getFin()).isNull();   // équipage en intervention conservé
    }
}
