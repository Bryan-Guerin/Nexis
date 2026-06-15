package com.bryan.nexis.core.datarepository;

import com.bryan.nexis.core.datamodel.JournalEvenement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JournalRepository extends JpaRepository<JournalEvenement, UUID> {
    Page<JournalEvenement> findByFaction(String faction, Pageable pageable);
    Page<JournalEvenement> findByFactionIsNull(Pageable pageable);
    List<JournalEvenement> findByReferenceOrderByCreeLeAsc(String reference);

    /** Toutes les entrées de plusieurs références en une requête (ordre chronologique). */
    List<JournalEvenement> findByReferenceInOrderByCreeLeAsc(Collection<String> references);

    /** Événements d'une faction sur une plage [from, to[ — main courante par jour. */
    List<JournalEvenement> findByFactionAndCreeLeGreaterThanEqualAndCreeLeLessThanOrderByCreeLeDesc(
            String faction, Instant from, Instant to);
}
