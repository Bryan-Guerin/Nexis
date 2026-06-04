package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.core.datarepository.RefUserRepository;
import com.bryan.nexis.gendarmerie.backend.dto.GnMembreDto;
import com.bryan.nexis.gendarmerie.datamodel.GnMembre;
import com.bryan.nexis.gendarmerie.datarepository.GnGradeRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnMembreRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class GnMembreService {

    private final GnMembreRepository membreRepo;
    private final RefUserRepository  userRepo;
    private final GnGradeRepository  gradeRepo;

    public GnMembreService(GnMembreRepository membreRepo, RefUserRepository userRepo, GnGradeRepository gradeRepo) {
        this.membreRepo = membreRepo;
        this.userRepo   = userRepo;
        this.gradeRepo  = gradeRepo;
    }

    @Transactional
    public List<GnMembreDto> listAll() {
        return membreRepo.findAll().stream().map(GnMembreDto::from).toList();
    }

    @Transactional
    public List<GnMembreDto> listActifs() {
        return membreRepo.findByActif(true).stream().map(GnMembreDto::from).toList();
    }

    @Transactional
    public GnMembreDto findByUsername(String username) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + username));
        return GnMembreDto.from(membreRepo.findByUserId(user.getId())
                .orElseThrow(() -> new NoSuchElementException("Membre GN introuvable pour l'utilisateur : " + username)));
    }

    @Transactional
    public GnMembreDto create(UUID userId, UUID gradeId, String matricule) {
        var user  = userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + userId));
        var grade = gradeRepo.findById(gradeId)
                .orElseThrow(() -> new NoSuchElementException("Grade GN introuvable : " + gradeId));
        return GnMembreDto.from(membreRepo.save(new GnMembre(user, grade, matricule)));
    }
}
