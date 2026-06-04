# Déploiement Nexis

Image **all-in-one** (front Svelte + back Micronaut) publiée sur **Docker Hub** par GitHub
Actions, puis **pull** par le VPS qui la sert derrière **Caddy** (HTTPS automatique).

```
GitHub Actions (build) ──push──► Docker Hub ◄──pull── VPS : Caddy(80/443) → app:8080 → Postgres
```

## Vocabulaire
- **GitHub Actions** : le moteur qui exécute les workflows (CI + CD). Voir `.github/workflows/`.
- **Docker Hub** : le registre où est stockée l'image (équivalent de GHCR, mais chez Docker).
- **CI** (`ci.yml`) : build + tests à chaque push/PR.
- **CD** (`release.yml`) : build → push Docker Hub → déploiement SSH sur le VPS.

## 1. Secrets GitHub à créer
*Repo → Settings → Secrets and variables → Actions → New repository secret*

| Secret | Valeur |
|---|---|
| `DOCKERHUB_USERNAME` | ton identifiant Docker Hub |
| `DOCKERHUB_TOKEN` | un *Access Token* Docker Hub (Account Settings → Security) |
| `VPS_HOST` | IP ou domaine du VPS |
| `VPS_USER` | utilisateur SSH (ex: `deploy`) |
| `VPS_SSH_KEY` | clé privée SSH autorisée sur le VPS |
| `VPS_PATH` | dossier du compose sur le VPS (ex: `/opt/nexis`) |
| `VPS_PORT` | *(optionnel)* port SSH, défaut 22 |

> Le nom du dépôt Docker Hub est `nexis` → image `:<user>/nexis`. Crée le repository
> sur Docker Hub (public de préférence ; sinon voir §4 pour l'auth du VPS).

## 2. Préparer le VPS (une seule fois)
```bash
# Docker + plugin compose installés (https://docs.docker.com/engine/install/)
sudo mkdir -p /opt/nexis && cd /opt/nexis

# Copier UNIQUEMENT ces fichiers depuis le repo (PAS docker-compose.override.yaml !) :
#   - docker-compose.yaml
#   - Caddyfile
# puis créer le .env :
cp .env.example .env && nano .env
```
Régler dans `.env` : `NEXIS_IMAGE=<user>/nexis:latest`, `NEXIS_DOMAIN=nexis.mondomaine.fr`,
`ACME_EMAIL`, `JWT_GENERATOR_SIGNATURE_SECRET` (≥32 car. : `openssl rand -base64 48`),
`POSTGRES_PASSWORD`.

Prérequis HTTPS : le **DNS A/AAAA** du domaine pointe vers le VPS et les **ports 80/443**
sont ouverts. Premier démarrage manuel :
```bash
docker compose pull
docker compose up -d
docker compose logs -f
```

## 3. Déploiement continu
- **Push sur `main`** → l'image `:latest` est reconstruite et poussée, puis le job `deploy`
  se connecte en SSH au VPS et fait `docker compose pull && up -d`.
- **Release versionnée** : `git tag v0.1.0 && git push --tags` → image taguée `0.1.0` / `0.1` / `latest`.

## 4. Image privée (optionnel)
Si le repository Docker Hub est privé, le VPS doit s'authentifier une fois :
```bash
docker login -u <user>      # token en mot de passe ; persiste dans ~/.docker/config.json
```

## 5. Local (poste de dev)
`docker-compose.override.yaml` (auto-chargé) construit l'image au lieu de la pull :
```bash
cp .env.example .env        # mettre au moins un JWT secret
docker compose up -d --build
# → http://localhost:8080  (NEXIS_DOMAIN=localhost ⇒ Caddy en HTTPS interne sur 443)
```

## Santé & exploitation
- `GET /health` : statut appli + DB (200 UP / 503 DOWN). Sert au `HEALTHCHECK` Docker.
- `docker compose ps` : voir l'état (healthy) ; Caddy n'accepte le trafic que si `app` est *healthy*.
- ⚠️ `docker compose down -v` efface la base **et** les certificats Caddy (volumes `nexis-db`, `caddy-data`).
