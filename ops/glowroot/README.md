# Glowroot — observabilité Nexis

Agent APM Java attaché via `-javaagent` (voir `JAVA_OPTS` dans `docker-compose.yaml`).
Données + config dans le volume monté `/opt/glowroot` (sur le VPS).

## Appliquer la conf de référence

`glowroot-config.reference.json` contient :
- **Gauges** : pool Hikari (saturation), threads JVM, CPU, heap.
- **Instrumentation** : capture des transactions web (Micronaut/Netty n'est PAS auto-instrumenté car non-servlet → on cible les classes annotées `@Controller`).
- **Alerte** : `ThreadsAwaitingConnection > 0` pendant 1 min (pool saturé).

### Méthode A — UI (simple)
Recopier les entrées dans Glowroot :
- Configuration > Gauges > Add gauge (un par `mbeanObjectName`).
- Configuration > Instrumentation > Add (classAnnotation = `io.micronaut.http.annotation.Controller`).
- Configuration > Alerts > Add.

### Méthode B — fichier (reproductible)
Fusionner les tableaux `gauges` / `instrumentation` / `alerts` dans le `config.json`
de Glowroot (dossier data, ex. `/opt/glowroot/glowroot/config.json`) **sans écraser**
les autres sections, puis redémarrer la JVM :
```bash
# éditer /opt/glowroot/glowroot/config.json (ajouter les entrées)
docker compose restart app
```

## Lecture pendant un incident « pending »
- **`ThreadsAwaitingConnection` > 0** → pool Hikari saturé = cause serveur. Regarder le plugin JDBC (requêtes lentes / N+1).
- **`ThreadsAwaitingConnection` = 0** et `ActiveConnections` < max → le pool n'est pas en cause → voir les transactions web (endpoint lent ? volume d'appels ?) ou le client.
- **`ThreadCount`** qui explose → fuite/saturation de threads.

## Pré-requis
- Image déployée avec `datasources.default.register-mbeans=true` + `pool-name=nexis-default`
  (sinon le MBean Hikari n'existe pas → absent de l'autocomplétion des gauges).
- Plugin JDBC chargé (`/opt/glowroot/plugins/` non vide) pour la capture SQL.
