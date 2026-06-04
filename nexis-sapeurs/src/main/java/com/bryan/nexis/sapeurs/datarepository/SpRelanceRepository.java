package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpRelance;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpRelanceRepository extends JpaRepository<SpRelance, UUID> {
    List<SpRelance> findByMembreIdOrderByCreeLeDesc(UUID membreId);
    List<SpRelance> findByStatutOrderByEcheanceAsc(String statut);
}
