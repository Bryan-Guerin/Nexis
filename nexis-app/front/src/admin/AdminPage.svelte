<script>
    import {onMount} from 'svelte'
    import {currentUser} from '../shared/stores.js'
    import {api} from '../shared/api.js'
    import Modal from '../shared/Modal.svelte'

    let users    = $state([])
  let roles    = $state([])
  let loading  = $state(false)
  let error    = $state('')
  let creating = $state(false)

  // Création d'utilisateur
  let form = $state({ username: '', password: '', roles: [] })
  let formError = $state('')
  let formLoading = $state(false)

  // Création de rôle
  let showCreateRole = $state(false)
  let roleForm  = $state({ code: '', label: '', parentCode: '' })
  let roleError = $state('')

  // Édition des rôles d'un utilisateur (modale)
  let editUser  = $state(null)
  let editRoles = $state([])
  let editError = $state('')

  // Réinitialisation de mot de passe (modale résultat)
  let resetResult = $state(null)   // { username, password }
  let resetError  = $state('')

  const isAdmin = $derived($currentUser?.roles?.includes('ROLE_SYSTEM') ?? false)

  onMount(() => {
    if (isAdmin) { loadUsers(); loadRoles() }
  })

  async function loadUsers() {
    loading = true; error = ''
    try { users = await api.get('/admin/users') }
    catch (e) { error = e.message }
    finally { loading = false }
  }

  async function loadRoles() {
    try { roles = await api.get('/admin/roles') } catch { /* non-bloquant */ }
  }

  // ── Création utilisateur ────────────────────────────────────────────────────
  async function submitCreate(e) {
    e.preventDefault()
    if (!form.username || !form.password) { formError = 'Identifiant et mot de passe requis'; return }
    formError = ''; formLoading = true
    try {
      const created = await api.post('/admin/users', form)
      users = [...users, created]
      form = { username: '', password: '', roles: [] }
      creating = false
    } catch (e) { formError = e.message }
    finally { formLoading = false }
  }

  function toggleRole(code) {
    form.roles = form.roles.includes(code) ? form.roles.filter(r => r !== code) : [...form.roles, code]
  }

  // ── Création rôle ───────────────────────────────────────────────────────────
  async function submitCreateRole(e) {
    e.preventDefault(); roleError = ''
    try {
      const created = await api.post('/admin/roles', {
        code: roleForm.code,
        label: roleForm.label,
        parentCode: roleForm.parentCode || null,
      })
      roles = [...roles, created]
      roleForm = { code: '', label: '', parentCode: '' }
      showCreateRole = false
    } catch (e) { roleError = e.message }
  }

  // ── Édition des rôles d'un utilisateur ──────────────────────────────────────
  function openEditRoles(user) {
    editUser = user
    editRoles = [...user.roles]
    editError = ''
  }
  function toggleEditRole(code) {
    editRoles = editRoles.includes(code) ? editRoles.filter(r => r !== code) : [...editRoles, code]
  }
  async function saveRoles() {
    editError = ''
    try {
      const updated = await api.patch(`/admin/users/${editUser.id}/roles`, { roles: editRoles })
      users = users.map(u => u.id === updated.id ? updated : u)
      editUser = null
    } catch (e) { editError = e.message }
  }

  // ── Reset mot de passe ──────────────────────────────────────────────────────
  async function resetPassword(user) {
    resetError = ''
    try {
      const res = await api.post(`/admin/users/${user.id}/reset-password`)
      resetResult = { username: user.username, password: res.password }
    } catch (e) { resetError = e.message }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────
  function badgeColor(code) {
    if (code === 'ROLE_SYSTEM')   return 'badge-system'
    if (code === 'ROLE_ADMIN_GN' || code === 'ROLE_ADMIN_SP') return 'badge-admin'
    if (code === 'ROLE_GN')       return 'badge-gn'
    if (code === 'ROLE_SP')       return 'badge-sp'
    return 'badge-default'
  }
  function roleLabel(code) {
    return roles.find(r => r.code === code)?.label ?? code.replace('ROLE_', '')
  }
</script>

{#if !isAdmin}
  <div class="page">
    <p class="access-denied">Accès réservé aux administrateurs système.</p>
  </div>
{:else}
  <div class="page">
    <div class="page-header">
      <h2>Gestion des utilisateurs</h2>
      <button class="btn-primary" onclick={() => { creating = !creating; formError = '' }}>
        {creating ? 'Annuler' : 'Nouvel utilisateur'}
      </button>
    </div>

    <!-- Création utilisateur -->
    {#if creating}
      <form class="create-form" onsubmit={submitCreate}>
        <h3>Créer un utilisateur</h3>
        {#if formError}<p class="inline-error">{formError}</p>{/if}
        <div class="form-row grid2">
          <label>Identifiant<input type="text" bind:value={form.username} autocomplete="off" required /></label>
          <label>Mot de passe<input type="password" bind:value={form.password} required /></label>
        </div>
        <div class="role-picker">
          <span class="role-label">Rôles</span>
          {#each roles as role}
            <label class="role-check">
              <input type="checkbox" checked={form.roles.includes(role.code)} onchange={() => toggleRole(role.code)} />
              {role.label}
            </label>
          {/each}
        </div>
        <button type="submit" class="btn-primary" disabled={formLoading}>
          {formLoading ? 'Création...' : 'Créer'}
        </button>
      </form>
    {/if}

    <!-- Utilisateurs -->
    {#if error}
      <p class="inline-error">{error}</p>
    {:else if loading}
      <p class="muted">Chargement...</p>
    {:else}
      <table>
        <thead>
          <tr><th>Identifiant</th><th>Rôles</th><th>Steam</th><th>Statut</th><th></th></tr>
        </thead>
        <tbody>
          {#each users as user (user.id)}
            <tr class:disabled={!user.enabled}>
              <td class="username">{user.username}</td>
              <td class="roles">
                {#each user.roles as code}
                  <span class="badge {badgeColor(code)}">{roleLabel(code)}</span>
                {/each}
              </td>
              <td class="steam">{user.steamId ?? '—'}</td>
              <td>
                <span class="status" class:active={user.enabled}>{user.enabled ? 'Actif' : 'Inactif'}</span>
              </td>
              <td class="actions">
                <button class="btn-ghost-sm" onclick={() => openEditRoles(user)}>Rôles</button>
                <button class="btn-ghost-sm" onclick={() => resetPassword(user)}>Reset MDP</button>
              </td>
            </tr>
          {/each}
          {#if users.length === 0}
            <tr><td colspan="5" class="empty">Aucun utilisateur</td></tr>
          {/if}
        </tbody>
      </table>
    {/if}

    {#if resetError}<p class="inline-error">{resetError}</p>{/if}

    <!-- Rôles -->
    <div class="section-header">
      <h3>Rôles</h3>
      <button class="btn-ghost-sm" onclick={() => { showCreateRole = !showCreateRole; roleError = '' }}>
        {showCreateRole ? 'Annuler' : '+ Nouveau rôle'}
      </button>
    </div>

    {#if showCreateRole}
      <form class="create-form" onsubmit={submitCreateRole}>
        {#if roleError}<p class="inline-error">{roleError}</p>{/if}
        <div class="form-row">
          <label>Code<input type="text" bind:value={roleForm.code} placeholder="ex: ROLE_SP_RH" required /></label>
          <label>Libellé<input type="text" bind:value={roleForm.label} placeholder="ex: Pompier RH" required /></label>
          <label>Parent (hérite des accès)
            <select bind:value={roleForm.parentCode}>
              <option value="">— aucun —</option>
              {#each roles as r}<option value={r.code}>{r.label}</option>{/each}
            </select>
          </label>
        </div>
        <button type="submit" class="btn-primary">Créer le rôle</button>
      </form>
    {/if}

    <div class="chips">
      {#each roles as r (r.code)}
        <span class="chip">
          <span class="chip-label">{r.label}</span>
          <span class="chip-code">{r.code}</span>
          {#if r.parentCode}<span class="role-parent">⤷ {r.parentCode.replace('ROLE_', '')}</span>{/if}
        </span>
      {/each}
      {#if roles.length === 0}<p class="muted">Aucun rôle</p>{/if}
    </div>
  </div>
{/if}

<!-- ── Modale édition des rôles ─────────────────────────────────────────────── -->
{#if editUser}
  <Modal title={`Rôles de ${editUser.username}`} onclose={() => editUser = null}>
    {#if editError}<p class="inline-error">{editError}</p>{/if}
    <div class="role-picker col">
      {#each roles as role}
        <label class="role-check">
          <input type="checkbox" checked={editRoles.includes(role.code)} onchange={() => toggleEditRole(role.code)} />
          {role.label} <span class="chip-code">{role.code}</span>
        </label>
      {/each}
    </div>
    {#snippet actions()}
      <button class="btn-ghost-sm" onclick={() => editUser = null}>Annuler</button>
      <button class="btn-primary" onclick={saveRoles}>Enregistrer</button>
    {/snippet}
  </Modal>
{/if}

<!-- ── Modale résultat reset mot de passe ───────────────────────────────────── -->
{#if resetResult}
  <Modal title="Mot de passe réinitialisé" onclose={() => resetResult = null}>
    <p class="muted small">Communiquez ce mot de passe temporaire à <strong>{resetResult.username}</strong>. Il ne sera plus affiché ensuite.</p>
    <div class="pwd-box">{resetResult.password}</div>
    {#snippet actions()}
      <button class="btn-primary" onclick={() => resetResult = null}>Fermer</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .access-denied { color: var(--color-muted); }

  /* Le formulaire de création d'utilisateur dispose 2 colonnes */
  .form-row.grid2 { display: grid; grid-template-columns: 1fr 1fr; }

  .role-picker { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
  .role-picker.col { flex-direction: column; align-items: stretch; gap: 8px; }
  .role-label { color: var(--color-muted); font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; }
  .role-check { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text); text-transform: none; letter-spacing: 0; cursor: pointer; }
  .role-parent { font-size: 11px; color: var(--color-muted); }

  tr.disabled td { opacity: 0.5; }
  .username { font-weight: 500; }
  .roles { display: flex; gap: 4px; flex-wrap: wrap; }
  .steam { color: var(--color-muted); font-size: 12px; }
  .status { font-size: 12px; font-weight: 500; color: var(--color-muted); }
  .status.active { color: var(--color-success); }
  .actions { display: flex; gap: 6px; justify-content: flex-end; }

  /* Badges de rôle (compacts, en capitales) */
  .badge { font-size: 10px; padding: 2px 7px; text-transform: uppercase; letter-spacing: 0.4px; }
  .badge-system  { background: rgba(180, 80, 220, 0.15); color: #b450dc; }
  .badge-admin   { background: rgba(224, 92, 92, 0.15);  color: var(--color-danger); }
  .badge-gn      { background: rgba(79, 110, 247, 0.15); color: var(--color-primary); }
  .badge-sp      { background: rgba(76, 175, 130, 0.15); color: var(--color-success); }
  .badge-default { background: var(--color-border);      color: var(--color-muted); }

  .pwd-box {
    font-family: monospace; font-size: 18px; letter-spacing: 1px;
    text-align: center; padding: 12px;
    background: var(--color-bg); border: 1px solid var(--color-border);
    border-radius: var(--radius); user-select: all;
  }
</style>
