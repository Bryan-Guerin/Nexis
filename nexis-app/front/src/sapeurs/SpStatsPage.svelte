<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import Skeleton from '../shared/Skeleton.svelte'
    import HeatMapView from '../shared/HeatMapView.svelte'

    let s = $state(null)
  let natures = $state([])

  // Heatmap : période + filtre nature
  const PERIODES = [
    ['7j',   '7 jours'],
    ['mois', '30 jours'],
    ['3m',   '3 mois'],
    ['6m',   '6 mois'],
    ['all',  'Depuis le début'],
  ]
  let periode = $state('mois')
  let natureFilter = $state('')
  let heatPoints = $state([])
  let heatLoading = $state(false)

  function bornes(p) {
    const now = new Date()
    if (p === 'all') return { from: null, to: null }
    const from = new Date(now)
    if (p === '7j')   from.setDate(now.getDate() - 7)
    else if (p === 'mois') from.setDate(now.getDate() - 30)
    else if (p === '3m')   from.setMonth(now.getMonth() - 3)
    else if (p === '6m')   from.setMonth(now.getMonth() - 6)
    return { from: from.toISOString(), to: now.toISOString() }
  }

  async function loadHeat() {
    heatLoading = true
    const { from, to } = bornes(periode)
    const qs = new URLSearchParams()
    if (from) qs.set('from', from)
    if (to)   qs.set('to', to)
    if (natureFilter) qs.set('natureId', natureFilter)
    try { heatPoints = await api.get(`/sp/stats/heatmap?${qs}`) }
    catch { heatPoints = [] /* toast par api.js */ }
    finally { heatLoading = false }
  }

  $effect(() => { void periode; void natureFilter; loadHeat() })

  onMount(async () => {
    try {
      const [stats, nats] = await Promise.all([
        api.get('/sp/stats/interventions'),
        api.get('/sp/natures').catch(() => []),
      ])
      s = stats; natures = nats
    } catch { /* toast par api.js */ }
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
  let totalHeat = $derived(heatPoints.reduce((a, p) => a + p.count, 0))
</script>

<div class="page">
  <div class="page-header"><h2>Statistiques — Interventions SP</h2></div>

  {#if !s}
    <Skeleton rows={5} />
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

    <!-- Heatmap géographique -->
    <section class="panel">
      <div class="heat-head">
        <h3>🌡️ Carte des interventions</h3>
        <div class="heat-filters">
          <label class="field-label">Période
            <select bind:value={periode}>
              {#each PERIODES as [v, l]}<option value={v}>{l}</option>{/each}
            </select>
          </label>
          <label class="field-label">Nature
            <select bind:value={natureFilter}>
              <option value="">Toutes</option>
              {#each natures as n (n.id)}<option value={n.id}>{n.label}</option>{/each}
            </select>
          </label>
          <span class="heat-total muted small">{totalHeat} intervention{totalHeat > 1 ? 's' : ''} sur {heatPoints.length} zone{heatPoints.length > 1 ? 's' : ''}</span>
        </div>
      </div>
      {#if heatLoading}
        <Skeleton rows={6} height="60px" />
      {:else if heatPoints.length === 0}
        <p class="muted small">Aucune intervention géolocalisée sur la période.</p>
      {:else}
        <HeatMapView points={heatPoints} />
        <div class="heat-legend">
          <span class="muted small">Concentration :</span>
          <span class="heat-grad"></span>
          <span class="muted small">faible → forte</span>
        </div>
      {/if}
    </section>
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

  .heat-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 12px; }
  .heat-head h3 { margin: 0; }
  .heat-filters { display: flex; align-items: flex-end; gap: 14px; flex-wrap: wrap; }
  .heat-total { margin-bottom: 6px; }
  .heat-legend { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
  .heat-grad { display: inline-block; width: 140px; height: 10px; border-radius: 5px; background: linear-gradient(to right, rgb(76,175,130), rgb(232,218,80), rgb(232,162,58), rgb(224,92,92)); }
</style>
