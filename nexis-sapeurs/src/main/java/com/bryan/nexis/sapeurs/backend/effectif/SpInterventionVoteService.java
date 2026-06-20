package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpVoteEtatDto;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpInterventionVote;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionVoteRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vote "intervention de la semaine".
 *
 * <p>Règles :
 * <ul>
 *   <li>Les <em>candidates</em> sont les interventions clôturées (fin non null) dont la
 *       date de fin tombe dans la <em>semaine précédente</em> (lundi → dimanche).</li>
 *   <li>Les SP actifs peuvent voter 1× par semaine de vote courante.</li>
 *   <li>Le <em>gagnant</em> est l'intervention avec le plus de votes (en cas d'égalité,
 *       la plus récente).</li>
 *   <li>Lundi → la semaine "candidates" change automatiquement vers la nouvelle
 *       semaine close (compteurs repartent à 0).</li>
 * </ul>
 */
@Singleton
public class SpInterventionVoteService {

    private final SpInterventionRepository     interRepo;
    private final SpInterventionVoteRepository voteRepo;
    private final SpMembreRepository           membreRepo;

    public SpInterventionVoteService(SpInterventionRepository interRepo,
                                     SpInterventionVoteRepository voteRepo,
                                     SpMembreRepository membreRepo) {
        this.interRepo  = interRepo;
        this.voteRepo   = voteRepo;
        this.membreRepo = membreRepo;
    }

    /** Lundi de la semaine de l'intervention votable (= semaine précédant aujourd'hui). */
    public LocalDate semaineVotableDate() {
        LocalDate lundiCourant = LocalDate.now(ZoneId.systemDefault())
                .with(DayOfWeek.MONDAY);
        return lundiCourant.minusWeeks(1);
    }

    @Transactional
    public SpVoteEtatDto etat(UUID membreId) {
        LocalDate sem = semaineVotableDate();
        ZoneId zone = ZoneId.systemDefault();
        Instant start = sem.atStartOfDay(zone).toInstant();
        Instant end   = sem.plusWeeks(1).atStartOfDay(zone).toInstant();

        // Candidates : interventions clôturées dans la fenêtre.
        var candidatesInter = interRepo.findAll().stream()
                .filter(i -> i.getFin() != null && !i.getFin().isBefore(start) && i.getFin().isBefore(end))
                .toList();
        var votes = voteRepo.findBySemaineDate(sem);
        Map<UUID, Long> countsByInter = votes.stream()
                .collect(Collectors.groupingBy(v -> v.getIntervention().getId(), Collectors.counting()));

        var candidates = candidatesInter.stream()
                .map(i -> new SpVoteEtatDto.Candidate(
                        i.getId(),
                        codeIntervention(i),
                        i.getMotif(),
                        i.getNature() != null ? i.getNature().getLabel() : null,
                        i.getCommune(),
                        countsByInter.getOrDefault(i.getId(), 0L).intValue()))
                .sorted(Comparator.comparingInt(SpVoteEtatDto.Candidate::votes).reversed())
                .toList();

        SpVoteEtatDto.Candidate gagnant = candidates.stream()
                .max(Comparator.comparingInt(SpVoteEtatDto.Candidate::votes)
                        .thenComparing(c -> candidatesInter.stream()
                                .filter(i -> i.getId().equals(c.interventionId()))
                                .findFirst().map(SpIntervention::getFin).orElse(Instant.EPOCH)))
                .filter(c -> c.votes() > 0)
                .orElse(null);

        UUID monVote = membreId == null ? null
                : voteRepo.findByMembreIdAndSemaineDate(membreId, sem)
                          .map(v -> v.getIntervention().getId()).orElse(null);

        return new SpVoteEtatDto(sem, candidates, monVote, gagnant);
    }

    @Transactional
    public SpVoteEtatDto voter(UUID membreId, UUID interventionId) {
        LocalDate sem = semaineVotableDate();
        var inter = interRepo.findById(interventionId).orElseThrow(
                () -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        ZoneId zone = ZoneId.systemDefault();
        Instant start = sem.atStartOfDay(zone).toInstant();
        Instant end   = sem.plusWeeks(1).atStartOfDay(zone).toInstant();
        if (inter.getFin() == null || inter.getFin().isBefore(start) || !inter.getFin().isBefore(end)) {
            throw new IllegalArgumentException("Cette intervention n'est pas éligible au vote de la semaine.");
        }
        var membre = membreRepo.findById(membreId).orElseThrow(
                () -> new NoSuchElementException("Membre SP introuvable : " + membreId));

        var existing = voteRepo.findByMembreIdAndSemaineDate(membreId, sem).orElse(null);
        if (existing != null) {
            existing.setIntervention(inter);
            existing.setVoteLe(Instant.now());
            voteRepo.update(existing);
        } else {
            voteRepo.save(new SpInterventionVote(inter, membre, sem));
        }
        return etat(membreId);
    }

    @Transactional
    public SpVoteEtatDto retirerVote(UUID membreId) {
        LocalDate sem = semaineVotableDate();
        voteRepo.findByMembreIdAndSemaineDate(membreId, sem).ifPresent(voteRepo::delete);
        return etat(membreId);
    }

    /**
     * IDs des interventions gagnantes du vote, toutes semaines passées confondues.
     * Pour chaque semaine ayant reçu des votes, l'intervention la plus votée (égalité →
     * fin la plus récente, comme {@link #etat}). Sert au badge « intervention de la semaine ».
     */
    @Transactional
    public Set<UUID> winningInterventionIds() {
        var votes = voteRepo.findAll();
        if (votes.isEmpty()) return Set.of();

        Map<LocalDate, Map<UUID, Long>> bySemaine = votes.stream().collect(Collectors.groupingBy(
                SpInterventionVote::getSemaineDate,
                Collectors.groupingBy(v -> v.getIntervention().getId(), Collectors.counting())));
        Map<UUID, Instant> finByInter = votes.stream().collect(Collectors.toMap(
                v -> v.getIntervention().getId(),
                v -> v.getIntervention().getFin() != null ? v.getIntervention().getFin() : Instant.EPOCH,
                (a, b) -> a));

        Set<UUID> gagnants = new HashSet<>();
        for (var counts : bySemaine.values()) {
            counts.entrySet().stream()
                    .max(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue)
                            .thenComparing(e -> finByInter.getOrDefault(e.getKey(), Instant.EPOCH)))
                    .map(Map.Entry::getKey)
                    .ifPresent(gagnants::add);
        }
        return gagnants;
    }

    private static String codeIntervention(SpIntervention i) {
        return i.getNumero() != null ? String.format("INT-%04d", i.getNumero()) : i.getId().toString().substring(0, 8);
    }
}
