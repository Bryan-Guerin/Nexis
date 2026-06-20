package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVictime;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpVictimeRepository extends JpaRepository<SpVictime, UUID> {

    @Query("SELECT v FROM SpVictime v WHERE v.intervention.id = :interventionId ORDER BY v.numero")
    List<SpVictime> findByInterventionId(UUID interventionId);

    @Query("SELECT COUNT(v) FROM SpVictime v WHERE v.intervention.id = :interventionId")
    long countByInterventionId(UUID interventionId);
}
