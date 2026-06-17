<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import Skeleton from '../shared/Skeleton.svelte'

    let grades   = $state([])
  let etats    = $state([])
  let loading  = $state(true)
  let error    = $state('')

  let showGrade   = $state(false)
  let gradeForm   = $state({ code: '', label: '' })
  let gradeError  = $state('')

  let showEtat    = $state(false)
  let etatForm    = $state({ code: '', label: '', couleur: '#4f6ef7' })
  let etatError   = $state('')

  onMount(loadAll)

  async function loadAll() {
    loading = true; error = ''
    try {
      ;[grades, etats] = await Promise.all([
        api.get('/gn/grades'),
        api.get('/gn/vehicules/etats'),
      ])
    } catch (e) { error = e.message }
    finally { loading = false }
  }

  async function submitGrade(e) {
    e.preventDefault(); gradeError = ''
    try {
      grades = [...grades, await api.post('/gn/grades', gradeForm)]
      showGrade = false; gradeForm = { code: '', label: '' }
    } catch (e) { gradeError = e.message }
  }

  async function submitEtat(e) {
    e.preventDefault(); etatError = ''
    try {
      etats = [...etats, await api.post('/gn/etats', etatForm)]
      showEtat = false; etatForm = { code: '', label: '', couleur: '#4f6ef7' }
    } catch (e) { etatError = e.message }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Configuration — Gendarmerie</h2>
  </div>

  {#if loading}
    <Skeleton rows={6} />
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <!-- Grades -->
    <div class="section-header">
      <h3>Grades</h3>
      <button class="btn-primary" onclick={() => { showGrade = !showGrade; gradeError = '' }}>
        {showGrade ? 'Annuler' : '+ Grade'}
      </button>
    </div>

    {#if showGrade}
      <form class="create-form" onsubmit={submitGrade}>
        {#if gradeError}<p class="inline-error">{gradeError}</p>{/if}
        <div class="form-row">
          <label>Code<input type="text" bind:value={gradeForm.code} placeholder="ex: GEND" required /></label>
          <label>Libellé<input type="text" bind:value={gradeForm.label} placeholder="ex: Gendarme" required /></label>
        </div>
        <button type="submit" class="btn-primary">Créer</button>
      </form>
    {/if}

    <div class="chips">
      {#each grades as g (g.id)}
        <span class="chip">
          <span class="chip-label">{g.label}</span>
          <span class="chip-code">{g.code}</span>
        </span>
      {/each}
      {#if grades.length === 0}<p class="muted">Aucun grade défini</p>{/if}
    </div>

    <!-- États véhicules -->
    <div class="section-header">
      <h3>Statuts véhicule</h3>
      <button class="btn-primary" onclick={() => { showEtat = !showEtat; etatError = '' }}>
        {showEtat ? 'Annuler' : '+ Statut'}
      </button>
    </div>

    {#if showEtat}
      <form class="create-form" onsubmit={submitEtat}>
        {#if etatError}<p class="inline-error">{etatError}</p>{/if}
        <div class="form-row">
          <label>Code<input type="text" bind:value={etatForm.code} placeholder="ex: EN_INTERVENTION" required /></label>
          <label>Libellé<input type="text" bind:value={etatForm.label} placeholder="ex: En intervention" required /></label>
          <label class="label-color">Couleur<input type="color" bind:value={etatForm.couleur} /></label>
        </div>
        <button type="submit" class="btn-primary">Créer</button>
      </form>
    {/if}

    <div class="chips">
      {#each etats as e (e.id)}
        <span class="chip" style="border-left: 3px solid {e.couleur}">
          <span class="etat-dot" style="background:{e.couleur}"></span>
          <span class="chip-label">{e.label}</span>
          <span class="chip-code">{e.code}</span>
        </span>
      {/each}
      {#if etats.length === 0}<p class="muted">Aucun état défini</p>{/if}
    </div>
  {/if}
</div>

<style>
  /* Les états ont un sélecteur de couleur ; on aligne la ligne en bas */
  .form-row { align-items: flex-end; }
  .label-color { flex: 0 0 80px; min-width: 80px; }
  input[type="color"] { height: 34px; width: 60px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-bg); cursor: pointer; padding: 2px; }
</style>
