package com.bryan.nexis;

import io.micronaut.runtime.Micronaut;

public class Application {

    public static void main(String[] args) {
        // Environnement "dev" par défaut : charge application-dev.properties
        // (connexion à la base PostgreSQL locale). Ignoré si un environnement est
        // explicitement défini — `test` activé par @MicronautTest, `prod` via
        // MICRONAUT_ENVIRONMENTS dans le docker-compose de prod.
        Micronaut.build(args)
                .mainClass(Application.class)
                .defaultEnvironments("dev")
                .start();
    }
}
