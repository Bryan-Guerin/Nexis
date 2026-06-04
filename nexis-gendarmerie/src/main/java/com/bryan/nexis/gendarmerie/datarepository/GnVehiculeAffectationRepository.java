package com.bryan.nexis.gendarmerie.datarepository;

import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeAffectation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GnVehiculeAffectationRepository extends JpaRepository<GnVehiculeAffectation, UUID> {
    List<GnVehiculeAffectation> findByFinIsNull();
    List<GnVehiculeAffectation> findByVehiculeIdAndFinIsNull(UUID vehiculeId);
    List<GnVehiculeAffectation> findByMembreIdAndFinIsNull(UUID membreId);
}
