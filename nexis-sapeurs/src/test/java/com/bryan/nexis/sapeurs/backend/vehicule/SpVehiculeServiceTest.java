package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.intervention.SpInterventionService;
import com.bryan.nexis.sapeurs.datamodel.*;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.bryan.nexis.sapeurs.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Calcul de l'armement : chaque poste obligatoire doit être couvert par un équipier
 * réellement disponible — un membre engagé sur une intervention via un autre véhicule
 * ne compte pas (double affectation).
 */
class SpVehiculeServiceTest {

    private SpVehiculeAffectationRepository affectationRepo;
    private SpVehiculeTypePosteRepository posteRepo;
    private SpInterventionService interventionService;

    private SpVehiculeService service;

    private SpVehiculeType typeFpt;
    private SpVehiculeTypePoste posteCa;       // obligatoire
    private SpVehiculeTypePoste posteEquipier; // non obligatoire
    private SpVehicule fpt;
    private SpMembre jean;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        affectationRepo     = mock(SpVehiculeAffectationRepository.class);
        posteRepo           = mock(SpVehiculeTypePosteRepository.class);
        interventionService = mock(SpInterventionService.class);

        service = new SpVehiculeService(
                mock(SpVehiculeRepository.class), mock(SpVehiculeTypeRepository.class),
                mock(SpVehiculeEtatRepository.class), mock(SpVehiculeStatutRepository.class),
                mock(SpCentreRepository.class), affectationRepo, posteRepo, interventionService,
                (ApplicationEventPublisher<RealtimeEvent>) mock(ApplicationEventPublisher.class),
                mock(SecurityService.class));

        typeFpt = type("FPT");
        var etatDispo   = etat("DISPONIBLE");
        var statutDispo = statut("DISPONIBLE", etatDispo);
        fpt  = vehicule(typeFpt, "FPT 1", etatDispo, statutDispo);
        jean = membre("jean", 352);

        posteCa       = poste(typeFpt, fonction("CATE"), 1, true);
        posteEquipier = poste(typeFpt, fonction("EQ INC"), 2, false);

        when(interventionService.membresOccupesSurAutreIntervention(fpt.getId())).thenReturn(Set.of());
    }

    @Test
    void arme_quandPosteObligatoireCouvertParUnMembreDisponible() {
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteCa, posteEquipier));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteCa)));

        assertThat(service.estArme(fpt)).isTrue();
    }

    @Test
    void nonArme_quandPosteObligatoireCouvertParUnMembreOccupeAilleurs() {
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteCa));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteCa)));
        when(interventionService.membresOccupesSurAutreIntervention(fpt.getId()))
                .thenReturn(Set.of(jean.getId()));

        assertThat(service.estArme(fpt)).isFalse();
    }

    @Test
    void nonArme_quandPosteObligatoireNonCouvert() {
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteCa, posteEquipier));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteEquipier)));   // seul le poste non obligatoire est tenu

        assertThat(service.estArme(fpt)).isFalse();
    }

    @Test
    void sansPosteObligatoire_armeDesQuUnEquipierDisponible() {
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteEquipier));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteEquipier)));

        assertThat(service.estArme(fpt)).isTrue();
    }

    @Test
    void sansPosteObligatoire_nonArmeSiToutLequipageEstOccupeAilleurs() {
        when(posteRepo.findByVehiculeTypeId(typeFpt.getId())).thenReturn(List.of(posteEquipier));
        when(affectationRepo.findByVehiculeIdAndFinIsNull(fpt.getId()))
                .thenReturn(List.of(affectation(fpt, jean, posteEquipier)));
        when(interventionService.membresOccupesSurAutreIntervention(fpt.getId()))
                .thenReturn(Set.of(jean.getId()));

        assertThat(service.estArme(fpt)).isFalse();
    }
}
