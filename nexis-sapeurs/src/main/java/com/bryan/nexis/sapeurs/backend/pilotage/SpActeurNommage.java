package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Résout l'acteur des entrées de main courante (login → nom RP du pompier).
 * Les entrées sans membre correspondant gardent {@code acteurNom == null}
 * (le front retombe sur le login).
 */
@Singleton
public class SpActeurNommage {

    private final SpMembreRepository membreRepo;

    public SpActeurNommage(SpMembreRepository membreRepo) {
        this.membreRepo = membreRepo;
    }

    @Transactional
    public List<JournalEntryDto> enrichir(List<JournalEntryDto> entries) {
        if (entries.isEmpty()) return entries;
        Map<String, String> noms = membreRepo.findAll().stream()
                .filter(m -> m.getNomComplet() != null && !m.getNomComplet().isBlank())
                .collect(Collectors.toMap(m -> m.getUser().getUsername(), SpMembre::getNomComplet, (a, b) -> a));
        return entries.stream()
                .map(e -> {
                    String nom = e.acteurUsername() == null ? null : noms.get(e.acteurUsername());
                    return nom == null ? e : e.withActeurNom(nom);
                })
                .toList();
    }
}
