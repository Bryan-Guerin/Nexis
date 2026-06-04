package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpVehiculeTypeRepository extends JpaRepository<SpVehiculeType, UUID> {
    Optional<SpVehiculeType> findByCode(String code);
}
