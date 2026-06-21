package com.bryan.nexis.core.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Événement de domaine publié par les services métier (via ApplicationEventPublisher),
 * puis persisté au journal + diffusé en WebSocket par le dispatcher de l'application.
 *
 * <p>Constantes de type usuelles fournies ; les modules peuvent en définir d'autres.</p>
 */
public class RealtimeEvent {

    public static final String AFFECTATION          = "AFFECTATION";
    public static final String DESAFFECTATION       = "DESAFFECTATION";
    public static final String ETAT_VEHICULE        = "ETAT_VEHICULE";
    public static final String INVENTAIRE           = "INVENTAIRE";
    public static final String PLANNING             = "PLANNING";
    public static final String PAIE                 = "PAIE";
    public static final String PAIE_VERSEE          = "PAIE_VERSEE";
    public static final String MAIN_COURANTE        = "MAIN_COURANTE";
    public static final String BIP                  = "BIP";
    public static final String INTERVENTION_OUVERTE  = "INTERVENTION_OUVERTE";
    public static final String INTERVENTION_RENFORT  = "INTERVENTION_RENFORT";
    public static final String INTERVENTION_CLOTUREE = "INTERVENTION_CLOTUREE";
    public static final String CRI_MAJ               = "CRI_MAJ";

    private final String type;
    private final String faction;            // "GN" / "SP" / null
    private final Scope scope;
    private final Set<String> recipients;    // usernames (scope USERS)
    private final String message;
    private final Map<String, String> payload;
    private final String actorUsername;
    private final Instant timestamp;
    private String reference;                // lien facultatif (ex. code intervention)
    private boolean ephemere;                // true = diffusé en WS mais NON journalisé (pas de main courante)

    private RealtimeEvent(String type, String faction, Scope scope, Set<String> recipients,
                          String message, Map<String, String> payload, String actorUsername) {
        this.type          = type;
        this.faction       = faction;
        this.scope         = scope;
        this.recipients    = recipients != null ? recipients : Set.of();
        this.message       = message;
        this.payload       = payload != null ? payload : Map.of();
        this.actorUsername = actorUsername;
        this.timestamp     = Instant.now();
    }

    /** Événement diffusé à toute une faction (ex. mise à jour du dispatch). */
    public static RealtimeEvent faction(String type, String faction, String message,
                                        Map<String, String> payload, String actorUsername) {
        return new RealtimeEvent(type, faction, Scope.FACTION, Set.of(), message, payload, actorUsername);
    }

    /** Événement adressé à des utilisateurs nominatifs (ex. équipage bipé). */
    public static RealtimeEvent users(String type, String faction, Set<String> recipients, String message,
                                      Map<String, String> payload, String actorUsername) {
        return new RealtimeEvent(type, faction, Scope.USERS, recipients, message, payload, actorUsername);
    }

    /** Attache une référence (ex. code intervention) ; renvoie l'événement (fluent). */
    public RealtimeEvent withReference(String reference) {
        this.reference = reference;
        return this;
    }

    /** Marque l'événement comme éphémère : diffusé en temps réel mais non persisté au journal. */
    public RealtimeEvent ephemere() {
        this.ephemere = true;
        return this;
    }

    public String getType()               { return type; }
    public String getFaction()            { return faction; }
    public Scope getScope()               { return scope; }
    public Set<String> getRecipients()    { return recipients; }
    public String getMessage()            { return message; }
    public Map<String, String> getPayload() { return payload; }
    public String getActorUsername()      { return actorUsername; }
    public Instant getTimestamp()         { return timestamp; }
    public String getReference()          { return reference; }
    public boolean isEphemere()           { return ephemere; }
}
