package com.bryan.nexis.core.datarepository;

import com.bryan.nexis.core.datamodel.RefUser;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefUserRepository extends JpaRepository<RefUser, UUID> {

    Optional<RefUser> findByUsername(String username);

    // Utilisé pour l'authentification Steam (futur)
    Optional<RefUser> findBySteamId(Long steamId);
}
