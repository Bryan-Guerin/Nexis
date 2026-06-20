package com.bryan.nexis.backend.realtime;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.core.realtime.RealtimeEventDto;
import com.bryan.nexis.core.realtime.Scope;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Reçoit les {@link RealtimeEvent} émis par les services métier, APRÈS commit de leur
 * transaction (aucun event fantôme en cas de rollback), puis :
 *   1. persiste au journal (main courante / audit),
 *   2. diffuse en WebSocket aux seules sessions concernées par l'audience.
 */
@Singleton
public class RealtimeDispatcher {

    /** Logger « main courante » : trace lisible des événements métier dans la console serveur. */
    private static final Logger JOURNAL = LoggerFactory.getLogger("com.bryan.nexis.maincourante");

    private final JournalService journalService;
    private final WebSocketBroadcaster broadcaster;   // null si le serveur ne supporte pas les WS

    public RealtimeDispatcher(JournalService journalService, @Nullable WebSocketBroadcaster broadcaster) {
        this.journalService = journalService;
        this.broadcaster    = broadcaster;
    }

    @TransactionalEventListener
    public void onEvent(RealtimeEvent event) {
        if (!event.isEphemere()) {
            journalService.record(event);   // éphémère : diffusé sans trace journal
            logJournal(event);
        }
        if (broadcaster != null) {
            broadcaster.broadcastSync(RealtimeEventDto.from(event), session -> matches(session, event));
        }
    }

    /** Trace chaque événement journalisé (création/clôture d'inter, affectation, statut, bip…). */
    private void logJournal(RealtimeEvent event) {
        var ctx = new StringBuilder();
        if (event.getActorUsername() != null) ctx.append(" · par ").append(event.getActorUsername());
        if (event.getScope() == Scope.USERS && event.getRecipients() != null && !event.getRecipients().isEmpty())
            ctx.append(" · à ").append(String.join(", ", event.getRecipients()));
        if (event.getReference() != null) ctx.append(" · ").append(event.getReference());
        JOURNAL.info("[{}] {}{}", event.getType(), event.getMessage(), ctx);
    }

    @SuppressWarnings("unchecked")
    private boolean matches(WebSocketSession session, RealtimeEvent event) {
        switch (event.getScope()) {
            case ALL:
                return true;
            case FACTION:
                Set<String> roles = session.get("roles", Set.class).orElse(Set.of());
                return event.getFaction() != null && roles.contains("ROLE_" + event.getFaction());
            case USERS:
                String username = session.get("username", String.class).orElse(null);
                return username != null && event.getRecipients().contains(username);
            default:
                return false;
        }
    }
}
