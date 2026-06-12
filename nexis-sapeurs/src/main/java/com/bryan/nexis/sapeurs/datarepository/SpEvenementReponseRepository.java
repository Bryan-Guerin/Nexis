package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpEvenementReponse;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpEvenementReponseRepository extends JpaRepository<SpEvenementReponse, UUID> {

    List<SpEvenementReponse> findByEvenementId(UUID evenementId);

    Optional<SpEvenementReponse> findByEvenementIdAndMembreId(UUID evenementId, UUID membreId);
}
