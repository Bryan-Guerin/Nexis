package com.bryan.nexis.sapeurs.backend.bilan;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Contenu typé du bilan SR (secours routier), sérialisé en JSON (jsonb) — un par intervention.
 * Une scène vue de haut (type de route) + les véhicules impliqués, positionnés (x,y normalisés
 * 0..1). Les victimes SAP se relient à un véhicule via {@code BilanSapContenu.vehiculeSrId} = id.
 */
@Serdeable
public record BilanSrContenu(@Nullable RouteType routeType, @Nullable List<VehiculeSr> vehicules) {

    /** Véhicule impliqué, posé sur la scène. {@code id} = uuid client (référencé par les victimes). */
    @Serdeable
    public record VehiculeSr(String id, TypeVehiculeSr type, String modele, String plaque, String carburation,
                             BilanSapContenu.LocalisationChoc choc,
                             Boolean incendie, Boolean desincarcere, Boolean stabilise,
                             Double x, Double y) {}

    public enum RouteType       { AUTOROUTE_3V, BIDIRECTIONNEL_2V }
    public enum TypeVehiculeSr  { VOITURE, CAMION, UTILITAIRE, MOTO }
}
