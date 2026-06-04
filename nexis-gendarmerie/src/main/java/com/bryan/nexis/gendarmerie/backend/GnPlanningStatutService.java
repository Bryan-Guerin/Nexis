package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.core.backend.dto.PlanningStatutDto;
import com.bryan.nexis.gendarmerie.datarepository.GnPlanningStatutRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class GnPlanningStatutService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final GnPlanningStatutRepository repo;

    public GnPlanningStatutService(GnPlanningStatutRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<PlanningStatutDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(PlanningStatutDto::from).toList();
    }
}
