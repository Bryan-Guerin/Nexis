package com.bryan.nexis.backend.audit;

import com.bryan.nexis.core.backend.JournalService;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.event.LoginFailedEvent;
import io.micronaut.security.event.LoginSuccessfulEvent;
import jakarta.inject.Singleton;

/** Journalise les connexions (réussies et échouées) pour l'audit. */
@Singleton
public class LoginAuditListener {

    private final JournalService journal;

    public LoginAuditListener(JournalService journal) {
        this.journal = journal;
    }

    @EventListener
    void onLoginSuccess(LoginSuccessfulEvent event) {
        Object source = event.getSource();
        String username = (source instanceof Authentication auth) ? auth.getName() : String.valueOf(source);
        journal.record("LOGIN", null, username, "Connexion de " + username);
    }

    @EventListener
    void onLoginFailed(LoginFailedEvent event) {
        String username = "?";
        try {
            var req = event.getAuthenticationRequest();
            if (req != null && req.getIdentity() != null) {
                username = req.getIdentity().toString();
            }
        } catch (Exception ignore) { /* identité indisponible */ }
        journal.record("LOGIN_FAILED", null, username, "Échec de connexion (mauvais identifiant/mot de passe) : " + username);
    }
}
