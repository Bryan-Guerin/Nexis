package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.vehicule.SpVehiculeAffectationService;
import com.bryan.nexis.sapeurs.datamodel.*;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.bryan.nexis.sapeurs.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mécanique intervention : calcul des membres occupés (support de l'armement),
 * aperçu de désaffectation au déclenchement, clôture automatique.
 */
class SpInterventionServiceTest {

    private SpInterventionRepository interventionRepo;
    private SpVehiculeRepository vehiculeRepo;
    private SpVehiculeAffectationRepository affectationRepo;
    private SpVehiculeTypePosteRepository posteRepo;
    private SpVehiculeStatutRepository statutRepo;
    private SpVehiculeEtatRepository etatRepo;

    private SpInterventionService service;

    private SpVehiculeType typeFpt;
    private SpVehicule fpt;
    private SpVehicule vsav;
    private SpVehiculeEtat etatDispo;
    private SpVehiculeStatut statutDispo;
    private SpMembre jean;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        interventionRepo = mock(SpInterventionRepository.class);
        vehiculeRepo     = mock(SpVehiculeRepository.class);
        affectationRepo  = mock(SpVehiculeAffectationRepository.class);
        posteRepo        = mock(SpVehiculeTypePosteRepository.class);
        statutRepo       = mock(SpVehiculeStatutRepository.class);
        etatRepo         = mock(SpVehiculeEtatRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.username()).thenReturn(Optional.of("codis"));

        service = new SpInterventionService(interventionRepo, vehiculeRepo,
                mock(SpNatureInterventionRepository.class), mock(SpVehiculeAffectationService.class),
                affectationRepo, posteRepo, statutRepo, etatRepo, mock(JournalService.class),
                (ApplicationEventPublisher<RealtimeEvent>) mock(ApplicationEventPublisher.class),
                securityService);

        typeFpt    = type("FPT");
        etatDispo  = etat("DISPONIBLE");
        statutDispo = statut("DISPONIBLE", etatDispo);
        fpt  = vehicule(typeFpt, "FPT 1", etatDispo, statutDispo);
        vsav = vehicule(type("VSAV"), "VSAV 1", etatDispo, statutDispo);
        jean = membre("jean", 352);

        when(interventionRepo.findByFinIsNull()).thenReturn(List.of());
    }

    // ── Membres occupés (support du calcul d'armement) ────────────────────────

    @Test
    void membreSurEnginDuneInterventionOuverte_estOccupePourLesAutresVehicules() {
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(intervention("Feu", fpt)));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, null)));

        assertThat(service.membresOccupesSurAutreIntervention(vsav.getId())).containsExactly(jean.getId());
        // Pour le véhicule lui-même engagé, son équipage n'est pas « occupé ailleurs »
        assertThat(service.membresOccupesSurAutreIntervention(fpt.getId())).isEmpty();
    }

    // ── Aperçu de désaffectation au déclenchement ─────────────────────────────

    @Test
    void preview_listeUniquementLesNonObligatoiresOccupesAilleurs() {
        var posteCa  = poste(typeFpt, fonction("CATE"), 1, true);
        var posteEq  = poste(typeFpt, fonction("EQ INC"), 2, false);
        var paul     = membre("paul", 353);
        var marc     = membre("marc", 354);

        when(vehiculeRepo.findById(fpt.getId())).thenReturn(Optional.of(fpt));
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteCa, posteEq));
        // Jean (non oblig, occupé ailleurs), Paul (non oblig, disponible), Marc (oblig, occupé ailleurs)
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId())).thenReturn(List.of(
                affectation(fpt, jean, posteEq),
                affectation(fpt, paul, posteEq),
                affectation(fpt, marc, posteCa)));
        // Jean et Marc sont engagés sur une intervention via le VSAV
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(intervention("AVP", vsav)));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(vsav.getId())).thenReturn(List.of(
                affectation(vsav, jean, null),
                affectation(vsav, marc, null)));

        var preview = service.previewDesaffectationNonObligatoire(List.of(fpt.getId()));

        assertThat(preview).hasSize(1);
        assertThat(preview.getFirst().nom()).isEqualTo("jean");
        assertThat(preview.getFirst().vehicule()).isEqualTo("FPT 1");
    }

    @Test
    void preview_vide_quandLeTypeNaAucunPosteObligatoire() {
        var posteEq = poste(typeFpt, fonction("EQ INC"), 2, false);
        when(vehiculeRepo.findById(fpt.getId())).thenReturn(Optional.of(fpt));
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteEq));

        var preview = service.previewDesaffectationNonObligatoire(List.of(fpt.getId()));

        assertThat(preview).isEmpty();
    }

    // ── Clôture automatique (pilotée par la case « clôture intervention » du statut) ──

    @Test
    void clotureAuto_quandTousLesEnginsOntUnStatutValidantLaCloture() {
        statutDispo.setClotureIntervention(true);
        var inter = intervention("Feu", fpt, vsav);
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(inter));
        when(interventionRepo.findById(inter.getId())).thenReturn(Optional.of(inter));
        when(interventionRepo.update(any(SpIntervention.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statutRepo.findByCode("DISPONIBLE")).thenReturn(Optional.of(statutDispo));
        when(etatRepo.findByCode("DISPONIBLE")).thenReturn(Optional.of(etatDispo));

        service.clotureSiEnginsValident(fpt.getId());

        assertThat(inter.getFin()).isNotNull();
    }

    @Test
    void pasDeClotureAuto_siUnStatutNeValidePasLaCloture() {
        // « Disponible radio » : véhicule libéré (état DISPONIBLE) mais case non cochée
        statutDispo.setClotureIntervention(false);
        var inter = intervention("Feu", fpt, vsav);
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(inter));

        service.clotureSiEnginsValident(fpt.getId());

        assertThat(inter.getFin()).isNull();
    }

    @Test
    void clotureConserveLeStatutFinalChoisiParLequipage() {
        // Bug constaté : véhicule passé en INVENTAIRE (statut validant) était réinitialisé
        // DISPONIBLE par la clôture. Le statut validant choisi doit être conservé.
        var statutInventaire = statut("INVENTAIRE", etat("INVENTAIRE"));
        statutInventaire.setClotureIntervention(true);
        statutDispo.setClotureIntervention(true);
        fpt.setStatut(statutInventaire);
        var inter = intervention("Feu", fpt, vsav);
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(inter));
        when(interventionRepo.findById(inter.getId())).thenReturn(Optional.of(inter));
        when(interventionRepo.update(any(SpIntervention.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statutRepo.findByCode("DISPONIBLE")).thenReturn(Optional.of(statutDispo));
        when(etatRepo.findByCode("DISPONIBLE")).thenReturn(Optional.of(etatDispo));

        service.clotureSiEnginsValident(fpt.getId());

        assertThat(inter.getFin()).isNotNull();
        assertThat(fpt.getStatut().getCode()).isEqualTo("INVENTAIRE");   // pas écrasé en DISPONIBLE
        verify(vehiculeRepo, never()).update(fpt);
    }

    // ── Blocage du départ (poste obligatoire tenu par un effectif déjà parti) ──

    @Test
    void departBloque_quandPosteObligatoireTenuParEffectifDejaEngage() {
        var posteCa = poste(typeFpt, fonction("CATE"), 1, true);
        var ancienne = intervention("Feu", vsav);          // jean est parti avec le VSAV
        var nouvelle = intervention("AVP");
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(ancienne, nouvelle));
        when(interventionRepo.findById(nouvelle.getId())).thenReturn(Optional.of(nouvelle));
        when(vehiculeRepo.findById(fpt.getId())).thenReturn(Optional.of(fpt));
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteCa));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteCa)));     // poste obligatoire du FPT
        when(affectationRepo.findByVehiculeIdAndFinIsNull(vsav.getId()))
                .thenReturn(List.of(affectation(vsav, jean, null)));
        when(affectationRepo.findByMembreIdAndFinIsNull(jean.getId()))
                .thenReturn(List.of(affectation(vsav, jean, null), affectation(fpt, jean, posteCa)));

        assertThatThrownBy(() -> service.addEngins(nouvelle.getId(), List.of(fpt.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("poste obligatoire CATE")
                .hasMessageContaining(ancienne.getCode());

        assertThat(nouvelle.getEngins()).isEmpty();   // aucun engagement effectué
    }

    // ── Réengagement (départ depuis « disponible radio ») ─────────────────────

    @Test
    void reengagement_detacheLeVehiculeDeLancienneIntervention_etLaClotureSiVide() {
        var ancienne = intervention("Feu", fpt);     // fpt encore rattaché (dispo radio)
        var nouvelle = intervention("AVP");
        when(interventionRepo.findByFinIsNull()).thenReturn(List.of(ancienne, nouvelle));
        when(interventionRepo.findById(nouvelle.getId())).thenReturn(Optional.of(nouvelle));
        when(interventionRepo.findById(ancienne.getId())).thenReturn(Optional.of(ancienne));
        when(interventionRepo.update(any(SpIntervention.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehiculeRepo.findById(fpt.getId())).thenReturn(Optional.of(fpt));
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of());
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId())).thenReturn(List.of());
        when(statutRepo.listOrderByPositionAsc()).thenReturn(List.of());
        when(statutRepo.findByCode("DISPONIBLE")).thenReturn(Optional.of(statutDispo));
        when(etatRepo.findByCode("DISPONIBLE")).thenReturn(Optional.of(etatDispo));

        service.addEngins(nouvelle.getId(), List.of(fpt.getId()));

        assertThat(ancienne.getEngins()).isEmpty();             // détaché de l'ancienne
        assertThat(ancienne.getFin()).isNotNull();              // ancienne clôturée (plus d'engin)
        assertThat(nouvelle.getEngins()).extracting("id").containsExactly(fpt.getId());
    }
}
