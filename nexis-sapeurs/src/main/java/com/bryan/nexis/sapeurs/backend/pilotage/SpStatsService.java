package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.sapeurs.backend.dto.HeatmapPointDto;
import com.bryan.nexis.sapeurs.backend.dto.SpInterventionStatsDto;
import com.bryan.nexis.sapeurs.backend.dto.SpInterventionStatsDto.MoisCount;
import com.bryan.nexis.sapeurs.backend.dto.SpInterventionStatsDto.NatureCount;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class SpStatsService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final SpInterventionRepository interventionRepo;

    public SpStatsService(SpInterventionRepository interventionRepo) {
        this.interventionRepo = interventionRepo;
    }

    @Transactional
    public SpInterventionStatsDto interventions() {
        var toutes = interventionRepo.findAll();

        int total     = toutes.size();
        int enCours   = (int) toutes.stream().filter(i -> i.getFin() == null).count();
        int cloturees = total - enCours;

        var closed = toutes.stream().filter(i -> i.getFin() != null).toList();
        long dureeMoyenne = closed.isEmpty() ? 0 : Math.round(closed.stream()
                .mapToLong(i -> Duration.between(i.getDebut(), i.getFin()).toMinutes()).average().orElse(0));

        long totalVictimes = toutes.stream().mapToLong(i -> i.getNbVictimes() != null ? i.getNbVictimes() : 0).sum();
        int nbIncendies    = (int) toutes.stream().filter(SpIntervention::isIncendie).count();
        int nbAvecVehicule = (int) toutes.stream().filter(SpIntervention::isVehiculeImplique).count();

        // Répartition par nature (libellé), décroissant
        Map<String, Long> parNatureMap = new LinkedHashMap<>();
        for (var i : toutes) {
            String n = i.getNature() != null ? i.getNature().getLabel() : "—";
            parNatureMap.merge(n, 1L, Long::sum);
        }
        var parNature = parNatureMap.entrySet().stream()
                .map(e -> new NatureCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(NatureCount::count).reversed())
                .toList();

        // Volume des 6 derniers mois (AAAA-MM)
        Map<YearMonth, Long> parMoisMap = new LinkedHashMap<>();
        for (var i : toutes) {
            parMoisMap.merge(YearMonth.from(i.getDebut().atZone(ZONE)), 1L, Long::sum);
        }
        YearMonth courant = YearMonth.from(LocalDate.now(ZONE));
        var parMois = new ArrayList<MoisCount>();
        for (int k = 5; k >= 0; k--) {
            YearMonth ym = courant.minusMonths(k);
            parMois.add(new MoisCount(ym.toString(), parMoisMap.getOrDefault(ym, 0L)));
        }

        return new SpInterventionStatsDto(total, enCours, cloturees, dureeMoyenne, totalVictimes,
                nbIncendies, nbAvecVehicule, parNature, parMois);
    }

    /**
     * Heatmap : interventions agrégées par coordonnées. {@code from}/{@code to} bornent
     * la fenêtre temporelle (sur la date de début), {@code natureId} filtre une nature.
     * Toutes les bornes sont optionnelles (null = tout).
     */
    @Transactional
    public List<HeatmapPointDto> heatmap(Instant from, Instant to, UUID natureId) {
        Map<String, Integer> agg = new HashMap<>();
        for (var i : interventionRepo.findAll()) {
            String c = i.getCoordonnees();
            if (c == null || c.length() < 6) continue;
            if (from != null && i.getDebut().isBefore(from)) continue;
            if (to   != null && i.getDebut().isAfter(to))    continue;
            if (natureId != null && (i.getNature() == null || !i.getNature().getId().equals(natureId))) continue;
            agg.merge(c, 1, Integer::sum);
        }
        return agg.entrySet().stream()
                .map(e -> new HeatmapPointDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(HeatmapPointDto::count).reversed())
                .toList();
    }
}
