package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.CreateSpInterventionRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpInterventionDto;
import com.bryan.nexis.sapeurs.backend.vehicule.SpVehiculeAffectationService;
import com.bryan.nexis.sapeurs.backend.dto.DesaffectationPreviewDto;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeStatut;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Sort;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class SpInterventionService {

    private static final Logger log = LoggerFactory.getLogger(SpInterventionService.class);

    private static final Sort BY_DEBUT_DESC = Sort.of(Sort.Order.desc("debut"));

    private final SpInterventionRepository       interventionRepo;
    private final SpVehiculeRepository           vehiculeRepo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpVehiculeAffectationService   affectationService;   // bip
    private final SpVehiculeAffectationRepository affectationRepo;     // contrôle équipier
    private final SpVehiculeTypePosteRepository   posteRepo;           // postes obligatoires
    private final SpVehiculeStatutRepository     statutRepo;           // statut "Déclenché" (premier)
    private final SpVehiculeEtatRepository       etatRepo;             // état "Indisponible"
    private final JournalService                 journalService;       // main courante
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService                securityService;

    public SpInterventionService(SpInterventionRepository interventionRepo, SpVehiculeRepository vehiculeRepo,
                                 SpNatureInterventionRepository natureRepo, SpVehiculeAffectationService affectationService,
                                 SpVehiculeAffectationRepository affectationRepo, SpVehiculeTypePosteRepository posteRepo,
                                 SpVehiculeStatutRepository statutRepo, SpVehiculeEtatRepository etatRepo,
                                 JournalService journalService,
                                 ApplicationEventPublisher<RealtimeEvent> events, SecurityService securityService) {
        this.interventionRepo   = interventionRepo;
        this.vehiculeRepo       = vehiculeRepo;
        this.natureRepo         = natureRepo;
        this.affectationService = affectationService;
        this.affectationRepo    = affectationRepo;
        this.posteRepo          = posteRepo;
        this.statutRepo         = statutRepo;
        this.etatRepo           = etatRepo;
        this.journalService     = journalService;
        this.events             = events;
        this.securityService    = securityService;
    }

    /**
     * Membres actuellement engagés sur une intervention OUVERTE via un véhicule AUTRE que celui exclu.
     * Sert au calcul d'armement : un membre occupé ailleurs ne couvre plus un poste obligatoire.
     */
    @Transactional
    public Set<UUID> membresOccupesSurAutreIntervention(UUID vehiculeIdExclu) {
        var enginsEngages = interventionRepo.findByFinIsNull().stream()
                .flatMap(i -> i.getEngins().stream())
                .map(SpVehicule::getId)
                .filter(vid -> !vid.equals(vehiculeIdExclu))
                .collect(Collectors.toSet());
        return enginsEngages.stream()
                .flatMap(vid -> affectationRepo.findByVehiculeIdAndFinIsNull(vid).stream())
                .map(a -> a.getMembre().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Aperçu des effectifs qui seraient désaffectés au déclenchement : ceux qui tiennent un poste
     * NON obligatoire d'un engin sélectionné tout en étant occupés ailleurs (intervention en cours
     * ou autre véhicule du même départ combiné). Un équipier disponible n'est jamais retiré.
     * NB : dans le cas « non obligatoire des deux côtés », il en gardera un en réalité — l'aperçu
     * peut donc sur-avertir, ce qui reste préférable à l'inverse.
     */
    @Transactional
    public List<DesaffectationPreviewDto> previewDesaffectationNonObligatoire(List<UUID> vehiculeIds) {
        var result = new ArrayList<DesaffectationPreviewDto>();
        if (vehiculeIds == null) return result;
        for (var vid : vehiculeIds) {
            var v = vehiculeRepo.findById(vid).orElse(null);
            if (v == null) continue;
            var oblig = obligPosteIds(v);
            if (oblig.isEmpty()) continue;   // aucun poste obligatoire → on ne désaffecte personne
            // Occupés ailleurs = inters ouvertes existantes + autres engins du même départ
            var occupes = new HashSet<>(membresOccupesSurAutreIntervention(vid));
            for (var autre : vehiculeIds) {
                if (autre.equals(vid)) continue;
                affectationRepo.findByVehiculeIdAndFinIsNull(autre)
                        .forEach(a -> occupes.add(a.getMembre().getId()));
            }
            for (var a : affectationRepo.findByVehiculeIdAndFinIsNull(vid)) {
                if (a.getPoste() != null && !oblig.contains(a.getPoste().getId())
                        && occupes.contains(a.getMembre().getId())) {
                    var m = a.getMembre();
                    result.add(new DesaffectationPreviewDto(v.getLibelle(), m.getGrade().getCode(),
                            m.getNomComplet() != null ? m.getNomComplet() : m.getUser().getUsername(),
                            a.getPoste().getFonction().getLabel()));
                }
            }
        }
        return result;
    }

    private Set<UUID> obligPosteIds(SpVehicule v) {
        return posteRepo.findByVehiculeTypeId(v.getType().getId()).stream()
                .filter(SpVehiculeTypePoste::isObligatoire)
                .map(SpVehiculeTypePoste::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Désaffecte, au déclenchement, les équipiers sur poste NON obligatoire d'un engin qui sont
     * occupés ailleurs (autre intervention en cours — y compris celle qu'on vient de créer, pour
     * les autres engins du départ combiné). Un équipier disponible part normalement avec l'engin.
     * Appelé APRÈS la sauvegarde de l'intervention : elle compte donc dans le calcul d'occupation.
     */
    private void desaffecterPostesNonObligatoires(List<SpVehicule> engins, String reference) {
        var now = Instant.now();
        for (var engin : engins) {
            var oblig = obligPosteIds(engin);
            if (oblig.isEmpty()) continue;   // aucun poste obligatoire → on ne désaffecte personne
            var occupes = membresOccupesSurAutreIntervention(engin.getId());
            for (var a : affectationRepo.findByVehiculeIdAndFinIsNull(engin.getId())) {
                if (a.getPoste() != null && !oblig.contains(a.getPoste().getId())
                        && occupes.contains(a.getMembre().getId())) {
                    affectationService.cloturer(a.getId(), now);
                }
            }
        }
    }

    private String actor() { return securityService.username().orElse(null); }

    private SpVehicule vehicule(UUID id) {
        return vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + id));
    }

    @Transactional
    public List<SpInterventionDto> listAll() {
        return interventionRepo.findAll(BY_DEBUT_DESC).stream().map(SpInterventionDto::from).toList();
    }

    @Transactional
    public List<SpInterventionDto> listEnCours() {
        return interventionRepo.findByFinIsNull().stream().map(SpInterventionDto::from).toList();
    }

    /** Main courante d'une intervention (journal relié à son code). */
    @Transactional
    public List<JournalEntryDto> mainCourante(UUID interventionId) {
        var inter = interventionRepo.findById(interventionId)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + interventionId));
        return journalService.byReference(inter.getCode());
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

        var engins = req.vehiculeIds() == null ? List.<SpVehicule>of() : req.vehiculeIds().stream().map(this::vehicule).toList();
        inter.getEngins().addAll(engins);
        var saved = interventionRepo.save(inter);

        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INTERVENTION_OUVERTE, "SP",
                "Intervention ouverte : " + saved.getMotif(),
                Map.of("interventionId", saved.getId().toString()), actor()).withReference(saved.getCode()));

        // D'abord libérer les équipiers occupés ailleurs (poste non obligatoire) : ils ne doivent
        // pas recevoir le bip de départ d'un engin avec lequel ils ne partent pas.
        desaffecterPostesNonObligatoires(engins, saved.getCode());
        engager(engins, saved);
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
        if (engageAilleurs) return;
        var dispoStatut = statutRepo.findByCode("DISPONIBLE").orElse(null);
        var dispoEtat   = etatRepo.findByCode("DISPONIBLE").orElse(null);
        if (dispoStatut == null || dispoEtat == null || dispoStatut.getId().equals(v.getStatut().getId())) return;
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
        inter.getEngins().addAll(toAdd);
        var saved = interventionRepo.update(inter);

        if (!toAdd.isEmpty()) {
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INTERVENTION_RENFORT, "SP",
                    "Renfort sur intervention : " + inter.getMotif(),
                    Map.of("interventionId", id.toString()), actor()).withReference(inter.getCode()));
            engager(toAdd, inter);
        }
        return SpInterventionDto.from(saved);
    }

    @Transactional
    public SpInterventionDto cloturer(UUID id) {
        var inter = interventionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intervention introuvable : " + id));
        if (inter.getFin() == null) {
            inter.setFin(Instant.now());
            inter = interventionRepo.update(inter);
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.INTERVENTION_CLOTUREE, "SP",
                    "Intervention clôturée : " + inter.getMotif(),
                    Map.of("interventionId", id.toString()), actor()).withReference(inter.getCode()));
            libererEngins(inter);
            log.info("Intervention {} clôturée par {}", inter.getCode(), actor());
        }
        return SpInterventionDto.from(inter);
    }

    /** Clôture les interventions ouvertes contenant ce véhicule dont TOUS les engins sont disponibles. */
    @Transactional
    public void clotureSiEnginsDisponibles(UUID vehiculeId) {
        log.trace("Vérification auto-clôture déclenchée par véhicule {}", vehiculeId);
        for (var inter : interventionRepo.findByFinIsNull()) {
            boolean contient = inter.getEngins().stream().anyMatch(e -> e.getId().equals(vehiculeId));
            if (!contient || inter.getEngins().isEmpty()) continue;
            boolean tousDispo = inter.getEngins().stream()
                    .allMatch(e -> "DISPONIBLE".equals(e.getEtat().getCode()));
            if (tousDispo) cloturer(inter.getId());
        }
    }

    /** À la clôture, repasse Disponible les engins qui ne sont plus engagés sur une autre intervention. */
    private void libererEngins(SpIntervention inter) {
        var dispoStatut = statutRepo.findByCode("DISPONIBLE").orElse(null);
        var dispoEtat   = etatRepo.findByCode("DISPONIBLE").orElse(null);
        if (dispoStatut == null || dispoEtat == null) return;

        var engagesAilleurs = interventionRepo.findByFinIsNull().stream()
                .filter(o -> !o.getId().equals(inter.getId()))
                .flatMap(o -> o.getEngins().stream())
                .map(SpVehicule::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (var engin : inter.getEngins()) {
            if (engagesAilleurs.contains(engin.getId())) continue;
            if (dispoStatut.getId().equals(engin.getStatut().getId())) continue;   // déjà disponible
            engin.setStatut(dispoStatut);
            engin.setEtat(dispoEtat);
            vehiculeRepo.update(engin);
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                    engin + " → " + dispoStatut.getLabel(),
                    Map.of("vehiculeId", engin.getId().toString(), "statut", dispoStatut.getCode(),
                           "etat", dispoEtat.getCode()), actor()).withReference(inter.getCode()));
        }
    }

    /**
     * Déclenchement : bascule chaque engin sur le premier statut (Déclenché) + état Indisponible,
     * puis bipe l'équipage avec les infos de départ.
     */
    private void engager(List<SpVehicule> engins, SpIntervention inter) {
        log.debug("Engagement de {} engin(s) sur {}", engins.size(), inter.getCode());
        SpVehiculeStatut declenche = statutRepo.listOrderByPositionAsc().stream().findFirst().orElse(null);
        var indispo = etatRepo.findByCode("INDISPONIBLE").orElse(null);
        for (var engin : engins) {
            if (declenche != null) {
                engin.setStatut(declenche);
                engin.setEtat(indispo != null ? indispo : declenche.getEtat());
                vehiculeRepo.update(engin);
                events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                        engin + " → " + declenche.getLabel(),
                        Map.of("vehiculeId", engin.getId().toString(), "statut", declenche.getCode(),
                               "etat", engin.getEtat().getCode()),
                        actor()).withReference(inter.getCode()));
            }
            affectationService.bipCrew(engin, bipMessage(inter), bipPayload(inter, engin), inter.getCode());
        }
    }

    private String bipMessage(SpIntervention inter) {
        String nat = inter.getNature() != null ? inter.getNature().getCode() : "";
        return ("🚨 DÉPART " + nat + " — " + inter.getMotif()).trim();
    }

    private Map<String, String> bipPayload(SpIntervention inter, SpVehicule engin) {
        var p = new java.util.HashMap<String, String>();
        p.put("type", "INTERVENTION");
        p.put("code", inter.getCode());
        p.put("motif", inter.getMotif());
        p.put("engin", engin.getLibelle());
        if (inter.getNature() != null)      p.put("nature", inter.getNature().getLabel());
        if (inter.getObservation() != null) p.put("observation", inter.getObservation());
        if (inter.getCommune() != null)     p.put("commune", inter.getCommune());
        if (inter.getCoordonnees() != null && inter.getCoordonnees().length() == 6)
            p.put("coord", inter.getCoordonnees().substring(0, 3) + " " + inter.getCoordonnees().substring(3));
        else if (inter.getCoordonnees() != null) p.put("coord", inter.getCoordonnees());
        return p;
    }
}
