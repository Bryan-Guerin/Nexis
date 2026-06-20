package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.TestFixtures;
import com.bryan.nexis.sapeurs.backend.dto.SpLotProposeLigneDto;
import com.bryan.nexis.sapeurs.datamodel.CibleQuestion;
import com.bryan.nexis.sapeurs.datamodel.DeclencheurFlag;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpQuestion;
import com.bryan.nexis.sapeurs.datamodel.SpTemplateDepart;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import com.bryan.nexis.sapeurs.datamodel.TypeQuestion;
import com.bryan.nexis.sapeurs.datarepository.SpIconeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpQuestionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpTemplateDepartRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpTemplateDepartServiceTest {

    private SpTemplateDepartRepository repo;
    private SpQuestionRepository       questionRepo;
    private SpTemplateDepartService    service;

    private SpVehiculeType vsav, vsr, fpt;

    @BeforeEach
    void setUp() {
        repo         = mock(SpTemplateDepartRepository.class);
        questionRepo = mock(SpQuestionRepository.class);
        service = new SpTemplateDepartService(repo, mock(SpNatureInterventionRepository.class),
                mock(SpVehiculeTypeRepository.class), mock(SpIconeRepository.class), questionRepo);
        vsav = TestFixtures.type("VSAV");
        vsr  = TestFixtures.type("VSR");
        fpt  = TestFixtures.type("FPT");
        when(repo.findByNatureIdOrderByPosition(any())).thenReturn(List.of());
        when(repo.findByDeclencheurFlagOrderByPosition(any())).thenReturn(List.of());
        when(questionRepo.findAll()).thenReturn(List.of());
    }

    private SpNatureIntervention nature() {
        return TestFixtures.withId(new SpNatureIntervention("INC", "Feu"), UUID.randomUUID());
    }

    @Test
    void proposerLot_fusionneNatureEtFlagsAuMaxParType() {
        var nat = nature();
        // nature INC : 1 FPT + 1 VSAV ; flag SR : 1 VSR + 1 VSAV → VSAV = max(1, 1) = 1 (pas de cumul).
        when(repo.findByNatureIdOrderByPosition(nat.getId())).thenReturn(List.of(
                new SpTemplateDepart(nat, fpt, 1), new SpTemplateDepart(nat, vsav, 1)));
        when(repo.findByDeclencheurFlagOrderByPosition(DeclencheurFlag.SR)).thenReturn(List.of(
                new SpTemplateDepart(DeclencheurFlag.SR, vsr, 1), new SpTemplateDepart(DeclencheurFlag.SR, vsav, 1)));

        var lot = service.proposerLot(nat.getId(), List.of(DeclencheurFlag.SR), 0);

        assertEquals(3, lot.size());        // FPT, VSAV, VSR
        assertEquals(1, qte(lot, "FPT"));
        assertEquals(1, qte(lot, "VSAV"));
        assertEquals(1, qte(lot, "VSR"));
    }

    @Test
    void proposerLot_recoParUnite_dimensionneSelonLaCapaciteVictime() {
        // VSAV capacité 2 ; 3 victimes → ceil(3/2) = 2 VSAV (capacity-aware).
        vsav.setCapaciteVictime(2);
        var q = new SpQuestion("Nombre de victimes", TypeQuestion.NOMBRE);
        q.setCible(CibleQuestion.NB_VICTIMES);
        q.setRecoParUnite(true);
        q.setRecoVehiculeType(vsav);
        when(questionRepo.findAll()).thenReturn(List.of(q));

        var lot = service.proposerLot(null, List.of(), 3);

        assertEquals(1, lot.size());
        assertEquals(2, qte(lot, "VSAV"));
    }

    @Test
    void proposerLot_dimensionneUnPorteurPresentDansLeLotNature() {
        // VSAV (capacité 1) dans le lot nature ; 2 victimes → 2 VSAV même sans reco par-unité.
        vsav.setCapaciteVictime(1);
        var nat = nature();
        when(repo.findByNatureIdOrderByPosition(nat.getId()))
                .thenReturn(List.of(new SpTemplateDepart(nat, vsav, 1)));

        var lot = service.proposerLot(nat.getId(), List.of(), 2);

        assertEquals(2, qte(lot, "VSAV"));
    }

    private int qte(List<SpLotProposeLigneDto> lot, String code) {
        return lot.stream().filter(l -> l.typeCode().equals(code)).findFirst().orElseThrow().quantite();
    }
}
