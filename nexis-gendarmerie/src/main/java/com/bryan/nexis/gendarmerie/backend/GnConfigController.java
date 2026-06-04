package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.CreateGnGradeRequest;
import com.bryan.nexis.gendarmerie.backend.dto.CreateGnVehiculeEtatRequest;
import com.bryan.nexis.gendarmerie.backend.dto.GnGradeDto;
import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeEtatDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;

@Controller("/api/gn")
@Secured("ROLE_ADMIN_GN")
public class GnConfigController {

    private final GnGradeService       gradeService;
    private final GnVehiculeEtatService etatService;

    public GnConfigController(GnGradeService gradeService, GnVehiculeEtatService etatService) {
        this.gradeService = gradeService;
        this.etatService  = etatService;
    }

    @Get("/grades")
    @Secured("ROLE_GN")
    List<GnGradeDto> listGrades() {
        return gradeService.listAll();
    }

    @Post("/grades")
    @Status(HttpStatus.CREATED)
    GnGradeDto createGrade(@Body CreateGnGradeRequest req) {
        return gradeService.create(req.code(), req.label());
    }

    @Post("/etats")
    @Status(HttpStatus.CREATED)
    GnVehiculeEtatDto createEtat(@Body CreateGnVehiculeEtatRequest req) {
        return etatService.create(req.code(), req.label(), req.couleur());
    }
}
