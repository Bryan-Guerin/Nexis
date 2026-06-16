import {get} from 'svelte/store'
import {authToken} from './stores.js'
import {toast} from './toasts.js'

// Empêche plusieurs redirections concurrentes (ex. Promise.all qui renvoie plusieurs 401)
let redirectingToLogin = false

async function request(method, path, body) {
  const token = get(authToken)
  const res = await fetch(`/api${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body != null ? JSON.stringify(body) : undefined,
  })

  // Session expirée / non authentifié → déconnexion et redirection vers l'écran de connexion.
  // On force un rechargement à la racine : cela garantit l'affichage du login quel que soit
  // l'état courant, alors qu'un simple changement de store ne re-rend pas toujours App.svelte
  // lorsque le 401 survient pendant le chargement d'une page (liste véhicules / effectifs).
  if (res.status === 401) {
    authToken.logout()
    if (!redirectingToLogin) {
      redirectingToLogin = true
      window.location.assign('/')
    }
    throw new Error('Session expirée, veuillez vous reconnecter')
  }

  if (!res.ok) {
    const raw = await res.text().catch(() => '')
    let msg = raw || res.statusText
    try {
      const j = JSON.parse(raw)
      // Formats possibles : { message } (nos ApiError) ou hateoas { _embedded: { errors: [{ message }] } }
      msg = j?._embedded?.errors?.[0]?.message ?? j?.message ?? msg
    } catch { /* corps non-JSON : on garde le texte brut */ }
    msg = String(msg).replace(/^Internal Server Error:\s*/i, '')
    const err = new Error(msg || String(res.status))
    toast.error(err.message)   // feedback d'erreur centralisé (toast partout)
    throw err
  }
  // Plus de toast de succès systématique : les succès « lourds » sont signalés
  // explicitement par les écrans (évite le spam sur chaque mutation).

  // Corps potentiellement vide (204, ou 200 sans contenu comme les endpoints /order) :
  // on lit le texte et on ne parse en JSON que s'il y a effectivement du contenu.
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  get:    path       => request('GET',    path),
  post:   (path, b)  => request('POST',   path, b),
  put:    (path, b)  => request('PUT',    path, b),
  patch:  (path, b)  => request('PATCH',  path, b),
  delete: path       => request('DELETE', path),
}
