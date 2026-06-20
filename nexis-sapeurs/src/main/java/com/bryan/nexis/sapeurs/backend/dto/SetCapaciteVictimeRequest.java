package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Capacité victime d'un type de véhicule (0 = ne transporte pas de victime). */
@Serdeable
public record SetCapaciteVictimeRequest(Integer valeur) {}
