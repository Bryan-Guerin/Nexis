package com.bryan.nexis.backend;

import com.bryan.nexis.backend.dto.CreateRoleRequest;
import com.bryan.nexis.backend.dto.CreateUserRequest;
import com.bryan.nexis.backend.dto.ResetPasswordResponse;
import com.bryan.nexis.backend.dto.UpdateUserRolesRequest;
import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.RefRoleService;
import com.bryan.nexis.core.backend.RefUserService;
import com.bryan.nexis.core.backend.dto.RefRoleDto;
import com.bryan.nexis.core.backend.dto.RefUserDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.utils.SecurityService;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Controller("/api/admin")
@Secured("ROLE_SYSTEM")
public class AdminUserController {

    private static final char[] PWD_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final int PWD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefUserService userService;
    private final RefRoleService roleService;
    private final JournalService journalService;
    private final SecurityService securityService;

    public AdminUserController(RefUserService userService, RefRoleService roleService,
                              JournalService journalService, SecurityService securityService) {
        this.userService = userService;
        this.roleService = roleService;
        this.journalService = journalService;
        this.securityService = securityService;
    }

    private String actor() { return securityService.username().orElse("?"); }

    // ── Utilisateurs ────────────────────────────────────────────────────────────

    @Get("/users")
    List<RefUserDto> listUsers() {
        return userService.listAll();
    }

    @Post("/users")
    @Status(HttpStatus.CREATED)
    RefUserDto createUser(@Body CreateUserRequest req) {
        String hash = BCrypt.hashpw(req.password(), BCrypt.gensalt(12));
        var created = userService.create(req.username(), hash, req.roles() != null ? req.roles() : Set.of());
        journalService.record("USER_CREE", null, actor(), "Utilisateur créé : " + created.username());
        return created;
    }

    @Patch("/users/{id}/roles")
    RefUserDto updateUserRoles(UUID id, @Body UpdateUserRolesRequest req) {
        var updated = userService.updateRoles(id, req.roles() != null ? req.roles() : Set.of());
        journalService.record("USER_ROLES", null, actor(),
                "Rôles modifiés : " + updated.username() + " → " + updated.roles());
        return updated;
    }

    @Post("/users/{id}/reset-password")
    ResetPasswordResponse resetPassword(UUID id) {
        String password = generatePassword();
        userService.updatePasswordHash(id, BCrypt.hashpw(password, BCrypt.gensalt(12)));
        journalService.record("MDP_RESET", null, actor(), "Réinitialisation du mot de passe (utilisateur " + id + ")");
        return new ResetPasswordResponse(password);
    }

    // ── Rôles ─────────────────────────────────────────────────────────────────

    @Get("/roles")
    List<RefRoleDto> listRoles() {
        return roleService.listAll();
    }

    @Post("/roles")
    @Status(HttpStatus.CREATED)
    RefRoleDto createRole(@Body CreateRoleRequest req) {
        var created = roleService.create(req.code(), req.label(), req.parentCode());
        journalService.record("ROLE_CREE", null, actor(), "Rôle créé : " + created.code());
        return created;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(PWD_LENGTH);
        for (int i = 0; i < PWD_LENGTH; i++) {
            sb.append(PWD_ALPHABET[RANDOM.nextInt(PWD_ALPHABET.length)]);
        }
        return sb.toString();
    }
}
