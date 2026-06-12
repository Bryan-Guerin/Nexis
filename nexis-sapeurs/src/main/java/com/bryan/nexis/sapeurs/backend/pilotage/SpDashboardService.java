package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.sapeurs.backend.dto.SpDashboardDto;
import com.bryan.nexis.sapeurs.backend.dto.SpDashboardDto.*;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeEtatRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class SpDashboardService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final String DISPO = "DISPONIBLE";

    private final SpMembreRepository       membreRepo;
    private final SpVehiculeRepository     vehiculeRepo;
    private final SpVehiculeEtatRepository etatRepo;
    private final SpInterventionRepository interventionRepo;
    private final SpPlanningService        planningService;

    public SpDashboardService(SpMembreRepository membreRepo, SpVehiculeRepository vehiculeRepo,
                              SpVehiculeEtatRepository etatRepo, SpInterventionRepository interventionRepo,
                              SpPlanningService planningService) {
        this.membreRepo       = membreRepo;
        this.vehiculeRepo     = vehiculeRepo;
        this.etatRepo         = etatRepo;
        this.interventionRepo = interventionRepo;
        this.planningService  = planningService;
    }

    @Transactional
    public SpDashboardDto load() {
        var vehicules    = vehiculeRepo.findAll();
        var toutes       = interventionRepo.findAll();
        var enCours      = toutes.stream().filter(i -> i.getFin() == null)
                                 .sorted(Comparator.comparing(SpIntervention::getDebut).reversed()).toList();

        long effectifsActifs = membreRepo.findByActif(true).size();
        var enServiceIds     = planningService.membresEnService();

        // ── Flotte par état ──
        Map<String, Long> parEtat = vehicules.stream()
                .collect(Collectors.groupingBy(v -> v.getEtat().getCode(), Collectors.counting()));
        var flotte = etatRepo.findAll(BY_POSITION).stream()
                .map(e -> new EtatCount(e.getCode(), e.getLabel(), e.getCouleur(), parEtat.getOrDefault(e.getCode(), 0L)))
                .toList();
        int disponibles = parEtat.getOrDefault(DISPO, 0L).intValue();

        // ── Personnel de garde (nominatif) ──
        var garde = enServiceIds.stream()
                .map(membreRepo::findById)
                .filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                .map(m -> new GardeMembre(m.getMatricule(), m.getUser().getUsername(),
                        m.getGrade().getCode(), m.getGrade().getLabel(), m.getNomComplet()))
                .toList();

        // ── Engins engagés (sur intervention en cours) + ensemble des ids engagés ──
        var enginsEngages = new ArrayList<EnginEngage>();
        var engagedIds = new java.util.HashSet<java.util.UUID>();
        for (var i : enCours) {
            for (var e : i.getEngins()) {
                engagedIds.add(e.getId());
                enginsEngages.add(new EnginEngage(e.getLibelle(), e.getType().getCode(), i.getCode(),
                        e.getStatut().getLabel(), e.getStatut().getCouleur()));
            }
        }

        // ── Alertes : véhicules non disponibles ET non engagés (maintenance, inventaire KO…) ──
        var alertes = vehicules.stream()
                .filter(v -> !DISPO.equals(v.getEtat().getCode()) && !engagedIds.contains(v.getId()))
                .map(v -> new Alerte(v.getLibelle(), v.getEtat().getLabel(), v.getEtat().getCouleur()))
                .toList();

        // ── Interventions en cours (synthèse) ──
        var interventions = enCours.stream().map(this::ligne).toList();

        // ── Durée moyenne des interventions clôturées (7 derniers jours) ──
        Instant depuis7j = Instant.now().minus(7, ChronoUnit.DAYS);
        var clEturees = toutes.stream()
                .filter(i -> i.getFin() != null && i.getDebut().isAfter(depuis7j)).toList();
        long dureeMoyenne = clEturees.isEmpty() ? 0 : Math.round(clEturees.stream()
                .mapToLong(i -> Duration.between(i.getDebut(), i.getFin()).toMinutes()).average().orElse(0));

        // ── Tendance : interventions ouvertes par jour (7 derniers jours) ──
        LocalDate today = LocalDate.now(ZONE);
        Map<LocalDate, Long> parJour = toutes.stream()
                .map(i -> i.getDebut().atZone(ZONE).toLocalDate())
                .filter(d -> !d.isBefore(today.minusDays(6)))
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        var activite = new ArrayList<JourActivite>();
        for (int k = 6; k >= 0; k--) {
            LocalDate d = today.minusDays(k);
            activite.add(new JourActivite(d.toString(), parJour.getOrDefault(d, 0L)));
        }

        return new SpDashboardDto(effectifsActifs, enServiceIds.size(), vehicules.size(), disponibles,
                enCours.size(), dureeMoyenne, flotte, garde, enginsEngages, alertes, interventions, activite);
    }

    private InterventionLigne ligne(SpIntervention i) {
        String nature = i.getNature() != null ? i.getNature().getCode() : "";
        return new InterventionLigne(i.getCode(), nature, i.getMotif(), i.getDebut(), i.getEngins().size());
    }
}
