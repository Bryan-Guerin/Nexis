package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.FamilleBilan;
import com.bryan.nexis.sapeurs.datamodel.SpBilan;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpBilanRepository extends JpaRepository<SpBilan, UUID> {

    @Query("SELECT b FROM SpBilan b WHERE b.intervention.id = :interventionId")
    List<SpBilan> findByInterventionId(UUID interventionId);

    @Query("SELECT b FROM SpBilan b WHERE b.victime.id = :victimeId")
    Optional<SpBilan> findByVictimeId(UUID victimeId);

    @Query("SELECT b FROM SpBilan b WHERE b.intervention.id = :interventionId AND b.famille = :famille")
    Optional<SpBilan> findByInterventionAndFamille(UUID interventionId, FamilleBilan famille);
}
