package com.bryan.nexis.core.backend;

import com.bryan.nexis.core.datamodel.RefRole;
import com.bryan.nexis.core.datarepository.RefRoleRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.*;

@Singleton
public class RoleHierarchyService {

    private final RefRoleRepository roleRepository;

    public RoleHierarchyService(RefRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    // Retourne les codes de tous les rôles couverts : ceux assignés + tous leurs descendants.
    @Transactional
    public List<String> expand(Collection<RefRole> directRoles) {
        List<RefRole> all = roleRepository.findAll();

        Map<String, List<String>> children = new HashMap<>();
        for (RefRole role : all) {
            if (role.getParent() != null) {
                children.computeIfAbsent(role.getParent().getCode(), k -> new ArrayList<>())
                        .add(role.getCode());
            }
        }

        Set<String> expanded = new LinkedHashSet<>();
        for (RefRole role : directRoles) {
            collectDescendants(role.getCode(), children, expanded);
        }
        return List.copyOf(expanded);
    }

    private void collectDescendants(String code, Map<String, List<String>> children, Set<String> result) {
        if (!result.add(code)) return;
        for (String child : children.getOrDefault(code, List.of())) {
            collectDescendants(child, children, result);
        }
    }
}
