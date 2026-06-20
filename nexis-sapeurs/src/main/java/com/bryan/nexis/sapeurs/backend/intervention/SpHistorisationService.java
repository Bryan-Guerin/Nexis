package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpInterventionEngin;
import com.bryan.nexis.sapeurs.datamodel.SpInterventionEquipier;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import jakarta.inject.Singleton;

/**
 * Historisation des engins d'une intervention : fige l'engin (libellé + type) et son équipage
 * en TEXTE, au moment où il quitte l'intervention (réengagement / retrait) ou à la clôture —
 * sinon l'équipage serait perdu (la FK véhicule live est vidée à la clôture).
 *
 * <p>Collaborateur de {@link SpInterventionService} (sens unique). Opère sur les entités gérées
 * passées par l'appelant, dans la transaction de celui-ci.</p>
 */
@Singleton
public class SpHistorisationService {

    private final SpVehiculeAffectationRepository affectationRepo;

    public SpHistorisationService(SpVehiculeAffectationRepository affectationRepo) {
        this.affectationRepo = affectationRepo;
    }

    /** Fige tous les engins encore présents (libellé + type) et leurs équipages, en texte. */
    public void snapshotEngins(SpIntervention inter) {
        for (var v : inter.getEngins()) snapshotUnEngin(inter, v);
    }

    /**
     * Fige UN engin et son équipage dans l'historique de l'intervention. Appelé soit à la clôture
     * (engins restants), soit au moment où l'engin quitte l'intervention (réengagement ailleurs ou
     * retrait manuel) — sinon son équipage serait perdu, l'engin n'étant plus rattaché à la clôture.
     */
    public void snapshotUnEngin(SpIntervention inter, SpVehicule v) {
        var engin = new SpInterventionEngin(inter, v.getLibelle(),
                v.getType() != null ? v.getType().getCode() : null, inter.getEnginsHisto().size());
        int eo = 0;
        for (var aff : affectationRepo.findByVehiculeIdAndFinIsNull(v.getId())) {
            var m = aff.getMembre();
            var fonction = aff.getPoste() != null ? aff.getPoste().getFonction() : null;
            String poste = fonction != null ? fonction.getLabel() : null;
            var typeFonction = fonction != null ? fonction.getTypeFonction() : null;
            String nom = (m.getNomComplet() != null && !m.getNomComplet().isBlank())
                    ? m.getNomComplet() : m.getUser().getUsername();
            engin.getEquipage().add(new SpInterventionEquipier(
                    engin, m.getId(), m.getMatricule(), nom, m.getGrade().getLabel(), poste, typeFonction, eo++));
        }
        inter.getEnginsHisto().add(engin);
    }
}
