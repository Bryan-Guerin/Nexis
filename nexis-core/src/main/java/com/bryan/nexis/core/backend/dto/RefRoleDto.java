package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.RefRole;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RefRoleDto(String code, String label, @Nullable String parentCode) {

    public static RefRoleDto from(RefRole r) {
        return new RefRoleDto(
                r.getCode(),
                r.getLabel(),
                r.getParent() != null ? r.getParent().getCode() : null
        );
    }
}
