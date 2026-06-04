<script>
  /**
   * Timeline d'événements du journal (main courante / audit), avec filtres
   * par tag (type, multi-sélection) et par date (jour unique ou intervalle).
   * Props : path (endpoint REST), title.
   */
  import { onMount } from 'svelte'
  import { api } from './api.js'
  import { realtime } from './realtime.js'

  let { path, title } = $props()

  let entries = $state([])
  let loading = $state(true)
  let error   = $state('')
  let reloadTimer = null

  // Filtres
  const MAX_INLINE = 8   // au-delà, on bascule sur un menu déroulant « + Type »
  let selectedTypes = $state(new Set())
  let dateFrom = $state('')
  let dateTo   = $state('')

  const TYPE = {
    AFFECTATION:           { l: 'Affectation',     c: 'var(--color-primary)' },
    DESAFFECTATION:        { l: 'Retrait',         c: 'var(--color-muted)' },
    ETAT_VEHICULE:         { l: 'Statut',          c: '#e8a23a' },
    INVENTAIRE:            { l: 'Inventaire',      c: '#4f6ef7' },
    PLANNING:              { l: 'Garde',           c: '#4caf82' },
    MAIN_COURANTE:         { l: 'Note',            c: '#b450dc' },
    BIP:                   { l: 'Bip',             c: 'var(--color-danger)' },
    INTERVENTION_OUVERTE:  { l: 'Inter. ouverte',  c: 'var(--color-success)' },
    INTERVENTION_RENFORT:  { l: 'Renfort',         c: 'var(--color-success)' },
    INTERVENTION_CLOTUREE: { l: 'Inter. clôturée', c: 'var(--color-muted)' },
    LOGIN:                 { l: 'Connexion',       c: '#b450dc' },
    LOGIN_FAILED:          { l: 'Échec connexion', c: 'var(--color-danger)' },
    USER_CREE:             { l: 'User créé',       c: '#4caf82' },
    USER_ROLES:            { l: 'Rôles modifiés',  c: '#4f6ef7' },
    MDP_RESET:             { l: 'MDP réinit.',     c: '#e8a23a' },
    MDP_CHANGE:            { l: 'MDP changé',       c: '#e8a23a' },
    ROLE_CREE:             { l: 'Rôle créé',       c: '#4caf82' },
  }
  function typeInfo(t) { return TYPE[t] ?? { l: t, c: 'var(--color-muted)' } }
  function fmt(iso) { return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' }) }
  function jourLocal(iso) {
    const d = new Date(iso)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  // Types réellement présents dans les entrées (pour la liste à cocher)
  let presentTypes = $derived([...new Set(entries.map(e => e.type))])

  let filtered = $derived(entries.filter(e => {
    if (selectedTypes.size > 0 && !selectedTypes.has(e.type)) return false
    const lo = dateFrom || dateTo
    const hi = dateTo || dateFrom
    if (lo || hi) {
      const j = jourLocal(e.creeLe)
      if (lo && j < lo) return false
      if (hi && j > hi) return false
    }
    return true
  }))

  function toggleType(t) {
    const s = new Set(selectedTypes)
    if (s.has(t)) s.delete(t); else s.add(t)
    selectedTypes = s
  }
  function resetFiltres() { selectedTypes = new Set(); dateFrom = ''; dateTo = '' }
  let filtresActifs = $derived(selectedTypes.size > 0 || !!dateFrom || !!dateTo)

  onMount(() => {
    load()
    return realtime.on(() => { clearTimeout(reloadTimer); reloadTimer = setTimeout(load, 500) })
  })

  async function load() {
    loading = true; error = ''
    try { entries = await api.get(path) }
    catch (e) { error = e.message }
    finally { loading = false }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>{title}</h2>
    <button class="btn-ghost" onclick={load}>Actualiser</button>
  </div>

  {#if loading}
    <p class="muted">Chargement…</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <!-- Filtres -->
    <div class="filters">
      <div class="tags">
        {#if presentTypes.length <= MAX_INLINE}
          {#each presentTypes as t}
            <button class="tag" class:on={selectedTypes.has(t)}
                    style={selectedTypes.has(t) ? `border-color:${typeInfo(t).c}; color:${typeInfo(t).c}` : ''}
                    onclick={() => toggleType(t)}>{typeInfo(t).l}</button>
          {/each}
        {:else}
          {#each [...selectedTypes] as t}
            <button class="tag on" style="border-color:{typeInfo(t).c}; color:{typeInfo(t).c}"
                    onclick={() => toggleType(t)}>{typeInfo(t).l} ✕</button>
          {/each}
          <details class="tag-dd">
            <summary class="tag add">+ Type</summary>
            <div class="dd-menu">
              {#each presentTypes.filter(t => !selectedTypes.has(t)) as t}
                <button class="dd-item" onclick={() => toggleType(t)}>{typeInfo(t).l}</button>
              {/each}
              {#if presentTypes.every(t => selectedTypes.has(t))}<span class="dd-empty">Tous sélectionnés</span>{/if}
            </div>
          </details>
        {/if}
      </div>
      <div class="dates">
        <label>Du <input type="date" bind:value={dateFrom} /></label>
        <label>au <input type="date" bind:value={dateTo} /></label>
        {#if filtresActifs}<button class="btn-ghost-sm" onclick={resetFiltres}>Réinitialiser</button>{/if}
      </div>
    </div>

    <div class="timeline">
      {#each filtered as e (e.id)}
        <div class="entry">
          <span class="t">{fmt(e.creeLe)}</span>
          <span class="badge" style="background:color-mix(in srgb, {typeInfo(e.type).c} 15%, transparent); color:{typeInfo(e.type).c}">{typeInfo(e.type).l}</span>
          <span class="msg">{e.message}</span>
          {#if e.acteurUsername}<span class="who">{e.acteurUsername}</span>{/if}
        </div>
      {/each}
      {#if filtered.length === 0}
        <p class="muted" style="padding:12px">{entries.length === 0 ? 'Aucun événement' : 'Aucun événement ne correspond aux filtres'}</p>
      {/if}
    </div>
  {/if}
</div>

<style>
  .filters { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
  .tags { flex: 1; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; min-width: 0; }
  .tag {
    background: var(--color-surface); border: 1px solid var(--color-border); color: var(--color-muted);
    border-radius: 20px; font-size: 11px; padding: 3px 10px; cursor: pointer; transition: border-color .12s, color .12s;
  }
  .tag.on { font-weight: 600; }
  .tag.add { color: var(--accent); border-style: dashed; }

  .tag-dd { position: relative; }
  .tag-dd summary { list-style: none; }
  .tag-dd summary::-webkit-details-marker { display: none; }
  .dd-menu {
    position: absolute; top: 100%; left: 0; margin-top: 4px; z-index: 20;
    background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
    padding: 4px; display: flex; flex-direction: column; gap: 2px; min-width: 150px; max-height: 240px; overflow-y: auto;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
  }
  .dd-item { text-align: left; background: none; border: none; color: var(--color-text); font-size: 12px; padding: 5px 8px; border-radius: var(--radius); cursor: pointer; }
  .dd-item:hover { background: var(--color-border); }
  .dd-empty { font-size: 11px; color: var(--color-muted); padding: 5px 8px; }

  .dates { flex-shrink: 0; display: flex; align-items: center; gap: 10px; font-size: 12px; color: var(--color-muted); }
  .dates input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 8px; }

  .timeline { border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .entry {
    display: flex; align-items: center; gap: 12px;
    padding: 8px 12px;
    border-bottom: 1px solid var(--color-border);
    font-size: 13px;
  }
  .entry:last-child { border-bottom: none; }
  .entry .t { font-family: monospace; font-size: 11px; color: var(--color-muted); white-space: nowrap; min-width: 140px; }
  .entry .badge { min-width: 110px; text-align: center; }
  .entry .msg { flex: 1; }
  .entry .who { font-size: 11px; color: var(--color-muted); white-space: nowrap; }
</style>
