package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpCri;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpCriRepository extends JpaRepository<SpCri, UUID> {
    List<SpCri> findByInterventionId(UUID interventionId);
    List<SpCri> findByInterventionIdIn(java.util.Collection<UUID> interventionIds);
    boolean existsByInterventionIdAndVehiculeId(UUID interventionId, UUID vehiculeId);
    boolean existsByVehiculeId(UUID vehiculeId);
    long countByStatut(String statut);
}
