package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpBadge;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpBadgeRepository extends JpaRepository<SpBadge, UUID> {

    boolean existsByCode(String code);

    @Query("SELECT b FROM SpBadge b WHERE b.nature.id = :natureId")
    List<SpBadge> findByNatureId(UUID natureId);
}
