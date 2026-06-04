<script>
    import {authToken} from '../shared/stores.js'

    let username = $state('')
  let password = $state('')
  let error    = $state('')
  let loading  = $state(false)

  async function handleSubmit(e) {
    e.preventDefault()
    error   = ''
    loading = true
    try {
      // Micronaut Security JWT expose /api/login par défaut (voir application.properties)
      const res = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      if (!res.ok) {
        error = 'Identifiants incorrects'
        return
      }
      const data = await res.json()
      authToken.login(data.access_token)
    } catch {
      error = 'Impossible de joindre le serveur'
    } finally {
      loading = false
    }
  }
</script>

<div class="login-wrapper">
  <form class="login-card" onsubmit={handleSubmit}>
    <h1>NEXIS</h1>
    <p class="subtitle">Plateforme de gestion opérationnelle</p>

    {#if error}
      <p class="error">{error}</p>
    {/if}

    <label>
      Identifiant
      <input type="text" bind:value={username} autocomplete="username" required />
    </label>

    <label>
      Mot de passe
      <input type="password" bind:value={password} autocomplete="current-password" required />
    </label>

    <button type="submit" disabled={loading}>
      {loading ? 'Connexion...' : 'Se connecter'}
    </button>
  </form>
</div>

<style>
  .login-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    background: var(--color-bg);
  }

  .login-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    padding: 40px;
    width: 360px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  h1 {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 3px;
    color: var(--color-primary);
    text-align: center;
  }

  .subtitle {
    color: var(--color-muted);
    font-size: 12px;
    text-align: center;
    margin-top: -8px;
  }

  .error {
    background: rgba(224, 92, 92, 0.15);
    border: 1px solid var(--color-danger);
    border-radius: var(--radius);
    color: var(--color-danger);
    padding: 8px 12px;
    font-size: 13px;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 6px;
    color: var(--color-muted);
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  input {
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    color: var(--color-text);
    font-size: 14px;
    padding: 10px 12px;
    outline: none;
    transition: border-color 0.2s;
  }

  input:focus {
    border-color: var(--color-primary);
  }

  button {
    background: var(--color-primary);
    border: none;
    border-radius: var(--radius);
    color: #fff;
    font-weight: 600;
    padding: 11px;
    margin-top: 8px;
    transition: background 0.2s;
  }

  button:hover:not(:disabled) { background: var(--color-primary-h); }
  button:disabled { opacity: 0.6; cursor: default; }
</style>
