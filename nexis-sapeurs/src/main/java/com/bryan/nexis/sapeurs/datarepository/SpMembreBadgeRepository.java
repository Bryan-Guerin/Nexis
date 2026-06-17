package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpMembreBadge;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpMembreBadgeRepository extends JpaRepository<SpMembreBadge, UUID> {
    @Query("SELECT mb FROM SpMembreBadge mb WHERE mb.membre.id = :membreId")
    List<SpMembreBadge> findByMembreId(UUID membreId);

    @Query("SELECT COUNT(mb) > 0 FROM SpMembreBadge mb WHERE mb.membre.id = :membreId AND mb.badge.id = :badgeId")
    boolean existsByMembreIdAndBadgeId(UUID membreId, UUID badgeId);
}
