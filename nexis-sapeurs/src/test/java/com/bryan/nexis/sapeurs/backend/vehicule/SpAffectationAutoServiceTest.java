package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.TestFixtures;
import com.bryan.nexis.sapeurs.backend.intervention.SpEngagementService;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeEtat;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeStatut;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpAffectationAutoServiceTest {

    private SpVehiculeRepository            vehiculeRepo;
    private SpVehiculeTypePosteRepository   posteRepo;
    private SpMembreRepository              membreRepo;
    private SpVehiculeAffectationRepository affectationRepo;
    private SpPlanningService               planningService;
    private SpEngagementService             engagement;
    private SpVehiculeAffectationService    affectationService;
    private SpAffectationAutoService        service;

    private SpVehiculeEtat   etat;
    private SpVehiculeStatut statut;

    @BeforeEach
    void setUp() {
        vehiculeRepo       = mock(SpVehiculeRepository.class);
        posteRepo          = mock(SpVehiculeTypePosteRepository.class);
        membreRepo         = mock(SpMembreRepository.class);
        affectationRepo    = mock(SpVehiculeAffectationRepository.class);
        planningService    = mock(SpPlanningService.class);
        engagement         = mock(SpEngagementService.class);
        affectationService = mock(SpVehiculeAffectationService.class);
        service = new SpAffectationAutoService(vehiculeRepo, posteRepo, membreRepo,
                affectationRepo, planningService, engagement, affectationService);

        etat   = TestFixtures.etat("DISPO");
        statut = TestFixtures.statut("DISPO", etat);
        when(affectationRepo.findByVehiculeIdAndFinIsNull(any())).thenReturn(List.of());
        when(engagement.membresOccupesSurAutreIntervention(any())).thenReturn(Set.of());
    }

    /**
     * 2 engins, effectifs juste suffisants : un remplissage glouton par engin pouvait affecter le
     * membre polyvalent au poste « commun » du 1er engin, affamant le poste « rare » du 2e. Le
     * matching global réserve la compétence rare au poste qui n'a qu'elle → les 2 engins armés.
     */
    @Test
    void affecterAutoLot_reserveLaCompetenceRareAuPosteQuiNaQueLle() {
        var fCommune = TestFixtures.fonction("EQ");    // les deux membres la possèdent
        var fRare    = TestFixtures.fonction("COD");   // seul m1 la possède

        var typeA = TestFixtures.type("FPT");
        var typeB = TestFixtures.type("EPA");
        var vA = TestFixtures.vehicule(typeA, "FPT", etat, statut);
        var vB = TestFixtures.vehicule(typeB, "EPA", etat, statut);
        var posteCommun = TestFixtures.poste(typeA, fCommune, 1, true);
        var posteRare   = TestFixtures.poste(typeB, fRare, 1, true);
        when(posteRepo.findByVehiculeTypeIdOrderByOrdreAsc(typeA.getId())).thenReturn(List.of(posteCommun));
        when(posteRepo.findByVehiculeTypeIdOrderByOrdreAsc(typeB.getId())).thenReturn(List.of(posteRare));

        var m1 = TestFixtures.membre("m1", 1);   // polyvalent : EQ + COD
        var m2 = TestFixtures.membre("m2", 2);   // EQ seulement
        TestFixtures.qualifier(m1, fCommune);
        TestFixtures.qualifier(m1, fRare);
        TestFixtures.qualifier(m2, fCommune);
        when(membreRepo.findByActif(true)).thenReturn(List.of(m1, m2));
        when(planningService.membresEnService()).thenReturn(List.of(m1.getId(), m2.getId()));

        service.affecterAutoLot(List.of(vA, vB));

        // m1 (seul COD) → poste rare ; m2 → poste commun ; les 2 postes couverts, aucun doublon.
        verify(affectationService).affecter(eq(vB.getId()), eq(m1.getId()), eq(posteRare.getId()), any());
        verify(affectationService).affecter(eq(vA.getId()), eq(m2.getId()), eq(posteCommun.getId()), any());
        verify(affectationService, times(2)).affecter(any(), any(), any(), any());
    }
}
