package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.backend.dto.RefUserDto;
import com.bryan.nexis.core.datamodel.RefRole;
import com.bryan.nexis.core.datamodel.RefUser;
import com.bryan.nexis.core.datarepository.RefRoleRepository;
import com.bryan.nexis.core.datarepository.RefUserRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class RefUserService {

    private final RefUserRepository repo;
    private final RefRoleRepository roleRepo;

    public RefUserService(RefUserRepository repo, RefRoleRepository roleRepo) {
        this.repo = repo;
        this.roleRepo = roleRepo;
    }

    @Transactional
    public List<RefUserDto> listAll() {
        return repo.findAll().stream().map(RefUserDto::from).toList();
    }

    /** Profil de l'utilisateur connecté. */
    @Transactional
    public RefUserDto me(String username) {
        return RefUserDto.from(repo.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + username)));
    }

    /** Met à jour l'avatar de l'utilisateur connecté. */
    @Transactional
    public RefUserDto updateAvatar(String username, String avatar) {
        var user = repo.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + username));
        user.setAvatar(avatar);
        return RefUserDto.from(repo.update(user));
    }

    // passwordHash doit être fourni déjà hashé (BCrypt) par l'appelant
    @Transactional
    public RefUserDto create(String username, String passwordHash, Set<String> roleCodes) {
        Set<RefRole> roles = roleCodes.stream()
                .map(code -> roleRepo.findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("Rôle inconnu : " + code)))
                .collect(Collectors.toSet());
        RefUser user = new RefUser(username, passwordHash);
        user.setRoles(roles);
        return RefUserDto.from(repo.save(user));
    }

    /** Remplace l'ensemble des rôles d'un utilisateur. */
    @Transactional
    public RefUserDto updateRoles(UUID userId, Set<String> roleCodes) {
        RefUser user = repo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + userId));
        Set<RefRole> roles = roleCodes.stream()
                .map(code -> roleRepo.findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("Rôle inconnu : " + code)))
                .collect(Collectors.toSet());
        user.setRoles(roles);
        return RefUserDto.from(repo.update(user));
    }

    /** Définit un nouveau hash de mot de passe (déjà hashé par l'appelant). */
    @Transactional
    public void updatePasswordHash(UUID userId, String passwordHash) {
        RefUser user = repo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + userId));
        user.setPasswordHash(passwordHash);
        repo.update(user);
    }

    /**
     * Changement de son propre mot de passe : vérifie l'ancien (via le prédicat
     * fourni par l'appelant, qui détient la logique BCrypt) avant d'appliquer le
     * nouveau hash. Atomique.
     */
    @Transactional
    public void changeOwnPassword(String username, Predicate<String> currentPasswordMatches, String newPasswordHash) {
        RefUser user = repo.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable : " + username));
        if (user.getPasswordHash() == null || !currentPasswordMatches.test(user.getPasswordHash())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect");
        }
        user.setPasswordHash(newPasswordHash);
        repo.update(user);
    }
}
