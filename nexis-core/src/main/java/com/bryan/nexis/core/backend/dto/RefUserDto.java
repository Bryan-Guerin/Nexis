package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.RefUser;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Serdeable
public record RefUserDto(
        UUID id,
        String username,
        Set<String> roles,
        @Nullable String avatar,
        boolean enabled,
        @Nullable Long steamId,
        Instant createdAt
) {
    public static RefUserDto from(RefUser u) {
        Set<String> roleCodes = u.getRoles().stream()
                .map(r -> r.getCode())
                .collect(Collectors.toSet());
        return new RefUserDto(u.getId(), u.getUsername(), roleCodes, u.getAvatar(),
                u.isEnabled(), u.getSteamId(), u.getCreatedAt());
    }
}
