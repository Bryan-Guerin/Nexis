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
function isSp()        { return roles().includes('ROLE_SP') }

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
  { type: 'GARDE_FIN_INTERVENTION', icon: '⏰', audience: () => true, test: ev => ev.payload?.membreUsername === username() },
  { type: 'INTERVENTION_OUVERTE', icon: '🚨', audience: isDispatch, test: () => true },     // alerte ouverture (dispatch/admin)
  { type: 'PAIE',           icon: '💶', audience: isRhOrAdmin, test: () => true },          // règlement global (RH/admin)
  { type: 'PAIE_VERSEE',    icon: '💶', audience: () => true,  test: () => true },          // personnel (USERS scope → destinataire seul)
]

// Bref bip d'alerte (sans fichier audio). Silencieux si l'audio n'est pas autorisé.
function beep() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'square'; osc.frequency.value = 880
    gain.gain.value = 0.06
    osc.connect(gain); gain.connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + 0.18)
    osc.onended = () => ctx.close()
  } catch { /* audio bloqué : on ignore */ }
}

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

// Dédoublonnage des versements déjà notifiés (par semaine), partagé entre le seed
// (login, membres hors ligne) et l'événement temps réel (membre connecté).
const PAIE_KEY = 'sp.paie.seen'
function paieSeen() {
  try { return new Set(JSON.parse(localStorage.getItem(PAIE_KEY) || '[]')) } catch { return new Set() }
}
function markPaieSeen(semaine) {
  const seen = paieSeen(); seen.add(semaine)
  localStorage.setItem(PAIE_KEY, JSON.stringify([...seen]))
}

// Versements de paie (notif personnelle « Votre paye a été versée ») — au chargement,
// pour les membres hors ligne au moment du règlement. Le temps réel couvre les connectés.
async function seedPaiesVersees() {
  if (!isSp()) return
  const list = await api.get('/sp/membres/me/paies').catch(() => [])
  if (!list.length) return
  const seen = paieSeen()
  const fresh = list.filter(v => !seen.has(v.semaine))
  push(fresh.map(v => notif('💶', `Votre paye a été versée — semaine du ${frSemaine(v.semaine)} (${(+v.montant).toFixed(2)} €)`)))
  fresh.forEach(v => markPaieSeen(v.semaine))
}
function frSemaine(iso) {
  return new Date(iso + 'T12:00:00').toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
}

let started = false
export function startNotifications() {
  if (started) return
  started = true
  realtime.on(ev => {
    const n = fromEvent(ev); if (n) push([n])
    // Versement reçu en direct : on le marque vu pour éviter un doublon au prochain login (seed).
    if (ev?.type === 'PAIE_VERSEE' && ev.payload?.semaine) markPaieSeen(ev.payload.semaine)
    // Bip sonore à l'ouverture d'une intervention (dispatch/admin).
    if (ev?.faction === 'SP' && ev.type === 'INTERVENTION_OUVERTE' && isDispatch()) beep()
  })
  seedRelancesEchues()
  seedPaiesVersees()
}

export function markAllRead() { notifications.update(l => l.map(n => (n.read ? n : { ...n, read: true }))) }
export function clearNotifications() { notifications.set([]) }
