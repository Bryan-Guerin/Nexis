package com.bryan.nexis.backend;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * Filtre exécuté <b>avant</b> la sécurité (donc sa phase de réponse enveloppe celle
 * du filtre de sécurité, même lorsqu'il court-circuite la requête).
 *
 * <p>Rôles :</p>
 * <ul>
 *   <li>Supprimer l'en-tête {@code WWW-Authenticate: Basic} des réponses 401 : c'est
 *       lui qui déclenche la boîte de dialogue native « login / mot de passe » du
 *       navigateur. Sans cet en-tête, le front reçoit proprement le 401 et redirige
 *       vers l'écran de connexion.</li>
 *   <li>Journaliser les rejets d'authentification / d'autorisation (401 / 403), qui ne
 *       parviennent jamais jusqu'à {@link RequestLogFilter} (situé après la sécurité).</li>
 * </ul>
 */
@ServerFilter("/api/**")
public class AuthChallengeFilter implements Ordered {

    private static final Logger LOG = LoggerFactory.getLogger("com.bryan.nexis.web");

    @ResponseFilter
    public void onResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        // Supprime le défi Basic qui provoque la pop-up native du navigateur
        response.getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);

        int code = response.code();
        if (code == 401 || code == 403) {
            // NB : sur le serveur JDK servlet, request.getUserPrincipal() lève
            // UnsupportedOperationException ; on lit donc l'attribut directement.
            String user = request.getAttribute(HttpAttributes.PRINCIPAL, Principal.class)
                    .map(Principal::getName).orElse("anonyme");
            LOG.warn("{} {} >> REFUSÉ ({}) [{}]",
                    request.getMethodName(), request.getPath(), code, user);
        }
    }

    @Override
    public int getOrder() {
        // Avant la sécurité : la phase « réponse » enveloppe ainsi le rejet de sécurité.
        return ServerFilterPhase.FIRST.order();
    }
}
