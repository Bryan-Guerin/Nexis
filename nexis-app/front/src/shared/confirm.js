import {writable} from 'svelte/store'

// Confirmation modale (remplace window.confirm). Usage :
//   if (await confirm({ title, message, danger })) { ... }
export const confirmState = writable(null)

export function confirm({ title = 'Confirmer', message = '', danger = false,
                          confirmLabel = 'Confirmer', cancelLabel = 'Annuler' } = {}) {
  return new Promise(resolve => {
    confirmState.set({ title, message, danger, confirmLabel, cancelLabel, resolve })
  })
}

export function resolveConfirm(value) {
  confirmState.update(s => { s?.resolve(value); return null })
}
