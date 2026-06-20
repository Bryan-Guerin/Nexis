package com.bryan.nexis.sapeurs.backend.bilan;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Contenu typé du bilan SAP (secours à personne), sérialisé en JSON dans {@code sp_bilan.contenu}.
 *
 * <p>Squelette de la phase 1 (4 champs représentant le « petit jeu de types de réponse » :
 * booléen, choix unique, numérique, texte). Les sections complètes X-A-B-C-D-E / AVP / SAMPLE et
 * les lésions localisées arrivent en phase 2. Extensible : ajouter un champ ne casse pas les
 * bilans déjà enregistrés (champ absent → {@code null} à la désérialisation).</p>
 */
@Serdeable
public record BilanSapContenu(
        Boolean hemorragie,             // O/N (booléen)
        PerteEstimee perteEstimee,      // choix unique (enum)
        Integer frequenceRespiratoire,  // valeur numérique
        String observations             // texte libre
) {
    public enum PerteEstimee { FAIBLE, IMPORTANTE }
}
