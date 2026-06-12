package com.bryan.nexis.sapeurs.backend.evenement;

import com.bryan.nexis.sapeurs.backend.dto.SpEvenementDto;
import com.bryan.nexis.sapeurs.backend.dto.SpEvenementReponsesDto;
import com.bryan.nexis.sapeurs.backend.dto.SpEvenementReponsesDto.MembrePresence;
import com.bryan.nexis.sapeurs.datamodel.SpEvenement;
import com.bryan.nexis.sapeurs.datamodel.SpEvenementReponse;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datarepository.SpEvenementReponseRepository;
import com.bryan.nexis.sapeurs.datarepository.SpEvenementRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpEvenementService {

    /** Marge sous laquelle un événement déjà commencé reste affiché comme « en cours ». */
    private static final long GRACE_HEURES = 6;

    private final SpEvenementRepository evenementRepo;
    private final SpEvenementReponseRepository reponseRepo;
    private final SpMembreRepository membreRepo;

    public SpEvenementService(SpEvenementRepository evenementRepo, SpEvenementReponseRepository reponseRepo,
                              SpMembreRepository membreRepo) {
        this.evenementRepo = evenementRepo;
        this.reponseRepo = reponseRepo;
        this.membreRepo = membreRepo;
    }

    /** Événements à venir / en cours, avec la réponse de l'utilisateur courant (membreId nullable). */
    @Transactional
    public List<SpEvenementDto> listAVenir(UUID membreId) {
        Instant from = Instant.now().minus(GRACE_HEURES, ChronoUnit.HOURS);
        return evenementRepo.findByDateEvenementGreaterThanEqualOrderByDateEvenementAsc(from)
                .stream().map(e -> toDto(e, membreId)).toList();
    }

    /** Tous les événements (gestion admin), du plus récent au plus ancien. */
    @Transactional
    public List<SpEvenementDto> listTous(UUID membreId) {
        return evenementRepo.findAllOrderByDateEvenementDesc()
                .stream().map(e -> toDto(e, membreId)).toList();
    }

    @Transactional
    public SpEvenementDto create(String titre, String texte, Instant date, String actor) {
        var e = new SpEvenement(titre, texte, date, actor);
        return toDto(evenementRepo.save(e), null);
    }

    @Transactional
    public void delete(UUID id) {
        evenementRepo.deleteById(id);
    }

    /** Déclaration de présence (upsert) de l'effectif courant pour un événement. */
    @Transactional
    public void repondre(UUID evenementId, UUID membreId, boolean present) {
        var existing = reponseRepo.findByEvenementIdAndMembreId(evenementId, membreId);
        if (existing.isPresent()) {
            var r = existing.get();
            r.setPresent(present);
            r.setReponduLe(Instant.now());
            reponseRepo.update(r);
            return;
        }
        var ev = evenementRepo.findById(evenementId)
                .orElseThrow(() -> new NoSuchElementException("Événement introuvable : " + evenementId));
        var m = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        reponseRepo.save(new SpEvenementReponse(ev, m, present));
    }

    /** Bilan présents / absents déclarés (les non-répondants ne sont pas listés). */
    @Transactional
    public SpEvenementReponsesDto reponses(UUID evenementId) {
        var reps = reponseRepo.findByEvenementId(evenementId);
        var presents = reps.stream().filter(SpEvenementReponse::isPresent).map(r -> toMembre(r.getMembre())).toList();
        var absents  = reps.stream().filter(r -> !r.isPresent()).map(r -> toMembre(r.getMembre())).toList();
        return new SpEvenementReponsesDto(presents, absents);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private SpEvenementDto toDto(SpEvenement e, UUID membreId) {
        var reps = reponseRepo.findByEvenementId(e.getId());
        long pres = reps.stream().filter(SpEvenementReponse::isPresent).count();
        Boolean ma = membreId == null ? null : reps.stream()
                .filter(r -> r.getMembre().getId().equals(membreId))
                .findFirst().map(SpEvenementReponse::isPresent).orElse(null);
        return new SpEvenementDto(e.getId(), e.getTitre(), e.getTexte(), e.getDateEvenement(),
                (int) pres, (int) (reps.size() - pres), ma);
    }

    private MembrePresence toMembre(SpMembre m) {
        return new MembrePresence(m.getId(), m.getMatricule(), m.getGrade().getCode(),
                m.getNomComplet(), m.getUser().getUsername());
    }
}
