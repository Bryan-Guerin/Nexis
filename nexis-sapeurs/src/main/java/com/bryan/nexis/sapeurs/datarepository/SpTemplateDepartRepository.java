package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpTemplateDepart;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpTemplateDepartRepository extends JpaRepository<SpTemplateDepart, UUID> {
    List<SpTemplateDepart> findByNatureIdOrderByPosition(UUID natureId);
}
