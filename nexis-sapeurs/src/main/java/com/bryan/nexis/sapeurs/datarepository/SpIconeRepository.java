package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpIcone;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.UUID;

@Repository
public interface SpIconeRepository extends JpaRepository<SpIcone, UUID> {
}
