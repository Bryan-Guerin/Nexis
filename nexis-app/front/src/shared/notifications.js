import {derived, get, writable} from 'svelte/store'
import {realtime} from './realtime.js'
import {currentUser} from './stores.js'
import {api} from './api.js'

// Centre de notifications (session) : écoute le bus temps réel et retient les
// événements pertinents pour l'utilisateur connecté. Les départs en intervention
// NE passent PAS par ici (ils restent au bip/pager). Voir docs/notifications.md.

let _id = 0
const MAX = 60

export const notifications = writable([])
export const unread = derived(notifications, ns => ns.filter(n => !n.read).length)

function roles()       { return get(currentUser)?.roles ?? [] }
function username()    { return get(currentUser)?.username }
function isDispatch()  { return roles().includes('ROLE_SP_DISPATCH') || roles().includes('ROLE_ADMIN_SP') }
function isAdmin()     { return roles().includes('ROLE_ADMIN_SP') }
function isRhOrAdmin() { return roles().includes('ROLE_SP_RH') || roles().includes('ROLE_ADMIN_SP') }

/**
 * Règles de notification temps réel. Pour en ajouter une : pousser une entrée ici.
 *   type     : type d'événement temps réel
 *   icon     : pictogramme
 *   audience : () => bool — qui voit cette notification
 *   test     : (ev) => bool — condition sur l'événement (payload…)
 */
const RULES = [
  { type: 'AFFECTATION',    icon: '👤', audience: isDispatch, test: () => true },
  { type: 'ETAT_VEHICULE',  icon: '✅', audience: isDispatch, test: ev => ev.payload?.etat === 'DISPONIBLE' },
  { type: 'ETAT_VEHICULE',  icon: '⚠️', audience: isAdmin,    test: ev => ['INDISPONIBLE', 'MAINTENANCE'].includes(ev.payload?.etat) },
  { type: 'INVENTAIRE',     icon: '📋', audience: isAdmin,    test: ev => ev.payload?.conforme === 'false' },
  { type: 'DESAFFECTATION', icon: '🚪', audience: () => true, test: ev => ev.payload?.membreUsername === username() },
]

function push(items) {
  if (!items.length) return
  notifications.update(list => [...items, ...list].slice(0, MAX))
}
function notif(icon, message) { return { id: ++_id, icon, message, time: Date.now(), read: false } }

function fromEvent(ev) {
  if (ev?.faction !== 'SP') return null
  for (const r of RULES) {
    if (ev.type === r.type && r.test(ev) && r.audience()) return notif(r.icon, ev.message)
  }
  return null
}

// Relances de compétence échues (RH/admin) — non temps réel : vérifiées au chargement.
async function seedRelancesEchues() {
  if (!isRhOrAdmin()) return
  const today = new Date().toISOString().slice(0, 10)
  const list = await api.get('/sp/rh/relances/ouvertes').catch(() => [])
  push(list.filter(r => r.echeance && r.echeance <= today)
           .map(r => notif('📌', `Relance échue — ${r.matricule} : ${r.texte}`)))
}

let started = false
export function startNotifications() {
  if (started) return
  started = true
  realtime.on(ev => { const n = fromEvent(ev); if (n) push([n]) })
  seedRelancesEchues()
}

export function markAllRead() { notifications.update(l => l.map(n => (n.read ? n : { ...n, read: true }))) }
export function clearNotifications() { notifications.set([]) }
