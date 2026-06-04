package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.core.backend.RefUserService;
import com.bryan.nexis.core.backend.dto.RefUserDto;
import com.bryan.nexis.gendarmerie.backend.dto.CreateFactionUserRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Set;

@Controller("/api/gn/users")
@Secured("ROLE_ADMIN_GN")
public class GnUserController {

    private final RefUserService userService;

    public GnUserController(RefUserService userService) {
        this.userService = userService;
    }

    @Post
    @Status(HttpStatus.CREATED)
    RefUserDto createUser(@Body CreateFactionUserRequest req) {
        String hash = BCrypt.hashpw(req.password(), BCrypt.gensalt(12));
        return userService.create(req.username(), hash, Set.of("ROLE_GN"));
    }
}
