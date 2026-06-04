package com.bryan.nexis.security.backend;

import com.bryan.nexis.core.backend.RoleHierarchyService;
import com.bryan.nexis.core.datamodel.RefRole;
import com.bryan.nexis.core.datamodel.RefUser;
import com.bryan.nexis.core.datarepository.RefUserRepository;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexisAuthenticationProviderTest {

    // Coût 4 pour les tests (rapide). En production, le hash est généré avec gensalt(12).
    private static final String HASH_ROOT = BCrypt.hashpw("root", BCrypt.gensalt(4));

    @Test
    void loginReussit_avecIdentifiantsValides() {
        var provider = provider(user("admin", HASH_ROOT, true, Set.of(role("ROLE_SYSTEM"))));

        AuthenticationResponse rep = provider.authenticate(null, creds("admin", "root"));

        assertThat(rep.isAuthenticated()).isTrue();
    }

    @Test
    void loginEchoue_mauvaisMotDePasse() {
        var provider = provider(user("admin", HASH_ROOT, true, Set.of(role("ROLE_GN"))));

        AuthenticationResponse rep = provider.authenticate(null, creds("admin", "mauvais"));

        assertThat(rep.isAuthenticated()).isFalse();
    }

    @Test
    void loginEchoue_compteDesactive() {
        var provider = provider(user("admin", HASH_ROOT, false, Set.of(role("ROLE_GN"))));

        AuthenticationResponse rep = provider.authenticate(null, creds("admin", "root"));

        assertThat(rep.isAuthenticated()).isFalse();
    }

    @Test
    void loginEchoue_utilisateurInexistant() {
        RefUserRepository repo = mock(RefUserRepository.class);
        RoleHierarchyService hierarchy = mock(RoleHierarchyService.class);
        when(repo.findByUsername("inconnu")).thenReturn(Optional.empty());
        var provider = new NexisAuthenticationProvider<>(repo, hierarchy);

        AuthenticationResponse rep = provider.authenticate(null, creds("inconnu", "root"));

        assertThat(rep.isAuthenticated()).isFalse();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private NexisAuthenticationProvider<Object> provider(RefUser user) {
        RefUserRepository repo = mock(RefUserRepository.class);
        RoleHierarchyService hierarchy = mock(RoleHierarchyService.class);
        when(repo.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(hierarchy.expand(anyCollection())).thenReturn(
                user.getRoles().stream().map(RefRole::getCode).toList()
        );
        return new NexisAuthenticationProvider<>(repo, hierarchy);
    }

    private RefUser user(String username, String hash, boolean enabled, Set<RefRole> roles) {
        RefUser u = new RefUser(username, hash);
        u.setEnabled(enabled);
        u.setRoles(roles);
        return u;
    }

    private RefRole role(String code) {
        return new RefRole(code, code.replace("ROLE_", ""), null);
    }

    private UsernamePasswordCredentials creds(String user, String pass) {
        return new UsernamePasswordCredentials(user, pass);
    }
}
