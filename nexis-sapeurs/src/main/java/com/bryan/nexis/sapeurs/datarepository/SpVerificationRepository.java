package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVerification;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpVerificationRepository extends JpaRepository<SpVerification, UUID> {
    List<SpVerification> findByVehiculeIdOrderByCreeLeDesc(UUID vehiculeId);
}
