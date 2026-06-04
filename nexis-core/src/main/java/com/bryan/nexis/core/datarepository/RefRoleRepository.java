package com.bryan.nexis.core.datarepository;

import com.bryan.nexis.core.datamodel.RefRole;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefRoleRepository extends JpaRepository<RefRole, UUID> {
    Optional<RefRole> findByCode(String code);
}
