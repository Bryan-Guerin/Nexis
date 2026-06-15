import {api} from './api.js'

// Cache des référentiels quasi-statiques (natures, statuts, fonctions…), clé = path.
// On mémoïse la PROMESSE : les appels concurrents (ex. dispatch + interventions qui
// demandent les natures en même temps) sont dédoublonnés, et les montages d'écran
// suivants réutilisent le résultat sans réseau. Vidé au reload/login (mémoire de session).
// À invalider manuellement après une modification du référentiel (écrans de config).

const cache = new Map()   // path -> Promise

/** GET caché par path. En cas d'erreur, on évince pour permettre une nouvelle tentative. */
export function refGet(path) {
  if (!cache.has(path)) {
    cache.set(path, api.get(path).catch(e => { cache.delete(path); throw e }))
  }
  return cache.get(path)
}

/** Invalide une entrée (ou tout si path omis) — à appeler après modification en config. */
export function invalidateRef(path) {
  if (path) cache.delete(path)
  else cache.clear()
}

// Raccourcis pour les référentiels les plus tirés.
export const refNatures         = () => refGet('/sp/natures')
export const refStatutsVeh      = () => refGet('/sp/vehicules/statuts')
export const refEtatsVeh        = () => refGet('/sp/vehicules/etats')
export const refFonctions       = () => refGet('/sp/fonctions')
export const refPlanningStatuts = () => refGet('/sp/planning/statuts')
export const refMe              = () => refGet('/sp/membres/me')
