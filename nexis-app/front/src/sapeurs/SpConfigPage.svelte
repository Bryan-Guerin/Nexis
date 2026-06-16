<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {invalidateRef} from '../shared/referentials.js'
    import SpEvenementsAdmin from './SpEvenementsAdmin.svelte'

    // Catégories de configuration (chacune = une "enum" ordonnée)
  const CATEGORIES = [
    { key: 'grades',    label: 'Grades',          list: '/sp/grades',    order: '/sp/grades/order',    kind: 'codelabel', deletable: true },
    { key: 'fonctions', label: 'Fonctions',       list: '/sp/fonctions', order: '/sp/fonctions/order', kind: 'codelabel', deletable: true, confirmDelete: true },
    { key: 'statutsveh', label: 'Statuts véhicule', list: '/sp/statuts', order: '/sp/statuts/order', kind: 'statutveh', deletable: true },
    { key: 'casiers',   label: 'Casiers',        list: '/sp/casiers',   order: '/sp/casiers/order',   kind: 'casier', deletable: true },
    { key: 'statuts',   label: 'Statuts planning', list: '/sp/planning/statuts', order: '/sp/planning/statuts/order', kind: 'statut' },
    { key: 'centres',   label: 'Centres',        list: '/sp/centres',   order: '/sp/centres/order',   kind: 'codelabel', deletable: true },
    { key: 'natures',   label: 'Natures intervention', list: '/sp/natures', order: '/sp/natures/order', kind: 'codelabel', deletable: true },
    { key: 'objets',    label: 'Objets inventaire', list: '/sp/objets-inventaire', order: '/sp/objets-inventaire/order', kind: 'codelabel', deletable: true },
    { key: 'evenements', label: 'Événements', kind: 'evenements' },
  ]

  const CATEGORIES_SERVICE = ['GARDE', 'ASTREINTE', 'AUTRE']

  let selectedKey = $state('grades')
  let cat     = $derived(CATEGORIES.find(c => c.key === selectedKey))
  let items   = $state([])
  let loading = $state(false)
  let error   = $state('')

  // Formulaire d'ajout (forme dépendante de la catégorie)
  let form      = $state({})
  let formError = $state('')

  // Drag & drop
  let dragIndex = $state(null)

  // Référentiel des états maîtres (pour lier un statut véhicule à un état)
  let etatsRef = $state([])

  onMount(async () => {
    etatsRef = await api.get('/sp/etats').catch(() => [])
    load()
  })

  async function load() {
    // Les catégories à pane custom (ex. Événements) gèrent leurs propres données.
    if (!cat.list) { items = []; loading = false; return }
    loading = true; error = ''; resetForm()
    try { items = await api.get(cat.list) }
    catch (e) { error = e.message; items = [] }
    finally { loading = false }
  }

  function selectCat(key) {
    if (key === selectedKey) return
    selectedKey = key
    load()
  }

  function resetForm() {
    form = cat.kind === 'etat'      ? { code: '', label: '', couleur: '#4caf82' }
         : cat.kind === 'statutveh' ? { code: '', label: '', couleur: '#4f6ef7', etatId: etatsRef[0]?.id ?? '', clotureIntervention: false }
         : cat.kind === 'statut'    ? { code: '', label: '', couleur: '#4f6ef7', categorie: 'GARDE' }
         : cat.kind === 'casier'    ? { numero: null }
         : { code: '', label: '' }
    formError = ''
  }

  async function submitCreate(e) {
    e.preventDefault(); formError = ''
    try {
      const payload = cat.kind === 'casier' ? { numero: Number(form.numero) } : { ...form }
      const created = await api.post(cat.list, payload)
      items = [...items, created]
      invalidateRef()
      resetForm()
    } catch (e) { formError = e.message }
  }

  // Catégorie d'une fonction (ordre d'affichage de l'équipage au dispatch).
  const TYPES_FONCTION = [['CHEF_AGRES', "Chef d'agrès"], ['CONDUCTEUR', 'Conducteur'], ['CHEF_EQUIPE', "Chef d'équipe"], ['EQUIPIER', 'Équipier']]
  async function setFonctionType(it, type) {
    try {
      const u = await api.put(`/sp/fonctions/${it.id}/type`, { type })
      items = items.map(x => x.id === u.id ? u : x)
      invalidateRef()
    } catch (e) { error = e.message }
  }

  // Coordonnées jeu d'une caserne (pour la carte).
  async function setCentreCoord(it, coordonnees) {
    try {
      const u = await api.put(`/sp/centres/${it.id}/coordonnees`, { coordonnees })
      items = items.map(x => x.id === u.id ? u : x)
    } catch (e) { error = e.message }
  }

  async function removeItem(it) {
    if (!cat.deletable) return
    const extra = cat.confirmDelete ? '\nLes postes liés seront aussi supprimés.'
                : cat.kind === 'statutveh' ? '\nLes véhicules concernés repasseront au statut par défaut.'
                : cat.key === 'natures' ? '\nElle sera retirée des types de véhicule (les véhicules sont conservés). Refusé si des interventions l’utilisent.' : ''
    if (!window.confirm(`Supprimer « ${itemLabel(it)} » ?${extra}`)) return
    error = ''
    try {
      await api.delete(`${cat.list}/${it.id}`)
      items = items.filter(x => x.id !== it.id)
      invalidateRef()
    } catch (e) {
      // Garde-fou back (ex: grade encore porté par des effectifs) → message détaillé.
      window.alert(e.message)
    }
  }

  // ── Drag & drop : réordonne en direct, persiste à la fin ────────────────────
  function onDragStart(i) { dragIndex = i }

  function onDragOver(e, i) {
    e.preventDefault()
    if (dragIndex === null || dragIndex === i) return
    const arr = [...items]
    const [moved] = arr.splice(dragIndex, 1)
    arr.splice(i, 0, moved)
    items = arr
    dragIndex = i
  }

  async function persistOrder() {
    if (dragIndex === null) return
    dragIndex = null
    try { await api.put(cat.order, { ids: items.map(x => x.id) }); invalidateRef() }
    catch (e) { error = e.message }
  }

  function itemLabel(it) {
    return cat.kind === 'casier' ? `Casier ${it.numero}` : it.label
  }

  // Désigne un statut véhicule comme "par défaut"
  async function setDefaut(it) {
    try { await api.put(`${cat.list}/${it.id}/defaut`); items = await api.get(cat.list); invalidateRef() }
    catch (e) { error = e.message }
  }

  // Bascule la case « clôture auto d'intervention » d'un statut véhicule
  async function toggleCloture(it) {
    try {
      const updated = await api.put(`${cat.list}/${it.id}/cloture-intervention`)
      items = items.map(x => x.id === updated.id ? updated : x)
      invalidateRef()
    } catch (e) { error = e.message }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Configuration — Sapeurs-Pompiers</h2>
  </div>

  <div class="split">
    <!-- Catégories -->
    <div class="list-pane">
      {#each CATEGORIES as c}
        <button class="cat-item" class:active={c.key === selectedKey} onclick={() => selectCat(c.key)}>
          {c.label}
        </button>
      {/each}
    </div>

    <!-- Détail de la catégorie -->
    <div class="detail-pane">
      {#if cat.kind === 'evenements'}
        <SpEvenementsAdmin />
      {:else}
      <h3>{cat.label} <span class="hint">— glisser-déposer pour réordonner</span></h3>

      {#if error}<p class="inline-error">{error}</p>{/if}

      {#if loading}
        <p class="muted">Chargement…</p>
      {:else}
        <ul class="order-list">
          {#each items as it, i (it.id)}
            <li
              class="order-item"
              class:dragging={dragIndex === i}
              draggable="true"
              ondragstart={() => onDragStart(i)}
              ondragover={(e) => onDragOver(e, i)}
              ondragend={persistOrder}
            >
              <span class="handle" title="Glisser pour déplacer">⠿</span>
              <span class="idx">{i}</span>
              {#if it.couleur}
                <span class="etat-dot" style="background:{it.couleur}"></span>
              {/if}
              <span class="it-label">{itemLabel(it)}</span>
              {#if it.code}<span class="chip-code">{it.code}</span>{/if}
              {#if cat.key === 'fonctions'}
                <select class="type-fonction-sel" value={it.type} title="Catégorie — ordre de l'équipage au dispatch"
                        onchange={e => setFonctionType(it, e.target.value)}>
                  {#each TYPES_FONCTION as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
              {/if}
              {#if cat.key === 'centres'}
                <input class="coord-input" type="text" inputmode="numeric" maxlength="6" placeholder="coord. 6 ch."
                       title="Coordonnées jeu de la caserne (carte)" value={it.coordonnees ?? ''}
                       oninput={e => e.target.value = e.target.value.replace(/\D/g, '').slice(0, 6)}
                       onchange={e => setCentreCoord(it, e.target.value)} />
              {/if}
              {#if cat.kind === 'statut'}<span class="cat-badge">{it.categorie}</span>{/if}
              {#if cat.kind === 'statutveh' && it.etat}<span class="cat-badge" title="État appliqué">→ {it.etat.label}</span>{/if}
              {#if cat.key === 'statutsveh'}
                <button class="defaut-btn" class:on={it.clotureIntervention}
                        title="Si coché : quand TOUS les engins d'une intervention ont (au moins) un statut coché, elle se clôture automatiquement"
                        onclick={() => toggleCloture(it)}>
                  {it.clotureIntervention ? '✓ clôture inter' : 'clôture inter'}
                </button>
                <button class="defaut-btn" class:on={it.parDefaut} title="Statut par défaut" onclick={() => setDefaut(it)}>
                  {it.parDefaut ? '★ défaut' : '☆'}
                </button>
              {/if}
              {#if cat.deletable && !(cat.kind === 'statutveh' && it.parDefaut)}
                <button class="rm-btn" onclick={() => removeItem(it)} title="Supprimer">×</button>
              {/if}
            </li>
          {/each}
          {#if items.length === 0}
            <p class="muted small">Aucun élément</p>
          {/if}
        </ul>

        <!-- Ajout -->
        <form class="add-form" onsubmit={submitCreate}>
          {#if formError}<p class="inline-error">{formError}</p>{/if}
          <div class="form-row">
            {#if cat.kind === 'casier'}
              <label>Numéro<input type="number" bind:value={form.numero} min="0" required /></label>
            {:else}
              <label>Code<input type="text" bind:value={form.code} placeholder="ex: CONDUCTEUR" required /></label>
              <label>Libellé<input type="text" bind:value={form.label} placeholder="ex: Conducteur" required /></label>
              {#if cat.kind === 'etat' || cat.kind === 'statut' || cat.kind === 'statutveh'}
                <label class="label-color">Couleur<input type="color" bind:value={form.couleur} /></label>
              {/if}
              {#if cat.kind === 'statutveh'}
                <label>État appliqué
                  <select bind:value={form.etatId} required>
                    {#each etatsRef as e (e.id)}<option value={e.id}>{e.label}</option>{/each}
                  </select>
                </label>
                <label class="check-label" title="Si coché : l'intervention se clôture quand tous ses engins portent un statut coché">
                  <input type="checkbox" bind:checked={form.clotureIntervention} /> Clôture l'intervention
                </label>
              {/if}
              {#if cat.kind === 'statut'}
                <label>Catégorie
                  <select bind:value={form.categorie}>
                    {#each CATEGORIES_SERVICE as c}<option value={c}>{c}</option>{/each}
                  </select>
                </label>
              {/if}
            {/if}
          </div>
          <button type="submit" class="btn-primary">Ajouter</button>
        </form>
      {/if}
      {/if}
    </div>
  </div>
</div>

<style>
  .split {
    display: flex;
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    overflow: hidden;
    flex: 1;
    min-height: 0;
    height: calc(100vh - 130px);
  }

  .list-pane {
    width: 200px;
    flex-shrink: 0;
    border-right: 1px solid var(--color-border);
    background: var(--color-surface);
    display: flex;
    flex-direction: column;
  }
  .cat-item {
    text-align: left;
    background: none;
    border: none;
    border-bottom: 1px solid var(--color-border);
    border-left: 3px solid transparent;
    padding: 12px 16px;
    color: var(--color-text);
    font-size: 13px;
    cursor: pointer;
    transition: background 0.12s;
  }
  .cat-item:hover { background: var(--hover); }
  .cat-item.active {
    background: color-mix(in srgb, var(--accent) 12%, transparent);
    border-left-color: var(--accent);
    font-weight: 500;
  }

  .detail-pane {
    flex: 1;
    overflow-y: auto;
    padding: 20px 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .hint { font-weight: 400; text-transform: none; letter-spacing: 0; font-size: 11px; color: var(--color-muted); }

  .order-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
  .order-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    cursor: grab;
  }
  .order-item.dragging { opacity: 0.5; border-color: var(--accent); }
  .handle { color: var(--color-muted); cursor: grab; user-select: none; }
  .idx { font-family: monospace; font-size: 11px; color: var(--color-muted); min-width: 18px; text-align: right; }
  .it-label { flex: 1; font-size: 13px; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }

  .cat-badge { font-size: 9px; font-weight: 700; letter-spacing: .4px; color: var(--color-muted); border: 1px solid var(--color-border); border-radius: 8px; padding: 1px 6px; }
  .defaut-btn { background: none; border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-muted); font-size: 10px; padding: 1px 6px; cursor: pointer; }
  .defaut-btn.on { border-color: var(--accent); color: var(--accent); }
  .type-fonction-sel { font-size: 11px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); padding: 2px 6px; cursor: pointer; }
  .coord-input { width: 90px; font-size: 11px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); padding: 2px 6px; }

  .add-form { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
  .check-label { display: flex; align-items: center; gap: 6px; font-size: 13px; align-self: flex-end; padding-bottom: 8px; white-space: nowrap; }
  .label-color { flex: 0 0 80px; min-width: 80px; }
  input[type="color"] { height: 34px; width: 60px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-bg); cursor: pointer; padding: 2px; }
</style>
