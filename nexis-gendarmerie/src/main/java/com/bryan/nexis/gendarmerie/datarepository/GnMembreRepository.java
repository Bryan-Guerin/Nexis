package com.bryan.nexis.gendarmerie.datarepository;

import com.bryan.nexis.gendarmerie.datamodel.GnMembre;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GnMembreRepository extends JpaRepository<GnMembre, UUID> {
    Optional<GnMembre> findByUserId(UUID userId);
    List<GnMembre> findByActif(boolean actif);
}
