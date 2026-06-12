# Nexis

Plateforme de gestion des services d'urgence pour serveur RP (Arma) : effectifs, véhicules,
planning de garde, dispatch, interventions et main courante — en temps réel (WebSocket).

Faction **Sapeurs-Pompiers** opérationnelle ; faction **Gendarmerie** en chantier.

## Stack

| Couche | Technologie |
|---|---|
| Backend | Java 25 · Micronaut 5 (HTTP Netty, Security JWT, Data JPA/Hibernate, WebSocket) |
| Base de données | PostgreSQL · migrations Flyway (`nexis-app/src/main/resources/db/migration`) |
| Frontend | Svelte 5 (runes) · Vite · `svelte-spa-router` — servi par le backend (fat jar all-in-one) |
| Build | Maven multi-module (wrapper `./mvnw`) · frontend-maven-plugin |
| Déploiement | Docker (image unique) · Caddy (HTTPS) · GitHub Actions (CI + CD vers Docker Hub + VPS) |

## Modules

```
nexis-core         Socle transverse : utilisateurs/rôles (hiérarchie), journal, notation, bus temps réel
nexis-security     Authentification (BCrypt + JWT), provider Micronaut Security
nexis-gendarmerie  Domaine GN (en chantier)
nexis-sapeurs      Domaine SP : effectifs, véhicules, planning, dispatch, interventions, RH/paie, inventaire…
nexis-app          Module exécutable : Application, configuration, migrations Flyway, front compilé
```

Convention de nommage : `{Module}{Entité}{Couche}` — ex. `SpInterventionController`, `SpMembreService`.

## Démarrage en développement

Prérequis : JDK 25+, PostgreSQL local (base `nexis`), Docker (pour les tests d'intégration).

```bash
# Backend (profil dev par défaut → jdbc:postgresql://localhost:5432/nexis)
./mvnw -pl nexis-app -am mn:run -Denforcer.skip=true

# Frontend en mode dev (proxy /api → localhost:8080)
cd nexis-app/front && npm install && npm run dev
```

Au premier démarrage sur une base vide, un administrateur **admin / root** est créé
automatiquement (⚠️ changer le mot de passe dès la première connexion). Le référentiel SP
(grades, fonctions, objets d'inventaire) peut être initialisé via `scripts/seed_sp_referentiel.sql`.

## Tests

```bash
# Tests unitaires (règles métier dispatch, sécurité…)
./mvnw -pl nexis-sapeurs -am test -Denforcer.skip=true

# Suite complète, y compris l'intégration (PostgreSQL Testcontainers — Docker requis)
./mvnw verify -Denforcer.skip=true
```

## Build & déploiement

```bash
./mvnw -pl nexis-app -am package -Denforcer.skip=true   # fat jar (front inclus)
docker compose up -d --build                            # stack locale complète
```

- **CI** (`.github/workflows/ci.yml`) : build + tests sur chaque push/PR vers `main`.
- **CD** (`.github/workflows/release.yml`) : push sur `main` ou tag `vX.Y.Z` → image Docker Hub
  → déploiement SSH sur le VPS.
- Runbook complet (VPS, base hôte, HTTPS, sauvegardes, branding) : **[docs/deploiement.md](docs/deploiement.md)**.

## Documentation

- [docs/deploiement.md](docs/deploiement.md) — installation et exploitation production
- [docs/notifications.md](docs/notifications.md) — règles du centre de notifications temps réel
- [nexis-app/documentation/todo.adoc](nexis-app/documentation/todo.adoc) — backlog de travail
