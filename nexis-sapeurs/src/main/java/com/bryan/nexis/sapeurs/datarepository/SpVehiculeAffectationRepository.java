package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpVehiculeAffectationRepository extends JpaRepository<SpVehiculeAffectation, UUID> {
    List<SpVehiculeAffectation> findByFinIsNull();

    @Query("SELECT a FROM SpVehiculeAffectation a WHERE a.vehicule.id = :vehiculeId AND a.fin IS NULL")
    List<SpVehiculeAffectation> findByVehiculeIdAndFinIsNull(UUID vehiculeId);

    @Query("SELECT a FROM SpVehiculeAffectation a WHERE a.membre.id = :membreId AND a.fin IS NULL")
    List<SpVehiculeAffectation> findByMembreIdAndFinIsNull(UUID membreId);

    @Query("SELECT a FROM SpVehiculeAffectation a WHERE a.membre.id = :membreId")
    List<SpVehiculeAffectation> findByMembreId(UUID membreId);

    @Query("SELECT COUNT(a) FROM SpVehiculeAffectation a WHERE a.vehicule.id = :vehiculeId AND a.poste.id = :posteId AND a.fin IS NULL")
    long countByVehiculeIdAndPosteIdAndFinIsNull(UUID vehiculeId, UUID posteId);

    @Query("SELECT COUNT(a) FROM SpVehiculeAffectation a WHERE a.poste.id = :posteId AND a.fin IS NULL")
    long countByPosteIdAndFinIsNull(UUID posteId);

    @Query("SELECT a FROM SpVehiculeAffectation a WHERE a.poste.id = :posteId AND a.fin IS NULL")
    List<SpVehiculeAffectation> findByPosteIdAndFinIsNull(UUID posteId);

    @Query("SELECT COUNT(a) FROM SpVehiculeAffectation a WHERE a.poste.id = :posteId")
    long countByPosteId(UUID posteId);
}
