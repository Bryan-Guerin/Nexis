package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpPaieVersement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpPaieVersementRepository extends JpaRepository<SpPaieVersement, UUID> {
    boolean existsBySemaineLundi(LocalDate semaineLundi);
    List<SpPaieVersement> findBySemaineLundi(LocalDate semaineLundi);
    List<SpPaieVersement> findByMembreIdOrderByRegleLeDesc(UUID membreId);
}
