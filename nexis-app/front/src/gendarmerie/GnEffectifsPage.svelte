<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import Skeleton from '../shared/Skeleton.svelte'

    let membres   = $state([])
  let users     = $state([])
  let grades    = $state([])
  let loading   = $state(true)
  let error     = $state('')

  let showAdd   = $state(false)
  let addForm   = $state({ userId: '', gradeId: '', matricule: '' })
  let addError  = $state('')

  // Création inline d'un compte
  let showNewUser  = $state(false)
  let newUserForm  = $state({ username: '', password: '' })
  let newUserError = $state('')

  // Planning expanded per member
  let planningOpen = $state({})
  let planningData = $state({})

  // Planning form
  let planningForm       = $state({ debut: '', fin: '', statutId: '', notes: '' })
  let planningFormMembre = $state(null)

  let statuts = $state([])   // statuts de planning configurés (/gn/planning/statuts)

  onMount(loadAll)

  async function loadAll() {
    loading = true; error = ''
    try {
      ;[membres, users, grades, statuts] = await Promise.all([
        api.get('/gn/membres'),
        api.get('/admin/users').catch(() => []),
        api.get('/gn/grades'),
        api.get('/gn/planning/statuts'),
      ])
    } catch (e) {
      error = e.message
    } finally {
      loading = false
    }
  }

  async function submitNewUser(e) {
    e.preventDefault(); newUserError = ''
    try {
      const created = await api.post('/gn/users', newUserForm)
      users = [...users, created]
      addForm.userId = created.id
      showNewUser = false
      newUserForm = { username: '', password: '' }
    } catch (e) { newUserError = e.message }
  }

  async function submitAdd(e) {
    e.preventDefault(); addError = ''
    try {
      const created = await api.post('/gn/membres', addForm)
      membres = [...membres, created]
      showAdd = false
      addForm = { userId: '', gradeId: '', matricule: '' }
    } catch (e) { addError = e.message }
  }

  async function togglePlanning(membre) {
    const id = membre.id
    if (planningOpen[id]) {
      planningOpen = { ...planningOpen, [id]: false }
      return
    }
    planningOpen = { ...planningOpen, [id]: true }
    if (!planningData[id]) {
      const data = await api.get(`/gn/membres/${id}/planning`).catch(() => [])
      planningData = { ...planningData, [id]: data }
    }
  }

  async function submitPlanning(e) {
    e.preventDefault()
    const id = planningFormMembre
    try {
      const created = await api.post(`/gn/membres/${id}/planning`, {
        debut:    new Date(planningForm.debut).toISOString(),
        fin:      new Date(planningForm.fin).toISOString(),
        statutId: planningForm.statutId,
        notes:    planningForm.notes || null,
      })
      planningData = { ...planningData, [id]: [...(planningData[id] ?? []), created] }
      planningFormMembre = null
      planningForm = { debut: '', fin: '', statutId: '', notes: '' }
    } catch { /* silent */ }
  }

  function fmt(iso) {
    if (!iso) return '—'
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Effectifs — Gendarmerie</h2>
    <button class="btn-primary" onclick={() => { showAdd = !showAdd; addError = ''; showNewUser = false }}>
      {showAdd ? 'Annuler' : 'Ajouter un membre'}
    </button>
  </div>

  {#if showAdd}
    <form class="create-form" onsubmit={submitAdd}>
      <h3>Nouveau membre</h3>
      {#if addError}<p class="inline-error">{addError}</p>{/if}

      <div class="form-row">
        <label>
          Compte utilisateur
          <div class="user-row">
            <select bind:value={addForm.userId} required>
              <option value="">— choisir —</option>
              {#each users as u}<option value={u.id}>{u.username}</option>{/each}
            </select>
            <button type="button" class="btn-ghost small" onclick={() => showNewUser = !showNewUser}>
              {showNewUser ? 'Annuler' : '+ Nouveau compte'}
            </button>
          </div>
        </label>
        <label>
          Grade
          <select bind:value={addForm.gradeId} required>
            <option value="">— choisir —</option>
            {#each grades as g}<option value={g.id}>{g.label}</option>{/each}
          </select>
        </label>
        <label>Matricule<input type="text" bind:value={addForm.matricule} placeholder="ex: GN-001" required /></label>
      </div>

      {#if showNewUser}
        <div class="sub-form">
          <p class="sub-title">Créer un compte GN</p>
          {#if newUserError}<p class="inline-error">{newUserError}</p>{/if}
          <div class="form-row">
            <label>Nom d'utilisateur<input type="text" bind:value={newUserForm.username} required /></label>
            <label>Mot de passe<input type="password" bind:value={newUserForm.password} required /></label>
          </div>
          <button type="button" class="btn-ghost" onclick={submitNewUser}>Créer le compte</button>
        </div>
      {/if}

      <button type="submit" class="btn-primary">Créer le membre</button>
    </form>
  {/if}

  {#if loading}
    <Skeleton rows={6} />
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <table>
      <thead>
        <tr><th>Matricule</th><th>Grade</th><th>Compte</th><th>Statut</th><th></th></tr>
      </thead>
      <tbody>
        {#each membres as m (m.id)}
          <tr>
            <td class="mono">{m.matricule}</td>
            <td>{m.grade}</td>
            <td class="muted">{m.username}</td>
            <td>
              <span class="badge {m.actif ? 'badge-actif' : 'badge-inactif'}">
                {m.actif ? 'Actif' : 'Inactif'}
              </span>
            </td>
            <td class="actions">
              <button class="btn-ghost" onclick={() => togglePlanning(m)}>
                {planningOpen[m.id] ? '▲ Planning' : '▼ Planning'}
              </button>
              <button class="btn-ghost" onclick={() => planningFormMembre = planningFormMembre === m.id ? null : m.id}>
                + Plage
              </button>
            </td>
          </tr>

          {#if planningFormMembre === m.id}
            <tr class="planning-form-row">
              <td colspan="5">
                <form class="planning-form" onsubmit={submitPlanning}>
                  <div class="form-row">
                    <label>Début<input type="datetime-local" bind:value={planningForm.debut} required /></label>
                    <label>Fin<input type="datetime-local" bind:value={planningForm.fin} required /></label>
                    <label>
                      Statut
                      <select bind:value={planningForm.statutId}>
                        {#each statuts as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
                      </select>
                    </label>
                    <label>Notes<input type="text" bind:value={planningForm.notes} placeholder="Optionnel" /></label>
                  </div>
                  <button type="submit" class="btn-primary">Enregistrer</button>
                </form>
              </td>
            </tr>
          {/if}

          {#if planningOpen[m.id]}
            <tr class="planning-row">
              <td colspan="5">
                {#if !planningData[m.id]}
                  <p class="muted small">Chargement...</p>
                {:else if planningData[m.id].length === 0}
                  <p class="muted small">Aucune plage planning</p>
                {:else}
                  <div class="planning-list">
                    {#each planningData[m.id] as p (p.id)}
                      <span class="plage">
                        <span class="badge" style="background:{p.statut.couleur}22;color:{p.statut.couleur}">{p.statut.label}</span>
                        {fmt(p.debut)} → {fmt(p.fin)}
                        {#if p.notes}<span class="muted small">{p.notes}</span>{/if}
                      </span>
                    {/each}
                  </div>
                {/if}
              </td>
            </tr>
          {/if}
        {/each}
        {#if membres.length === 0}
          <tr><td colspan="5" class="empty">Aucun membre enregistré</td></tr>
        {/if}
      </tbody>
    </table>
  {/if}
</div>

<style>
  .actions { display: flex; gap: 6px; }
  .planning-row td, .planning-form-row td { background: rgba(255,255,255,0.02); padding: 12px 16px; }
  .planning-list { display: flex; flex-direction: column; gap: 6px; }
  .plage { display: flex; align-items: center; gap: 10px; font-size: 13px; }
</style>
