package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.sapeurs.datamodel.SpPlanning;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpPlanningRepository extends JpaRepository<SpPlanning, UUID> {

    // JPQL explicite : la requête dérivée échoue parfois selon la version Micronaut Data
    // (cherche `membreId` au lieu de naviguer `membre.id`) → forçage.
    @Query("SELECT p FROM SpPlanning p WHERE p.membre.id = :membreId")
    List<SpPlanning> findByMembreId(UUID membreId);

    @Query("SELECT p FROM SpPlanning p WHERE p.membre.id = :membreId AND p.debut < :fin AND p.fin > :debut")
    List<SpPlanning> findOverlapping(UUID membreId, Instant debut, Instant fin);

    @Query("SELECT DISTINCT p.membre.id FROM SpPlanning p WHERE p.statut.categorie = :categorie AND p.debut <= :now AND p.fin > :now AND (p.quitteLe IS NULL OR p.quitteLe > :now)")
    List<UUID> findMembreIdsEnService(Instant now, TypeService categorie);

    /** Plages d'une catégorie donnée (ex. GARDE) chevauchant une fenêtre [debut, fin) — pour la paie. */
    @Query("SELECT p FROM SpPlanning p WHERE p.statut.categorie = :categorie AND p.debut < :fin AND p.fin > :debut")
    List<SpPlanning> findByCategorieOverlapping(TypeService categorie, Instant debut, Instant fin);

    /** Plage active d'un membre, d'une catégorie donnée, couvrant {@code now} et non quittée. */
    @Query("SELECT p FROM SpPlanning p WHERE p.membre.id = :membreId AND p.statut.categorie = :categorie AND p.debut <= :now AND p.fin > :now AND (p.quitteLe IS NULL OR p.quitteLe > :now)")
    List<SpPlanning> findMembreCouvrant(UUID membreId, TypeService categorie, Instant now);
}
