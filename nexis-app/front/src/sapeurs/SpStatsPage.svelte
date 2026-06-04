<script>
  import { onMount } from 'svelte'
  import { api } from '../shared/api.js'

  let s = $state(null)
  let error = $state('')

  onMount(async () => {
    try { s = await api.get('/sp/stats/interventions') }
    catch (e) { error = e.message }
  })

  function dureeTexte(min) {
    if (!min) return '—'
    const h = Math.floor(min / 60), m = min % 60
    return h > 0 ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`
  }
  function moisCourt(ym) {
    const [y, m] = ym.split('-')
    return new Date(+y, +m - 1, 1).toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' })
  }
  let maxNature = $derived(s ? Math.max(1, ...s.parNature.map(n => n.count)) : 1)
  let maxMois   = $derived(s ? Math.max(1, ...s.parMois.map(n => n.count)) : 1)
</script>

<div class="page">
  <div class="page-header"><h2>Statistiques — Interventions SP</h2></div>

  {#if error}<p class="inline-error">{error}</p>{/if}

  {#if !s}
    <p class="muted">Chargement…</p>
  {:else}
    <div class="stat-grid">
      <div class="stat-card"><span class="stat-label">Total</span><span class="stat-value">{s.total}</span></div>
      <div class="stat-card"><span class="stat-label">En cours</span><span class="stat-value" class:alert={s.enCours > 0}>{s.enCours}</span></div>
      <div class="stat-card"><span class="stat-label">Clôturées</span><span class="stat-value">{s.cloturees}</span></div>
      <div class="stat-card"><span class="stat-label">Durée moyenne</span><span class="stat-value">{dureeTexte(s.dureeMoyenneMinutes)}</span></div>
      <div class="stat-card"><span class="stat-label">Victimes (cumul)</span><span class="stat-value">{s.totalVictimes}</span></div>
      <div class="stat-card"><span class="stat-label">Incendies</span><span class="stat-value">{s.nbIncendies}</span></div>
      <div class="stat-card"><span class="stat-label">Avec véhicule</span><span class="stat-value">{s.nbAvecVehicule}</span></div>
    </div>

    <div class="cols">
      <section class="panel">
        <h3>Par nature</h3>
        {#if s.parNature.length === 0}
          <p class="muted small">Aucune intervention.</p>
        {:else}
          <div class="bars">
            {#each s.parNature as n (n.nature)}
              <div class="bar-row">
                <span class="bar-label">{n.nature}</span>
                <div class="bar-track"><div class="bar-fill" style="width:{(n.count / maxNature) * 100}%"></div></div>
                <span class="bar-count">{n.count}</span>
              </div>
            {/each}
          </div>
        {/if}
      </section>

      <section class="panel">
        <h3>Volume — 6 derniers mois</h3>
        <div class="trend">
          {#each s.parMois as m (m.mois)}
            <div class="trend-col">
              <span class="trend-n">{m.count}</span>
              <div class="trend-bar" style="height:{Math.round((m.count / maxMois) * 70)}px"></div>
              <span class="trend-day">{moisCourt(m.mois)}</span>
            </div>
          {/each}
        </div>
      </section>
    </div>
  {/if}
</div>

<style>
  .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 12px; }
  .stat-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 14px; display: flex; flex-direction: column; gap: 4px; }
  .stat-label { font-size: 11px; text-transform: uppercase; letter-spacing: .5px; color: var(--color-muted); }
  .stat-value { font-size: 26px; font-weight: 700; line-height: 1; }
  .stat-value.alert { color: var(--color-danger); }

  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
  @media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
  .panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; }
  .panel h3 { margin: 0 0 12px; font-size: 14px; }

  .bars { display: flex; flex-direction: column; gap: 8px; }
  .bar-row { display: flex; align-items: center; gap: 10px; font-size: 13px; }
  .bar-label { width: 130px; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .bar-track { flex: 1; height: 10px; background: var(--color-bg); border-radius: 5px; overflow: hidden; }
  .bar-fill { height: 100%; background: var(--accent); border-radius: 5px; }
  .bar-count { width: 28px; text-align: right; font-family: monospace; color: var(--color-muted); }

  .trend { display: flex; align-items: flex-end; justify-content: space-between; gap: 8px; height: 110px; }
  .trend-col { display: flex; flex-direction: column; align-items: center; gap: 3px; flex: 1; justify-content: flex-end; }
  .trend-n { font-size: 11px; color: var(--color-muted); }
  .trend-bar { width: 70%; min-height: 2px; background: var(--accent); border-radius: 3px 3px 0 0; }
  .trend-day { font-size: 10px; color: var(--color-muted); }
</style>
