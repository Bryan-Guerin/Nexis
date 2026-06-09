# Déploiement Nexis

Image **all-in-one** (front Svelte + back Micronaut) publiée sur **Docker Hub** par GitHub
Actions, puis **pull** par le VPS. Déploiement en **HTTP direct** (port 8080) dans un premier
temps, puis passage en **HTTPS** via Caddy (Let's Encrypt) une fois le domaine en place.

**PostgreSQL tourne en service système sur l'hôte** (hors Docker, persistant). L'app
conteneurisée (volatile) s'y connecte via `host.docker.internal`. Docker = jetable, la
base = durable.

```
GitHub Actions (build) ──push──► Docker Hub ◄──pull── VPS
                                                       HTTP  : app:8080 exposé directement
                                                       HTTPS : Caddy(80/443) → app:8080 (profil "https")
                                                       app ──host.docker.internal:5432──► PostgreSQL (service hôte)
```

## Vocabulaire
- **GitHub Actions** : moteur qui exécute les workflows (CI + CD). Voir `.github/workflows/`.
- **Docker Hub** : registre où est stockée l'image.
- **CI** (`ci.yml`) : build + tests à chaque push/PR.
- **CD** (`release.yml`) : build → push Docker Hub → déploiement SSH sur le VPS.

---

## Architecture des fichiers sur le VPS

```
/home/nexis/nexis/              ← poste de pilotage (compose + secrets)
├── docker-compose.yaml         (copié du repo, tel quel)
├── .env                        (créé depuis .env.example)
└── Caddyfile                   (copié du repo — UNIQUEMENT pour le HTTPS)

/opt/volumes/nexis/             ← données persistées (config + logs)
├── application.properties      (surcharges runtime, sans rebuild)
├── log4j2.xml                  (copié du repo — niveaux de log modifiables à chaud)
├── branding/                   (logos paramétrables par instance — ex: sp-logo.png)
└── logs/                       (rempli par l'application)
```

> 🎨 **Branding** : dépose le logo de la caserne dans `/opt/volumes/nexis/branding/sp-logo.png`.
> Il est servi sur `/branding/sp-logo.png` et s'affiche sur le tableau de bord SP. Aucun
> fichier = aucun logo affiché (pas d'erreur). Modifiable à chaud, sans rebuild ni redéploiement.

> ⚠️ Ne **jamais** copier `docker-compose.override.yaml` sur le VPS : il force un build local
> au lieu du pull de l'image.

---

## 1. Secrets GitHub à créer
*Repo → Settings → Secrets and variables → Actions → New repository secret*

| Secret | Valeur |
|---|---|
| `DOCKERHUB_USERNAME` | identifiant Docker Hub |
| `DOCKERHUB_TOKEN` | *Access Token* Docker Hub (Account Settings → Security) |
| `VPS_HOST` | IP ou domaine du VPS |
| `VPS_USER` | utilisateur SSH (ex: `nexis`) |
| `VPS_SSH_KEY` | clé privée SSH autorisée sur le VPS |
| `VPS_PATH` | dossier du compose : **`/home/nexis/nexis`** |
| `VPS_PORT` | *(optionnel)* port SSH, défaut 22 |

---

## 2. Préparer le VPS (une seule fois)

### 2.1 Droits Docker sans sudo
```bash
sudo usermod -aG docker nexis     # (ou ton user)
# déconnexion / reconnexion SSH pour activer le groupe
docker ps                         # doit marcher sans sudo
```

### 2.2 Arborescence
```bash
mkdir -p /home/nexis/nexis
sudo mkdir -p /opt/volumes/nexis/logs /opt/volumes/nexis/branding
sudo chown -R nexis:nexis /opt/volumes/nexis
# (optionnel) déposer le logo de la caserne :
#   cp mon-logo.png /opt/volumes/nexis/branding/sp-logo.png
```

### 2.3 Partager /home/nexis avec un autre user admin (ex: debian) — optionnel
```bash
sudo setfacl -R  -m u:debian:rwX /home/nexis    # accès immédiat
sudo setfacl -R -d -m u:debian:rwX /home/nexis  # héritage pour les nouveaux fichiers
# (sudo apt install acl si setfacl manque)
```

### 2.4 Déposer les fichiers
| Fichier | Emplacement | Source |
|---|---|---|
| `docker-compose.yaml` | `/home/nexis/nexis/` | copié du repo |
| `.env` | `/home/nexis/nexis/` | créé depuis `.env.example` |
| `log4j2.xml` | `/opt/volumes/nexis/` | copié de `nexis-app/src/main/resources/log4j2.xml` |
| `application.properties` | `/opt/volumes/nexis/` | créé (voir 2.6) |

> Les fichiers de `/opt/volumes/nexis/` sont montés en **bind-mount de fichier** : ils
> **doivent exister avant** le premier `up`, sinon Docker crée des dossiers à leur place.

### 2.5 PostgreSQL hôte (service système)

La base tourne **sur l'hôte**, pas en conteneur. L'app la joint via `host.docker.internal`
(configuré dans le compose : `extra_hosts: host-gateway`). Il faut créer le user/la base,
puis autoriser les connexions venant du sous-réseau Docker.

**a) Créer le user et la base** (une fois) :
```bash
sudo -u postgres psql <<'SQL'
CREATE USER nexis WITH PASSWORD 'mot-de-passe-fort';
CREATE DATABASE nexis OWNER nexis;
GRANT ALL PRIVILEGES ON DATABASE nexis TO nexis;
SQL
```

**b) Écoute réseau** — dans `postgresql.conf` (`sudo -u postgres psql -c 'SHOW config_file;'`) :
```conf
listen_addresses = '*'        # ou 'localhost,172.17.0.1'
```

**c) Autoriser le sous-réseau Docker** — dans `pg_hba.conf` (même dossier) :
```conf
# type   db      user    address          method
host     nexis   nexis   172.16.0.0/12    scram-sha-256
```

**d) Redémarrer** :
```bash
sudo systemctl restart postgresql
```

**e) iptables** — ouvrir 5432 pour Docker, **fermer l'accès public** :
```bash
sudo iptables -A INPUT -s 172.16.0.0/12 -p tcp --dport 5432 -j ACCEPT
sudo iptables -D INPUT -p tcp --dport 5432 -j ACCEPT   # retire une éventuelle expo publique
sudo netfilter-persistent save
```

> Les migrations Flyway s'appliquent automatiquement au démarrage de l'app (jusqu'à la
> dernière version Vxx) **dans cette base hôte**. Vérifier : `sudo -u postgres psql -d nexis -c '\dt'`.

### 2.6 `.env` (déploiement HTTP)
```properties
NEXIS_IMAGE=<user>/nexis:latest
POSTGRES_USER=nexis
POSTGRES_PASSWORD=<mot-de-passe-fort>
PUID=1000          # résultat de `id -u`
PGID=1000          # résultat de `id -g`
APP_PORT=8080
JWT_GENERATOR_SIGNATURE_SECRET=<openssl rand -base64 48>
# HTTPS (plus tard) :
# NEXIS_DOMAIN=pompier.serveur.fr
# ACME_EMAIL=toi@serveur.fr
```

### 2.7 `application.properties` externe (minimal)
La connexion DB et le secret JWT viennent des variables d'env du compose (prioritaires).
Ce fichier sert aux surcharges ajustables sans rebuild :
```properties
# Surcharges de production, modifiables sans reconstruire l'image.
# micronaut.security.token.generator.access-token.expiration=28800
# micronaut.server.max-request-size=27262976
```

### 2.8 Ouvrir le port HTTP (iptables)
```bash
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
sudo netfilter-persistent save
```

### 2.9 Démarrer
```bash
cd /home/nexis/nexis
docker compose pull
docker compose up -d          # démarre l'app (caddy reste éteint : profil https)
docker compose ps             # app doit passer "healthy"
docker compose logs -f app    # Flyway doit appliquer les migrations dans la base hôte
```
Accès : **http://IP_DU_VPS:8080**

### 2.10 Premier administrateur (auto-seed)
L'admin est **créé automatiquement** au 1er démarrage **si la base ne contient aucun
utilisateur**, avec des identifiants **en dur** :

| Identifiant | Mot de passe |
|---|---|
| `admin` | `root` |

> 🔐 **À FAIRE dès la première connexion** : menu utilisateur (haut droite) →
> « Modifier mon mot de passe ». Le mot de passe `root` est volontairement trivial et
> ne doit pas rester en place.

Idempotent : dès qu'un utilisateur existe, le seed ne fait plus rien. Pour créer un admin
**a posteriori** sur une base déjà peuplée, voir la création manuelle en annexe (§7).

### 2.11 Initialiser le référentiel SP (optionnel)
Grades, fonctions et objets d'inventaire par défaut. Re-jouable sans risque (`ON CONFLICT`).
```bash
sudo -u postgres psql -d nexis -f scripts/seed_sp_referentiel.sql
```

---

## 3. Déploiement continu
- **Push sur `main`** → image `:latest` reconstruite et poussée, puis le job `deploy` se
  connecte en SSH et exécute `docker compose pull app && up -d` dans `VPS_PATH`.
- **Release versionnée** : `git tag v0.1.0 && git push --tags` → image taguée `0.1.0` / `0.1` / `latest`.

---

## 4. Passage en HTTPS (`pompier.serveur.fr`)

Prérequis : un enregistrement **DNS A** `pompier → IP_VPS` (le sous-domaine peut viser un VPS
différent de la machine du domaine principal), propagation vérifiée (`ping pompier.serveur.fr`).

1. Copier le `Caddyfile` du repo dans `/home/nexis/nexis/`.
2. Dans `.env` : renseigner `NEXIS_DOMAIN=pompier.serveur.fr` et `ACME_EMAIL=...`.
3. Ouvrir 80/443 :
   ```bash
   sudo iptables -A INPUT -p tcp --dport 80  -j ACCEPT
   sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```
4. Couper l'exposition directe : commenter le bloc `ports:` du service `app` dans le compose.
5. Démarrer avec le profil https :
   ```bash
   docker compose --profile https up -d
   ```
   Caddy obtient le certificat Let's Encrypt automatiquement → **https://pompier.serveur.fr**.

---

## 5. Image privée (optionnel)
Si le repository Docker Hub est privé, le VPS doit s'authentifier une fois :
```bash
docker login -u <user>      # token en mot de passe ; persiste dans ~/.docker/config.json
```

---

## 6. Local (poste de dev)
`docker-compose.override.yaml` (auto-chargé) construit l'image au lieu de la pull :
```bash
cp .env.example .env        # au minimum un JWT secret
docker compose up -d --build
# → http://localhost:8080
```

---

## 7. Annexe — créer un admin manuellement (base déjà peuplée)
L'auto-seed (§2.10) ne s'applique que sur une base vide. Pour ajouter un admin ensuite :
```bash
sudo apt install -y apache2-utils
htpasswd -bnBC 12 "" 'TonMotDePasse' | tr -d ':\n' | sed 's/\$2y\$/\$2a\$/'   # → hash $2a$...
sudo -u postgres psql -d nexis
```
```sql
INSERT INTO ref_user (username, password_hash, enabled)
VALUES ('admin2', '$2a$12$colle-ton-hash-ici', true);

INSERT INTO ref_user_role (user_id, role_id)
SELECT u.id, r.id FROM ref_user u, ref_role r
WHERE u.username = 'admin2' AND r.code = 'ROLE_SYSTEM';
```

---

## Sauvegarde de la base
La base vit sur l'hôte → sauvegarde indépendante de Docker. Script fourni
(`scripts/nexis-backup.sh`) : dump compressé daté, **conserve les 3 dernières** sur le VPS.
```bash
sudo mkdir -p /opt/scripts
sudo cp scripts/nexis-backup.sh /opt/scripts/nexis-backup.sh
sudo chmod +x /opt/scripts/nexis-backup.sh
sudo crontab -e
# Ajouter (tous les jours à 3 h) :
0 3 * * * /opt/scripts/nexis-backup.sh
```
Restauration :
```bash
gunzip -c /opt/backups/nexis_2026-06-09.sql.gz | sudo -u postgres psql -d nexis
```
> Sauvegardes persistées sur le VPS uniquement (pas de copie hors-site automatisée).
> Si possible, télécharge ponctuellement un dump en local (`scp`) pour parer à une perte du VPS.

---

## Configuration externalisée — comment ça marche
- `MICRONAUT_CONFIG_FILES=/config/application.properties` → Micronaut charge la config externe
  **en plus** du packagé, avec priorité supérieure. Les variables d'env (`DATASOURCES_*`,
  `JWT_*`) restent au-dessus de tout.
- `-Dlog4j2.configurationFile=/config/log4j2.xml` → log4j lit la config externe.
- `-DLOG_DIR=/logs` (monté sur `/opt/volumes/nexis/logs`) → logs persistés sur l'hôte.
- `user: "${PUID}:${PGID}"` → le container écrit les logs avec ton UID : les fichiers
  t'appartiennent (pas de fichiers root dans le volume).

## Santé & exploitation
- `GET /health` : statut appli + DB (200 UP / 503 DOWN). Sert au `HEALTHCHECK` Docker.
- `docker compose ps` : voir l'état (healthy).
- Logs applicatifs : `/opt/volumes/nexis/logs/nexis.log` (jour en cours) + archives `nexis-AAAA-MM-JJ.log.gz`.
  Rotation : **1 fichier/jour**, rétention **10 jours OU 2 Go cumulés** (le premier seuil atteint).
- **La base est sur l'hôte** : `docker compose down` (même avec `-v`) **ne la touche pas**.
  Sauvegarde : `sudo -u postgres pg_dump nexis > nexis_$(date +%F).sql`.
- ⚠️ `docker compose down -v` efface uniquement les volumes Docker restants (certificats
  Caddy : `caddy-data`). Pense à re-déclencher l'émission du certificat si tu le fais.
