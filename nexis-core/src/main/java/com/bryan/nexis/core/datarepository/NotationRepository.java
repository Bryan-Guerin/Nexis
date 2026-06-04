package com.bryan.nexis.core.datarepository;

import com.bryan.nexis.core.datamodel.Notation;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotationRepository extends JpaRepository<Notation, UUID> {

    @Query("SELECT n FROM Notation n WHERE n.faction = :faction AND n.membreId = :membreId ORDER BY n.mois DESC, n.creeLe DESC")
    List<Notation> findForMembre(String faction, UUID membreId);
}
