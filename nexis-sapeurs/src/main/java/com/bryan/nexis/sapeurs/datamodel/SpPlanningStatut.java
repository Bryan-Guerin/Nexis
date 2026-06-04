package com.bryan.nexis.sapeurs.datamodel;

import com.bryan.nexis.core.datamodel.AbstractPlanningStatut;
import com.bryan.nexis.core.datamodel.TypeService;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sp_planning_statut")
public class SpPlanningStatut extends AbstractPlanningStatut {

    protected SpPlanningStatut() {}

    public SpPlanningStatut(String code, String label, String couleur, TypeService categorie) {
        super(code, label, couleur, categorie);
    }
}
