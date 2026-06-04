package com.bryan.nexis.backend;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.security.Principal;

/**
 * Filtre exécuté <b>après</b> la sécurité (l'utilisateur authentifié est donc connu).
 *
 * <ul>
 *   <li>Renseigne le contexte de log (MDC) avec l'utilisateur à l'origine de l'appel,
 *       repris dans le motif de log via {@code %X{user}}. Toutes les lignes émises
 *       pendant le traitement de la requête (services compris) portent ainsi l'auteur.</li>
 *   <li>Journalise chaque appel web abouti : {@code MÉTHODE chemin >> OK|ERROR (code)}.</li>
 * </ul>
 *
 * <p>Les rejets de sécurité (401 / 403) ne traversent pas ce filtre ; ils sont
 * journalisés par {@link AuthChallengeFilter}.</p>
 */
@ServerFilter("/api/**")
public class RequestLogFilter implements Ordered {

    private static final Logger LOG = LoggerFactory.getLogger("com.bryan.nexis.web");

    @RequestFilter
    public void onRequest(HttpRequest<?> request) {
        // NB : sur le serveur JDK servlet, request.getUserPrincipal() lève
        // UnsupportedOperationException ; on lit donc l'attribut directement.
        String user = request.getAttribute(HttpAttributes.PRINCIPAL, Principal.class)
                .map(Principal::getName).orElse("anonyme");
        MDC.put("user", user);
    }

    @ResponseFilter
    public void onResponse(HttpRequest<?> request, @Nullable HttpResponse<?> response) {
        try {
            int code = response != null ? response.code() : 0;
            String outcome = (code >= 200 && code < 400) ? "OK" : "ERROR";
            LOG.info("{} {} >> {} ({})",
                    request.getMethodName(), request.getPath(), outcome, code);
        } finally {
            MDC.remove("user");
        }
    }

    @Override
    public int getOrder() {
        // Après la sécurité, pour disposer de l'utilisateur authentifié.
        return ServerFilterPhase.SECURITY.after();
    }
}
