package com.bryan.nexis.backend.realtime;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.core.realtime.RealtimeEventDto;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * Reçoit les {@link RealtimeEvent} émis par les services métier, APRÈS commit de leur
 * transaction (aucun event fantôme en cas de rollback), puis :
 *   1. persiste au journal (main courante / audit),
 *   2. diffuse en WebSocket aux seules sessions concernées par l'audience.
 */
@Singleton
public class RealtimeDispatcher {

    private final JournalService journalService;
    private final WebSocketBroadcaster broadcaster;   // null si le serveur ne supporte pas les WS

    public RealtimeDispatcher(JournalService journalService, @Nullable WebSocketBroadcaster broadcaster) {
        this.journalService = journalService;
        this.broadcaster    = broadcaster;
    }

    @TransactionalEventListener
    public void onEvent(RealtimeEvent event) {
        journalService.record(event);
        if (broadcaster != null) {
            broadcaster.broadcastSync(RealtimeEventDto.from(event), session -> matches(session, event));
        }
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
