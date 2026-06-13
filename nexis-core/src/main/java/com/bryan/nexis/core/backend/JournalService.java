package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.core.datamodel.JournalEvenement;
import com.bryan.nexis.core.datarepository.JournalRepository;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class JournalService {

    private static final Sort RECENT = Sort.of(Sort.Order.desc("creeLe"));

    private final JournalRepository repo;

    public JournalService(JournalRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(RealtimeEvent e) {
        repo.save(new JournalEvenement(e.getType(), e.getFaction(), e.getActorUsername(), e.getMessage(), e.getReference()));
    }

    @Transactional
    public void record(String type, String faction, String acteur, String message) {
        repo.save(new JournalEvenement(type, faction, acteur, message, null));
    }

    /** Main courante d'un objet relié (ex. une intervention), par ordre chronologique. */
    @Transactional
    public List<JournalEntryDto> byReference(String reference) {
        return repo.findByReferenceOrderByCreeLeAsc(reference).stream().map(JournalEntryDto::from).toList();
    }

    /** Derniers événements toutes factions. */
    @Transactional
    public List<JournalEntryDto> recent(int limit) {
        return repo.findAll(Pageable.from(0, limit, RECENT)).getContent()
                .stream().map(JournalEntryDto::from).toList();
    }

    /** Derniers événements système / admin (faction nulle) : connexions, gestion users/rôles… */
    @Transactional
    public List<JournalEntryDto> recentSysteme(int limit) {
        return repo.findByFactionIsNull(Pageable.from(0, limit, RECENT)).getContent()
                .stream().map(JournalEntryDto::from).toList();
    }

    /** Derniers événements d'une faction (main courante). */
    @Transactional
    public List<JournalEntryDto> recentByFaction(String faction, int limit) {
        return repo.findByFaction(faction, Pageable.from(0, limit, RECENT)).getContent()
                .stream().map(JournalEntryDto::from).toList();
    }

    /** Événements d'une faction sur une plage [from, to[ (main courante par jour). */
    @Transactional
    public List<JournalEntryDto> byFactionBetween(String faction, java.time.Instant from, java.time.Instant to) {
        return repo.findByFactionAndCreeLeGreaterThanEqualAndCreeLeLessThanOrderByCreeLeDesc(faction, from, to)
                .stream().map(JournalEntryDto::from).toList();
    }
}
