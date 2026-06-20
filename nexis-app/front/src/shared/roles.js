import {derived} from 'svelte/store'
import {currentUser} from './stores.js'

// Capacités SP centralisées : source unique pour les gardes de rôle au front
// (évite les définitions divergentes de « isDispatcher » d'un écran à l'autre).
// ROLE_ADMIN_SP est le super-rôle SP → il couvre dispatch et RH.
export const can = derived(currentUser, ($u) => {
  const r = $u?.roles ?? []
  const admin = r.includes('ROLE_ADMIN_SP')
  return {
    sp:       admin || r.includes('ROLE_SP') || r.includes('ROLE_SP_DISPATCH') || r.includes('ROLE_SP_RH'),
    dispatch: admin || r.includes('ROLE_SP_DISPATCH'),
    rh:       admin || r.includes('ROLE_SP_RH'),
    admin,
  }
})
