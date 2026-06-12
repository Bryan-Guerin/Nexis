package com.bryan.nexis.sapeurs;

import com.bryan.nexis.core.datamodel.RefUser;
import com.bryan.nexis.sapeurs.datamodel.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

/**
 * Fabrique d'entités pour les tests unitaires de la mécanique dispatch.
 * Les identifiants étant générés par JPA, ils sont injectés par réflexion
 * (les règles métier comparent systématiquement par getId()).
 */
public final class TestFixtures {

    private TestFixtures() {}

    /** Injecte l'id (champ JPA généré) par réflexion. */
    public static <T> T withId(T entity, UUID id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Impossible d'injecter l'id de test", e);
        }
    }

    public static SpVehiculeType type(String code) {
        return withId(new SpVehiculeType(code, code), UUID.randomUUID());
    }

    public static SpFonction fonction(String code) {
        return withId(new SpFonction(code, code), UUID.randomUUID());
    }

    public static SpVehiculeTypePoste poste(SpVehiculeType type, SpFonction fonction, int places, boolean obligatoire) {
        return withId(new SpVehiculeTypePoste(type, fonction, (short) places, obligatoire), UUID.randomUUID());
    }

    public static SpVehiculeEtat etat(String code) {
        return withId(new SpVehiculeEtat(code, code, "#000000"), UUID.randomUUID());
    }

    public static SpVehiculeStatut statut(String code, SpVehiculeEtat etat) {
        return withId(new SpVehiculeStatut(code, code, "#000000", etat), UUID.randomUUID());
    }

    public static SpVehicule vehicule(SpVehiculeType type, String libelle, SpVehiculeEtat etat, SpVehiculeStatut statut) {
        return withId(new SpVehicule(type, libelle, etat, statut), UUID.randomUUID());
    }

    public static SpMembre membre(String username, int compteur) {
        var user  = new RefUser(username, "hash");
        var grade = withId(new SpGrade("SGT", "Sergent"), UUID.randomUUID());
        return withId(new SpMembre(user, grade, "SPV", 0, compteur), UUID.randomUUID());
    }

    /** Ajoute une qualification (fonction) au membre. */
    public static void qualifier(SpMembre membre, SpFonction fonction) {
        membre.getQualifications().add(new SpMembreQualification(membre, fonction, "test"));
    }

    public static SpVehiculeAffectation affectation(SpVehicule vehicule, SpMembre membre, SpVehiculeTypePoste poste) {
        return withId(new SpVehiculeAffectation(vehicule, membre, poste, Instant.now()), UUID.randomUUID());
    }

    public static SpIntervention intervention(String motif, SpVehicule... engins) {
        var inter = withId(new SpIntervention(motif, "test"), UUID.randomUUID());
        inter.setNature(withId(new SpNatureIntervention("DIV", "Divers"), UUID.randomUUID()));
        inter.setNumero(1);
        for (var e : engins) inter.getEngins().add(e);
        return inter;
    }
}
