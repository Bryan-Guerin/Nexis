<script>
  import {onMount} from 'svelte'
  import {api} from '../shared/api.js'
  import Skeleton from '../shared/Skeleton.svelte'
  import EmptyState from '../shared/EmptyState.svelte'

  // Audit opérationnel (dispatch / officiers) : affectations forcées + validations de CRI.
  let forcages    = $state([])
  let validations = $state([])
  let loading     = $state(true)

  onMount(async () => {
    try {
      const a = await api.get('/sp/audit')
      forcages = a.forcages; validations = a.validations
    } catch { /* toast par api.js */ }
    finally { loading = false }
  })

  const fmt = t => t ? new Date(t).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'
</script>

<h2>Audit — Sapeurs-Pompiers</h2>

{#if loading}
  <Skeleton rows={6} />
{:else}
  <section class="panel">
    <h3>⚠ Affectations forcées <span class="muted small">(bypass qualification)</span></h3>
    {#if forcages.length === 0}
      <EmptyState message="Aucune affectation forcée." />
    {:else}
      <table>
        <thead><tr><th>Effectif</th><th>Véhicule</th><th>Fonction</th><th>Forcée par</th><th>Quand</th><th>Statut</th></tr></thead>
        <tbody>
          {#each forcages as f}
            <tr>
              <td>{f.matricule ?? ''} {f.membre ?? ''}</td>
              <td>{f.vehicule}</td>
              <td>{f.fonction ?? '—'}</td>
              <td>{f.forcePar ?? '—'}</td>
              <td>{fmt(f.forceLe)}</td>
              <td>{f.fin ? 'terminée' : 'active'}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </section>

  <section class="panel">
    <h3>✓ Validations de CRI</h3>
    {#if validations.length === 0}
      <EmptyState message="Aucun CRI validé." />
    {:else}
      <table>
        <thead><tr><th>Intervention</th><th>Véhicule</th><th>Soumis par</th><th>Soumis le</th><th>Validé par</th><th>Validé le</th></tr></thead>
        <tbody>
          {#each validations as v}
            <tr>
              <td class="mono">{v.interventionCode}</td>
              <td>{v.vehicule}</td>
              <td>{v.soumisPar ?? '—'}</td>
              <td>{fmt(v.soumisLe)}</td>
              <td>{v.validePar ?? '—'}</td>
              <td>{fmt(v.valideLe)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </section>
{/if}

<style>
  .panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; margin-bottom: 16px; }
  .panel h3 { margin: 0 0 10px; font-size: 14px; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th { text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: .4px; color: var(--color-muted); padding: 6px 8px; border-bottom: 1px solid var(--color-border); }
  td { padding: 7px 8px; border-bottom: 1px solid color-mix(in srgb, var(--color-border) 50%, transparent); }
  .mono { font-family: var(--font-mono, monospace); }
</style>
