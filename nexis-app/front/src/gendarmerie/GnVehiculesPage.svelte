<script>
  import { onMount } from 'svelte'
  import { api } from '../shared/api.js'

  let types     = $state([])
  let vehicules = $state([])
  let etats     = $state([])
  let loading   = $state(true)
  let error     = $state('')

  let showAddVehicule = $state(false)
  let addVeh = $state({ typeId: '', libelle: '', immatriculation: '' })
  let addVehError = $state('')

  let showAddType = $state(false)
  let addType = $state({ code: '', label: '' })
  let addTypeError = $state('')

  onMount(loadAll)

  async function loadAll() {
    loading = true; error = ''
    try {
      ;[types, vehicules, etats] = await Promise.all([
        api.get('/gn/vehicules/types'),
        api.get('/gn/vehicules'),
        api.get('/gn/vehicules/etats'),
      ])
    } catch (e) {
      error = e.message
    } finally {
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
    <p class="muted">Chargement...</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <table>
      <thead>
        <tr>
          <th>Immat.</th><th>Type</th><th>Libellé</th><th>Statut</th><th>Changer le statut</th>
        </tr>
      </thead>
      <tbody>
        {#each vehicules as v (v.id)}
          <tr>
            <td class="immat">{v.immatriculation ?? '—'}</td>
            <td class="muted">{v.type.label}</td>
            <td>{v.libelle}</td>
            <td>
              <span class="badge" style="background:{v.etat.couleur}22; color:{v.etat.couleur}; border:1px solid {v.etat.couleur}44">
                {v.etat.label}
              </span>
            </td>
            <td>
              <select value={v.etat.id} onchange={e => changeEtat(v, e.target.value)} class="etat-select">
                {#each etats as e}
                  <option value={e.id}>{e.label}</option>
                {/each}
              </select>
            </td>
          </tr>
        {/each}
        {#if vehicules.length === 0}
          <tr><td colspan="5" class="empty">Aucun véhicule enregistré</td></tr>
        {/if}
      </tbody>
    </table>
  {/if}

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
</style>
