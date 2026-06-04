package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpVehiculeRepository extends JpaRepository<SpVehicule, UUID> {
    List<SpVehicule> findByEtatCode(String code);
    List<SpVehicule> findByStatutId(UUID statutId);
}
