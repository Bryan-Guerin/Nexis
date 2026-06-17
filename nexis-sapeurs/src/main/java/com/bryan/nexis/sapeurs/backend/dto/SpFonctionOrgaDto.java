package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpFonctionOrga;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/**
 * Vue exposée d'une fonction d'organigramme.
 * {@code parentId} / {@code parentCode} = null à la racine.
 */
@Serdeable
public record SpFonctionOrgaDto(
        UUID id, String code, String label,
        UUID parentId, String parentCode,
        int position, String icone) {

    public static SpFonctionOrgaDto from(SpFonctionOrga f) {
        SpFonctionOrga p = f.getParent();
        return new SpFonctionOrgaDto(
                f.getId(), f.getCode(), f.getLabel(),
                p != null ? p.getId() : null,
                p != null ? p.getCode() : null,
                f.getPosition(), f.getIcone());
    }
}
