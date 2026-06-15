package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.datarepository.RefUserRepository;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liste de révocation en mémoire des comptes désactivés (usernames).
 *
 * <p>Remplace la lecture DB par requête du filtre d'authentification : le contrôle devient
 * un lookup O(1) en mémoire, donc aucun I/O bloquant sur le chemin chaud et aucune pression
 * sur le pool de connexions. Désactivation effective immédiate (la radiation met à jour le set).</p>
 *
 * <p>Chargée au démarrage, puis maintenue à jour par le service qui (dé)active un compte.
 * Une modification de {@code enabled} faite hors application (SQL direct) ne sera prise en compte
 * qu'au prochain démarrage — acceptable, la gestion des comptes passe par l'application.</p>
 */
@Singleton
public class AccountRevocation implements ApplicationEventListener<StartupEvent> {

    private final RefUserRepository userRepo;
    private final Set<String> disabled = ConcurrentHashMap.newKeySet();

    public AccountRevocation(RefUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public void onApplicationEvent(StartupEvent event) {
        disabled.clear();
        userRepo.findAll().stream()
                .filter(u -> !u.isEnabled())
                .forEach(u -> disabled.add(u.getUsername()));
    }

    /** Le compte est-il révoqué (désactivé) ? Lookup mémoire, sans DB. */
    public boolean isRevoked(String username) {
        return username != null && disabled.contains(username);
    }

    /** À appeler quand un compte est (ré)activé/désactivé pour garder le set à jour. */
    public void set(String username, boolean enabled) {
        if (username == null) return;
        if (enabled) disabled.remove(username);
        else disabled.add(username);
    }
}
