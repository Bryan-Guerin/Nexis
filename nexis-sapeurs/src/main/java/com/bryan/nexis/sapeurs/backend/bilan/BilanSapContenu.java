package com.bryan.nexis.sapeurs.backend.bilan;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Contenu typé du bilan SAP (secours à personne), sérialisé en JSON (jsonb) — méthode XABCDE +
 * AVP + schéma corporel (lésions) + SAMPLE. Chaque section est un record dédié ; tout est nullable
 * (saisie partielle). Extensible sans migration : ajouter un champ ne casse pas les bilans existants.
 */
@Serdeable
public record BilanSapContenu(
        @Nullable Hemorragie x,
        @Nullable VoiesAeriennes a,
        @Nullable Ventilation b,
        @Nullable Circulation c,
        @Nullable Neuro d,
        @Nullable Exposition e,
        @Nullable Avp avp,
        @Nullable List<Lesion> lesions,
        @Nullable Sample sample
) {
    /** X — Hémorragie. */
    @Serdeable
    public record Hemorragie(Boolean presente, Boolean active, PerteEstimee perte,
                             Boolean positionAllongee, Boolean compressionManuelle,
                             Boolean pansementCompressif, Boolean garrot) {}

    /** A — Voies aériennes. */
    @Serdeable
    public record VoiesAeriennes(Boolean obstruction, Boolean extractionDigitale, Boolean basculeTete,
                                 Boolean elevationMenton, Boolean canuleOroPharyngee, Boolean aspirationBuccale) {}

    /** B — Ventilation. */
    @Serdeable
    public record Ventilation(Boolean absenceOuFrFaible, Boolean irreguliere, Boolean superficielle,
                              Boolean signesLutte, Boolean cyanose, Boolean difficultesParole,
                              Integer frequenceRespiratoire, Integer spo2AirAmbiant, Integer spo2SousO2,
                              Boolean insufflations, Boolean inhalation) {}

    /** C — Circulation. PNI = pression non invasive (texte « 120/80 »). */
    @Serdeable
    public record Circulation(Boolean arretCirculatoire, Boolean malFrappe, Boolean poulsRadialNonPercu,
                              Boolean irregulier, Boolean froideurExtremites, Boolean paleurCutanee, Boolean trcSup3,
                              Integer frequenceCardiaque, String pniD, String pniG, String pniRef) {}

    /** D — Neurologique. */
    @Serdeable
    public record Neuro(Avpu avpu, Boolean pci, Boolean convulsion,
                        EtatPupille pupilleDroite, EtatPupille pupilleGauche, Boolean pupillesReactives,
                        Boolean troubleSensitif, Boolean troubleMoteur, Boolean resucrage) {}

    /** E — Exposition. */
    @Serdeable
    public record Exposition(Boolean chuteSup2m, Boolean traumatismePenetrant, Boolean sectionMembreTotale,
                             Boolean plaieProfonde, Boolean brulureSup5pct, Boolean brulureElectrique,
                             Boolean brulureChimique, Boolean localisationBrulureAggravante, Double temperature) {}

    /** AVP — circonstances de l'accident. */
    @Serdeable
    public record Avp(SituationArrivee situation, Integer cinetique, PositionVehicule position,
                      LocalisationChoc localisationChoc, Boolean casquee, Boolean ceinturee, Boolean tonneaux) {}

    /** Lésion localisée sur le schéma corporel : position relative (0..1) + type. */
    @Serdeable
    public record Lesion(double x, double y, TypeLesion type) {}

    /** SAMPLE — anamnèse (texte libre). */
    @Serdeable
    public record Sample(String symptomes, String allergies, String medicaments,
                         String dernierRepas, String evenements, String observations) {}

    public enum PerteEstimee     { FAIBLE, IMPORTANTE }
    public enum Avpu             { ALERT, VERBAL, PAIN, UNRESPONSIVE }
    public enum EtatPupille      { MYDRIASE, NORMALE, MYOSIS }
    public enum SituationArrivee { EJECTEE, INCARCEREE, PIEGEE, SORTIE_VEHICULE }
    public enum PositionVehicule { CONDUCTEUR, PASSAGER_AVANT, PASSAGER_ARRIERE }
    public enum LocalisationChoc { FRONTAL, LATERAL_GAUCHE, LATERAL_DROIT, ARRIERE, AUTRE }
    public enum TypeLesion       { DEFORMATION, CONTUSION, ABRASION, HEMORRAGIE, PLAIE, BRULURE, TUMEFACTION, LACERATION, DOULEUR }
}
