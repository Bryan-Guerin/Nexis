package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpInterventionEquipier;
import com.bryan.nexis.sapeurs.datamodel.TypeFonction;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpInterventionEquipierRepository extends JpaRepository<SpInterventionEquipier, UUID> {

    /** Interventions (distinctes) historisées où ce membre figure dans un équipage. */
    @Query("SELECT DISTINCT e.engin.intervention.id FROM SpInterventionEquipier e WHERE e.membreId = :membreId")
    List<UUID> distinctInterventionIds(UUID membreId);

    /** Idem, restreint à une nature d'intervention. */
    @Query("SELECT DISTINCT e.engin.intervention.id FROM SpInterventionEquipier e "
            + "WHERE e.membreId = :membreId AND e.engin.intervention.nature.id = :natureId")
    List<UUID> distinctInterventionIdsByNature(UUID membreId, UUID natureId);

    /** Idem, restreint à un type de fonction tenu (rôle figé au snapshot). */
    @Query("SELECT DISTINCT e.engin.intervention.id FROM SpInterventionEquipier e "
            + "WHERE e.membreId = :membreId AND e.typeFonction = :typeFonction")
    List<UUID> distinctInterventionIdsByTypeFonction(UUID membreId, TypeFonction typeFonction);
}
