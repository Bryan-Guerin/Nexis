package com.bryan.nexis.backend;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.backend.dto.ChangePasswordRequest;
import com.bryan.nexis.backend.dto.UpdateAvatarRequest;
import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.RefUserService;
import com.bryan.nexis.core.backend.dto.RefUserDto;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import org.mindrot.jbcrypt.BCrypt;

/** Compte de l'utilisateur connecté (toutes factions confondues). */
@Controller("/api/account")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AccountController {

    private final RefUserService userService;
    private final JournalService journalService;

    public AccountController(RefUserService userService, JournalService journalService) {
        this.userService = userService;
        this.journalService = journalService;
    }

    /** Profil courant (username, rôles, avatar). */
    @Get
    RefUserDto me(Authentication auth) {
        return userService.me(auth.getName());
    }

    @Patch("/avatar")
    RefUserDto updateAvatar(Authentication auth, @Body UpdateAvatarRequest req) {
        return userService.updateAvatar(auth.getName(), req.avatar());
    }

    @Post("/password")
    HttpResponse<?> changePassword(Authentication auth, @Body ChangePasswordRequest req) {
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            return HttpResponse.badRequest("Le nouveau mot de passe doit faire au moins 6 caractères");
        }
        try {
            userService.changeOwnPassword(
                    auth.getName(),
                    hash -> BCrypt.checkpw(req.currentPassword(), hash),
                    BCrypt.hashpw(req.newPassword(), BCrypt.gensalt(12)));
            journalService.record("MDP_CHANGE", null, auth.getName(), "Changement de mot de passe");
            return HttpResponse.noContent();
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(e.getMessage());
        }
    }
}
