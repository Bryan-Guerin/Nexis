package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.DeclencheurFlag;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** Demande de lot recommandé : la nature choisie + les flags actifs + le nombre de victimes. */
@Serdeable
public record SpLotProposeRequest(
        @Nullable UUID natureId,
        @Nullable List<DeclencheurFlag> flags,
        @Nullable Integer nbVictimes
) {}
