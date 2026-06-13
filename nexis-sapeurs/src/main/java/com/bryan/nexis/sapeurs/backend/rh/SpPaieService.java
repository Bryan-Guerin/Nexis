package com.bryan.nexis.sapeurs.backend.rh;

import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.sapeurs.backend.dto.SpPaieSemaineDto;
import com.bryan.nexis.sapeurs.backend.dto.SpPaieSemaineDto.Ligne;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datarepository.SpPlanningRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public class SpPaieService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final SpPlanningRepository planningRepo;

    public SpPaieService(SpPlanningRepository planningRepo) {
        this.planningRepo = planningRepo;
    }

    /**
     * Paie de la semaine contenant {@code dateDansSemaine} (lundi 00:00 → lundi suivant, Europe/Paris).
     * GARDE rémunérée au taux horaire, ASTREINTE au taux d'astreinte, du grade du membre.
     */
    @Transactional
    public SpPaieSemaineDto semaine(LocalDate dateDansSemaine) {
        LocalDate base = dateDansSemaine != null ? dateDansSemaine : LocalDate.now(ZONE);
        LocalDate lundi = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = lundi.atStartOfDay(ZONE).toInstant();
        Instant end   = lundi.plusDays(7).atStartOfDay(ZONE).toInstant();

        Map<UUID, Long> gardeMin     = new LinkedHashMap<>();
        Map<UUID, Long> astreinteMin = new LinkedHashMap<>();
        Map<UUID, SpMembre> membres  = new LinkedHashMap<>();
        cumuler(planningRepo.findByCategorieOverlapping(TypeService.GARDE, start, end), start, end, gardeMin, membres);
        cumuler(planningRepo.findByCategorieOverlapping(TypeService.ASTREINTE, start, end), start, end, astreinteMin, membres);

        BigDecimal total = BigDecimal.ZERO;
        var lignes = new java.util.ArrayList<Ligne>();
        for (var membre : membres.values()) {
            // Arrondi au quart d'heure supérieur sur le TOTAL hebdomadaire (par catégorie).
            double hG = heures(ceilQuarter(gardeMin.getOrDefault(membre.getId(), 0L)));
            double hA = heures(ceilQuarter(astreinteMin.getOrDefault(membre.getId(), 0L)));
            BigDecimal tauxG = membre.getGrade().getTauxHoraire();
            BigDecimal tauxA = membre.getGrade().getTauxAstreinte();
            BigDecimal montant = tauxG.multiply(BigDecimal.valueOf(hG))
                    .add(tauxA.multiply(BigDecimal.valueOf(hA))).setScale(2, RoundingMode.HALF_UP);
            total = total.add(montant);
            lignes.add(new Ligne(membre.getId(), membre.getMatricule(), membre.getUser().getUsername(),
                    membre.getGrade().getLabel(), hG, hA, tauxG, tauxA, montant));
        }
        lignes.sort(Comparator.comparing(Ligne::matricule));

        return new SpPaieSemaineDto(lundi.toString(), lundi.plusDays(6).toString(),
                total.setScale(2, RoundingMode.HALF_UP), lignes);
    }

    private void cumuler(java.util.List<com.bryan.nexis.sapeurs.datamodel.SpPlanning> plages, Instant start, Instant end,
                         Map<UUID, Long> minutesParMembre, Map<UUID, SpMembre> membres) {
        for (var p : plages) {
            // Temps en service réel : jusqu'au départ effectif (quitteLe) si renseigné, sinon la fin.
            Instant finEff = p.getQuitteLe() != null && p.getQuitteLe().isBefore(p.getFin()) ? p.getQuitteLe() : p.getFin();
            Instant d = p.getDebut().isAfter(start) ? p.getDebut() : start;
            Instant f = finEff.isBefore(end) ? finEff : end;
            long minutes = Math.max(0, Duration.between(d, f).toMinutes());
            if (minutes == 0) continue;
            minutesParMembre.merge(p.getMembre().getId(), minutes, Long::sum);
            membres.putIfAbsent(p.getMembre().getId(), p.getMembre());
        }
    }

    /** Arrondit un total de minutes au quart d'heure supérieur (payes « rondes » à la semaine). */
    private static long ceilQuarter(long minutes) {
        return ((minutes + 14) / 15) * 15;
    }

    private static double heures(long minutes) { return Math.round(minutes / 60.0 * 100.0) / 100.0; }
}
