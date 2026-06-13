package com.bryan.nexis.backend;

import io.micronaut.core.annotation.Nullable;
import com.bryan.nexis.core.datamodel.RefUser;
import com.bryan.nexis.core.datarepository.RefUserRepository;
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
 * <p>Sans cache : une lecture indexée sur le username par requête (charge négligeable
 * à l'échelle du serveur). Le front, sur 401, déconnecte et redirige vers le login.</p>
 */
@ServerFilter("/api/**")
public class ActiveAccountFilter implements Ordered {

    private final RefUserRepository userRepo;

    public ActiveAccountFilter(RefUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> onRequest(HttpRequest<?> request) {
        String username = request.getAttribute(HttpAttributes.PRINCIPAL, Principal.class)
                .map(Principal::getName).orElse(null);
        if (username == null) {
            return null; // requête non authentifiée : la sécurité s'en charge
        }
        boolean actif = userRepo.findByUsername(username).map(RefUser::isEnabled).orElse(false);
        return actif ? null : HttpResponse.unauthorized();
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.after();   // principal authentifié déjà résolu
    }
}
