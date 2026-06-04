package com.bryan.nexis.sapeurs.backend.user;

import com.bryan.nexis.core.backend.RefUserService;
import com.bryan.nexis.core.backend.dto.RefUserDto;
import com.bryan.nexis.sapeurs.backend.dto.CreateFactionUserRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Set;

@Controller("/api/sp/users")
@Secured("ROLE_ADMIN_SP")
public class SpUserController {

    private final RefUserService userService;

    public SpUserController(RefUserService userService) {
        this.userService = userService;
    }

    @Post
    @Status(HttpStatus.CREATED)
    RefUserDto createUser(@Body CreateFactionUserRequest req) {
        String hash = BCrypt.hashpw(req.password(), BCrypt.gensalt(12));
        return userService.create(req.username(), hash, Set.of("ROLE_SP"));
    }
}
