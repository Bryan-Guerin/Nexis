import { get } from 'svelte/store'
import { authToken } from './stores.js'

// ── Client WebSocket unique (bus d'événements temps réel) ────────────────────
let socket = null
let pingTimer = null
let reconnectTimer = null
const listeners = new Set()

function wsUrl() {
  const token = get(authToken)
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws/events?token=${encodeURIComponent(token ?? '')}`
}

function open() {
  if (socket || !get(authToken)) return
  socket = new WebSocket(wsUrl())

  socket.onopen = () => {
    // heartbeat : garde la connexion ouverte à travers les proxys
    pingTimer = setInterval(() => { try { socket?.send('ping') } catch { /* ignore */ } }, 25000)
  }
  socket.onmessage = (e) => {
    let ev
    try { ev = JSON.parse(e.data) } catch { return }
    listeners.forEach(h => { try { h(ev) } catch { /* ignore */ } })
  }
  socket.onclose = () => {
    clearInterval(pingTimer)
    socket = null
    if (get(authToken)) reconnectTimer = setTimeout(open, 3000)   // reconnexion auto
  }
  socket.onerror = () => { try { socket?.close() } catch { /* ignore */ } }
}

export const realtime = {
  connect() { open() },
  disconnect() {
    clearTimeout(reconnectTimer)
    clearInterval(pingTimer)
    if (socket) { socket.onclose = null; try { socket.close() } catch { /* ignore */ }; socket = null }
  },
  /** Abonne un handler aux événements reçus ; retourne une fonction de désabonnement. */
  on(handler) { listeners.add(handler); return () => listeners.delete(handler) },
}

// ── Bip sonore (Web Audio, pas d'asset externe) ──────────────────────────────
let audioCtx = null

// Les navigateurs bloquent l'audio avant toute interaction : on (ré)active le
// contexte au premier geste de l'utilisateur (il en fait forcément en utilisant l'app).
function unlockAudio() {
  try {
    audioCtx ??= new (window.AudioContext || window.webkitAudioContext)()
    if (audioCtx.state === 'suspended') audioCtx.resume()
  } catch { /* ignore */ }
}
if (typeof window !== 'undefined') {
  window.addEventListener('click', unlockAudio)
  window.addEventListener('keydown', unlockAudio)
}

// Boucle sonore d'alerte : sonne jusqu'à stopBipLoop() ou expiration (défaut 30 s).
let bipInterval = null
let bipTimeout = null
export function startBipLoop(durationMs = 30000) {
  stopBipLoop()
  playBip()
  bipInterval = setInterval(playBip, 2000)
  bipTimeout = setTimeout(stopBipLoop, durationMs)
}
export function stopBipLoop() {
  if (bipInterval) clearInterval(bipInterval)
  if (bipTimeout) clearTimeout(bipTimeout)
  bipInterval = null
  bipTimeout = null
}

export function playBip() {
  try {
    audioCtx ??= new (window.AudioContext || window.webkitAudioContext)()
    if (audioCtx.state === 'suspended') audioCtx.resume()
    const ctx = audioCtx
    const start = ctx.currentTime
    for (let i = 0; i < 3; i++) {        // 3 bips courts
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'square'
      osc.frequency.value = 988
      const t = start + i * 0.22
      gain.gain.setValueAtTime(0.0001, t)
      gain.gain.exponentialRampToValueAtTime(0.25, t + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.16)
      osc.connect(gain); gain.connect(ctx.destination)
      osc.start(t); osc.stop(t + 0.18)
    }
  } catch { /* audio indisponible : silencieux */ }
}
