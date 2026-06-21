package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.TestFixtures;
import com.bryan.nexis.sapeurs.backend.bilan.BilanSapContenu;
import com.bryan.nexis.sapeurs.datamodel.SpBilan;
import com.bryan.nexis.sapeurs.datamodel.SpVictime;
import com.bryan.nexis.sapeurs.datarepository.SpBilanRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.datarepository.SpVictimeRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.type.Argument;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpBilanServiceTest {

    private SpInterventionRepository        interventionRepo;
    private SpVictimeRepository             victimeRepo;
    private SpBilanRepository               bilanRepo;
    private SecurityService                 security;
    private ObjectMapper                    json;
    private SpBilanService                  service;

    @BeforeEach
    void setUp() {
        interventionRepo = mock(SpInterventionRepository.class);
        victimeRepo      = mock(SpVictimeRepository.class);
        bilanRepo        = mock(SpBilanRepository.class);
        var affectationRepo = mock(SpVehiculeAffectationRepository.class);
        security = mock(SecurityService.class);
        json     = ObjectMapper.getDefault();   // vrai serde (round-trip réel)
        @SuppressWarnings("unchecked")
        ApplicationEventPublisher<RealtimeEvent> events = mock(ApplicationEventPublisher.class);
        service  = new SpBilanService(interventionRepo, victimeRepo, bilanRepo, affectationRepo, security, json, events);
        when(security.username()).thenReturn(Optional.of("admin"));
        when(security.hasRole("ROLE_ADMIN_SP")).thenReturn(true);   // bypass équipier
    }

    @Test
    void enregistrerBilanSap_round_trip_du_contenu_typeJson() throws Exception {
        var inter   = TestFixtures.intervention("AVP");
        var victime = TestFixtures.withId(new SpVictime(inter, 1), UUID.randomUUID());
        when(victimeRepo.findById(victime.getId())).thenReturn(Optional.of(victime));
        when(bilanRepo.findByVictimeId(victime.getId())).thenReturn(Optional.empty());
        when(bilanRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var saisi = new BilanSapContenu(
                new BilanSapContenu.Hemorragie(true, true, BilanSapContenu.PerteEstimee.IMPORTANTE, false, false, true, false),
                null, null, null, null, null, null, null,
                new BilanSapContenu.Sample("douleur thoracique", null, null, null, null, "RAS"));
        var dto = service.enregistrerBilanSap(victime.getId(), saisi);

        assertEquals("SAP", dto.famille());
        assertEquals(victime.getId(), dto.victimeId());

        // Le contenu stocké, re-désérialisé, est identique à la saisie (sérialisation fidèle).
        var captor = ArgumentCaptor.forClass(SpBilan.class);
        verify(bilanRepo).save(captor.capture());
        var relu = json.readValue(captor.getValue().getContenu(), Argument.of(BilanSapContenu.class));
        assertEquals(saisi, relu);
    }

    @Test
    void ajouterVictime_numeroteSequentiellement() {
        var inter = TestFixtures.intervention("INC");
        when(interventionRepo.findById(inter.getId())).thenReturn(Optional.of(inter));
        when(victimeRepo.countByInterventionId(inter.getId())).thenReturn(2L);
        when(victimeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = service.ajouterVictime(inter.getId(), "Conducteur", "Durand", "Paul", "H");

        assertEquals(3, dto.numero());
        assertEquals("Conducteur", dto.libelle());
    }
}
