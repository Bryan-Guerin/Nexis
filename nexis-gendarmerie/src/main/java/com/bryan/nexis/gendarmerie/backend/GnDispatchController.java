package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnDispatchDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.List;

@Secured("ROLE_GN")
@Controller("/api/gn/dispatch")
public class GnDispatchController {

    private final GnDispatchService dispatchService;

    public GnDispatchController(GnDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Get
    public List<GnDispatchDto> listDispatch() {
        return dispatchService.listDispatch();
    }
}
