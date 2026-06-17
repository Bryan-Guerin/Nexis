package com.bryan.nexis.backend.error;

import com.bryan.nexis.backend.dto.ApiError;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

/**
 * Traduit les exceptions métier en réponses HTTP propres (statut adéquat + message
 * lisible), au lieu d'un 500 générique. Le front lit simplement {@code body.message}.
 */
public final class BusinessExceptionHandlers {

    private BusinessExceptionHandlers() {}

    /** Règle métier violée (conflit de planning, capacité atteinte, non qualifié…). */
    @Produces
    @Singleton
    @Requires(classes = {IllegalStateException.class, ExceptionHandler.class})
    public static class IllegalState implements ExceptionHandler<IllegalStateException, HttpResponse<?>> {
        @Override
        public HttpResponse<?> handle(HttpRequest request, IllegalStateException e) {
            return HttpResponse.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
        }
    }

    /** Requête invalide (argument incorrect). */
    @Produces
    @Singleton
    @Requires(classes = {IllegalArgumentException.class, ExceptionHandler.class})
    public static class IllegalArgument implements ExceptionHandler<IllegalArgumentException, HttpResponse<?>> {
        @Override
        public HttpResponse<?> handle(HttpRequest request, IllegalArgumentException e) {
            return HttpResponse.badRequest(new ApiError(e.getMessage()));
        }
    }

    /** Ressource introuvable. */
    @Produces
    @Singleton
    @Requires(classes = {NoSuchElementException.class, ExceptionHandler.class})
    public static class NotFound implements ExceptionHandler<NoSuchElementException, HttpResponse<?>> {
        @Override
        public HttpResponse<?> handle(HttpRequest request, NoSuchElementException e) {
            return HttpResponse.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
        }
    }

    /**
     * Filet de sécurité : toute exception non gérée → 500 + log de la stack trace
     * complète côté serveur (sinon Micronaut peut absorber et ne rien afficher).
     * Le message technique est renvoyé tel quel au front (utile en dev ; à muter
     * en "Erreur interne" pour la prod si on veut masquer).
     */
    @Produces
    @Singleton
    @Requires(classes = {RuntimeException.class, ExceptionHandler.class})
    public static class Generic implements ExceptionHandler<RuntimeException, HttpResponse<?>> {
        private static final Logger LOG = LoggerFactory.getLogger(Generic.class);
        @Override
        public HttpResponse<?> handle(HttpRequest request, RuntimeException e) {
            LOG.error("Erreur non gérée sur {} {} : {}", request.getMethod(), request.getPath(), e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return HttpResponse.serverError(new ApiError(msg));
        }
    }
}
