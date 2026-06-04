package com.bryan.nexis.gendarmerie.datamodel;

import com.bryan.nexis.core.datamodel.AbstractPlanningStatut;
import com.bryan.nexis.core.datamodel.TypeService;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "gn_planning_statut")
public class GnPlanningStatut extends AbstractPlanningStatut {

    protected GnPlanningStatut() {}

    public GnPlanningStatut(String code, String label, String couleur, TypeService categorie) {
        super(code, label, couleur, categorie);
    }
}
