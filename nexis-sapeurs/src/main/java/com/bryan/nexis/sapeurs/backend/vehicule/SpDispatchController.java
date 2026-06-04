package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;

import java.util.List;

@Secured("ROLE_SP")
@Controller("/api/sp/dispatch")
public class SpDispatchController {

    private final SpDispatchService dispatchService;

    public SpDispatchController(SpDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Get
    public List<SpDispatchDto> listDispatch() {
        return dispatchService.listDispatch();
    }
}
