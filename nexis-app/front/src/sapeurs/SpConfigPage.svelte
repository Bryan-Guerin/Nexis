<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {toast} from '../shared/toasts.js'
    import {confirm} from '../shared/confirm.js'
    import {invalidateRef} from '../shared/referentials.js'
    import Skeleton from '../shared/Skeleton.svelte'
    import IconePicker from '../shared/IconePicker.svelte'
    import SpEvenementsAdmin from './SpEvenementsAdmin.svelte'
    import SpBadgesAdmin from './SpBadgesAdmin.svelte'

    // Catégories de configuration (chacune = une "enum" ordonnée)
  const CATEGORIES = [
    { key: 'grades',    label: 'Grades',          list: '/sp/grades',    order: '/sp/grades/order',    kind: 'codelabel', deletable: true },
    { key: 'fonctions', label: 'Fonctions',       list: '/sp/fonctions', order: '/sp/fonctions/order', kind: 'codelabel', deletable: true, confirmDelete: true },
    { key: 'fonctionsorga', label: 'Fonctions organigramme', list: '/sp/fonctions-orga', order: '/sp/fonctions-orga/order', kind: 'fonctionorga', deletable: true },
    { key: 'statutsveh', label: 'Statuts véhicule', list: '/sp/statuts', order: '/sp/statuts/order', kind: 'statutveh', deletable: true },
    { key: 'casiers',   label: 'Casiers',        list: '/sp/casiers',   order: '/sp/casiers/order',   kind: 'casier', deletable: true },
    { key: 'statuts',   label: 'Statuts planning', list: '/sp/planning/statuts', order: '/sp/planning/statuts/order', kind: 'statut' },
    { key: 'centres',   label: 'Centres & hôpitaux', list: '/sp/centres', order: '/sp/centres/order', kind: 'codelabel', deletable: true },
    { key: 'natures',   label: 'Natures intervention', list: '/sp/natures', order: '/sp/natures/order', kind: 'codelabel', deletable: true },
    { key: 'objets',    label: 'Objets inventaire', list: '/sp/objets-inventaire', order: '/sp/objets-inventaire/order', kind: 'codelabel', deletable: true },
    { key: 'evenements', label: 'Événements', kind: 'evenements' },
    { key: 'badges',     label: 'Badges (succès)', kind: 'badges' },
  ]

  const CATEGORIES_SERVICE = ['GARDE', 'ASTREINTE', 'AUTRE']

  let selectedKey = $state('grades')
  let cat     = $derived(CATEGORIES.find(c => c.key === selectedKey))
  let items   = $state([])
  let loading = $state(false)

  // Formulaire d'ajout (forme dépendante de la catégorie)
  let form      = $state({})
  let formError = $state('')

  // Drag & drop
  let dragIndex = $state(null)

  // Référentiel des états maîtres (pour lier un statut véhicule à un état)
  let etatsRef = $state([])

  // Attribution des casiers : numéro → libellé du membre qui l'occupe.
  let casierAttrib = $state({})
  async function loadCasierAttrib() {
    const ms = await api.get('/sp/membres/grade').catch(() => [])
    const map = {}
    for (const m of ms) if (m.numeroCasier != null) map[m.numeroCasier] = `${m.matricule} · ${m.nomComplet || m.username}`
    casierAttrib = map
  }

  onMount(async () => {
    etatsRef = await api.get('/sp/etats').catch(() => [])
    load()
  })

  async function load() {
    // Les catégories à pane custom (ex. Événements) gèrent leurs propres données.
    if (!cat.list) { items = []; loading = false; return }
    loading = true; resetForm()
    try {
      items = await api.get(cat.list)
      if (cat.key === 'centres') await loadHopitaux()
      if (cat.key === 'casiers') await loadCasierAttrib()
    }
    catch { items = [] /* toast par api.js */ }
    finally { loading = false }
  }

  function selectCat(key) {
    if (key === selectedKey) return
    selectedKey = key
    load()
  }

  function resetForm() {
    form = cat.kind === 'etat'         ? { code: '', label: '', couleur: '#4caf82' }
         : cat.kind === 'statutveh'    ? { code: '', label: '', couleur: '#4f6ef7', etatId: etatsRef[0]?.id ?? '', clotureIntervention: false }
         : cat.kind === 'statut'       ? { code: '', label: '', couleur: '#4f6ef7', categorie: 'GARDE' }
         : cat.kind === 'casier'       ? { numero: null }
         : cat.kind === 'fonctionorga' ? { code: '', label: '', parentId: '', icone: '', iconeImageId: null }
         : { code: '', label: '' }
    formError = ''
  }

  async function submitCreate(e) {
    e.preventDefault(); formError = ''
    try {
      const payload = cat.kind === 'casier' ? { numero: Number(form.numero) }
                    : cat.kind === 'fonctionorga' ? { code: form.code, label: form.label, parentId: form.parentId || null, icone: form.icone || null, iconeImageId: form.iconeImageId || null }
                    : { ...form }
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
    } catch { /* toast par api.js */ }
  }

  // Icône d'une nature (carte) : emoji + image. Lue depuis l'item (lié par IconePicker).
  async function setNatureIcone(it) {
    try {
      const u = await api.put(`/sp/natures/${it.id}/icone`, { icone: it.icone || null, iconeImageId: it.iconeImageId || null })
      items = items.map(x => x.id === u.id ? u : x)
    } catch { /* toast par api.js */ }
  }

  // Met à jour une fonction d'organigramme (label / parent / icône). Patch fusionné côté front.
  async function updateFonctionOrga(it, patch) {
    const next = { label: it.label, parentId: it.parentId ?? null, icone: it.icone ?? null,
                   iconeImageId: it.iconeImageId ?? null, ...patch }
    try {
      const u = await api.put(`/sp/fonctions-orga/${it.id}`, next)
      items = items.map(x => x.id === u.id ? u : x)
    } catch { /* toast par api.js */ }
  }

  // Coordonnées jeu d'une caserne (pour la carte).
  async function setCentreCoord(it, coordonnees) {
    try {
      const u = await api.put(`/sp/centres/${it.id}/coordonnees`, { coordonnees })
      items = items.map(x => x.id === u.id ? u : x)
    } catch { /* toast par api.js */ }
  }

  async function removeItem(it) {
    if (!cat.deletable) return
    const extra = cat.confirmDelete ? '\nLes postes liés seront aussi supprimés.'
                : cat.kind === 'statutveh' ? '\nLes véhicules concernés repasseront au statut par défaut.'
                : cat.key === 'natures' ? '\nElle sera retirée des types de véhicule (les véhicules sont conservés). Refusé si des interventions l’utilisent.' : ''
    if (!await confirm({ title: 'Supprimer', message: `Supprimer « ${itemLabel(it)} » ?${extra}`, danger: true })) return
    try {
      await api.delete(`${cat.list}/${it.id}`)
      items = items.filter(x => x.id !== it.id)
      invalidateRef()
    } catch { /* garde-fou back (ex: grade encore porté) déjà signalé par toast */ }
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
    catch { /* toast par api.js */ }
  }

  function itemLabel(it) {
    return cat.kind === 'casier' ? `Casier ${it.numero}` : it.label
  }

  // Désigne un statut véhicule comme "par défaut"
  async function setDefaut(it) {
    try { await api.put(`${cat.list}/${it.id}/defaut`); items = await api.get(cat.list); invalidateRef() }
    catch { /* toast par api.js */ }
  }

  // Bascule la case « clôture auto d'intervention » d'un statut véhicule
  async function toggleCloture(it) {
    try {
      const updated = await api.put(`${cat.list}/${it.id}/cloture-intervention`)
      items = items.map(x => x.id === updated.id ? updated : x)
      invalidateRef()
    } catch { /* toast par api.js */ }
  }

  // Action carte branchée sur un statut véhicule (transport hôpital, sur place…).
  const ACTIONS_CARTE = [['AUCUNE', '— ne bouge pas'], ['EN_ROUTE', 'En route'], ['SUR_PLACE', 'Sur place'],
    ['TRANSPORT_HOPITAL', 'Transport hôpital'], ['RETOUR_CASERNE', 'Retour caserne'], ['DEPANNEUR', 'Dépanneur']]
  async function setStatutAction(it, action) {
    try {
      const u = await api.put(`/sp/statuts/${it.id}/action-carte`, { action })
      items = items.map(x => x.id === u.id ? u : x)
      invalidateRef()
    } catch { /* toast par api.js */ }
  }

  // ── Hôpitaux (sous-section de l'écran Centres : référentiel + coords carte) ──
  let hopitaux = $state([])
  let hopForm  = $state({ code: '', label: '' })
  let hopError = $state('')
  async function loadHopitaux() { hopitaux = await api.get('/sp/hopitaux').catch(() => []) }
  async function addHopital(e) {
    e.preventDefault(); hopError = ''
    try { hopitaux = [...hopitaux, await api.post('/sp/hopitaux', { ...hopForm })]; hopForm = { code: '', label: '' } }
    catch (err) { hopError = err.message }
  }
  async function setHopitalCoord(it, coordonnees) {
    try { const u = await api.put(`/sp/hopitaux/${it.id}/coordonnees`, { coordonnees }); hopitaux = hopitaux.map(x => x.id === u.id ? u : x) }
    catch { /* toast par api.js */ }
  }
  async function removeHopital(it) {
    if (!await confirm({ title: 'Supprimer l\'hôpital', message: `Supprimer l'hôpital « ${it.label} » ?`, danger: true })) return
    try { await api.delete(`/sp/hopitaux/${it.id}`); hopitaux = hopitaux.filter(x => x.id !== it.id) }
    catch { /* toast */ }
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
      {:else if cat.kind === 'badges'}
        <SpBadgesAdmin />
      {:else}
      <h3>{cat.label} <span class="hint">— glisser-déposer pour réordonner</span></h3>

      {#if loading}
        <Skeleton rows={6} />
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
              {#if cat.kind === 'casier'}
                {#if casierAttrib[it.numero]}
                  <span class="casier-attrib" title="Attribué à">👤 {casierAttrib[it.numero]}</span>
                {:else}
                  <span class="casier-libre">libre</span>
                {/if}
              {/if}
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
              {#if cat.key === 'natures'}
                <IconePicker bind:emoji={it.icone} bind:imageId={it.iconeImageId} onchange={() => setNatureIcone(it)} />
              {/if}
              {#if cat.kind === 'fonctionorga'}
                <IconePicker bind:emoji={it.icone} bind:imageId={it.iconeImageId} onchange={() => updateFonctionOrga(it, {})} />
                <select class="type-fonction-sel" title="Fonction parente dans l'arbre"
                        value={it.parentId ?? ''}
                        onchange={e => updateFonctionOrga(it, { parentId: e.target.value || null })}>
                  <option value="">— racine —</option>
                  {#each items.filter(x => x.id !== it.id) as p (p.id)}
                    <option value={p.id}>{p.label}</option>
                  {/each}
                </select>
              {/if}
              {#if cat.kind === 'statut'}<span class="cat-badge">{it.categorie}</span>{/if}
              {#if cat.kind === 'statutveh' && it.etat}<span class="cat-badge" title="État appliqué">→ {it.etat.label}</span>{/if}
              {#if cat.key === 'statutsveh'}
                <select class="type-fonction-sel" value={it.actionCarte ?? 'AUCUNE'} title="Action carte déclenchée par ce statut"
                        onchange={e => setStatutAction(it, e.target.value)}>
                  {#each ACTIONS_CARTE as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
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
              {#if cat.kind === 'fonctionorga'}
                <label>Parent (optionnel)
                  <select bind:value={form.parentId}>
                    <option value="">— racine —</option>
                    {#each items as p (p.id)}<option value={p.id}>{p.label}</option>{/each}
                  </select>
                </label>
                <label>Icône
                  <IconePicker bind:emoji={form.icone} bind:imageId={form.iconeImageId} />
                </label>
              {/if}
            {/if}
          </div>
          <button type="submit" class="btn-primary">Ajouter</button>
        </form>

        {#if cat.key === 'centres'}
          <h3 style="margin-top:18px">Hôpitaux <span class="hint">— destinations de transport &amp; repères carte</span></h3>
          <ul class="order-list">
            {#each hopitaux as h (h.id)}
              <li class="order-item" style="cursor:default">
                <span class="it-label">{h.label}</span>
                <span class="chip-code">{h.code}</span>
                <input class="coord-input" type="text" inputmode="numeric" maxlength="6" placeholder="coord. 6 ch."
                       title="Coordonnées jeu de l'hôpital (carte)" value={h.coordonnees ?? ''}
                       oninput={e => e.target.value = e.target.value.replace(/\D/g, '').slice(0, 6)}
                       onchange={e => setHopitalCoord(h, e.target.value)} />
                <button class="rm-btn" onclick={() => removeHopital(h)} title="Supprimer">×</button>
              </li>
            {/each}
            {#if hopitaux.length === 0}<p class="muted small">Aucun hôpital</p>{/if}
          </ul>
          <form class="add-form" onsubmit={addHopital}>
            {#if hopError}<p class="inline-error">{hopError}</p>{/if}
            <div class="form-row">
              <label>Code<input type="text" bind:value={hopForm.code} placeholder="ex: CH_CENTRAL" required /></label>
              <label>Libellé<input type="text" bind:value={hopForm.label} placeholder="ex: CH Central" required /></label>
            </div>
            <button type="submit" class="btn-primary">Ajouter un hôpital</button>
          </form>
        {/if}
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

  /* Mobile : split vertical (catégories en haut, détail dessous). */
  @media (max-width: 768px) {
    .split { flex-direction: column; height: auto; }
    .list-pane { width: 100%; max-height: 180px; overflow-y: auto; border-right: none; border-bottom: 1px solid var(--color-border); }
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
  .casier-attrib { font-size: 11px; color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, transparent); border-radius: 10px; padding: 2px 8px; white-space: nowrap; }
  .casier-libre { font-size: 10px; color: var(--color-muted); font-style: italic; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }

  .cat-badge { font-size: 9px; font-weight: 700; letter-spacing: .4px; color: var(--color-muted); border: 1px solid var(--color-border); border-radius: 8px; padding: 1px 6px; }
  .defaut-btn { background: none; border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-muted); font-size: 10px; padding: 1px 6px; cursor: pointer; }
  .defaut-btn.on { border-color: var(--accent); color: var(--accent); }
  .type-fonction-sel { font-size: 11px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); padding: 2px 6px; cursor: pointer; }
  .coord-input { width: 90px; font-size: 11px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); padding: 2px 6px; }
  .icone-input { width: 56px; text-align: center; font-size: 14px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); padding: 2px 6px; }

  .add-form { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
  .check-label { display: flex; align-items: center; gap: 6px; font-size: 13px; align-self: flex-end; padding-bottom: 8px; white-space: nowrap; }
  .label-color { flex: 0 0 80px; min-width: 80px; }
  input[type="color"] { height: 34px; width: 60px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-bg); cursor: pointer; padding: 2px; }
</style>
