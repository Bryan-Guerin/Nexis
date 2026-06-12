package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpSanction;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpSanctionRepository extends JpaRepository<SpSanction, UUID> {

    List<SpSanction> findByMembreIdOrderByDateSanctionDesc(UUID membreId);
}
