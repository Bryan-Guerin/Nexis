package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpInterventionVote;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpInterventionVoteRepository extends JpaRepository<SpInterventionVote, UUID> {
    List<SpInterventionVote> findBySemaineDate(LocalDate semaineDate);

    @Query("SELECT v FROM SpInterventionVote v WHERE v.membre.id = :membreId AND v.semaineDate = :semaineDate")
    Optional<SpInterventionVote> findByMembreIdAndSemaineDate(UUID membreId, LocalDate semaineDate);
}
