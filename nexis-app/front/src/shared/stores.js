import {writable} from 'svelte/store'

// Décode le payload du JWT (base64url) sans vérification de signature — côté client uniquement.
// La vraie validation se fait côté serveur à chaque requête API.
function jwtPayload(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(b64))
  } catch {
    return null
  }
}

// Token JWT persisté dans localStorage
function createAuthStore() {
  const stored = localStorage.getItem('nexis_token')
  const { subscribe, set } = writable(stored)
  return {
    subscribe,
    login(token) {
      localStorage.setItem('nexis_token', token)
      set(token)
      // Peuple currentUser depuis le payload JWT (sub = username, roles = rôles)
      const payload = jwtPayload(token)
      if (payload) {
        currentUser.set({ username: payload.sub, roles: payload.roles ?? [] })
      }
    },
    logout() {
      localStorage.removeItem('nexis_token')
      set(null)
      currentUser.set(null)
    },
  }
}

// Infos de l'utilisateur connecté : { username: string, roles: string[] }
export const currentUser = writable(
  (() => {
    const token = localStorage.getItem('nexis_token')
    if (!token) return null
    const p = jwtPayload(token)
    return p ? { username: p.sub, roles: p.roles ?? [] } : null
  })()
)

export const authToken = createAuthStore()

// ── Thème clair / sombre (persisté, appliqué via une classe sur <html>) ──────
const THEME_KEY = 'nexis_theme'
function applyTheme(t) {
  if (typeof document !== 'undefined') document.documentElement.classList.toggle('light', t === 'light')
}
function createThemeStore() {
  let current = localStorage.getItem(THEME_KEY) || 'dark'
  applyTheme(current)
  const { subscribe, set } = writable(current)
  function setTheme(t) { current = t; localStorage.setItem(THEME_KEY, t); applyTheme(t); set(t) }
  return { subscribe, set: setTheme, toggle: () => setTheme(current === 'light' ? 'dark' : 'light') }
}
export const theme = createThemeStore()

// Demande de filtre passée à la Feuille de garde lors d'une navigation (ex. clic sur
// « mon affectation »). Valeurs : 'moi' (filtre sur l'utilisateur courant) ou un tableau
// de membreId. La Feuille la consomme au montage puis la remet à null.
export const feuilleFiltreDemande = writable(null)
