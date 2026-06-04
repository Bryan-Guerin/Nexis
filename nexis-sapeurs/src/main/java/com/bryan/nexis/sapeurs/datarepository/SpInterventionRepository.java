package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpInterventionRepository extends JpaRepository<SpIntervention, UUID> {
    List<SpIntervention> findByFinIsNull();

    @Query("SELECT COALESCE(MAX(i.numero), 0) FROM SpIntervention i")
    int findMaxNumero();
}
