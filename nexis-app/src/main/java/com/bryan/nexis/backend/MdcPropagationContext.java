package com.bryan.nexis.backend;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Élément du contexte propagé Micronaut qui transporte l'état MDC à travers les sauts de
 * thread (event loop → {@code @ExecuteOn(BLOCKING)}/virtual thread → réactif).
 *
 * <p>Micronaut appelle {@link #updateThreadContext()} chaque fois que le contexte devient
 * courant sur un thread, et {@link #restoreThreadContext} en sortie. Le MDC (thread-local)
 * est ainsi présent sur TOUS les threads d'une requête — filtres, controller, services
 * profonds — sans {@code MDC.put/remove} dispersé dans le code métier. {@code %X{user}}
 * est donc rempli partout.</p>
 */
final class MdcPropagationContext implements ThreadPropagatedContextElement<Map<String, String>> {

    private final Map<String, String> state;

    MdcPropagationContext(Map<String, String> state) {
        this.state = state;
    }

    @Override
    public Map<String, String> updateThreadContext() {
        Map<String, String> previous = MDC.getCopyOfContextMap();   // null si vide
        if (state == null || state.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(state);
        }
        return previous;
    }

    @Override
    public void restoreThreadContext(@Nullable Map<String, String> previous) {
        if (previous == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
