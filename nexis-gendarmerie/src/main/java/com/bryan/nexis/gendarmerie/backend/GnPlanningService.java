package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.core.backend.AbstractPlanningService;
import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.gendarmerie.datamodel.GnPlanning;
import com.bryan.nexis.gendarmerie.datarepository.GnMembreRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnPlanningRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnPlanningStatutRepository;
import io.micronaut.data.jpa.repository.JpaRepository;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class GnPlanningService extends AbstractPlanningService<GnPlanning> {

    private final GnPlanningRepository       planningRepo;
    private final GnMembreRepository         membreRepo;
    private final GnPlanningStatutRepository statutRepo;

    public GnPlanningService(GnPlanningRepository planningRepo, GnMembreRepository membreRepo,
                             GnPlanningStatutRepository statutRepo) {
        this.planningRepo = planningRepo;
        this.membreRepo   = membreRepo;
        this.statutRepo   = statutRepo;
    }

    @Override protected JpaRepository<GnPlanning, UUID> repo() { return planningRepo; }

    @Override protected List<GnPlanning> findByMembre(UUID membreId) {
        return planningRepo.findByMembreId(membreId);
    }

    @Override protected List<GnPlanning> findOverlapping(UUID membreId, Instant debut, Instant fin) {
        return planningRepo.findOverlapping(membreId, debut, fin);
    }

    @Override protected List<UUID> findMembreIdsEnService(Instant now) {
        return planningRepo.findMembreIdsEnService(now, TypeService.GARDE);
    }

    @Override protected GnPlanning build(UUID membreId, UUID statutId, Instant debut, Instant fin, String notes) {
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre GN introuvable : " + membreId));
        var statut = statutRepo.findById(statutId)
                .orElseThrow(() -> new NoSuchElementException("Statut planning introuvable : " + statutId));
        var planning = new GnPlanning(membre, debut, fin, statut);
        planning.setNotes(notes);
        return planning;
    }
}
