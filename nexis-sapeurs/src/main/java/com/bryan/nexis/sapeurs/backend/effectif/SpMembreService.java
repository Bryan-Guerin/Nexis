package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.core.backend.AccountRevocation;
import com.bryan.nexis.core.datarepository.RefUserRepository;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpMembreDto;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datamodel.SpMembreQualification;
import com.bryan.nexis.sapeurs.datarepository.SpFonctionOrgaRepository;
import com.bryan.nexis.sapeurs.datarepository.SpFonctionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpGradeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Singleton
public class SpMembreService {

    private final SpMembreRepository       membreRepo;
    private final RefUserRepository        userRepo;
    private final SpGradeRepository        gradeRepo;
    private final SpFonctionRepository     fonctionRepo;
    private final SpFonctionOrgaRepository fonctionOrgaRepo;
    private final SecurityService          securityService;
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final AccountRevocation        accountRevocation;
    private final SpRpService              rpService;   // éval badges après ajout de qualif

    public SpMembreService(SpMembreRepository membreRepo, RefUserRepository userRepo,
                           SpGradeRepository gradeRepo, SpFonctionRepository fonctionRepo,
                           SpFonctionOrgaRepository fonctionOrgaRepo,
                           SecurityService securityService,
                           ApplicationEventPublisher<RealtimeEvent> events,
                           AccountRevocation accountRevocation,
                           SpRpService rpService) {
        this.membreRepo       = membreRepo;
        this.userRepo         = userRepo;
        this.gradeRepo        = gradeRepo;
        this.fonctionRepo     = fonctionRepo;
        this.fonctionOrgaRepo = fonctionOrgaRepo;
        this.securityService  = securityService;
        this.events           = events;
        this.accountRevocation = accountRevocation;
        this.rpService        = rpService;
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    @Transactional
    public List<SpMembreDto> listAll() {
        return membreRepo.findAll().stream().map(SpMembreDto::from).toList();
    }

    @Transactional
    public List<SpMembreDto> listAllOrderByGrade() {
        // Order by grade position descending
        return membreRepo.findAllOrderByGradePositionDesc().stream().map(SpMembreDto::from).toList();
    }

    @Transactional
    public List<SpMembreDto> listActifs() {
        return membreRepo.findByActif(true).stream().map(SpMembreDto::from).toList();
    }

    @Transactional
    public SpMembreDto getById(UUID id) {
        return SpMembreDto.from(membreRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + id)));
    }

    @Transactional
    public SpMembreDto findByUsername(String username) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + username));
        return SpMembreDto.from(membreRepo.findByUserId(user.getId())
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable pour : " + username)));
    }

    // ── Création ─────────────────────────────────────────────────────────────

    @Transactional
    public SpMembreDto create(UUID userId, UUID gradeId, String contrat, int numeroCasier, String nomComplet, String telephone) {
        var user  = userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + userId));
        var grade = gradeRepo.findById(gradeId)
                .orElseThrow(() -> new NoSuchElementException("Grade SP introuvable : " + gradeId));

        // Compteur commun SPP / SPV, commence à 352
        int nextCompteur = membreRepo.findMaxNumeroCompteur() + 1;

        var membre = new SpMembre(user, grade, contrat, numeroCasier, nextCompteur);
        if (nomComplet != null && !nomComplet.isBlank()) membre.setNomComplet(nomComplet.trim());
        if (telephone != null && !telephone.isBlank())   membre.setTelephone(telephone.trim());
        return SpMembreDto.from(membreRepo.save(membre));
    }

    // ── Mise à jour (admin) ───────────────────────────────────────────────────

    @Transactional
    public SpMembreDto update(UUID id, UUID gradeId, String contrat, Integer numeroCasier, String nomComplet, String telephone) {
        var membre = membreRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + id));
        if (nomComplet != null) membre.setNomComplet(nomComplet.isBlank() ? null : nomComplet.trim());
        if (telephone != null) membre.setTelephone(telephone.isBlank() ? null : telephone.trim());

        if (gradeId != null && !gradeId.equals(membre.getGrade().getId())) {
            var grade = gradeRepo.findById(gradeId)
                    .orElseThrow(() -> new NoSuchElementException("Grade SP introuvable : " + gradeId));
            membre.setGrade(grade);
            membre.setDateDernierePromotion(Instant.now());   // changement de grade = promotion
        }
        if (contrat != null) {
            membre.setContrat(contrat);   // recalcule automatiquement le matricule
        }
        if (numeroCasier != null) {
            membre.setNumeroCasier(numeroCasier);
        }

        return SpMembreDto.from(membreRepo.update(membre));
    }

    /**
     * Radie ({@code actif=false}) ou réintègre un effectif. Le compte utilisateur lié est
     * désactivé/réactivé en conséquence (blocage du login). Soft-delete : l'historique
     * (qualifications, sanctions, notations, affectations) est conservé.
     */
    @Transactional
    public SpMembreDto setActif(UUID id, boolean actif) {
        var membre = membreRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + id));
        membre.setActif(actif);
        var user = membre.getUser();
        user.setEnabled(actif);
        userRepo.update(user);
        accountRevocation.set(user.getUsername(), actif);   // maj de la liste de révocation en mémoire
        var saved = membreRepo.update(membre);
        // Radiation : déconnexion immédiate côté client (le filtre serveur bloque déjà l'API).
        if (!actif) {
            events.publishEvent(RealtimeEvent.users("COMPTE_DESACTIVE", "SP", Set.of(user.getUsername()),
                    "Votre compte a été désactivé.", Map.of(), securityService.username().orElse(null)));
        }
        return SpMembreDto.from(saved);
    }

    // ── Qualifications (fonctions du membre) ──────────────────────────────────

    @Transactional
    public void addQualification(UUID membreId, UUID fonctionId) {
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        if (membre.getQualifications().stream().anyMatch(q -> q.getFonction().getId().equals(fonctionId))) {
            return;   // déjà qualifié
        }
        var fonction = fonctionRepo.findById(fonctionId)
                .orElseThrow(() -> new NoSuchElementException("Fonction introuvable : " + fonctionId));
        membre.getQualifications().add(
                new SpMembreQualification(membre, fonction, securityService.username().orElse(null)));
        var saved = membreRepo.update(membre);
        // Attribution immédiate des badges de qualification (sans attendre le CRON horaire).
        rpService.evalForMembre(saved);
    }

    @Transactional
    public void removeQualification(UUID membreId, UUID fonctionId) {
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        membre.getQualifications().removeIf(q -> q.getFonction().getId().equals(fonctionId));
        membreRepo.update(membre);
    }

    // ── Fonctions organigramme (cumulables) ───────────────────────────────────

    /** Remplace l'ensemble des fonctions d'organigramme du membre par celles fournies. */
    @Transactional
    public SpMembreDto setFonctionsOrga(UUID membreId, List<UUID> fonctionOrgaIds) {
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        var nouvelles = new HashSet<>(fonctionOrgaRepo.findAll().stream()
                .filter(f -> fonctionOrgaIds.contains(f.getId()))
                .toList());
        membre.getFonctionsOrga().clear();
        membre.getFonctionsOrga().addAll(nouvelles);
        var saved = membreRepo.update(membre);
        // Attribution immédiate des badges d'appartenance (FONCTION_ORGA), sans attendre le CRON.
        rpService.evalForMembre(saved);
        return SpMembreDto.from(saved);
    }
}
