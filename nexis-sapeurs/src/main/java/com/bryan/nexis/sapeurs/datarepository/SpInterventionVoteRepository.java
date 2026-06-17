package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpInterventionVote;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpInterventionVoteRepository extends JpaRepository<SpInterventionVote, UUID> {
    List<SpInterventionVote> findBySemaineDate(LocalDate semaineDate);
    Optional<SpInterventionVote> findByMembreIdAndSemaineDate(UUID membreId, LocalDate semaineDate);
}
