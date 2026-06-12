package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeStatutDto;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeStatut;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeEtatRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeStatutRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpVehiculeStatutService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpVehiculeStatutRepository repo;
    private final SpVehiculeEtatRepository   etatRepo;
    private final com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository vehiculeRepo;

    public SpVehiculeStatutService(SpVehiculeStatutRepository repo, SpVehiculeEtatRepository etatRepo,
                                   com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository vehiculeRepo) {
        this.repo         = repo;
        this.etatRepo     = etatRepo;
        this.vehiculeRepo = vehiculeRepo;
    }

    @Transactional
    public List<SpVehiculeStatutDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpVehiculeStatutDto::from).toList();
    }

    @Transactional
    public SpVehiculeStatutDto create(String code, String label, String couleur, UUID etatId, boolean clotureIntervention) {
        var etat = etatRepo.findById(etatId)
                .orElseThrow(() -> new NoSuchElementException("État véhicule introuvable : " + etatId));
        var statut = new SpVehiculeStatut(code, label, couleur, etat);
        statut.setPosition((int) repo.count());
        statut.setClotureIntervention(clotureIntervention);
        return SpVehiculeStatutDto.from(repo.save(statut));
    }

    /** Bascule la case « clôture intervention » d'un statut. */
    @Transactional
    public SpVehiculeStatutDto toggleClotureIntervention(UUID id) {
        var statut = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Statut véhicule introuvable : " + id));
        statut.setClotureIntervention(!statut.isClotureIntervention());
        return SpVehiculeStatutDto.from(repo.update(statut));
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var s = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Statut véhicule introuvable : " + id));
            s.setPosition(i);
            repo.update(s);
        }
    }

    /**
     * Supprime un statut. Le statut par défaut ne peut pas être supprimé. Les véhicules portant
     * le statut supprimé sont basculés sur le statut par défaut (et l'état lié).
     */
    @Transactional
    public void delete(UUID id) {
        var statut = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Statut véhicule introuvable : " + id));
        if (statut.isParDefaut()) {
            throw new IllegalStateException("Impossible de supprimer le statut par défaut.");
        }
        var defaut = repo.findByParDefautTrue()
                .orElseThrow(() -> new IllegalStateException("Aucun statut par défaut défini."));
        for (var v : vehiculeRepo.findByStatutId(id)) {
            v.setStatut(defaut);
            v.setEtat(defaut.getEtat());
            vehiculeRepo.update(v);
        }
        repo.deleteById(id);
    }

    /** Conservé tant que le principe « par défaut » existe (déclenchement = premier de la liste). */
    @Transactional
    public void setDefault(UUID id) {
        var cible = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Statut véhicule introuvable : " + id));
        for (var s : repo.findAll()) {
            if (s.isParDefaut() && !s.getId().equals(id)) { s.setParDefaut(false); repo.update(s); }
        }
        cible.setParDefaut(true);
        repo.update(cible);
    }
}
