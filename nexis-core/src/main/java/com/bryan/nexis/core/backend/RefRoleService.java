package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.backend.dto.RefRoleDto;
import com.bryan.nexis.core.datamodel.RefRole;
import com.bryan.nexis.core.datarepository.RefRoleRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class RefRoleService {

    private final RefRoleRepository repo;

    public RefRoleService(RefRoleRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<RefRoleDto> listAll() {
        return repo.findAll().stream().map(RefRoleDto::from).toList();
    }

    /**
     * Crée un rôle (ex. « ROLE_SP_RH »), éventuellement rattaché à un rôle parent
     * dont il hérite les accès.
     */
    @Transactional
    public RefRoleDto create(String code, String label, String parentCode) {
        RefRole parent = (parentCode == null || parentCode.isBlank()) ? null
                : repo.findByCode(parentCode)
                      .orElseThrow(() -> new IllegalArgumentException("Rôle parent inconnu : " + parentCode));
        return RefRoleDto.from(repo.save(new RefRole(code, label, parent)));
    }
}
