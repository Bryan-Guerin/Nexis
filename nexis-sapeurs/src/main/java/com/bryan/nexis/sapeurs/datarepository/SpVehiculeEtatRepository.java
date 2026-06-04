package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeEtat;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpVehiculeEtatRepository extends JpaRepository<SpVehiculeEtat, UUID> {
    Optional<SpVehiculeEtat> findByCode(String code);
}
