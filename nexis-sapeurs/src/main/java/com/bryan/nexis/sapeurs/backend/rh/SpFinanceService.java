package com.bryan.nexis.sapeurs.backend.rh;

import com.bryan.nexis.sapeurs.backend.dto.SpFinanceDto;
import com.bryan.nexis.sapeurs.datamodel.SpFinanceCategorie;
import com.bryan.nexis.sapeurs.datamodel.SpFinanceCompte;
import com.bryan.nexis.sapeurs.datamodel.SpFinanceMouvement;
import com.bryan.nexis.sapeurs.datarepository.SpFinanceCategorieRepository;
import com.bryan.nexis.sapeurs.datarepository.SpFinanceCompteRepository;
import com.bryan.nexis.sapeurs.datarepository.SpFinanceMouvementRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Trésorerie SP : compte unique, catégories configurables, mouvements gains/dépenses historisés. */
@Singleton
public class SpFinanceService {

    private final SpFinanceCompteRepository    compteRepo;
    private final SpFinanceCategorieRepository categorieRepo;
    private final SpFinanceMouvementRepository mouvementRepo;

    private static final Sort RECENT = Sort.of(Sort.Order.desc("dateMouvement"), Sort.Order.desc("creeLe"));

    public SpFinanceService(SpFinanceCompteRepository compteRepo, SpFinanceCategorieRepository categorieRepo,
                            SpFinanceMouvementRepository mouvementRepo) {
        this.compteRepo    = compteRepo;
        this.categorieRepo = categorieRepo;
        this.mouvementRepo = mouvementRepo;
    }

    /** Le compte unique (créé en base par la migration). */
    private SpFinanceCompte compte() {
        return compteRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Compte de trésorerie SP non initialisé."));
    }

    @Transactional
    public SpFinanceDto resume() {
        var compte = compte();
        var mouvements = mouvementRepo.findAll(RECENT);
        BigDecimal gains = somme(mouvements, SpFinanceMouvement.GAIN);
        BigDecimal depenses = somme(mouvements, SpFinanceMouvement.DEPENSE);
        BigDecimal solde = compte.getSoldeInitial().add(gains).subtract(depenses);
        return new SpFinanceDto(
                compte.getLibelle(), compte.getSoldeInitial(), solde, gains, depenses,
                categorieRepo.findAllOrderByLibelle().stream().map(SpFinanceDto.Categorie::from).toList(),
                mouvements.stream().map(SpFinanceDto.Mouvement::from).toList());
    }

    private BigDecimal somme(List<SpFinanceMouvement> mvts, String type) {
        return mvts.stream().filter(m -> type.equals(m.getType()))
                .map(SpFinanceMouvement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public SpFinanceDto updateCompte(String libelle, BigDecimal soldeInitial) {
        var compte = compte();
        if (libelle != null && !libelle.isBlank()) compte.setLibelle(libelle.trim());
        if (soldeInitial != null) compte.setSoldeInitial(soldeInitial);
        compteRepo.update(compte);
        return resume();
    }

    @Transactional
    public SpFinanceDto addCategorie(String libelle) {
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libellé de catégorie requis.");
        categorieRepo.save(new SpFinanceCategorie(libelle.trim()));
        return resume();
    }

    @Transactional
    public SpFinanceDto deleteCategorie(UUID id) {
        categorieRepo.deleteById(id);   // mouvements liés : categorie_id → NULL (FK ON DELETE SET NULL)
        return resume();
    }

    @Transactional
    public SpFinanceDto addMouvement(String type, BigDecimal montant, String libelle, String date,
                                     UUID categorieId, String creePar) {
        if (!SpFinanceMouvement.GAIN.equals(type) && !SpFinanceMouvement.DEPENSE.equals(type)) {
            throw new IllegalArgumentException("Type de mouvement invalide (GAIN ou DEPENSE).");
        }
        if (montant == null || montant.signum() <= 0) throw new IllegalArgumentException("Montant strictement positif requis.");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libellé requis.");
        LocalDate jour = date != null && !date.isBlank() ? LocalDate.parse(date) : LocalDate.now();
        SpFinanceCategorie categorie = categorieId == null ? null : categorieRepo.findById(categorieId)
                .orElseThrow(() -> new NoSuchElementException("Catégorie introuvable : " + categorieId));
        mouvementRepo.save(new SpFinanceMouvement(type, montant, libelle.trim(), jour, categorie, creePar));
        return resume();
    }
}
