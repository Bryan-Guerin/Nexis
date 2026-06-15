package com.bryan.nexis.backend;

import com.bryan.nexis.core.backend.AccountRevocation;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

import java.security.Principal;

/**
 * Rejette (401) toute requête authentifiée d'un compte désactivé (effectif radié).
 * Le JWT reste « techniquement valide » jusqu'à expiration ; ce re-check rend la
 * désactivation effective immédiatement, sans révocation de token.
 *
 * <p>Contrôle en mémoire via {@link AccountRevocation} : aucun I/O sur le chemin chaud,
 * donc s'exécute sans risque sur l'event-loop (pas de DB, pas de pression sur le pool).
 * Le front, sur 401, déconnecte et redirige vers le login.</p>
 */
@ServerFilter("/api/**")
public class ActiveAccountFilter implements Ordered {

    private final AccountRevocation revocation;

    public ActiveAccountFilter(AccountRevocation revocation) {
        this.revocation = revocation;
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> onRequest(HttpRequest<?> request) {
        String username = request.getAttribute(HttpAttributes.PRINCIPAL, Principal.class)
                .map(Principal::getName).orElse(null);
        if (username == null) {
            return null; // requête non authentifiée : la sécurité s'en charge
        }
        return revocation.isRevoked(username) ? HttpResponse.unauthorized() : null;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.after();   // principal authentifié déjà résolu
    }
}
