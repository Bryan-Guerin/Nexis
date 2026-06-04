package com.bryan.nexis.gendarmerie.datarepository;

import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeType;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GnVehiculeTypeRepository extends JpaRepository<GnVehiculeType, UUID> {
    Optional<GnVehiculeType> findByCode(String code);
}
