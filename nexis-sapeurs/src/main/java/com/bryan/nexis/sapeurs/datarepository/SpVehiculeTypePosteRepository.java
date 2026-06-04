package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpVehiculeTypePosteRepository extends JpaRepository<SpVehiculeTypePoste, UUID> {
    List<SpVehiculeTypePoste> findByVehiculeTypeId(UUID vehiculeTypeId);
    List<SpVehiculeTypePoste> findByFonctionId(UUID fonctionId);
}
