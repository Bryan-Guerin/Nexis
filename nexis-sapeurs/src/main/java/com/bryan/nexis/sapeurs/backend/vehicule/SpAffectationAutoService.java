package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeAffectationDto;
import com.bryan.nexis.sapeurs.backend.intervention.SpEngagementService;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Affectation automatique de l'équipage de garde aux postes des engins.
 *
 * <p>Le lot d'engins est armé en <b>un seul passage</b> via un <b>matching biparti</b>
 * (membres ↔ postes) résolu globalement : un membre n'est jamais affecté à deux engins, et
 * le matching de cardinalité maximale couvre <b>le plus de postes possible</b> même quand les
 * effectifs sont juste suffisants — là où un remplissage glouton par engin échouait (un engin
 * pouvait « voler » le seul membre qualifié dont un autre avait besoin).</p>
 *
 * <p>Deux phases : <b>postes obligatoires d'abord</b> (les optionnels ne peuvent jamais priver
 * un poste obligatoire de son membre), niveau croissant pour un départage déterministe. À
 * cardinalité égale, les candidats d'un poste sont essayés dans l'ordre du {@link #comparateur}
 * (le moins sur-qualifié, puis le moins gradé, puis le plus récemment formé) : préférence
 * opérationnelle qui réserve naturellement les compétences rares aux postes qui n'ont qu'elles.</p>
 */
@Singleton
public class SpAffectationAutoService {

    private final SpVehiculeRepository             vehiculeRepo;
    private final SpVehiculeTypePosteRepository    posteRepo;
    private final SpMembreRepository               membreRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SpPlanningService                planningService;
    private final SpEngagementService              engagement;
    private final SpVehiculeAffectationService     affectationService;

    public SpAffectationAutoService(SpVehiculeRepository vehiculeRepo, SpVehiculeTypePosteRepository posteRepo,
                                    SpMembreRepository membreRepo, SpVehiculeAffectationRepository affectationRepo,
                                    SpPlanningService planningService, SpEngagementService engagement,
                                    SpVehiculeAffectationService affectationService) {
        this.vehiculeRepo        = vehiculeRepo;
        this.posteRepo           = posteRepo;
        this.membreRepo          = membreRepo;
        this.affectationRepo     = affectationRepo;
        this.planningService     = planningService;
        this.engagement          = engagement;
        this.affectationService  = affectationService;
    }

    /** Une place à pourvoir : un engin + un poste (un poste à N places génère N slots). */
    private record Slot(UUID vehiculeId, SpVehiculeTypePoste poste) {
        UUID fonctionId() { return poste.getFonction().getId(); }
        int niveau()      { return poste.getFonction().getTypeFonction().getNiveau(); }
    }

    /** Arme un seul engin (armement manuel dispatch / feuille de garde). */
    @Transactional
    public List<SpVehiculeAffectationDto> affecterAuto(UUID vehiculeId) {
        var vehicule = vehiculeRepo.findById(vehiculeId)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + vehiculeId));
        return affecterAutoLot(List.of(vehicule));
    }

    /**
     * Arme le lot d'engins en un seul passage (pool partagé + matching global).
     * Chaque place pourvue déclenche l'affectation réelle (et le bip de départ du membre).
     */
    @Transactional
    public List<SpVehiculeAffectationDto> affecterAutoLot(List<SpVehicule> engins) {
        if (engins.isEmpty()) return List.of();

        // ── Slots à pourvoir + membres déjà posés sur le lot (équipage existant conservé). ──
        var slots = new ArrayList<Slot>();
        Set<UUID> surLot = new HashSet<>();
        for (var v : engins) {
            var actives = affectationRepo.findByVehiculeIdAndFinIsNull(v.getId());
            actives.forEach(a -> surLot.add(a.getMembre().getId()));
            Map<UUID, Long> occupeParPoste = actives.stream().filter(a -> a.getPoste() != null)
                    .collect(Collectors.groupingBy(a -> a.getPoste().getId(), Collectors.counting()));
            for (var p : posteRepo.findByVehiculeTypeIdOrderByOrdreAsc(v.getType().getId())) {
                long restantes = p.getNbPlaces() - occupeParPoste.getOrDefault(p.getId(), 0L);
                for (long k = 0; k < restantes; k++) slots.add(new Slot(v.getId(), p));
            }
        }

        // ── Pool affectable : de garde, ni occupé hors lot, ni déjà posé sur le lot. ──
        // membresOccupesSurAutreIntervention(engin0) = membres sur les engins d'inter ouvertes
        // ≠ engin0 ; en ôtant surLot il ne reste que les occupés HORS lot (cf. autres engins du
        // lot encore sans équipage).
        Set<UUID> occupesHorsLot = new HashSet<>(engagement.membresOccupesSurAutreIntervention(engins.get(0).getId()));
        occupesHorsLot.removeAll(surLot);
        Set<UUID> enService = new HashSet<>(planningService.membresEnService());
        List<SpMembre> pool = membreRepo.findByActif(true).stream()
                .filter(m -> enService.contains(m.getId()))
                .filter(m -> !occupesHorsLot.contains(m.getId()))
                .filter(m -> !surLot.contains(m.getId()))
                .collect(Collectors.toList());

        // ── Matching : obligatoires d'abord, puis optionnels avec les membres restants. ──
        var slotsOblig = slots.stream().filter(s -> s.poste().isObligatoire())
                .sorted(Comparator.comparingInt(Slot::niveau)).toList();
        var slotsOpt = slots.stream().filter(s -> !s.poste().isObligatoire())
                .sorted(Comparator.comparingInt(Slot::niveau)).toList();

        Set<Integer> tous = IntStream.range(0, pool.size()).boxed().collect(Collectors.toCollection(HashSet::new));
        Map<Integer, Integer> matchOblig = matcher(slotsOblig, pool, tous);
        Set<Integer> restants = new HashSet<>(tous);
        restants.removeAll(matchOblig.values());
        Map<Integer, Integer> matchOpt = matcher(slotsOpt, pool, restants);

        // ── Application (obligatoires puis optionnels, ordre de slot stable). ──
        var resultat = new ArrayList<SpVehiculeAffectationDto>();
        appliquer(slotsOblig, matchOblig, pool, resultat);
        appliquer(slotsOpt, matchOpt, pool, resultat);
        return resultat;
    }

    private void appliquer(List<Slot> phaseSlots, Map<Integer, Integer> match, List<SpMembre> pool,
                           List<SpVehiculeAffectationDto> out) {
        match.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            Slot s = phaseSlots.get(e.getKey());
            SpMembre m = pool.get(e.getValue());
            out.add(affectationService.affecter(s.vehiculeId(), m.getId(), s.poste().getId(), Instant.now()));
        });
    }

    /**
     * Matching biparti de cardinalité max (Kuhn). Retourne, par index de slot, l'index du membre
     * (dans {@code pool}) qui le pourvoit. {@code dispo} = membres affectables pour cette phase.
     */
    private Map<Integer, Integer> matcher(List<Slot> phaseSlots, List<SpMembre> pool, Set<Integer> dispo) {
        // Candidats par slot, ordonnés par préférence opérationnelle (départage à cardinalité égale).
        List<List<Integer>> cand = new ArrayList<>();
        for (Slot s : phaseSlots) {
            cand.add(dispo.stream()
                    .filter(mi -> qualifiePour(pool.get(mi), s.fonctionId()))
                    .sorted(Comparator.comparing((Integer mi) -> pool.get(mi), comparateur(s.fonctionId())))
                    .collect(Collectors.toList()));
        }
        int[] slotDuMembre = new int[pool.size()];
        Arrays.fill(slotDuMembre, -1);
        for (int u = 0; u < phaseSlots.size(); u++) augmente(u, cand, slotDuMembre, new boolean[pool.size()]);

        var res = new HashMap<Integer, Integer>();
        for (int m = 0; m < slotDuMembre.length; m++) if (slotDuMembre[m] != -1) res.put(slotDuMembre[m], m);
        return res;
    }

    /** Cherche un chemin augmentant pour le slot {@code u} (réaffecte au besoin les membres déjà pris). */
    private boolean augmente(int u, List<List<Integer>> cand, int[] slotDuMembre, boolean[] vu) {
        for (int m : cand.get(u)) {
            if (vu[m]) continue;
            vu[m] = true;
            if (slotDuMembre[m] == -1 || augmente(slotDuMembre[m], cand, slotDuMembre, vu)) {
                slotDuMembre[m] = u;
                return true;
            }
        }
        return false;
    }

    /** Le moins sur-qualifié, puis le moins gradé, puis le plus récemment formé sur la fonction. */
    private Comparator<SpMembre> comparateur(UUID fonctionId) {
        return Comparator.<SpMembre>comparingInt(this::niveauMax)
                .thenComparingInt(m -> m.getGrade().getPosition())
                .thenComparing(m -> dateQualif(m, fonctionId), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SpMembre::getMatricule);
    }

    private int niveauMax(SpMembre m) {
        return m.getQualifications().stream()
                .mapToInt(q -> q.getFonction().getTypeFonction().getNiveau()).max().orElse(0);
    }

    private boolean qualifiePour(SpMembre m, UUID fonctionId) {
        return m.getQualifications().stream().anyMatch(q -> q.getFonction().getId().equals(fonctionId));
    }

    private Instant dateQualif(SpMembre m, UUID fonctionId) {
        return m.getQualifications().stream()
                .filter(q -> q.getFonction().getId().equals(fonctionId))
                .map(q -> q.getDateDelivrance()).findFirst().orElse(null);
    }
}
