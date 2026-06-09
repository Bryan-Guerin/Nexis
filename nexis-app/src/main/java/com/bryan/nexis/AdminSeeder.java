package com.bryan.nexis;

import com.bryan.nexis.core.backend.RefUserService;
import com.bryan.nexis.core.datarepository.RefUserRepository;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Crée un compte administrateur initial au démarrage SI la table ref_user est vide.
 *
 * Identifiants par défaut : admin / root (ROLE_SYSTEM).
 * ⚠️ Le mot de passe DOIT être changé après la première connexion
 *    (menu utilisateur → « Modifier mon mot de passe »).
 *
 * Idempotent : ne fait rien dès qu'au moins un utilisateur existe (relances sans risque).
 */
@Singleton
public class AdminSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(AdminSeeder.class);

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "root";

    private final RefUserRepository userRepo;
    private final RefUserService userService;

    public AdminSeeder(RefUserRepository userRepo, RefUserService userService) {
        this.userRepo = userRepo;
        this.userService = userService;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        if (userRepo.count() > 0) {
            return; // des utilisateurs existent déjà → aucun seed
        }
        String hash = BCrypt.hashpw(DEFAULT_PASSWORD, BCrypt.gensalt(12));
        userService.create(DEFAULT_USERNAME, hash, Set.of("ROLE_SYSTEM"));
        LOG.warn("Administrateur initial créé : '{}' / '{}' (ROLE_SYSTEM). "
                + "CHANGEZ CE MOT DE PASSE dès la première connexion !", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
