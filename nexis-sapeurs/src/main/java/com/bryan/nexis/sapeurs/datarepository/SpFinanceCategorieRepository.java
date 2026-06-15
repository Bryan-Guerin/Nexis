package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpFinanceCategorie;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpFinanceCategorieRepository extends JpaRepository<SpFinanceCategorie, UUID> {
    List<SpFinanceCategorie> findAllOrderByLibelle();
    Optional<SpFinanceCategorie> findByLibelle(String libelle);
}
