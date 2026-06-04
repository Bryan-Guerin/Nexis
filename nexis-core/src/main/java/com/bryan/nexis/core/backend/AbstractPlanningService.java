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
        if (!findOverlapping(membreId, debut, fin).isEmpty()) {
            throw new IllegalStateException("Conflit de planning : une plage existe déjà sur ce créneau.");
        }
        P saved = repo().save(build(membreId, statutId, debut, fin, notes));
        onCreated(saved);   // ex. publier un événement temps réel (dans la transaction → diffusé après commit)
        return PlanningDto.from(saved);
    }

    /** Hook après création (dans la transaction). Par défaut sans effet ; surchargé par les modules. */
    protected void onCreated(P planning) {}

    /**
     * Déclaration par le membre lui-même. Principe « quart d'heure entamé = quart d'heure payé » :
     * le début est arrondi au quart d'heure <em>inférieur</em> (15h55 → 15h45) et la fin au quart
     * <em>supérieur</em>. Le début ne peut pas être placé avant le quart d'heure courant (non rétroactif
     * au-delà de l'entamé) : un début passé est ramené à maintenant avant arrondi.
     */
    @Transactional
    public PlanningDto declareSelf(UUID membreId, UUID statutId, Instant debut, Instant fin, String notes) {
        Instant base = (debut == null || debut.isBefore(Instant.now())) ? Instant.now() : debut;
        Instant debutSnap = floorToQuarter(base);
        if (fin == null) throw new IllegalArgumentException("La fin est obligatoire.");
        Instant finSnap = ceilToQuarter(fin);
        if (!finSnap.isAfter(debutSnap)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début (" + debutSnap + ").");
        }
        return create(membreId, statutId, debutSnap, finSnap, notes);
    }

    /** Arrondit un instant au quart d'heure inférieur (les bornes :00/:15/:30/:45 sont conservées). */
    public static Instant floorToQuarter(Instant t) {
        long quarter = 15L * 60_000L;
        return Instant.ofEpochMilli((t.toEpochMilli() / quarter) * quarter);
    }

    /** Arrondit un instant au quart d'heure supérieur (les bornes :00/:15/:30/:45 sont conservées). */
    public static Instant ceilToQuarter(Instant t) {
        long quarter = 15L * 60_000L;
        long ms = t.toEpochMilli();
        return Instant.ofEpochMilli(((ms + quarter - 1) / quarter) * quarter);
    }

    /** Membres actuellement en service (plage GARDE couvrant l'instant présent). */
    @Transactional
    public List<UUID> membresEnService() {
        return findMembreIdsEnService(Instant.now());
    }
}
