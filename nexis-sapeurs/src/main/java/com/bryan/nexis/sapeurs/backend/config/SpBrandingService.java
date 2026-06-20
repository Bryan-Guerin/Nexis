package com.bryan.nexis.sapeurs.backend.config;

import com.bryan.nexis.sapeurs.datamodel.SpBranding;
import com.bryan.nexis.sapeurs.datarepository.SpBrandingRepository;
import com.bryan.nexis.sapeurs.datarepository.SpIconeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.UUID;

/** Branding de l'instance (ligne unique) : lecture/écriture du logo de la caserne. */
@Singleton
public class SpBrandingService {

    private final SpBrandingRepository repo;
    private final SpIconeRepository    iconeRepo;

    public SpBrandingService(SpBrandingRepository repo, SpIconeRepository iconeRepo) {
        this.repo      = repo;
        this.iconeRepo = iconeRepo;
    }

    /** Id de l'icône servant de logo (null si non défini). */
    @Transactional
    public UUID getLogoIconeId() {
        return repo.findAll().stream().findFirst()
                .map(b -> b.getLogoIcone() != null ? b.getLogoIcone().getId() : null)
                .orElse(null);
    }

    /** Définit (ou efface si null) le logo. Crée la ligne de branding au besoin. */
    @Transactional
    public UUID setLogo(UUID iconeImageId) {
        var branding = repo.findAll().stream().findFirst().orElse(null);
        boolean creation = branding == null;
        if (creation) branding = new SpBranding();
        branding.setLogoIcone(iconeImageId != null ? iconeRepo.findById(iconeImageId).orElse(null) : null);
        var saved = creation ? repo.save(branding) : repo.update(branding);
        return saved.getLogoIcone() != null ? saved.getLogoIcone().getId() : null;
    }
}
