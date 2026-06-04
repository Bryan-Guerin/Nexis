package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.backend.dto.CreateNotationRequest;
import com.bryan.nexis.core.backend.dto.NotationDto;
import com.bryan.nexis.core.datamodel.Notation;
import com.bryan.nexis.core.datarepository.NotationRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@Singleton
public class NotationService {

    private final NotationRepository repo;

    public NotationService(NotationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<NotationDto> list(String faction, UUID membreId) {
        return repo.findForMembre(faction, membreId)
                .stream().map(NotationDto::from).toList();
    }

    @Transactional
    public NotationDto create(String faction, UUID membreId, CreateNotationRequest req, String evaluateur) {
        if (req.mois() == null || !req.mois().matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Mois invalide (format attendu : AAAA-MM).");
        }
        check("Comportement & discipline", req.comportementDiscipline());
        check("Compétences techniques", req.competencesTechniques());
        check("Aptitude physique", req.aptitudePhysique());
        check("Initiative & autonomie", req.initiativeAutonomie());
        check("Esprit d'équipe", req.espritEquipe());
        check("Respect des consignes de sécurité", req.respectSecurite());

        var n = new Notation(faction, membreId, req.mois(),
                req.comportementDiscipline(), req.competencesTechniques(), req.aptitudePhysique(),
                req.initiativeAutonomie(), req.espritEquipe(), req.respectSecurite(),
                req.observations(), req.objectifs(), evaluateur);
        return NotationDto.from(repo.save(n));
    }

    private static void check(String libelle, int note) {
        if (note < 0 || note > 5) {
            throw new IllegalArgumentException("« " + libelle + " » doit être compris entre 0 et 5.");
        }
    }
}
