# Centre de notifications — Nexis SP

La cloche 🔔 (en haut à droite) regroupe des notifications **temps réel** (via le bus WebSocket)
et un **contrôle au chargement** pour les relances échues. Implémentation front :
[`nexis-app/front/src/shared/notifications.js`](../nexis-app/front/src/shared/notifications.js).

> ⚠️ Les **départs en intervention** ne passent **pas** par le centre de notifications : ils
> restent gérés par le **bip / pager** (événement `BIP`).

## Notifications actives

| Notification | Déclencheur (événement) | Condition | Diffusé à | Icône |
|---|---|---|---|---|
| **Affectation d'un effectif** | `AFFECTATION` | — | Dispatcher + Admin SP | 👤 |
| **Véhicule repassé Disponible** | `ETAT_VEHICULE` | `etat = DISPONIBLE` (état système) | Dispatcher + Admin SP | ✅ |
| **Véhicule indisponible / maintenance** | `ETAT_VEHICULE` | `etat ∈ {INDISPONIBLE, MAINTENANCE}` | Admin SP | ⚠️ |
| **Inventaire non conforme** | `INVENTAIRE` | `conforme = false` | Admin SP | 📋 |
| **Effectif désengagé d'un véhicule** | `DESAFFECTATION` | `membreUsername = utilisateur connecté` | L'effectif concerné | 🚪 |
| **Relance de compétence échue** | *(contrôle au chargement)* `GET /sp/rh/relances/ouvertes` | `échéance ≤ aujourd'hui` | RH + Admin SP | 📌 |

### Notes d'audience
- **Dispatcher + Admin SP** = rôles `ROLE_SP_DISPATCH` ou `ROLE_ADMIN_SP` (l'admin couvre le dispatcher par héritage).
- **Admin SP** = `ROLE_ADMIN_SP`.
- **RH + Admin SP** = `ROLE_SP_RH` ou `ROLE_ADMIN_SP`.
- **L'effectif concerné** : ciblage côté client par `username` (le payload `DESAFFECTATION` porte `membreUsername`).

## Comportement
- **Non-lus** : badge rouge sur la cloche ; ouvrir le panneau marque tout comme lu.
- **Persistance** : par **session** (vidé au rechargement de la page). Les relances échues sont
  réinjectées à chaque chargement. *(Évolution possible : persistance via le journal serveur.)*
- **Bouton « Tout effacer »** dans le panneau.

## Ajouter une notification
Ajouter une entrée au tableau `RULES` de `notifications.js` :
```js
{ type: 'TYPE_EVENEMENT', icon: '🔔', audience: () => /* rôles */, test: ev => /* condition sur ev.payload */ }
```
Si l'événement n'embarque pas l'info nécessaire dans son `payload`, l'enrichir côté backend
(service qui publie le `RealtimeEvent`).
