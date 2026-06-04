package com.bryan.nexis.sapeurs.backend.effectif;

import java.util.UUID;

/**
 * Réagit à la suppression d'une fonction SP. Chaque service possédant une dépendance
 * sur les fonctions en fournit une implémentation (injectée en liste par
 * {@link SpFonctionService}) : il peut <b>empêcher</b> la suppression (en levant une
 * {@link IllegalStateException}) ou <b>cascader</b> sur ses propres données.
 *
 * <p>Pattern de décoration par injection : la suppression d'une fonction n'a pas besoin
 * de connaître ses dépendants ; ce sont eux qui s'enregistrent.</p>
 */
public interface SpFonctionDeletionListener {
    void onDeleteFonction(UUID fonctionId);
}
