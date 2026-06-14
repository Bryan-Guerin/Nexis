<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'

    let types      = $state([])
  let vehicules  = $state([])
  let etats      = $state([])   // états maîtres (système)
  let statuts    = $state([])   // statuts RP (ordonnés)
  let centres    = $state([])
  let fonctions  = $state([])
  let objets     = $state([])   // catalogue d'objets d'inventaire
  let postes     = $state({})   // typeId → postes
  let inventaire = $state({})   // typeId → items d'inventaire
  let loading    = $state(true)
  let error      = $state('')

  // Recherche
  let recherche = $state('')
  let vehiculesFiltres = $derived(vehicules.filter(v => {
    const q = recherche.trim().toLowerCase()
    if (!q) return true
    return [v.libelle, v.immatriculation, v.type?.label, v.type?.code, v.centre?.label]
      .filter(Boolean).some(s => s.toLowerCase().includes(q))
  }))

  // Ajout véhicule
  let showAddVehicule = $state(false)
  let addVeh = $state({ typeId: '', libelle: '', immatriculation: '', centreId: '', capaciteEau: '', notes: '' })
  let addVehError = $state('')

  // Ajout type
  let showAddType = $state(false)
  let addType = $state({ code: '', label: '' })
  let addTypeError = $state('')

  // Type déplié
  let typeExpanded = $state({})
  let addPosteFor  = $state(null)
  let addPoste     = $state({ fonctionId: '', nbPlaces: 1, obligatoire: false })
  let natures      = $state([])   // catalogue des natures d'intervention (tags type)
  let invForm      = $state({ objetId: '', quantite: 1 })   // ajout d'un item au modèle

  // Édition véhicule
  let editVeh   = $state(null)
  let editForm  = $state({})
  let editError = $state('')

  // Vérification d'inventaire
  let verifVeh     = $state(null)
  let verifLignes  = $state([])
  let verifHistory = $state([])
  let verifError   = $state('')

  onMount(loadAll)

  async function loadAll() {
    loading = true; error = ''
    try {
      ;[types, vehicules, fonctions, etats, statuts, centres, objets, natures] = await Promise.all([
        api.get('/sp/vehicules/types'),
        api.get('/sp/vehicules'),
        api.get('/sp/fonctions'),
        api.get('/sp/vehicules/etats'),
        api.get('/sp/vehicules/statuts'),
        api.get('/sp/centres'),
        api.get('/sp/objets-inventaire'),
        api.get('/sp/natures'),
      ])
    } catch (e) { error = e.message }
    finally { loading = false }
  }

  async function submitAddVehicule(e) {
    e.preventDefault(); addVehError = ''
    try {
      const created = await api.post('/sp/vehicules', {
        typeId: addVeh.typeId || null,
        libelle: addVeh.libelle,
        immatriculation: addVeh.immatriculation || null,
        centreId: addVeh.centreId || null,
        capaciteEau: addVeh.capaciteEau ? Number(addVeh.capaciteEau) : null,
        notes: addVeh.notes || null,
      })
      vehicules = [...vehicules, created]
      showAddVehicule = false
      addVeh = { typeId: '', libelle: '', immatriculation: '', centreId: '', capaciteEau: '', notes: '' }
    } catch (e) { addVehError = e.message }
  }

  // État maître (système) — change direct
  async function changeEtat(v, etatId) {
    try {
      const updated = await api.put(`/sp/vehicules/${v.id}/etat?etatId=${etatId}`)
      vehicules = vehicules.map(x => x.id === v.id ? updated : x)
    } catch (e) { error = e.message }
  }

  // Statut RP — transition avant uniquement, le serveur garde la cohérence
  async function changeStatut(v, statutId) {
    try {
      const updated = await api.put(`/sp/vehicules/${v.id}/statut?statutId=${statutId}`)
      vehicules = vehicules.map(x => x.id === v.id ? updated : x)
    } catch (e) { error = e.message; await loadAll() }
  }
  // Options de statut proposées : le statut courant + les suivants (ordre ≥ courant)
  function statutOptions(v) {
    return statuts.filter(s => s.position >= (v.statut?.position ?? 0))
  }

  // ── Édition véhicule ────────────────────────────────────────────────────────
  function openEdit(v) {
    editVeh = v
    editForm = {
      libelle: v.libelle,
      immatriculation: v.immatriculation ?? '',
      centreId: v.centre?.id ?? '',
      capaciteEau: v.capaciteEau ?? '',
      notes: v.notes ?? '',
    }
    editError = ''
  }
  async function submitEdit(e) {
    e.preventDefault(); editError = ''
    try {
      const updated = await api.patch(`/sp/vehicules/${editVeh.id}`, {
        libelle: editForm.libelle,
        immatriculation: editForm.immatriculation || null,
        centreId: editForm.centreId || null,
        capaciteEau: editForm.capaciteEau !== '' ? Number(editForm.capaciteEau) : null,
        notes: editForm.notes || null,
      })
      vehicules = vehicules.map(x => x.id === updated.id ? updated : x)
      editVeh = null
    } catch (e) { editError = e.message }
  }

  // ── Types : postes + inventaire ──────────────────────────────────────────────
  async function toggleType(t) {
    const id = t.id
    typeExpanded = { ...typeExpanded, [id]: !typeExpanded[id] }
    if (typeExpanded[id]) {
      if (!postes[id])     postes = { ...postes, [id]: await api.get(`/sp/vehicules/types/${id}/postes`).catch(() => []) }
      if (!inventaire[id]) inventaire = { ...inventaire, [id]: await api.get(`/sp/vehicules/types/${id}/inventaire`).catch(() => []) }
    }
  }

  async function submitAddPoste(e, typeId) {
    e.preventDefault()
    try {
      const created = await api.post(`/sp/vehicules/types/${typeId}/postes`, {
        fonctionId: addPoste.fonctionId, nbPlaces: Number(addPoste.nbPlaces), obligatoire: addPoste.obligatoire,
      })
      postes = { ...postes, [typeId]: [...(postes[typeId] ?? []), created] }
      addPosteFor = null; addPoste = { fonctionId: '', nbPlaces: 1, obligatoire: false }
    } catch (e) { error = e.message }
  }

  async function toggleObligatoire(typeId, poste) {
    try {
      const updated = await api.put(`/sp/vehicules/postes/${poste.id}/obligatoire`)
      postes = { ...postes, [typeId]: postes[typeId].map(p => p.id === updated.id ? updated : p) }
    } catch (e) { error = e.message }
  }
  async function deletePoste(typeId, poste) {
    try {
      await api.delete(`/sp/vehicules/postes/${poste.id}`)
      postes = { ...postes, [typeId]: postes[typeId].filter(p => p.id !== poste.id) }
    } catch (e) {
      // Garde-fou back : poste occupé par un équipage actif → message engin + poste.
      window.alert(e.message)
    }
  }

  // Tags type ↔ nature
  async function toggleNature(t, natureId) {
    const current = t.natureIds ?? []
    const next = current.includes(natureId) ? current.filter(x => x !== natureId) : [...current, natureId]
    try {
      const updated = await api.put(`/sp/vehicules/types/${t.id}/natures`, { natureIds: next })
      types = types.map(x => x.id === updated.id ? updated : x)
    } catch (e) { error = e.message }
  }

  // Étoile la nature principale (catégorie de regroupement dispatch).
  async function setPrincipale(t, natureId) {
    const next = t.naturePrincipale?.id === natureId ? null : natureId
    try {
      const updated = await api.put(`/sp/vehicules/types/${t.id}/nature-principale`, { natureId: next })
      types = types.map(x => x.id === updated.id ? updated : x)
    } catch (e) { error = e.message }
  }

  async function submitAddType(e) {
    e.preventDefault(); addTypeError = ''
    try {
      const created = await api.post('/sp/vehicules/types', addType)
      types = [...types, created]
      showAddType = false; addType = { code: '', label: '' }
    } catch (e) { addTypeError = e.message }
  }

  async function addItem(typeId) {
    if (!invForm.objetId) return
    try {
      const created = await api.post(`/sp/vehicules/types/${typeId}/inventaire`,
        { objetId: invForm.objetId, quantite: Number(invForm.quantite) || 1 })
      inventaire = { ...inventaire, [typeId]: [...(inventaire[typeId] ?? []), created] }
      invForm = { objetId: '', quantite: 1 }
    } catch (e) { error = e.message }
  }
  async function deleteItem(typeId, itemId) {
    try {
      await api.delete(`/sp/inventaire/${itemId}`)
      inventaire = { ...inventaire, [typeId]: inventaire[typeId].filter(i => i.id !== itemId) }
    } catch (e) { error = e.message }
  }

  // ── Réordonnancement du modèle d'inventaire (glisser-déposer, persisté à la fin) ──
  let invDrag = $state({ typeId: null, index: null })

  function invDragStart(typeId, i) { invDrag = { typeId, index: i } }

  function invDragOver(e, typeId, i) {
    e.preventDefault()
    if (invDrag.typeId !== typeId || invDrag.index === null || invDrag.index === i) return
    const arr = [...(inventaire[typeId] ?? [])]
    const [moved] = arr.splice(invDrag.index, 1)
    arr.splice(i, 0, moved)
    inventaire = { ...inventaire, [typeId]: arr }
    invDrag = { typeId, index: i }
  }

  async function invDragEnd(typeId) {
    if (invDrag.typeId !== typeId) { invDrag = { typeId: null, index: null }; return }
    invDrag = { typeId: null, index: null }
    try { await api.put(`/sp/vehicules/types/${typeId}/inventaire/order`, { ids: (inventaire[typeId] ?? []).map(i => i.id) }) }
    catch (e) { error = e.message }
  }

  // ── Vérification d'inventaire d'un véhicule ───────────────────────────────────
  async function openVerif(v) {
    verifVeh = v; verifError = ''
    const items = await api.get(`/sp/vehicules/types/${v.type.id}/inventaire`).catch(() => [])
    // ok = conforme (quantité complète) ; sinon on saisit le nombre manquant
    verifLignes = items.map(i => ({
      libelle: i.objetLabel, quantiteAttendue: i.quantite, ok: true, manquant: 1,
    }))
    verifHistory = await api.get(`/sp/vehicules/${v.id}/verifications`).catch(() => [])
  }
  function quantitePresente(l) {
    return l.ok ? l.quantiteAttendue : Math.max(0, l.quantiteAttendue - (Number(l.manquant) || 0))
  }
  let verifConforme = $derived(verifLignes.every(l => quantitePresente(l) >= l.quantiteAttendue))
  async function submitVerif() {
    verifError = ''
    try {
      await api.post(`/sp/vehicules/${verifVeh.id}/verifications`, {
        lignes: verifLignes.map(l => ({
          libelle: l.libelle, quantiteAttendue: l.quantiteAttendue, quantitePresente: quantitePresente(l),
        })),
      })
      verifVeh = null
      await loadAll()   // le statut du véhicule a pu changer (disponible / indisponible)
    } catch (e) { verifError = e.message }
  }
  function fmt(iso) { return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) }
</script>

<div class="page">
  <div class="page-header">
    <h2>Véhicules — Sapeurs-Pompiers</h2>
    <button class="btn-primary" onclick={() => { showAddVehicule = !showAddVehicule; addVehError = '' }}>
      {showAddVehicule ? 'Annuler' : 'Ajouter un véhicule'}
    </button>
  </div>

  {#if showAddVehicule}
    <form class="create-form" onsubmit={submitAddVehicule}>
      <h3>Nouveau véhicule</h3>
      {#if addVehError}<p class="inline-error">{addVehError}</p>{/if}
      <div class="form-row">
        <label>Type
          <select bind:value={addVeh.typeId} required>
            <option value="">— choisir —</option>
            {#each types as t}<option value={t.id}>{t.label}</option>{/each}
          </select>
        </label>
        <label>Libellé<input type="text" bind:value={addVeh.libelle} required /></label>
        <label>Immatriculation<input type="text" bind:value={addVeh.immatriculation} placeholder="Optionnel" /></label>
      </div>
      <div class="form-row">
        <label>Centre
          <select bind:value={addVeh.centreId}>
            <option value="">—</option>
            {#each centres as c}<option value={c.id}>{c.label}</option>{/each}
          </select>
        </label>
        <label>Capacité eau (L)<input type="number" bind:value={addVeh.capaciteEau} min="0" placeholder="Optionnel" /></label>
      </div>
      <label>Commentaire<input type="text" bind:value={addVeh.notes} placeholder="Optionnel" /></label>
      <p class="muted small">Le véhicule est créé Disponible (état & statut).</p>
      <button type="submit" class="btn-primary">Créer</button>
    </form>
  {/if}

  {#if loading}
    <p class="muted">Chargement...</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <input class="veh-search" type="search" bind:value={recherche} placeholder="Rechercher (libellé, immat, type, centre)…" />
    <table>
      <thead>
        <tr><th>Libellé</th><th>Immat.</th><th>Type</th><th>Statut (RP)</th><th>État (système)</th><th>Centre</th><th>Eau</th><th>Commentaire</th><th></th></tr>
      </thead>
      <tbody>
        {#each vehiculesFiltres as v (v.id)}
          <tr>
            <td><strong>{v.libelle}</strong></td>
            <td class="mono">{v.immatriculation ?? '—'}</td>
            <td class="muted">{v.type.code}</td>
            <td>
              <span class="etat-dot" style="background:{v.statut.couleur}"></span>
              <select value={v.statut.id} onchange={e => changeStatut(v, e.target.value)} class="etat-select" title="Transition avant uniquement">
                {#each statutOptions(v) as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
              </select>
            </td>
            <td>
              <select value={v.etat.id} onchange={e => changeEtat(v, e.target.value)} class="etat-select" title="État maître (système)">
                {#each etats as e (e.id)}<option value={e.id}>{e.label}</option>{/each}
              </select>
            </td>
            <td class="muted">{v.centre?.label ?? '—'}</td>
            <td class="mono">{v.capaciteEau != null ? v.capaciteEau + ' L' : '—'}</td>
            <td class="notes" title={v.notes ?? ''}>{v.notes ?? '—'}</td>
            <td class="actions">
              <button class="btn-ghost-sm" onclick={() => openEdit(v)}>Éditer</button>
              <button class="btn-ghost-sm" onclick={() => openVerif(v)}>Vérifier</button>
            </td>
          </tr>
        {/each}
        {#if vehiculesFiltres.length === 0}
          <tr><td colspan="8" class="empty">{vehicules.length === 0 ? 'Aucun véhicule enregistré' : 'Aucun résultat'}</td></tr>
        {/if}
      </tbody>
    </table>
  {/if}

  <!-- Types : postes + inventaire -->
  <div class="section-header">
    <h3>Types de véhicule</h3>
    <button class="btn-secondary" onclick={() => { showAddType = !showAddType; addTypeError = '' }}>
      {showAddType ? 'Annuler' : 'Ajouter un type'}
    </button>
  </div>

  {#if showAddType}
    <form class="create-form" onsubmit={submitAddType}>
      {#if addTypeError}<p class="inline-error">{addTypeError}</p>{/if}
      <div class="form-row">
        <label>Code<input type="text" bind:value={addType.code} placeholder="ex: FPT" required /></label>
        <label>Libellé<input type="text" bind:value={addType.label} placeholder="ex: Fourgon Pompe Tonne" required /></label>
      </div>
      <button type="submit" class="btn-primary">Créer</button>
    </form>
  {/if}

  <div class="types-list">
    {#each types as t (t.id)}
      <div class="type-block">
        <div class="type-header" onclick={() => toggleType(t)} role="button" tabindex="0">
          <span class="type-label">{t.label}</span>
          <span class="chip-code">{t.code}</span>
          <span class="expand-icon">{typeExpanded[t.id] ? '▲' : '▼'}</span>
        </div>

        {#if typeExpanded[t.id]}
          <div class="type-body">
            <!-- Postes -->
            <div class="sub">
              <span class="sub-h">Postes</span>
              <div class="chips-row">
                {#each postes[t.id] ?? [] as p (p.id)}
                  <span class="poste-chip" class:oblig={p.obligatoire}>
                    {p.fonctionLabel}{#if p.nbPlaces > 1}<span class="nb">×{p.nbPlaces}</span>{/if}
                    <button class="poste-oblig" title="Obligatoire pour être armé" onclick={() => toggleObligatoire(t.id, p)}>{p.obligatoire ? '★' : '☆'}</button>
                    <button class="poste-rm" title="Supprimer" onclick={() => deletePoste(t.id, p)}>×</button>
                  </span>
                {/each}
                {#if (postes[t.id] ?? []).length === 0}<span class="muted small">Aucun poste</span>{/if}
                <button class="btn-ghost small" onclick={() => addPosteFor = addPosteFor === t.id ? null : t.id}>
                  {addPosteFor === t.id ? 'Annuler' : '+ Poste'}
                </button>
              </div>
              {#if addPosteFor === t.id}
                <form class="poste-form" onsubmit={e => submitAddPoste(e, t.id)}>
                  <div class="form-row">
                    <label>Fonction
                      <select bind:value={addPoste.fonctionId} required>
                        <option value="">— choisir —</option>
                        {#each fonctions as f (f.id)}<option value={f.id}>{f.label}</option>{/each}
                      </select>
                    </label>
                    <label>Places<input type="number" bind:value={addPoste.nbPlaces} min="1" max="10" required style="width:70px" /></label>
                    <label class="chk-oblig"><input type="checkbox" bind:checked={addPoste.obligatoire} /> Obligatoire</label>
                  </div>
                  <button type="submit" class="btn-primary">Ajouter</button>
                </form>
              {/if}
            </div>

            <!-- Natures (proposition d'engins) + nature principale (★ = catégorie dispatch) -->
            <div class="sub">
              <span class="sub-h">Natures — proposition <span class="hint">— ★ = catégorie principale (dispatch)</span></span>
              <div class="chips-row">
                {#each natures as n (n.id)}
                  {@const on = (t.natureIds ?? []).includes(n.id)}
                  <span class="nature-wrap">
                    <button class="nature-tag" class:on onclick={() => toggleNature(t, n.id)}>{n.code}</button>
                    {#if on}
                      <button class="star-btn" class:on={t.naturePrincipale?.id === n.id}
                              title="Définir comme catégorie principale (dispatch)" onclick={() => setPrincipale(t, n.id)}>
                        {t.naturePrincipale?.id === n.id ? '★' : '☆'}
                      </button>
                    {/if}
                  </span>
                {/each}
                {#if natures.length === 0}<span class="muted small">Aucune nature configurée</span>{/if}
              </div>
            </div>

            <!-- Inventaire -->
            <div class="sub">
              <span class="sub-h">Inventaire (modèle) <span class="hint">— glisser pour réordonner</span></span>
              <ul class="inv-list">
                {#each inventaire[t.id] ?? [] as it, i (it.id)}
                  <li
                    class:dragging={invDrag.typeId === t.id && invDrag.index === i}
                    draggable="true"
                    ondragstart={() => invDragStart(t.id, i)}
                    ondragover={(e) => invDragOver(e, t.id, i)}
                    ondragend={() => invDragEnd(t.id)}
                  >
                    <span class="handle" title="Glisser pour déplacer">⠿</span>
                    <span>{it.objetLabel} <span class="qty">×{it.quantite}</span></span>
                    <button class="rm-btn" title="Supprimer" onclick={() => deleteItem(t.id, it.id)}>×</button>
                  </li>
                {/each}
                {#if (inventaire[t.id] ?? []).length === 0}<li class="muted small">Aucun item</li>{/if}
              </ul>
              <div class="inv-add">
                <select bind:value={invForm.objetId}>
                  <option value="">— objet —</option>
                  {#each objets as o (o.id)}<option value={o.id}>{o.label}</option>{/each}
                </select>
                <input type="number" bind:value={invForm.quantite} min="1" title="Quantité attendue" style="width:70px" />
                <button class="btn-ghost-sm" onclick={() => addItem(t.id)}>Ajouter</button>
              </div>
              {#if objets.length === 0}<span class="muted small">Aucun objet au catalogue — ajoutez-en dans Configuration.</span>{/if}
            </div>
          </div>
        {/if}
      </div>
    {/each}
    {#if types.length === 0 && !loading}<p class="muted">Aucun type défini</p>{/if}
  </div>
</div>

<!-- ── Modale édition véhicule ──────────────────────────────────────────────── -->
{#if editVeh}
  <div class="backdrop" onclick={() => editVeh = null}>
    <div class="modal" onclick={e => e.stopPropagation()}>
      <h3>Éditer — {editVeh.libelle}</h3>
      {#if editError}<p class="inline-error">{editError}</p>{/if}
      <form onsubmit={submitEdit} style="display:flex;flex-direction:column;gap:12px">
        <label class="field-label">Libellé<input type="text" bind:value={editForm.libelle} required /></label>
        <label class="field-label">Immatriculation<input type="text" bind:value={editForm.immatriculation} /></label>
        <label class="field-label">Centre
          <select bind:value={editForm.centreId}>
            <option value="">—</option>
            {#each centres as c}<option value={c.id}>{c.label}</option>{/each}
          </select>
        </label>
        <label class="field-label">Capacité eau (L)<input type="number" bind:value={editForm.capaciteEau} min="0" /></label>
        <label class="field-label">Commentaire<input type="text" bind:value={editForm.notes} /></label>
        <div class="modal-actions">
          <button type="button" class="btn-ghost-sm" onclick={() => editVeh = null}>Annuler</button>
          <button type="submit" class="btn-primary">Enregistrer</button>
        </div>
      </form>
    </div>
  </div>
{/if}

<!-- ── Modale vérification inventaire (fermable uniquement via la croix) ─────── -->
{#if verifVeh}
  <div class="backdrop">
    <div class="modal wide">
      <button class="modal-close" title="Fermer" onclick={() => verifVeh = null}>×</button>
      <h3>Vérifier l'inventaire — {verifVeh.libelle}</h3>
      {#if verifError}<p class="inline-error">{verifError}</p>{/if}

      {#if verifLignes.length === 0}
        <p class="muted small">Aucun item d'inventaire défini pour ce type de véhicule.</p>
      {:else}
        <div class="result" class:ko={!verifConforme}>
          {verifConforme ? '✓ Inventaire conforme → véhicule disponible' : '✗ Inventaire incomplet → véhicule indisponible'}
        </div>
        <div class="check-list">
          {#each verifLignes as l, i (i)}
            <div class="check-line" class:missing={!l.ok}>
              <label class="chk"><input type="checkbox" bind:checked={l.ok} /> {l.libelle}</label>
              <span class="att">attendu&nbsp;: {l.quantiteAttendue}</span>
              {#if !l.ok}
                <label class="mq">manquant
                  <input type="number" bind:value={l.manquant} min="1" max={l.quantiteAttendue} style="width:60px" />
                </label>
              {/if}
            </div>
          {/each}
        </div>
      {/if}

      {#if verifHistory.length > 0}
        <div class="histo">
          <span class="sub-h">Historique</span>
          {#each verifHistory as h (h.id)}
            <div class="histo-line">
              <span class="mono">{fmt(h.creeLe)}</span>
              <span class="muted">{h.par ?? ''}</span>
              <span class="badge" class:ko-badge={!h.conforme}>{h.conforme ? 'Conforme' : 'Non conforme'}</span>
            </div>
          {/each}
        </div>
      {/if}

      <div class="modal-actions">
        {#if verifLignes.length > 0}<button class="btn-primary" onclick={submitVerif}>Valider</button>{/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .veh-search { width: 320px; max-width: 100%; margin-bottom: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; }
  .actions { display: flex; gap: 6px; }
  .etat-select { font-size: 12px; padding: 4px 8px; }
  .etat-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
  .notes { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--color-muted); font-size: 12px; }

  .types-list { display: flex; flex-direction: column; gap: 8px; }
  .type-block { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .type-header { display: flex; align-items: center; gap: 10px; padding: 12px 16px; cursor: pointer; transition: background 0.15s; }
  .type-header:hover { background: var(--hover); }
  .type-label { font-size: 14px; font-weight: 500; flex: 1; }
  .expand-icon { color: var(--color-muted); font-size: 11px; }

  .type-body { border-top: 1px solid var(--color-border); padding: 12px 16px; display: flex; flex-direction: column; gap: 16px; }
  .sub { display: flex; flex-direction: column; gap: 8px; }
  .sub-h { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: var(--color-muted); }
  .chips-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
  .poste-chip { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; font-size: 12px; padding: 4px 10px 4px 12px; display: flex; gap: 6px; align-items: center; }
  .poste-chip.oblig { border-color: var(--accent); }
  .poste-oblig { background: none; border: none; color: var(--color-muted); font-size: 12px; padding: 0; cursor: pointer; line-height: 1; }
  .poste-chip.oblig .poste-oblig { color: var(--accent); }
  .poste-rm { background: none; border: none; color: var(--color-muted); font-size: 14px; padding: 0; cursor: pointer; line-height: 1; }
  .poste-rm:hover { color: var(--color-danger); }
  .chk-oblig { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; }
  .nature-tag { background: var(--color-bg); border: 1px solid var(--color-border); color: var(--color-muted); border-radius: 16px; font-size: 11px; padding: 3px 10px; cursor: pointer; }
  .nature-tag.on { border-color: var(--accent); color: var(--accent); font-weight: 600; background: color-mix(in srgb, var(--accent) 10%, transparent); }
  .nature-wrap { display: inline-flex; align-items: center; gap: 2px; }
  .star-btn { background: none; border: none; cursor: pointer; font-size: 13px; line-height: 1; padding: 0 2px; color: var(--color-muted); }
  .star-btn.on { color: #e8a23a; }
  .nb { font-weight: 600; color: var(--accent); font-size: 11px; }

  .inv-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
  .inv-list li { display: flex; align-items: center; gap: 8px; font-size: 13px; padding: 4px 8px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); cursor: grab; }
  .inv-list li.dragging { opacity: .5; border-color: var(--accent); }
  .inv-list li > span:not(.handle) { flex: 1; }
  .inv-list .handle { flex: 0 0 auto; color: var(--color-muted); cursor: grab; user-select: none; font-size: 12px; }
  .hint { font-weight: 400; text-transform: none; letter-spacing: 0; font-size: 11px; color: var(--color-muted); }
  .inv-add { display: flex; gap: 8px; }
  .inv-add input { flex: 1; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 10px; outline: none; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; cursor: pointer; padding: 0 4px; }
  .rm-btn:hover { color: var(--color-danger); }

  .qty { color: var(--accent); font-weight: 600; font-size: 12px; }

  .modal.wide { width: 600px; position: relative; }
  .modal-close { position: absolute; top: 12px; right: 14px; background: none; border: none; color: var(--color-muted); font-size: 24px; line-height: 1; cursor: pointer; padding: 0 4px; }
  .modal-close:hover { color: var(--color-text); }
  .modal select, .modal input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; outline: none; }

  .result { font-size: 13px; font-weight: 600; padding: 8px 12px; border-radius: var(--radius); background: color-mix(in srgb, var(--color-success, #4caf82) 16%, transparent); color: var(--color-success, #4caf82); }
  .result.ko { background: color-mix(in srgb, var(--color-danger) 16%, transparent); color: var(--color-danger); }

  .check-list { display: flex; flex-direction: column; gap: 6px; max-height: 42vh; overflow-y: auto; }
  .check-line { display: flex; align-items: center; gap: 12px; padding: 4px 8px; border-radius: var(--radius); }
  .check-line.missing { background: color-mix(in srgb, var(--color-danger) 8%, transparent); }
  .check-line .chk { flex: 1; display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .check-line .att { font-size: 11px; color: var(--color-muted); }
  .check-line .mq { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--color-danger); }
  .histo { display: flex; flex-direction: column; gap: 4px; border-top: 1px solid var(--color-border); padding-top: 10px; margin-top: 8px; }
  .histo-line { display: flex; gap: 12px; font-size: 12px; align-items: center; }
  .ko-badge { background: color-mix(in srgb, var(--color-danger) 18%, transparent) !important; color: var(--color-danger) !important; }
</style>
