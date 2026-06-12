package com.bryan.nexis.sapeurs.backend.inventaire;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.CreateVerificationRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpInventaireItemDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVerificationDto;
import com.bryan.nexis.sapeurs.datamodel.*;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpInventaireService {

    /** Statut véhicule appliqué selon la conformité de la vérification. */
    private static final String ETAT_OK = "DISPONIBLE";
    private static final String ETAT_KO = "INDISPONIBLE";

    private final SpInventaireItemRepository itemRepo;
    private final SpObjetInventaireRepository objetRepo;
    private final SpVerificationRepository    verifRepo;
    private final SpVehiculeRepository        vehiculeRepo;
    private final SpVehiculeTypeRepository    typeRepo;
    private final SpVehiculeEtatRepository    etatRepo;
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService             securityService;

    public SpInventaireService(SpInventaireItemRepository itemRepo, SpObjetInventaireRepository objetRepo,
                               SpVerificationRepository verifRepo, SpVehiculeRepository vehiculeRepo,
                               SpVehiculeTypeRepository typeRepo, SpVehiculeEtatRepository etatRepo,
                               ApplicationEventPublisher<RealtimeEvent> events, SecurityService securityService) {
        this.itemRepo        = itemRepo;
        this.objetRepo       = objetRepo;
        this.verifRepo       = verifRepo;
        this.vehiculeRepo    = vehiculeRepo;
        this.typeRepo        = typeRepo;
        this.etatRepo        = etatRepo;
        this.events          = events;
        this.securityService = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    // ── Modèle d'inventaire (porté par le type) ──────────────────────────────
    @Transactional
    public List<SpInventaireItemDto> listItems(UUID typeId) {
        return itemRepo.findByVehiculeTypeIdOrderByPosition(typeId).stream().map(SpInventaireItemDto::from).toList();
    }

    @Transactional
    public SpInventaireItemDto addItem(UUID typeId, UUID objetId, int quantite) {
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule introuvable : " + typeId));
        var objet = objetRepo.findById(objetId)
                .orElseThrow(() -> new NoSuchElementException("Objet d'inventaire introuvable : " + objetId));
        var item = new SpInventaireItem(type, objet, Math.max(1, quantite));
        item.setPosition((int) itemRepo.countByVehiculeTypeId(typeId));
        return SpInventaireItemDto.from(itemRepo.save(item));
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        itemRepo.deleteById(itemId);
    }

    /** Réordonne le modèle d'inventaire d'un type : position = index dans la liste fournie. */
    @Transactional
    public void reorderItems(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var item = itemRepo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Item d'inventaire introuvable : " + id));
            item.setPosition(i);
            itemRepo.update(item);
        }
    }

    // ── Vérifications (checklist + historique + disponibilité) ───────────────
    @Transactional
    public List<SpVerificationDto> listVerifications(UUID vehiculeId) {
        return verifRepo.findByVehiculeIdOrderByCreeLeDesc(vehiculeId).stream().map(SpVerificationDto::from).toList();
    }

    @Transactional
    public SpVerificationDto createVerification(UUID vehiculeId, CreateVerificationRequest req) {
        var vehicule = vehiculeRepo.findById(vehiculeId)
                .orElseThrow(() -> new NoSuchElementException("Véhicule introuvable : " + vehiculeId));

        var verif = new SpVerification(vehicule, actor());
        boolean conforme = true;
        if (req.lignes() != null) {
            for (var l : req.lignes()) {
                var ligne = new SpVerificationLigne(l.libelle(), l.quantiteAttendue(), l.quantitePresente());
                verif.addLigne(ligne);
                conforme &= ligne.isConforme();
            }
        }
        verif.setConforme(conforme);
        var saved = verifRepo.save(verif);

        // Inventaire OK → véhicule disponible ; KO → indisponible
        etatRepo.findByCode(conforme ? ETAT_OK : ETAT_KO).ifPresent(etat -> appliquerEtat(vehicule, etat));

        // Main courante : qui a vérifié + résultat (sans le détail des manquants)
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INVENTAIRE, "SP",
                "Inventaire " + vehicule + (conforme ? " : validé (conforme)" : " : NON conforme"),
                Map.of("vehiculeId", vehicule.getId().toString(), "conforme", String.valueOf(conforme)), actor()));

        return SpVerificationDto.from(saved);
    }

    private void appliquerEtat(SpVehicule vehicule, SpVehiculeEtat etat) {
        if (vehicule.getEtat() != null && etat.getId().equals(vehicule.getEtat().getId())) return;
        vehicule.setEtat(etat);
        vehiculeRepo.update(vehicule);
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                vehicule + " → " + etat.getLabel(),
                Map.of("vehiculeId", vehicule.getId().toString(), "etat", etat.getCode()), actor()));
    }
}
