package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpInventaireItem;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpInventaireItemRepository extends JpaRepository<SpInventaireItem, UUID> {
    List<SpInventaireItem> findByVehiculeTypeIdOrderByPosition(UUID vehiculeTypeId);
    long countByVehiculeTypeId(UUID vehiculeTypeId);
}
