package com.bryan.nexis.backend.realtime;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Canal temps réel unique. Le JWT est passé en query-param (`/ws/events?token=…`)
 * car un navigateur ne peut pas poser d'en-tête sur une WebSocket. Il est validé à
 * l'ouverture ; on mémorise username + rôles dans la session pour le ciblage.
 */
@ServerWebSocket("/ws/events")
public class EventWebSocket {

    private final Collection<TokenValidator<HttpRequest<?>>> tokenValidators;

    public EventWebSocket(Collection<TokenValidator<HttpRequest<?>>> tokenValidators) {
        this.tokenValidators = tokenValidators;
    }

    @OnOpen
    public void onOpen(WebSocketSession session, HttpRequest<?> request) {
        String token = request.getParameters().get("token");
        Authentication auth = token == null ? null : authenticate(token, request);
        if (auth == null) {
            session.close(CloseReason.UNSUPPORTED_DATA);
            return;
        }
        session.put("username", auth.getName());
        session.put("roles", new HashSet<>(auth.getRoles()));
    }

    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        // Heartbeat : le client envoie "ping" périodiquement pour garder la connexion ouverte.
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        // rien — Micronaut retire la session de son registre
    }

    private Authentication authenticate(String token, HttpRequest<?> request) {
        for (TokenValidator<HttpRequest<?>> validator : tokenValidators) {
            Authentication auth = firstOrNull(validator.validateToken(token, request));
            if (auth != null) {
                return auth;
            }
        }
        return null;
    }

    /** Récupère le premier élément d'un Publisher (reactive-streams) de façon bloquante, sans Reactor. */
    private static Authentication firstOrNull(Publisher<Authentication> publisher) {
        CompletableFuture<Authentication> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<>() {
            @Override public void onSubscribe(Subscription s) { s.request(1); }
            @Override public void onNext(Authentication a)    { future.complete(a); }
            @Override public void onError(Throwable t)        { future.complete(null); }
            @Override public void onComplete()                { future.complete(null); }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }
}

