package com.bryan.nexis.sapeurs.backend.rh;

import com.bryan.nexis.sapeurs.backend.dto.SpRelanceDto;
import com.bryan.nexis.sapeurs.datamodel.SpRelance;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpRelanceRepository;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpRelanceService {

    private final SpRelanceRepository repo;
    private final SpMembreRepository  membreRepo;
    private final SecurityService     securityService;

    public SpRelanceService(SpRelanceRepository repo, SpMembreRepository membreRepo, SecurityService securityService) {
        this.repo            = repo;
        this.membreRepo      = membreRepo;
        this.securityService = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    @Transactional
    public List<SpRelanceDto> listForMembre(UUID membreId) {
        return repo.findByMembreIdOrderByCreeLeDesc(membreId).stream().map(SpRelanceDto::from).toList();
    }

    /** Toutes les relances ouvertes (vue d'ensemble RH : repérer les recyclages à prévoir). */
    @Transactional
    public List<SpRelanceDto> ouvertes() {
        return repo.findByStatutOrderByEcheanceAsc(SpRelance.OUVERT).stream().map(SpRelanceDto::from).toList();
    }

    @Transactional
    public SpRelanceDto create(UUID membreId, String texte, LocalDate echeance) {
        if (texte == null || texte.isBlank()) throw new IllegalArgumentException("Le texte de la relance est requis.");
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        return SpRelanceDto.from(repo.save(new SpRelance(membre, texte.trim(), echeance, actor())));
    }

    @Transactional
    public SpRelanceDto marquerFait(UUID id) {
        var r = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Relance introuvable : " + id));
        r.setStatut(SpRelance.FAIT);
        r.setFaitPar(actor());
        r.setFaitLe(Instant.now());
        return SpRelanceDto.from(repo.update(r));
    }

    @Transactional
    public void supprimer(UUID id) {
        repo.deleteById(id);
    }
}
