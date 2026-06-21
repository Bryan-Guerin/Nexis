package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.CreateSpInterventionRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpInterventionDto;
import com.bryan.nexis.sapeurs.backend.vehicule.SpAffectationAutoService;
import com.bryan.nexis.sapeurs.backend.vehicule.SpVehiculeAffectationService;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Sort;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpInterventionService {

    private static final Logger log = LoggerFactory.getLogger(SpInterventionService.class);

    private static final Sort BY_DEBUT_DESC = Sort.of(Sort.Order.desc("debut"));

    private final SpInterventionRepository       interventionRepo;
    private final SpVehiculeRepository           vehiculeRepo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpVehiculeAffectationService   affectationService;   // bip
    private final SpVehiculeAffectationRepository affectationRepo;     // contrôle équipier
    private final SpVehiculeStatutRepository     statutRepo;           // statut "Déclenché" (premier)
    private final SpVehiculeEtatRepository       etatRepo;             // état "Indisponible"
    private final JournalService                 journalService;       // main courante
    private final com.bryan.nexis.sapeurs.backend.pilotage.SpActeurNommage nommage; // login → nom RP
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService                securityService;
    private final com.bryan.nexis.sapeurs.backend.effectif.SpRpService rpService;    // éval badges à la clôture
    private final SpHistorisationService         historisation;    // fige engins + équipages
    private final SpEngagementService            engagement;       // engager / postes / occupation
    private final SpAffectationAutoService       affectationAutoService;  // armement auto à la création
    private final SpCriService                   criService;       // créer CRI manquants à la clôture
    private final com.bryan.nexis.sapeurs.datarepository.SpCriRepository criRepo;   // statuts CRI pour le statut de clôture dérivé

    public SpInterventionService(SpInterventionRepository interventionRepo, SpVehiculeRepository vehiculeRepo,
                                 SpNatureInterventionRepository natureRepo, SpVehiculeAffectationService affectationService,
                                 SpVehiculeAffectationRepository affectationRepo,
                                 SpVehiculeStatutRepository statutRepo, SpVehiculeEtatRepository etatRepo,
                                 JournalService journalService,
                                 com.bryan.nexis.sapeurs.backend.pilotage.SpActeurNommage nommage,
                                 ApplicationEventPublisher<RealtimeEvent> events, SecurityService securityService,
                                 com.bryan.nexis.sapeurs.backend.effectif.SpRpService rpService,
                                 SpHistorisationService historisation, SpEngagementService engagement,
                                 SpAffectationAutoService affectationAutoService,
                                 SpCriService criService,
                                 com.bryan.nexis.sapeurs.datarepository.SpCriRepository criRepo) {
        this.interventionRepo   = interventionRepo;
        this.vehiculeRepo       = vehiculeRepo;
        this.natureRepo         = natureRepo;
        this.affectationService = affectationService;
        this.affectationRepo    = affectationRepo;
        this.nommage            = nommage;
        this.statutRepo         = statutRepo;
        this.etatRepo           = etatRepo;
        this.journalService     = journalService;
        this.events             = events;
        this.rpService          = rpService;
        this.securityService    = securityService;
        this.historisation      = historisation;
        this.engagement         = engagement;
        this.affectationAutoService = affectationAutoService;
        this.criService             = criService;
        this.criRepo                = criRepo;
    }

    private String actor() { return securityService.username().orElse(null); }

    private SpVehicule vehicule(UUID id) {
        return vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + id));
    }

    /** Nombre de lignes de main courante remontées dans la vue liste. */
    private static final int DERNIERES_MC = 5;

    @Transactional
    public List<SpInterventionDto> listAll() {
        return projeter(interventionRepo.findAll(BY_DEBUT_DESC));
    }

    /** Détail d'une intervention (avec engins + dernières lignes de main courante). */
    @Transactional
    public SpInterventionDto getById(UUID id) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        return projeter(List.of(inter)).get(0);
    }

    /** Détail d'une intervention via son code (ex. INT-0035) — pour partage d'URL.
     *  Le « code » est dérivé du numéro (formaté INT-%04d), pas stocké : on parse → numéro → findByNumero. */
    @Transactional
    public SpInterventionDto getByCode(String code) {
        int numero = parseNumeroDepuisCode(code);
        var inter = interventionRepo.findByNumero(numero)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + code));
        return projeter(List.of(inter)).get(0);
    }

    private static int parseNumeroDepuisCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code intervention vide.");
        String s = code.trim().toUpperCase();
        if (s.startsWith("INT-")) s = s.substring(4);
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Code intervention invalide : " + code);
        }
    }

    @Transactional
    public List<SpInterventionDto> listEnCours() {
        return projeter(interventionRepo.findByFinIsNull());
    }

    /**
     * Projette une liste d'interventions en DTO avec leurs dernières lignes de main courante,
     * en lot : 1 requête journal (toutes références) + 1 carte de noms (au lieu d'un N+1).
     */
    private List<SpInterventionDto> projeter(List<SpIntervention> interventions) {
        if (interventions.isEmpty()) return List.of();
        var codes = interventions.stream().map(SpIntervention::getCode).toList();
        var journauxParCode = journalService.byReferences(codes);   // 1 requête
        var noms = nommage.noms();                                   // 1 requête
        // Statuts CRI en lot pour les interventions closes : 1 requête au lieu de N.
        var idsClos = interventions.stream().filter(i -> i.getFin() != null).map(SpIntervention::getId).toList();
        var crisParInter = idsClos.isEmpty()
                ? java.util.Map.<UUID, List<com.bryan.nexis.sapeurs.datamodel.SpCri>>of()
                : criRepo.findByInterventionIdIn(idsClos).stream()
                        .collect(java.util.stream.Collectors.groupingBy(c -> c.getIntervention().getId()));
        return interventions.stream().map(i -> {
            var all = journauxParCode.getOrDefault(i.getCode(), List.of());
            int n = all.size();
            var dern = n <= DERNIERES_MC ? all : all.subList(n - DERNIERES_MC, n);
            return SpInterventionDto.from(i, nommage.appliquer(dern, noms),
                    statutCloture(i, crisParInter.getOrDefault(i.getId(), List.of())));
        }).toList();
    }

    /** Calcule le statut de clôture dérivé de fin + statuts des CRI.
     *  EN_COURS = ouvert | ATTENTE_CRI = >=1 CRI non SOUMIS/VALIDE | ATTENTE_VALIDATION = tous SOUMIS, >=1 non VALIDE | CLOSE = tous VALIDE. */
    private static String statutCloture(SpIntervention i, List<com.bryan.nexis.sapeurs.datamodel.SpCri> cris) {
        if (i.getFin() == null) return "EN_COURS";
        if (cris.isEmpty()) return "CLOSE";
        boolean enAttenteCri = cris.stream().anyMatch(c ->
                !com.bryan.nexis.sapeurs.datamodel.SpCri.SOUMIS.equals(c.getStatut())
                && !com.bryan.nexis.sapeurs.datamodel.SpCri.VALIDE.equals(c.getStatut()));
        if (enAttenteCri) return "ATTENTE_CRI";
        boolean enAttenteValidation = cris.stream().anyMatch(c ->
                com.bryan.nexis.sapeurs.datamodel.SpCri.SOUMIS.equals(c.getStatut()));
        return enAttenteValidation ? "ATTENTE_VALIDATION" : "CLOSE";
    }

    /** Main courante d'une intervention (journal relié à son code). */
    @Transactional
    public List<JournalEntryDto> mainCourante(UUID interventionId) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        return nommage.enrichir(journalService.byReference(inter.getCode()));
    }

    /** Ajoute une note de main courante. Réservé à un équipier de l'intervention (ou admin SP). */
    @Transactional
    public void addMainCourante(UUID interventionId, String message) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("La note ne peut pas être vide.");
        }
        if (!securityService.hasRole("ROLE_ADMIN_SP") && !estEquipier(inter, actor())) {
            throw new IllegalStateException("Seul un équipier de l'intervention peut ajouter une note.");
        }
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.MAIN_COURANTE, "SP",
                message.trim(), Map.of("interventionId", interventionId.toString()), actor())
                .withReference(inter.getCode()));
    }

    /** Met à jour les statuts de renfort GN / VINCI (éditable par tous). */
    @Transactional
    public SpInterventionDto updateRenfort(UUID id, String gn, String vinci) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        if (gn != null && !gn.equals(inter.getRenfortGn())) {
            inter.setRenfortGn(valideRenfort(gn));
            noteRenfort(inter, "GN", gn);
        }
        if (vinci != null && !vinci.equals(inter.getRenfortVinci())) {
            inter.setRenfortVinci(valideRenfort(vinci));
            noteRenfort(inter, "VINCI", vinci);
        }
        return SpInterventionDto.from(interventionRepo.update(inter));
    }

    private static final java.util.Map<String, String> RENFORT_LABEL = java.util.Map.of(
            SpIntervention.RENFORT_NON_PREVENU, "non prévenu",
            SpIntervention.RENFORT_PREVENU, "prévenu",
            SpIntervention.RENFORT_SUR_PLACE, "sur place");

    private String valideRenfort(String v) {
        if (!RENFORT_LABEL.containsKey(v)) throw new IllegalArgumentException("Statut de renfort invalide : " + v);
        return v;
    }

    private void noteRenfort(SpIntervention inter, String cible, String statut) {
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.MAIN_COURANTE, "SP",
                "Renfort " + cible + " : " + RENFORT_LABEL.get(statut),
                Map.of("interventionId", inter.getId().toString()), actor()).withReference(inter.getCode()));
    }

    /** Le username est-il équipier d'au moins un engin de l'intervention ? */
    private boolean estEquipier(SpIntervention inter, String username) {
        if (username == null) return false;
        return inter.getEngins().stream().anyMatch(engin ->
                affectationRepo.findByVehiculeIdAndFinIsNull(engin.getId()).stream()
                        .anyMatch(a -> username.equals(a.getMembre().getUser().getUsername())));
    }

    /** Code de l'intervention en cours contenant ce véhicule (pour rattacher les changements de statut). */
    @Transactional
    public java.util.Optional<String> codeInterventionEnCours(UUID vehiculeId) {
        return interventionRepo.findByFinIsNull().stream()
                .filter(i -> i.getEngins().stream().anyMatch(e -> e.getId().equals(vehiculeId)))
                .map(SpIntervention::getCode)
                .findFirst();
    }

    @Transactional
    public SpInterventionDto create(CreateSpInterventionRequest req) {
        if (req.motif() == null || req.motif().isBlank()) throw new IllegalArgumentException("Le motif est obligatoire.");
        if (req.natureId() == null) throw new IllegalArgumentException("La nature de l'intervention est obligatoire.");
        var inter = new SpIntervention(req.motif().trim(), actor());
        inter.setNumero(interventionRepo.findMaxNumero() + 1);
        inter.setNature(natureRepo.findById(req.natureId())
                .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + req.natureId())));
        inter.setRequerant(req.requerant());
        inter.setTelephone(req.telephone());
        inter.setObservation(req.observation());
        inter.setCommune(req.commune());
        inter.setCoordonnees(req.coordonnees());
        inter.setNbVictimes(req.nbVictimes());
        inter.setIncendie(req.incendie());
        inter.setVehiculeImplique(req.vehiculeImplique());
        inter.setSr(req.sr());

        var engins = req.vehiculeIds() == null ? List.<SpVehicule>of() : req.vehiculeIds().stream().map(this::vehicule).toList();
        // Bloque AVANT toute écriture : poste obligatoire tenu par un effectif déjà parti.
        engagement.verifierEquipageObligatoireDisponible(engins);
        inter.getEngins().addAll(engins);
        var saved = interventionRepo.save(inter);

        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INTERVENTION_OUVERTE, "SP",
                "Intervention ouverte : " + saved.getMotif(),
                Map.of("interventionId", saved.getId().toString()), actor()).withReference(saved.getCode()));

        // Réengagement : détache les engins encore rattachés à une intervention ouverte (dispo radio).
        detacherDesInterventionsPrecedentes(engins, saved);
        // D'abord libérer les équipiers occupés ailleurs (poste non obligatoire) : ils ne doivent
        // pas recevoir le bip de départ d'un engin avec lequel ils ne partent pas.
        engagement.desaffecterPostesNonObligatoires(engins, saved.getCode());
        engagement.engager(engins, saved);
        // Armement auto côté serveur, dans CETTE transaction : le lot est armé en un seul passage
        // (matching global → pas de double-affectation ni de famine entre engins). L'affecter() de
        // chaque membre déclenche son bip de départ.
        if (req.armerAuto()) {
            affectationAutoService.affecterAutoLot(engins);
        }
        log.info("Intervention {} créée par {} (nature={}, {} engin(s))", saved.getCode(), actor(),
                saved.getNature() != null ? saved.getNature().getCode() : "?", engins.size());
        return SpInterventionDto.from(saved);
    }

    /** Édition des champs d'une intervention (null = inchangé). */
    @Transactional
    public SpInterventionDto update(UUID id, com.bryan.nexis.sapeurs.backend.dto.UpdateSpInterventionRequest req) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        if (req.motif() != null && !req.motif().isBlank()) inter.setMotif(req.motif().trim());
        if (req.natureId() != null) inter.setNature(natureRepo.findById(req.natureId())
                .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + req.natureId())));
        if (req.requerant() != null)   inter.setRequerant(req.requerant());
        if (req.telephone() != null)   inter.setTelephone(req.telephone());
        if (req.observation() != null) inter.setObservation(req.observation());
        if (req.commune() != null)     inter.setCommune(req.commune());
        if (req.coordonnees() != null) inter.setCoordonnees(req.coordonnees());
        return SpInterventionDto.from(interventionRepo.update(inter));
    }

    /** Retire un engin de l'intervention et le libère (s'il n'est pas engagé ailleurs). */
    @Transactional
    public SpInterventionDto retirerEngin(UUID id, UUID vehiculeId) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        if (inter.getFin() != null) throw new IllegalStateException("Intervention clôturée.");
        var v = inter.getEngins().stream().filter(e -> e.getId().equals(vehiculeId)).findFirst().orElse(null);
        if (v != null) historisation.snapshotUnEngin(inter, v);   // fige l'engin + équipage avant retrait
        boolean retire = inter.getEngins().removeIf(e -> e.getId().equals(vehiculeId));
        var saved = interventionRepo.update(inter);
        if (retire) libererVehicule(vehiculeId, inter.getCode());
        return SpInterventionDto.from(saved);
    }

    private void libererVehicule(UUID vehiculeId, String reference) {
        var v = vehiculeRepo.findById(vehiculeId).orElse(null);
        if (v == null) return;
        boolean engageAilleurs = interventionRepo.findByFinIsNull().stream()
                .anyMatch(o -> o.getEngins().stream().anyMatch(e -> e.getId().equals(vehiculeId)));
        if (engageAilleurs) {
            log.debug("Retrait de {} : conservé tel quel (engagé sur une autre intervention)", v);
            return;
        }
        // Statut validant la clôture = état final choisi par l'équipage : ne pas l'écraser.
        if (v.getStatut() != null && v.getStatut().isClotureIntervention()) {
            log.debug("Retrait de {} : conservé en {} (statut validant la clôture)", v, v.getStatut().getCode());
            return;
        }
        var dispoStatut = statutRepo.findByCode("DISPONIBLE").orElse(null);
        var dispoEtat   = etatRepo.findByCode("DISPONIBLE").orElse(null);
        if (dispoStatut == null || dispoEtat == null || dispoStatut.getId().equals(v.getStatut().getId())) return;
        log.info("Retrait de {} : réinitialisé {} → {}", v, v.getStatut().getCode(), dispoStatut.getCode());
        v.setStatut(dispoStatut);
        v.setEtat(dispoEtat);
        vehiculeRepo.update(v);
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                v + " retiré de l'intervention → " + dispoStatut.getLabel(),
                Map.of("vehiculeId", vehiculeId.toString(), "statut", dispoStatut.getCode(),
                       "etat", dispoEtat.getCode()), actor()).withReference(reference));
    }

    @Transactional
    public SpInterventionDto addEngins(UUID id, List<UUID> vehiculeIds) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        if (inter.getFin() != null) {
            throw new IllegalStateException("Intervention déjà clôturée : impossible d'ajouter un renfort.");
        }
        var toAdd = (vehiculeIds == null ? List.<UUID>of() : vehiculeIds).stream()
                .filter(vid -> inter.getEngins().stream().noneMatch(e -> e.getId().equals(vid)))
                .map(this::vehicule)
                .toList();
        // Bloque AVANT toute écriture : poste obligatoire tenu par un effectif déjà parti.
        engagement.verifierEquipageObligatoireDisponible(toAdd);
        inter.getEngins().addAll(toAdd);
        var saved = interventionRepo.update(inter);

        if (!toAdd.isEmpty()) {
            detacherDesInterventionsPrecedentes(toAdd, inter);
            // Libère les équipiers sur poste non obligatoire déjà engagés ailleurs (ex. effectif B
            // tenant un poste obligatoire d'un autre engin) → ils ne partent pas et ne sont pas bipés.
            engagement.desaffecterPostesNonObligatoires(toAdd, inter.getCode());
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INTERVENTION_RENFORT, "SP",
                    "Renfort sur intervention : " + inter.getMotif(),
                    Map.of("interventionId", id.toString()), actor()).withReference(inter.getCode()));
            engagement.engager(toAdd, inter);
        }
        return SpInterventionDto.from(saved);
    }

    @Transactional
    public SpInterventionDto cloturer(UUID id) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        if (inter.getFin() == null) {
            inter.setFin(Instant.now());
            // Snapshot des engins + équipages AVANT de libérer (les affectations sont encore actives).
            historisation.snapshotEngins(inter);
            // Crée les CRI manquants AVANT de détacher les engins : sinon les équipages ne peuvent
            // plus saisir leur CRI une fois l'intervention en attente CRI (engins vidés).
            criService.creerCrisManquants(inter);
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INTERVENTION_CLOTUREE, "SP",
                    "Intervention en attente CRI : " + inter.getMotif(),
                    Map.of("interventionId", id.toString()), actor()).withReference(inter.getCode()));
            libererEngins(inter);
            // Retire la FK véhicule : l'archive vit désormais dans le snapshot historisé.
            inter.getEngins().clear();
            inter = interventionRepo.update(inter);
            log.info("Intervention {} passée en attente CRI par {}", inter.getCode(), actor());
            // Auto-éval badges : interventions/temps/nature peuvent franchir un seuil.
            // Coût acceptable pour le volume RP ; sinon ciblerait les seuls porteurs.
            try { rpService.evalAll(); }
            catch (RuntimeException e) { log.warn("Éval badges après clôture : {}", e.getMessage()); }
        }
        return SpInterventionDto.from(inter);
    }

    /**
     * Clôture les interventions ouvertes contenant ce véhicule dont TOUS les engins portent
     * un statut validant la clôture (case « clôture intervention » du statut).
     * Un statut « Disponible radio » non coché libère donc le véhicule sans fermer l'intervention
     * (renforts et main courante restent possibles jusqu'au retour effectif).
     */
    @Transactional
    public void clotureSiEnginsValident(UUID vehiculeId) {
        log.trace("Vérification auto-clôture déclenchée par véhicule {}", vehiculeId);
        for (var inter : interventionRepo.findByFinIsNull()) {
            boolean contient = inter.getEngins().stream().anyMatch(e -> e.getId().equals(vehiculeId));
            if (contient && tousValidentCloture(inter)) cloturer(inter.getId());
        }
    }

    /** Tous les engins de l'intervention portent-ils un statut validant la clôture ? */
    private boolean tousValidentCloture(SpIntervention inter) {
        if (inter.getEngins().isEmpty()) return false;
        return inter.getEngins().stream()
                .allMatch(e -> e.getStatut() != null && e.getStatut().isClotureIntervention());
    }

    /**
     * Réengagement : un véhicule encore rattaché à une intervention ouverte (ex. « Disponible
     * radio ») qui part sur une nouvelle est détaché de l'ancienne, avec note en main courante.
     * L'ancienne se clôture alors si tous ses engins restants valident la clôture.
     */
    private void detacherDesInterventionsPrecedentes(List<SpVehicule> engins, SpIntervention nouvelle) {
        for (var inter : interventionRepo.findByFinIsNull()) {
            if (inter.getId().equals(nouvelle.getId())) continue;
            boolean modifie = false;
            for (var engin : engins) {
                boolean present = inter.getEngins().stream().anyMatch(e -> e.getId().equals(engin.getId()));
                if (present) {
                    historisation.snapshotUnEngin(inter, engin);   // fige l'engin + équipage avant de le détacher
                    inter.getEngins().removeIf(e -> e.getId().equals(engin.getId()));
                    modifie = true;
                    events.publishEvent(RealtimeEvent.faction(RealtimeEvent.MAIN_COURANTE, "SP",
                            engin + " réengagé sur " + nouvelle.getCode(),
                            Map.of("vehiculeId", engin.getId().toString()), actor()).withReference(inter.getCode()));
                    log.info("{} détaché de {} (réengagé sur {})", engin, inter.getCode(), nouvelle.getCode());
                }
            }
            if (modifie) {
                interventionRepo.update(inter);
                // Plus d'engin, ou tous les restants valident → l'ancienne intervention se clôture.
                if (inter.getEngins().isEmpty() || tousValidentCloture(inter)) cloturer(inter.getId());
            }
        }
    }

    /** À la clôture, repasse Disponible les engins qui ne sont plus engagés sur une autre intervention. */
    private void libererEngins(SpIntervention inter) {
        var dispoStatut = statutRepo.findByCode("DISPONIBLE").orElse(null);
        var dispoEtat   = etatRepo.findByCode("DISPONIBLE").orElse(null);
        if (dispoStatut == null || dispoEtat == null) {
            log.warn("Libération des engins de {} impossible : statut/état DISPONIBLE non configuré", inter.getCode());
            return;
        }

        var engagesAilleurs = interventionRepo.findByFinIsNull().stream()
                .filter(o -> !o.getId().equals(inter.getId()))
                .flatMap(o -> o.getEngins().stream())
                .map(SpVehicule::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (var engin : inter.getEngins()) {
            if (engagesAilleurs.contains(engin.getId())) {
                log.debug("Clôture {} : {} conservé tel quel (engagé sur une autre intervention)", inter.getCode(), engin);
                continue;
            }
            // Un statut validant la clôture est un état final CHOISI (inventaire, dispo caserne…) :
            // on ne l'écrase pas. Seuls les statuts « en cours d'intervention » sont réinitialisés
            // (cas de la clôture forcée par le dispatcher).
            if (engin.getStatut() != null && engin.getStatut().isClotureIntervention()) {
                log.debug("Clôture {} : {} conservé en {} (statut validant la clôture)",
                        inter.getCode(), engin, engin.getStatut().getCode());
                continue;
            }
            if (dispoStatut.getId().equals(engin.getStatut().getId())) continue;   // déjà disponible
            log.info("Clôture {} : {} réinitialisé {} → {}", inter.getCode(), engin,
                    engin.getStatut().getCode(), dispoStatut.getCode());
            engin.setStatut(dispoStatut);
            engin.setEtat(dispoEtat);
            vehiculeRepo.update(engin);
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                    engin + " → " + dispoStatut.getLabel(),
                    Map.of("vehiculeId", engin.getId().toString(), "statut", dispoStatut.getCode(),
                           "etat", dispoEtat.getCode()), actor()).withReference(inter.getCode()));
        }
    }

}
