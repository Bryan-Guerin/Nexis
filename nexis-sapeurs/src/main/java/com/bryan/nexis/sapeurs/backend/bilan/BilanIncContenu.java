package com.bryan.nexis.sapeurs.backend.bilan;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Contenu typé du bilan INC — feu de forêt (jsonb, 1 par intervention). Sinistre + propagation +
 * enjeux + moyens (hydrauliques/aériens) + technique + tracé cartographique de la zone brûlée
 * (polygone en coordonnées monde Arma [lat,lng], mètres). Les lances référencent les engins de
 * l'intervention par leur vehiculeId (pas de duplication).
 */
@Serdeable
public record BilanIncContenu(
        @Nullable Sinistre sinistre,
        @Nullable Propagation propagation,
        @Nullable Enjeux enjeux,
        @Nullable MoyensHydrauliques hydraulique,
        @Nullable MoyensAeriens aeriens,
        @Nullable Technique technique,
        @Nullable List<List<Double>> polygone   // sommets [lat, lng] (coords monde, mètres)
) {
    @Serdeable
    public record Sinistre(Double surfaceBrulee, SurfaceSource surfaceBruleeSource, Double surfaceMenacee,
                           List<Couvert> couvert, EtatFeu etat,
                           String heureDebut, String heureMaitrise, String heureExtinction) {}

    @Serdeable
    public record Propagation(Direction direction, Double vitesse, Double longueurFront,
                              Direction ventDirection, String ventForce, Topographie topographie, Double pentePct) {}

    @Serdeable
    public record Enjeux(Boolean habitations, Boolean route, Boolean ligneElec, Boolean autreSite, String autreSiteRef) {}

    /** Lance établie : type + engin source (réf vehiculeId d'un engin de l'intervention) + débit. */
    @Serdeable
    public record Lance(TypeLance type, String enginVehiculeId, Double debit) {}

    @Serdeable
    public record PointEau(TypePointEau type, String ref) {}

    @Serdeable
    public record MoyensHydrauliques(List<Lance> lances, Double eauConsommee, UniteEau eauUnite, List<PointEau> pointsEau) {}

    @Serdeable
    public record MoyensAeriens(Boolean engages, Integer nbLargages) {}

    public enum SurfaceSource { TRACE, MANUEL }
    public enum Couvert       { HERBE, BROUSSAILLES, RESINEUX, FEUILLUS, CULTURES }
    public enum EtatFeu       { EN_COURS, MAITRISE, ETEINT, SOUS_SURVEILLANCE }
    public enum Direction     { N, NE, E, SE, S, SO, O, NO }
    public enum Topographie   { PLAT, MONTANT, DESCENDANT }
    public enum TypeLance     { LDV, LDT, AUTRE }
    public enum TypePointEau  { PEI, CITERNE, NATUREL }
    public enum UniteEau      { L, M3 }
    public enum Technique     { DIRECTE, INDIRECTE, FEU_TACTIQUE, NOYAGE }
}
