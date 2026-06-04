import { writable } from 'svelte/store'

// File de petites notifications éphémères.
export const toasts = writable([])

let seq = 0
export function pushToast(message, kind = 'success', ms = 1800) {
  const id = ++seq
  toasts.update(list => [...list, { id, message, kind }])
  setTimeout(() => toasts.update(list => list.filter(t => t.id !== id)), ms)
}
