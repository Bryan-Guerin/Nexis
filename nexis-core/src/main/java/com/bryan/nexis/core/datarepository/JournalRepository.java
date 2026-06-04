package com.bryan.nexis.core.datarepository;

import com.bryan.nexis.core.datamodel.JournalEvenement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.UUID;

@Repository
public interface JournalRepository extends JpaRepository<JournalEvenement, UUID> {
    Page<JournalEvenement> findByFaction(String faction, Pageable pageable);
    Page<JournalEvenement> findByFactionIsNull(Pageable pageable);
    java.util.List<JournalEvenement> findByReferenceOrderByCreeLeAsc(String reference);
}
