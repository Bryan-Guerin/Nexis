package com.bryan.nexis.gendarmerie.datarepository;

import com.bryan.nexis.gendarmerie.datamodel.GnVehicule;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GnVehiculeRepository extends JpaRepository<GnVehicule, UUID> {
    List<GnVehicule> findByEtatCode(String code);
}
