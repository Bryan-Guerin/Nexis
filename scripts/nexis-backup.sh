#!/usr/bin/env bash
# Sauvegarde quotidienne de la base Nexis (PostgreSQL sur l'hôte).
# Conserve uniquement les 3 dernières sauvegardes. Persiste sur le VPS.
#
# Installation :
#   sudo cp scripts/nexis-backup.sh /opt/scripts/nexis-backup.sh
#   sudo chmod +x /opt/scripts/nexis-backup.sh
#   sudo crontab -e   →   0 3 * * * /opt/scripts/nexis-backup.sh
set -euo pipefail

BACKUP_DIR=/opt/backups
KEEP=3

mkdir -p "$BACKUP_DIR"

# Dump compressé, daté
sudo -u postgres pg_dump nexis | gzip > "$BACKUP_DIR/nexis_$(date +%F).sql.gz"

# Purge : ne garder que les $KEEP plus récents (les plus anciens sont supprimés)
ls -1t "$BACKUP_DIR"/nexis_*.sql.gz | tail -n +$((KEEP + 1)) | xargs -r rm -f
