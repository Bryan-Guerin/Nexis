package com.bryan.nexis.core.realtime;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;

/** Charge utile envoyée aux clients WebSocket (sans l'audience interne). */
@Serdeable
public record RealtimeEventDto(
        String type,
        String faction,
        String message,
        Map<String, String> payload,
        String actorUsername,
        Instant timestamp
) {
    public static RealtimeEventDto from(RealtimeEvent e) {
        return new RealtimeEventDto(e.getType(), e.getFaction(), e.getMessage(),
                e.getPayload(), e.getActorUsername(), e.getTimestamp());
    }
}
