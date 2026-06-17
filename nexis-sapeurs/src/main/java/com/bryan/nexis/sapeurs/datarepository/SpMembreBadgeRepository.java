package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpMembreBadge;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpMembreBadgeRepository extends JpaRepository<SpMembreBadge, UUID> {
    List<SpMembreBadge> findByMembreId(UUID membreId);
    boolean existsByMembreIdAndBadgeId(UUID membreId, UUID badgeId);
}
