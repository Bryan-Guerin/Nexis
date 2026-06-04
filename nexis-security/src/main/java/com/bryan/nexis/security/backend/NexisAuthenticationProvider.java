package com.bryan.nexis.security.backend;

import com.bryan.nexis.core.backend.RoleHierarchyService;
import com.bryan.nexis.core.datarepository.RefUserRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NexisAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

    private static final Logger log = LoggerFactory.getLogger(NexisAuthenticationProvider.class);

    private final RefUserRepository userRepository;
    private final RoleHierarchyService roleHierarchyService;

    public NexisAuthenticationProvider(RefUserRepository userRepository,
                                       RoleHierarchyService roleHierarchyService) {
        this.userRepository = userRepository;
        this.roleHierarchyService = roleHierarchyService;
    }

    @Override
    public AuthenticationResponse authenticate(@Nullable HttpRequest<B> request,
                                               AuthenticationRequest<String, String> authRequest) {
        String username = authRequest.getIdentity();
        String password = authRequest.getSecret();

        return userRepository.findByUsername(username)
                .filter(user -> user.isEnabled()
                        && user.getPasswordHash() != null
                        && BCrypt.checkpw(password, user.getPasswordHash()))
                .map(user -> {
                    log.info("Login successful : {}", user.getUsername());
                    var expandedRoles = roleHierarchyService.expand(user.getRoles());
                    return AuthenticationResponse.success(user.getUsername(), expandedRoles);
                })
                .orElseGet(() -> {
                    log.warn("Login failed : {}", username);
                    return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
                });
    }
}
