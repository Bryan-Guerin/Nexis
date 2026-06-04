package com.bryan.nexis.sapeurs.backend.vehicule;
import com.bryan.nexis.sapeurs.backend.effectif.SpFonctionDeletionListener;

import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Dépendance "postes de type" sur les fonctions : à la suppression d'une fonction, on
 * supprime en cascade les postes qui la requièrent. Les affectations (actives + historique)
 * qui référencent ces postes sont « détachées » (poste_id → NULL) par la base via la FK
 * ON DELETE SET NULL : l'historique des affectations est conservé.
 *
 * (Les qualifications membre référençant la fonction sont supprimées en cascade par la
 * base — FK ON DELETE CASCADE sur sp_membre_qualification.)
 */
@Singleton
public class PosteFonctionDeletionListener implements SpFonctionDeletionListener {

    private final SpVehiculeTypePosteRepository posteRepo;

    public PosteFonctionDeletionListener(SpVehiculeTypePosteRepository posteRepo) {
        this.posteRepo = posteRepo;
    }

    @Override
    public void onDeleteFonction(UUID fonctionId) {
        for (var poste : posteRepo.findByFonctionId(fonctionId)) {
            posteRepo.delete(poste);
        }
    }
}
