package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpVehiculeAffectationRepository extends JpaRepository<SpVehiculeAffectation, UUID> {
    List<SpVehiculeAffectation> findByFinIsNull();
    List<SpVehiculeAffectation> findByVehiculeIdAndFinIsNull(UUID vehiculeId);
    List<SpVehiculeAffectation> findByMembreIdAndFinIsNull(UUID membreId);
    long countByVehiculeIdAndPosteIdAndFinIsNull(UUID vehiculeId, UUID posteId);
    long countByPosteIdAndFinIsNull(UUID posteId);
    List<SpVehiculeAffectation> findByPosteIdAndFinIsNull(UUID posteId);
    long countByPosteId(UUID posteId);
}
