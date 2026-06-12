package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpEvenement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpEvenementRepository extends JpaRepository<SpEvenement, UUID> {

    /** Événements à venir / en cours (date >= seuil), du plus proche au plus lointain. */
    List<SpEvenement> findByDateEvenementGreaterThanEqualOrderByDateEvenementAsc(Instant from);

    /** Tous les événements, du plus récent au plus ancien (gestion admin). */
    List<SpEvenement> findAllOrderByDateEvenementDesc();
}
