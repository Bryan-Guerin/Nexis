package com.bryan.nexis.gendarmerie.datarepository;

import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.gendarmerie.datamodel.GnPlanning;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface GnPlanningRepository extends JpaRepository<GnPlanning, UUID> {

    List<GnPlanning> findByMembreId(UUID membreId);

    @Query("SELECT p FROM GnPlanning p WHERE p.membre.id = :membreId AND p.debut < :fin AND p.fin > :debut")
    List<GnPlanning> findOverlapping(UUID membreId, Instant debut, Instant fin);

    @Query("SELECT DISTINCT p.membre.id FROM GnPlanning p WHERE p.statut.categorie = :categorie AND p.debut <= :now AND p.fin > :now")
    List<UUID> findMembreIdsEnService(Instant now, TypeService categorie);
}
