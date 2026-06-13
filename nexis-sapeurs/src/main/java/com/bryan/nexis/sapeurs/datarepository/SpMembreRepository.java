package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpMembreRepository extends JpaRepository<SpMembre, UUID> {

    Optional<SpMembre> findByUserId(UUID userId);

    List<SpMembre> findByActif(boolean actif);

    List<SpMembre> findByGradeId(UUID gradeId);

    List<SpMembre> findAllOrderByGradePositionDesc();

    /** Retourne le plus grand numéro de compteur existant, ou 351 si aucun membre. */
    @Query("SELECT COALESCE(MAX(m.numeroCompteur), 351) FROM SpMembre m")
    int findMaxNumeroCompteur();
}
