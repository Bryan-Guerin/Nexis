<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {compareBy, nextSort} from '../shared/tableSort.js'
    import Skeleton from '../shared/Skeleton.svelte'
    import SortableTh from '../shared/SortableTh.svelte'
    import Pagination from '../shared/Pagination.svelte'
    import EmptyState from '../shared/EmptyState.svelte'

    let types     = $state([])
  let vehicules = $state([])
  let etats     = $state([])
  let loading   = $state(true)

  let showAddVehicule = $state(false)
  let addVeh = $state({ typeId: '', libelle: '', immatriculation: '' })
  let addVehError = $state('')

  let showAddType = $state(false)
  let addType = $state({ code: '', label: '' })
  let addTypeError = $state('')

  // Recherche + tri + pagination
  let recherche = $state('')
  let sort      = $state({ col: 'immatriculation', dir: 'asc' })
  let vPage     = $state(1)
  let vPageSize = $state(25)
  const KEYS = {
    immatriculation: v => v.immatriculation,
    type:            v => v.type?.label,
    libelle:         v => v.libelle,
    etat:            v => v.etat?.label,
  }
  let vehiculesFiltres = $derived(vehicules.filter(v => {
    const q = recherche.trim().toLowerCase()
    if (!q) return true
    return [v.immatriculation, v.libelle, v.type?.label, v.type?.code, v.etat?.label]
      .filter(Boolean).some(s => s.toLowerCase().includes(q))
  }))
  let vehiculesTries = $derived([...vehiculesFiltres].sort(compareBy(KEYS[sort.col], sort.dir)))
  let vehiculesPage  = $derived(vehiculesTries.slice((vPage - 1) * vPageSize, vPage * vPageSize))

  onMount(loadAll)

  async function loadAll() {
    loading = true
    try {
      ;[types, vehicules, etats] = await Promise.all([
        api.get('/gn/vehicules/types'),
        api.get('/gn/vehicules'),
        api.get('/gn/vehicules/etats'),
      ])
    } catch { /* toast par api.js */ }
    finally {
      loading = false
    }
  }

  async function submitAddVehicule(e) {
    e.preventDefault(); addVehError = ''
    try {
      const created = await api.post('/gn/vehicules', {
        typeId: addVeh.typeId || null,
        libelle: addVeh.libelle,
        immatriculation: addVeh.immatriculation || null,
      })
      vehicules = [...vehicules, created]
      showAddVehicule = false
      addVeh = { typeId: '', libelle: '', immatriculation: '' }
    } catch (e) { addVehError = e.message }
  }

  async function submitAddType(e) {
    e.preventDefault(); addTypeError = ''
    try {
      const created = await api.post('/gn/vehicules/types', addType)
      types = [...types, created]
      showAddType = false
      addType = { code: '', label: '' }
    } catch (e) { addTypeError = e.message }
  }

  async function changeEtat(v, etatId) {
    try {
      const updated = await api.put(`/gn/vehicules/${v.id}/etat?etatId=${etatId}`)
      vehicules = vehicules.map(x => x.id === v.id ? updated : x)
    } catch { /* silent */ }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Véhicules — Gendarmerie</h2>
    <button class="btn-primary" onclick={() => { showAddVehicule = !showAddVehicule; addVehError = '' }}>
      {showAddVehicule ? 'Annuler' : 'Ajouter un véhicule'}
    </button>
  </div>

  {#if showAddVehicule}
    <form class="create-form" onsubmit={submitAddVehicule}>
      <h3>Nouveau véhicule</h3>
      {#if addVehError}<p class="inline-error">{addVehError}</p>{/if}
      <div class="form-row">
        <label>
          Type
          <select bind:value={addVeh.typeId} required>
            <option value="">— choisir —</option>
            {#each types as t}<option value={t.id}>{t.label}</option>{/each}
          </select>
        </label>
        <label>
          Libellé
          <input type="text" bind:value={addVeh.libelle} required />
        </label>
        <label>
          Immatriculation
          <input type="text" bind:value={addVeh.immatriculation} placeholder="Optionnel" />
        </label>
      </div>
      <button type="submit" class="btn-primary">Créer</button>
    </form>
  {/if}

  {#if loading}
    <Skeleton rows={6} />
  {:else if vehicules.length === 0}
    <EmptyState icon="🚓" title="Aucun véhicule enregistré" message="Ajoutez votre premier véhicule." />
  {:else}
    <input class="veh-search" type="search" bind:value={recherche} placeholder="Rechercher (immat, libellé, type, statut)…" />
    <table>
      <thead>
        <tr>
          <SortableTh col="immatriculation" label="Immat." {sort} onsort={c => sort = nextSort(sort, c)} />
          <SortableTh col="type"            label="Type"   {sort} onsort={c => sort = nextSort(sort, c)} />
          <SortableTh col="libelle"         label="Libellé" {sort} onsort={c => sort = nextSort(sort, c)} />
          <SortableTh col="etat"            label="Statut" {sort} onsort={c => sort = nextSort(sort, c)} />
          <th>Changer le statut</th>
        </tr>
      </thead>
      <tbody>
        {#each vehiculesPage as v (v.id)}
          <tr>
            <td class="immat" data-label="Immat.">{v.immatriculation ?? '—'}</td>
            <td class="muted" data-label="Type">{v.type.label}</td>
            <td data-label="Libellé">{v.libelle}</td>
            <td data-label="Statut">
              <span class="badge" style="background:{v.etat.couleur}22; color:{v.etat.couleur}; border:1px solid {v.etat.couleur}44">
                {v.etat.label}
              </span>
            </td>
            <td data-label="Changer">
              <select value={v.etat.id} onchange={e => changeEtat(v, e.target.value)} class="etat-select">
                {#each etats as e}
                  <option value={e.id}>{e.label}</option>
                {/each}
              </select>
            </td>
          </tr>
        {/each}
        {#if vehiculesFiltres.length === 0}
          <tr><td colspan="5" class="empty">Aucun résultat</td></tr>
        {/if}
      </tbody>
    </table>
    {#if vehiculesFiltres.length > 0}
      <Pagination bind:page={vPage} bind:pageSize={vPageSize} total={vehiculesFiltres.length} />
    {/if}
  {/if}

  <div class="section-header">
    <h3>Types de véhicule</h3>
    <button class="btn-ghost" onclick={() => { showAddType = !showAddType; addTypeError = '' }}>
      {showAddType ? 'Annuler' : 'Ajouter un type'}
    </button>
  </div>

  {#if showAddType}
    <form class="create-form" onsubmit={submitAddType}>
      {#if addTypeError}<p class="inline-error">{addTypeError}</p>{/if}
      <div class="form-row">
        <label>Code<input type="text" bind:value={addType.code} placeholder="ex: FOURGON" required /></label>
        <label>Libellé<input type="text" bind:value={addType.label} placeholder="ex: Fourgon de gendarmerie" required /></label>
      </div>
      <button type="submit" class="btn-primary">Créer</button>
    </form>
  {/if}

  <div class="chips">
    {#each types as t}
      <span class="chip">{t.label} <span class="chip-code">{t.code}</span></span>
    {/each}
    {#if types.length === 0 && !loading}
      <p class="muted">Aucun type défini</p>
    {/if}
  </div>
</div>

<style>
  .etat-select { font-size: 12px; padding: 4px 8px; }
  .veh-search { width: 100%; max-width: 360px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; margin-bottom: 10px; }
  .veh-search:focus { border-color: var(--accent); }
</style>
