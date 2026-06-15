package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpVerification;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpVerificationRepository extends JpaRepository<SpVerification, UUID> {
    List<SpVerification> findByVehiculeIdOrderByCreeLeDesc(UUID vehiculeId);

    /** Date de la dernière vérification d'un véhicule (sans charger les lignes). */
    @Query("select max(v.creeLe) from SpVerification v where v.vehicule.id = :vehiculeId")
    Optional<Instant> findDerniereVerif(UUID vehiculeId);

    /** Date de dernière vérification par véhicule, en une requête : [vehiculeId, max(creeLe)]. */
    @Query("select v.vehicule.id, max(v.creeLe) from SpVerification v group by v.vehicule.id")
    List<Object[]> findDernieresVerifs();
}
