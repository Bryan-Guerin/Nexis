package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** Mise à jour des statuts de renfort (null = inchangé). */
@Serdeable
public record UpdateRenfortRequest(@Nullable String renfortGn, @Nullable String renfortVinci) {}
