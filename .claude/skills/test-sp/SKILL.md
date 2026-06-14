---
name: test-sp
description: Lance les tests du module Sapeurs-Pompiers (nexis-sapeurs) et ses dépendances avec le bon JDK.
disable-model-invocation: true
---

# test-sp

Exécute la suite de tests du module `nexis-sapeurs` (+ modules amont via `-am`).

## Étapes

1. Depuis la racine du dépôt `C:/dev/nexis`, lancer via le tool Bash :

   ```bash
   cd C:/dev/nexis && ./mvnw -B -ntp -pl nexis-sapeurs -am test -Denforcer.skip=true
   ```

   - `JAVA_HOME` est fixé globalement (`settings.json` → JDK 26) ; ne pas le ré-exporter.
   - Le cwd du tool Bash peut être perdu après un `cd front` antérieur → toujours préfixer `cd C:/dev/nexis &&`.

2. Si un argument est fourni (ex. `/test-sp SpPlanningServiceTest`), filtrer :

   ```bash
   cd C:/dev/nexis && ./mvnw -B -ntp -pl nexis-sapeurs -am test -Denforcer.skip=true -Dtest=<arg>
   ```

3. Rapporter : tests passés/échoués, et pour chaque échec la classe + message d'assertion.
