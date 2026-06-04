<script>
  import { onMount } from 'svelte'
  import { api } from '../shared/api.js'

  let vehicules    = $state([])
  let membres      = $state([])
  let enServiceIds = $state([])
  let loading      = $state(true)
  let error        = $state('')

  let enServiceSet     = $derived(new Set(enServiceIds))
  let enServiceMembres = $derived(membres.filter(m => enServiceSet.has(m.id)))

  onMount(load)

  async function load() {
    loading = true; error = ''
    try {
      ;[vehicules, membres, enServiceIds] = await Promise.all([
        api.get('/gn/dispatch'),
        api.get('/gn/membres?actif=true'),
        api.get('/gn/membres/en-service'),
      ])
    } catch (e) { error = e.message }
    finally { loading = false }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Dispatch — Gendarmerie</h2>
    <button class="btn-ghost" onclick={load}>Actualiser</button>
  </div>

  {#if loading}
    <p class="muted">Chargement...</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <!-- Personnel actuellement de garde -->
    <div class="garde-panel">
      <span class="garde-title"><span class="garde-dot"></span> De garde — {enServiceMembres.length}</span>
      {#if enServiceMembres.length > 0}
        <div class="garde-list">
          {#each enServiceMembres as m (m.id)}
            <span class="garde-chip">{m.matricule} · {m.username} <span class="g-grade">{m.grade}</span></span>
          {/each}
        </div>
      {:else}
        <span class="muted small">Personne de garde actuellement</span>
      {/if}
    </div>

    <div class="grid">
      {#each vehicules as v (v.id)}
        <div class="card" style="border-top: 3px solid {v.etat.couleur}">
          <div class="card-head">
            <div class="veh-info">
              <span class="veh-type">{v.type.code}</span>
              <span class="veh-lib">{v.libelle}</span>
              {#if v.immatriculation}
                <span class="veh-immat">{v.immatriculation}</span>
              {/if}
            </div>
            <span class="etat-badge" style="background:{v.etat.couleur}22; color:{v.etat.couleur}; border:1px solid {v.etat.couleur}55">
              <span class="etat-dot" style="background:{v.etat.couleur}"></span>
              {v.etat.label}
            </span>
          </div>

          {#if v.equipe.length > 0}
            <ul class="crew">
              {#each v.equipe as m (m.membreId)}
                <li class="crew-member">
                  {#if enServiceSet.has(m.membreId)}<span class="garde-dot" title="De garde"></span>{/if}
                  <span class="crew-matricule">{m.matricule}</span>
                  <span class="crew-name">{m.username}</span>
                  <span class="crew-grade">{m.grade}</span>
                </li>
              {/each}
            </ul>
          {:else}
            <p class="empty-crew">Aucun personnel embarqué</p>
          {/if}
        </div>
      {/each}
      {#if vehicules.length === 0}
        <p class="muted">Aucun véhicule enregistré</p>
      {/if}
    </div>
  {/if}
</div>

<style>
  .etat-dot { width: 6px; height: 6px; }

  /* Panneau "de garde" */
  .garde-panel {
    background: var(--color-surface); border: 1px solid var(--color-border);
    border-radius: var(--radius); padding: 12px 16px;
    display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  }
  .garde-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: var(--color-success); display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
  .garde-list { display: flex; gap: 8px; flex-wrap: wrap; }
  .garde-chip { font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; padding: 3px 10px; display: inline-flex; gap: 6px; align-items: center; }
  .garde-chip .g-grade { color: var(--color-muted); font-size: 10px; }
  .garde-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-success); flex-shrink: 0; }
</style>
