package com.bryan.nexis.backend;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Map;

/**
 * Filtre exécuté <b>après</b> la sécurité (l'utilisateur authentifié est connu) :
 *
 * <ol>
 *   <li>installe l'utilisateur dans le <b>contexte propagé</b> Micronaut via
 *       {@link MdcPropagationContext}. Micronaut le ré-applique sur chaque thread où la
 *       requête s'exécute (controller {@code @ExecuteOn(BLOCKING)}, services, réactif) →
 *       {@code %X{user}} est rempli partout, sans {@code MDC.put/remove} dans le métier ;</li>
 *   <li>journalise chaque appel abouti : {@code MÉTHODE chemin >> OK|ERROR (code)}.</li>
 * </ol>
 *
 * <p>Les rejets de sécurité (401 / 403) ne traversent pas ce filtre ; ils sont journalisés
 * par {@code AuthChallengeFilter}.</p>
 */
@Filter("/api/**")
public class RequestContextFilter implements HttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger("com.bryan.nexis.web");

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.after();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        // NB : request.getUserPrincipal() lève UnsupportedOperationException ici ; on lit l'attribut.
        String user = request.getAttribute(HttpAttributes.PRINCIPAL, Principal.class)
                .map(Principal::getName).orElse("anonyme");

        // Le contexte est capté par chain.proceed() et restauré sur chaque thread aval.
        PropagatedContext ctx = PropagatedContext.getOrEmpty()
                .plus(new MdcPropagationContext(Map.of("user", user)));
        try (PropagatedContext.Scope ignore = ctx.propagate()) {
            return Publishers.map(chain.proceed(request), response -> {
                int code = response.code();
                String outcome = (code >= 200 && code < 400) ? "OK" : "ERROR";
                LOG.info("{} {} >> {} ({})", request.getMethodName(), request.getPath(), outcome, code);
                return response;
            });
        }
    }
}
