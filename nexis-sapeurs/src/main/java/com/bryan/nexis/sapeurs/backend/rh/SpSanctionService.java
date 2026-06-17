package com.bryan.nexis.sapeurs.backend.rh;

import com.bryan.nexis.sapeurs.backend.dto.SpSanctionDto;
import com.bryan.nexis.sapeurs.datamodel.SpSanction;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpSanctionRepository;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpSanctionService {

    private final SpSanctionRepository repo;
    private final SpMembreRepository   membreRepo;
    private final SecurityService      securityService;

    public SpSanctionService(SpSanctionRepository repo, SpMembreRepository membreRepo, SecurityService securityService) {
        this.repo            = repo;
        this.membreRepo      = membreRepo;
        this.securityService = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    @Transactional
    public List<SpSanctionDto> listForMembre(UUID membreId) {
        return repo.findByMembreIdOrderByDateSanctionDesc(membreId).stream().map(SpSanctionDto::from).toList();
    }

    @Transactional
    public SpSanctionDto create(UUID membreId, String type, String motif, LocalDate dateSanction) {
        if (motif == null || motif.isBlank()) throw new IllegalArgumentException("Le motif de la sanction est requis.");
        var date = dateSanction != null ? dateSanction : LocalDate.now();   // défaut : aujourd'hui
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        var t = (type == null || type.isBlank()) ? null : type.trim();
        return SpSanctionDto.from(repo.save(new SpSanction(membre, t, motif.trim(), date, actor())));
    }

    @Transactional
    public void supprimer(UUID id) {
        repo.deleteById(id);
    }
}
