package com.bryan.nexis.sapeurs.backend.rh;

import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpPaieSemaineDto;
import com.bryan.nexis.sapeurs.backend.dto.SpPaieSemaineDto.Ligne;
import com.bryan.nexis.sapeurs.backend.dto.SpPaieVersementDto;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datamodel.SpPaieVersement;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpPaieVersementRepository;
import com.bryan.nexis.sapeurs.datarepository.SpPlanningRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Singleton
public class SpPaieService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SpPlanningRepository planningRepo;
    private final SpMembreRepository   membreRepo;
    private final SpPaieVersementRepository versementRepo;
    private final ApplicationEventPublisher<RealtimeEvent> events;

    public SpPaieService(SpPlanningRepository planningRepo, SpMembreRepository membreRepo,
                         SpPaieVersementRepository versementRepo,
                         ApplicationEventPublisher<RealtimeEvent> events) {
        this.planningRepo  = planningRepo;
        this.membreRepo    = membreRepo;
        this.versementRepo = versementRepo;
        this.events        = events;
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

        // État de règlement de la semaine (qui a réglé, quand).
        boolean payee = versementRepo.existsBySemaineLundi(lundi);
        String reglePar = null, regleLe = null;
        if (payee) {
            var v = versementRepo.findBySemaineLundi(lundi).stream().findFirst().orElse(null);
            if (v != null) { reglePar = v.getReglePar(); regleLe = v.getRegleLe().toString(); }
        }

        return new SpPaieSemaineDto(lundi.toString(), lundi.plusDays(6).toString(),
                total.setScale(2, RoundingMode.HALF_UP), lignes, payee, reglePar, regleLe);
    }

    /**
     * Marque la paie de la semaine comme réglée (action RH, irréversible) : enregistre un versement
     * par membre, trace en main courante (qui a réglé) et permet la notification « vous avez été payé »
     * de chaque membre (même non connecté, via le seed au login).
     */
    @Transactional
    public SpPaieSemaineDto regler(LocalDate dateDansSemaine, String payePar) {
        SpPaieSemaineDto paie = semaine(dateDansSemaine);
        LocalDate lundi = LocalDate.parse(paie.debut());
        if (paie.payee()) throw new IllegalStateException("La paie de cette semaine est déjà réglée.");
        if (paie.lignes().isEmpty()) throw new IllegalStateException("Aucune heure à régler sur cette semaine.");

        for (var l : paie.lignes()) {
            var membre = membreRepo.findById(l.membreId())
                    .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + l.membreId()));
            versementRepo.save(new SpPaieVersement(membre, lundi, l.montant(), payePar));
            // Notif personnelle temps réel au membre (éphémère : pas de trace en main courante).
            events.publishEvent(RealtimeEvent.users(RealtimeEvent.PAIE_VERSEE, "SP",
                    Set.of(membre.getUser().getUsername()),
                    "Votre paye a été versée (" + l.montant() + " €)",
                    Map.of("semaine", lundi.toString(), "montant", l.montant().toString()), payePar)
                    .ephemere());
        }
        // Une seule ligne de main courante (la notif individuelle des membres passe par le seed).
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.PAIE, "SP",
                "Paie de la semaine du " + lundi.format(JOUR) + " réglée — total " + paie.total()
                        + " € (" + paie.lignes().size() + " effectifs)",
                Map.of("semaine", lundi.toString()), payePar));
        return semaine(dateDansSemaine);
    }

    /** Versements de paie d'un membre (récent → ancien), pour la notification « vous avez été payé ». */
    @Transactional
    public List<SpPaieVersementDto> mesVersements(UUID membreId) {
        return versementRepo.findByMembreIdOrderByRegleLeDesc(membreId).stream()
                .map(SpPaieVersementDto::from).toList();
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
