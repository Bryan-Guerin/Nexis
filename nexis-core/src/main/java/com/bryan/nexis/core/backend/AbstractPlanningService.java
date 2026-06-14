package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.backend.dto.PlanningDto;
import com.bryan.nexis.core.datamodel.AbstractPlanning;
import io.micronaut.data.jpa.repository.JpaRepository;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Logique de planning commune à toutes les factions : lecture, création (avec
 * contrôle de chevauchement) et détermination des membres « en service ».
 *
 * <p>Chaque module fournit, via les méthodes abstraites, son repository typé et
 * la construction de son entité concrète. La projection vers {@link PlanningDto}
 * et l'orchestration restent ici, en un seul endroit.</p>
 */
public abstract class AbstractPlanningService<P extends AbstractPlanning> {

    protected abstract JpaRepository<P, UUID> repo();
    protected abstract List<P> findByMembre(UUID membreId);
    protected abstract List<P> findOverlapping(UUID membreId, Instant debut, Instant fin);
    /** Identifiants des membres ayant une plage de catégorie GARDE couvrant {@code now}. */
    protected abstract List<UUID> findMembreIdsEnService(Instant now);
    protected abstract P build(UUID membreId, UUID statutId, Instant debut, Instant fin, String notes);

    @Transactional
    public List<PlanningDto> listAll() {
        return repo().findAll().stream().map(PlanningDto::from).toList();
    }

    @Transactional
    public List<PlanningDto> listByMembre(UUID membreId) {
        return findByMembre(membreId).stream().map(PlanningDto::from).toList();
    }

    @Transactional
    public PlanningDto create(UUID membreId, UUID statutId, Instant debut, Instant fin, String notes) {
        // Chevauchement avec des plages du MÊME statut → on les fusionne en une seule plage étendue
        // (« prolonger la période »). Si le créneau est déjà couvert (ne déborde pas), on ne fait rien.
        var memeStatut = findOverlapping(membreId, debut, fin).stream()
                .filter(p -> p.getStatutView().getId().equals(statutId))
                .toList();
        Instant d = debut, f = fin;
        for (P p : memeStatut) {
            if (p.getDebut().isBefore(d)) d = p.getDebut();
            if (p.getFin().isAfter(f))    f = p.getFin();
        }
        // Déjà entièrement couvert par une plage unique inchangée → no-op.
        boolean noop = memeStatut.size() == 1
                && d.equals(memeStatut.get(0).getDebut()) && f.equals(memeStatut.get(0).getFin());

        // Chevauchement avec une plage d'un AUTRE statut → la nouvelle plage écrase la zone
        // commune : la plage existante est rognée (et non supprimée si une partie reste hors
        // du créneau ; scindée en deux si elle l'englobe entièrement).
        for (P p : findOverlapping(membreId, d, f)) {
            if (p.getStatutView().getId().equals(statutId)) continue;   // même statut : géré par la fusion
            boolean avant = p.getDebut().isBefore(d);
            boolean apres = p.getFin().isAfter(f);
            if (avant && apres) {
                Instant finOrig = p.getFin();
                p.setFin(d);
                repo().update(p);
                repo().save(build(p.getMembreId(), p.getStatutView().getId(), f, finOrig, p.getNotes()));
            } else if (avant) {
                p.setFin(d);
                repo().update(p);
            } else if (apres) {
                p.setDebut(f);
                repo().update(p);
            } else {
                repo().delete(p);   // entièrement couverte par le nouveau créneau
            }
        }

        if (noop) return PlanningDto.from(memeStatut.get(0));
        for (P p : memeStatut) repo().delete(p);   // absorbées dans la plage fusionnée
        P saved = repo().save(build(membreId, statutId, d, f, notes));
        onCreated(saved);   // ex. publier un événement temps réel (dans la transaction → diffusé après commit)
        return PlanningDto.from(saved);
    }

    /** Hook après création (dans la transaction). Par défaut sans effet ; surchargé par les modules. */
    protected void onCreated(P planning) {}

    /** Tolérance de déclaration rétroactive : 30 minutes de retard maximum. */
    private static final long RETARD_MAX_MS = 30L * 60_000L;

    /**
     * Déclaration d'une plage. Le début ne peut pas être placé à plus de 30 min dans le passé
     * (un début null vaut maintenant). Pas d'arrondi : début et fin sont pris tels quels.
     */
    @Transactional
    public PlanningDto declareSelf(UUID membreId, UUID statutId, Instant debut, Instant fin, String notes) {
        Instant now = Instant.now();
        Instant deb = debut == null ? now : debut;
        if (deb.isBefore(now.minusMillis(RETARD_MAX_MS))) {
            throw new IllegalArgumentException("Déclaration impossible dans le passé (plus de 30 min de retard).");
        }
        if (fin == null) throw new IllegalArgumentException("La fin est obligatoire.");
        if (!fin.isAfter(deb)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début.");
        }
        return create(membreId, statutId, deb, fin, notes);
    }

    /** Membres actuellement en service (plage GARDE couvrant l'instant présent). */
    @Transactional
    public List<UUID> membresEnService() {
        return findMembreIdsEnService(Instant.now());
    }
}
