import {writable} from 'svelte/store'

// File de notifications éphémères (coin haut-droite).
export const toasts = writable([])

let seq = 0
function push(message, kind = 'success', ms = kind === 'error' ? 6000 : 4000) {
  if (!message) return
  const id = ++seq
  toasts.update(list => [...list, { id, message: String(message), kind }])
  if (ms) setTimeout(() => dismiss(id), ms)
  return id
}
export function dismiss(id) { toasts.update(list => list.filter(t => t.id !== id)) }

// API conviviale : erreurs partout, succès sur actions « lourdes ».
export const toast = {
  success: (m, ms) => push(m, 'success', ms),
  error:   (m, ms) => push(m, 'error', ms),
  info:    (m, ms) => push(m, 'info', ms),
  dismiss,
}

// Compat : ancien helper (utilisé par quelques écrans) → succès.
export function pushToast(message, kind = 'success', ms) { return push(message, kind, ms) }
