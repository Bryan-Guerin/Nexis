<script>
// @ts-nocheck

    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {confirm} from '../shared/confirm.js'
    import {toast} from '../shared/toasts.js'
    import {currentUser} from '../shared/stores.js'
    import Skeleton from '../shared/Skeleton.svelte'
    import Modal from '../shared/Modal.svelte'

    // ── Données ──────────────────────────────────────────────────────────────
  let membres   = $state([])
  let grades    = $state([])
  let fonctions = $state([])   // catalogue des fonctions = des qualifications
  let fonctionsOrga = $state([])   // catalogue des fonctions d'organigramme (RH, chef…)
  let profilRp      = $state(null) // profil RP du membre sélectionné (XP, niveau, badges)
  let users     = $state([])
  let loading   = $state(true)

  let isAdmin = $derived($currentUser?.roles?.includes('ROLE_ADMIN_SP') ?? false)
  let isRh    = $derived($currentUser?.roles?.includes('ROLE_SP_RH') ?? false)
  let canManageNotation = $derived(isAdmin || isRh)
  // Le RH (et l'admin) peut éditer les informations d'un membre (radiation/création restent admin).
  let canEditInfos = $derived(isAdmin || isRh)

  // Notations du membre sélectionné
  let notations = $state([])
  let notOpen   = $state(false)
  function moisCourant() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` }
  function emptyNotForm() { return { mois: moisCourant(), comportementDiscipline: 3, competencesTechniques: 3, aptitudePhysique: 3, initiativeAutonomie: 3, espritEquipe: 3, respectSecurite: 3, observations: '', objectifs: '' } }
  let notForm  = $state(emptyNotForm())
  let notError = $state('')

  const NOTATION_CRITERES = [
    ['comportementDiscipline', 'Comportement & discipline'],
    ['competencesTechniques', 'Compétences techniques'],
    ['aptitudePhysique', 'Aptitude physique'],
    ['initiativeAutonomie', 'Initiative & autonomie'],
    ['espritEquipe', "Esprit d'équipe"],
    ['respectSecurite', 'Respect des consignes de sécurité'],
  ]

  function canSeeNotation(m) {
    return m && (canManageNotation || m.username === $currentUser?.username)
  }
  async function loadNotations(m) {
    notations = []
    if (!canSeeNotation(m)) return
    const path = canManageNotation ? `/sp/rh/membres/${m.id}/notations` : '/sp/membres/me/notations'
    notations = await api.get(path).catch(() => [])
  }
  async function submitNotation() {
    notError = ''
    try {
      await api.post(`/sp/rh/membres/${selectedId}/notations`, notForm)
      notOpen = false; notForm = emptyNotForm()
      await loadNotations(selected)
    } catch (e) { notError = e.message }
  }
  function fmtDate(iso) { return iso ? new Date(iso).toLocaleDateString('fr-FR', { dateStyle: 'medium' }) : '—' }

  // ── Relances (RH/admin) ─────────────────────────────────────────────────────
  let relances         = $state([])   // du membre sélectionné
  let relancesOuvertes = $state([])   // vue d'ensemble → indicateur dans la liste
  let relForm          = $state({ texte: '', echeance: '' })
  let relError         = $state('')
  const todayISO = new Date().toISOString().slice(0, 10)
  let relanceMembreIds  = $derived(new Set(relancesOuvertes.map(r => r.membreId)))
  let relanceOverdueIds = $derived(new Set(relancesOuvertes.filter(r => r.echeance && r.echeance < todayISO).map(r => r.membreId)))

  async function loadRelancesOuvertes() { relancesOuvertes = await api.get('/sp/rh/relances/ouvertes').catch(() => []) }
  async function loadRelancesMembre(m) {
    relances = canManageNotation && m ? await api.get(`/sp/rh/membres/${m.id}/relances`).catch(() => []) : []
  }
  async function createRelance() {
    relError = ''
    if (!relForm.texte.trim()) { relError = 'Texte requis'; return }
    try {
      await api.post(`/sp/rh/membres/${selectedId}/relances`, { texte: relForm.texte, echeance: relForm.echeance || null })
      relForm = { texte: '', echeance: '' }
      await loadRelancesMembre(selected); await loadRelancesOuvertes()
    } catch (e) { relError = e.message }
  }
  async function relanceFaite(r) {
    try { await api.put(`/sp/rh/relances/${r.id}/fait`); await loadRelancesMembre(selected); await loadRelancesOuvertes() }
    catch (e) { relError = e.message }
  }
  async function supprimerRelance(r) {
    if (!await confirm({ title: 'Supprimer la relance', message: 'Supprimer cette relance ?', danger: true })) return
    try { await api.delete(`/sp/rh/relances/${r.id}`); await loadRelancesMembre(selected); await loadRelancesOuvertes() }
    catch (e) { relError = e.message }
  }

  // ── Sanctions (RH/admin) ────────────────────────────────────────────────────
  let sanctions = $state([])
  let sancForm  = $state({ type: '', motif: '' })   // date = aujourd'hui (serveur)
  let sancError = $state('')
  async function loadSanctions(m) {
    sanctions = canManageNotation && m ? await api.get(`/sp/rh/membres/${m.id}/sanctions`).catch(() => []) : []
  }
  async function createSanction() {
    sancError = ''
    if (!sancForm.motif.trim())   { sancError = 'Motif requis'; return }
    try {
      await api.post(`/sp/rh/membres/${selectedId}/sanctions`,
        { type: sancForm.type || null, motif: sancForm.motif })
      sancForm = { type: '', motif: '' }
      await loadSanctions(selected)
    } catch (e) { sancError = e.message }
  }
  async function supprimerSanction(s) {
    if (!await confirm({ title: 'Supprimer la sanction', message: 'Supprimer cette sanction ?', danger: true })) return
    try { await api.delete(`/sp/rh/sanctions/${s.id}`); await loadSanctions(selected) }
    catch (e) { sancError = e.message }
  }

  // ── Recherche + filtre contrat + tri / sélection ───────────────────────────
  let recherche = $state('')
  let filtreContrat = $state('')   // '' = tous, 'SPP' | 'SPV'
  let triBy = $state('grade')       // 'grade' | 'nom' | 'matricule'
  // L'indice de grade vient du référentiel `grades`.
  let gradeOrdre = $derived(new Map(grades.map(g => [g.id, g.ordre ?? 0])))
  let membresFiltres = $derived(
    membres
      .filter(m => {
        if (filtreContrat && m.contrat !== filtreContrat) return false
        const q = recherche.trim().toLowerCase()
        if (!q) return true
        return [m.matricule, m.username, m.nomComplet, m.grade, m.contrat].filter(Boolean).some(s => s.toLowerCase().includes(q))
      })
      .sort((a, b) => {
        if (triBy === 'nom')       return (a.nomComplet || a.username).localeCompare(b.nomComplet || b.username, 'fr')
        if (triBy === 'matricule') return String(a.matricule).localeCompare(String(b.matricule), 'fr', { numeric: true })
        // grade décroissant (plus gradé en premier), puis nom
        return (gradeOrdre.get(b.gradeId) ?? 0) - (gradeOrdre.get(a.gradeId) ?? 0)
            || (a.nomComplet || a.username).localeCompare(b.nomComplet || b.username, 'fr')
      })
  )
  let selectedId = $state(null)
  // Détail chargé à la demande (GET dédié) — toujours frais, indépendant de la liste.
  let selected   = $state(null)
  let detailLoading = $state(false)

  // Seul RH / ADMIN peut gérer les qualifications.
  let canManageQuals = $derived(isAdmin || isRh)

  // Qualifications du membre sélectionné (safe — jamais undefined)
  let selectedQuals = $derived(selected?.qualifications ?? [])

  // Casiers disponibles (0-30 hors ceux déjà pris)
  let takenCasiers      = $derived(new Set(membres.map(m => m.numeroCasier)))
  let freeCasiers       = $derived(Array.from({length: 31}, (_, i) => i).filter(n => !takenCasiers.has(n)))
  let casierEditOptions = $derived(Array.from({length: 31}, (_, i) => i).filter(n => !takenCasiers.has(n) || n === selected?.numeroCasier))

  // Sections collapsibles dans le détail
  let qualifsOpen = $state(true)
  let fctsOrgaOpen = $state(true)

  // Mode de visualisation : 'detail' (split sélection+détail) | 'annuaire' (table) | 'organigramme' (arbre)
  let vueMode = $state('detail')
  function switchVue(mode) { vueMode = mode }

  // Arbre organigramme : construit depuis fonctionsOrga (racines = sans parent).
  let racinesOrga = $derived(fonctionsOrga.filter(f => !f.parentId))
  function enfantsDe(parentId) { return fonctionsOrga.filter(f => f.parentId === parentId) }
  function membresPourFonction(fId) {
    return membres.filter(m => (m.fonctionsOrga ?? []).some(f => f.id === fId))
  }
  function membresSansFonction() {
    return membres.filter(m => !(m.fonctionsOrga ?? []).length)
  }

  // Toggle d'une fonction d'organigramme sur le membre sélectionné (cumul possible).
  async function toggleFonctionOrga(fonctionId) {
    if (!selected) return
    const current = (selected.fonctionsOrga ?? []).map(f => f.id)
    const next = current.includes(fonctionId) ? current.filter(x => x !== fonctionId) : [...current, fonctionId]
    try {
      const updated = await api.put(`/sp/membres/${selected.id}/fonctions-orga`, { fonctionOrgaIds: next })
      refreshMembre(updated)   // maj liste (annuaire/organigramme) + détail
    } catch { /* toast par api.js */ }
  }

  // ── État inline d'édition (admin) ────────────────────────────────────────
  let editName        = $state(false)
  let editNameVal     = $state('')
  let editTel         = $state(false)
  let editTelVal      = $state('')
  let editGrade       = $state(false)
  let editGradeId     = $state('')
  let editContrat     = $state(false)
  let editCasier      = $state(false)
  let editCasierVal   = $state(0)
  let saveError       = $state('')

  // ── Qualifications ─────────────────────────────────────────────────────────
  let addQualOpen     = $state(false)
  let addQualFonction = $state('')

  // ── Modal création membre ────────────────────────────────────────────────
  let showCreate   = $state(false)
  let createForm   = $state({ userId: '', gradeId: '', contrat: 'SPV', numeroCasier: 0, nomComplet: '', telephone: '' })
  let createError  = $state('')
  let showNewUser  = $state(false)
  let newUserForm  = $state({ username: '', password: '' })
  let newUserError = $state('')

  onMount(() => { loadAll(); if (canManageNotation) loadRelancesOuvertes() })

  async function loadAll() {
    loading = true
    try {
      const [mem, g, u, f, fo] = await Promise.all([
        api.get('/sp/membres/grade'),
        api.get('/sp/grades'),
        // /admin/users est réservé ROLE_SYSTEM : appel silencieux (pas de toast « accès refusé »).
        api.get('/admin/users', { silent: true }).catch(() => []),
        api.get('/sp/fonctions'),
        api.get('/sp/fonctions-orga').catch(() => []),
      ])
      membres = mem; grades = g; users = u; fonctions = f; fonctionsOrga = fo
      // À l'arrivée : ouvre par défaut la fiche de l'utilisateur courant (si présent).
      if (!selectedId) {
        const moi = membres.find(m => m.username === $currentUser?.username)
        if (moi) select(moi)
      }
    } catch { /* toast par api.js */ }
    finally { loading = false }
  }

  // ── Sélection ────────────────────────────────────────────────────────────
  async function select(m) {
    selectedId   = m.id
    editName     = false
    editTel      = false
    editGrade    = false
    editContrat  = false
    editCasier   = false
    addQualOpen  = false
    saveError    = ''
    qualifsOpen  = true
    notOpen      = false
    notError     = ''
    relError     = ''
    relForm      = { texte: '', echeance: '' }
    // Charge le détail à la demande (back) plutôt que de réutiliser la liste.
    detailLoading = true
    try {
      selected = await api.get(`/sp/membres/${m.id}`)
    } catch (e) {
      selected = m   // repli sur la donnée de liste
      saveError = e.message
    } finally {
      detailLoading = false
    }
    loadNotations(selected)
    loadRelancesMembre(selected)
    loadSanctions(selected)
    loadProfilRp(selected?.id)
  }

  async function loadProfilRp(membreId) {
    if (!membreId) { profilRp = null; return }
    profilRp = await api.get(`/sp/membres/${membreId}/profil-rp`).catch(() => null)
  }

  // Suivi des badges dévoilés en live (pour déclencher l'animation confetti une fois).
  let revealedBadgeIds = $state(new Set())

  async function decouvrirBadge(badgeId) {
    try {
      await api.put(`/sp/membres/me/badges/${badgeId}/decouvrir`)
      revealedBadgeIds = new Set([...revealedBadgeIds, badgeId])
      // Met à jour le profil pour que decouvert=true dans le store
      const b = (profilRp?.badges ?? []).find(x => x.badgeId === badgeId)
      if (b) profilRp.badges = profilRp.badges.map(x => x.badgeId === badgeId ? { ...x, decouvert: true } : x)
      // Animation : retrait du flag après ~2 s
      setTimeout(() => {
        const s = new Set(revealedBadgeIds); s.delete(badgeId)
        revealedBadgeIds = s
      }, 2200)
    } catch { /* toast par api.js */ }
  }

  async function evaluerBadges() {
    try {
      const res = await api.post('/sp/badges/eval')
      if (res?.attribues > 0) toast.success(`${res.attribues} nouveau(x) badge(s) attribué(s).`)
      else toast.info('Aucun nouveau badge à attribuer.')
      await loadProfilRp(selectedId)
    } catch { /* toast par api.js */ }
  }

  /** Recharge le détail du membre sélectionné depuis le back + met la liste à jour. */
  async function reloadSelected() {
    if (!selectedId) return
    const updated = await api.get(`/sp/membres/${selectedId}`)
    refreshMembre(updated)
  }

  // ── Sauvegarde admin ─────────────────────────────────────────────────────
  async function saveName() {
    saveError = ''
    try {
      const updated = await api.patch(`/sp/membres/${selectedId}`, { nomComplet: editNameVal })
      refreshMembre(updated)
      editName = false
    } catch (e) { saveError = e.message }
  }

  async function toggleActif() {
    const next = !selected.actif
    const msg = next
      ? `Réintégrer ${selected.nomComplet || selected.username} ? Le compte sera réactivé.`
      : `Radier ${selected.nomComplet || selected.username} ?\nLe compte sera désactivé (login bloqué). L'historique (formations, sanctions, notations) est conservé.`
    if (!await confirm({ title: 'Confirmer', message: msg, danger: true })) return
    saveError = ''
    try {
      const updated = await api.put(`/sp/membres/${selectedId}/actif?actif=${next}`)
      refreshMembre(updated)
    } catch (e) { saveError = e.message }
  }

  async function saveTelephone() {
    saveError = ''
    try {
      const updated = await api.patch(`/sp/membres/${selectedId}`, { telephone: editTelVal })
      refreshMembre(updated)
      editTel = false
    } catch (e) { saveError = e.message }
  }

  async function saveGrade() {
    if (!editGradeId) return
    saveError = ''
    try {
      const updated = await api.patch(`/sp/membres/${selectedId}`, { gradeId: editGradeId })
      refreshMembre(updated)
      editGrade = false
    } catch (e) { saveError = e.message }
  }

  async function saveContrat(val) {
    saveError = ''
    try {
      const updated = await api.patch(`/sp/membres/${selectedId}`, { contrat: val })
      refreshMembre(updated)
      editContrat = false
    } catch (e) { saveError = e.message }
  }

  async function saveCasier() {
    saveError = ''
    try {
      const updated = await api.patch(`/sp/membres/${selectedId}`, { numeroCasier: editCasierVal })
      refreshMembre(updated)
      editCasier = false
    } catch (e) { saveError = e.message }
  }

  function refreshMembre(updated) {
    membres = membres.map(m => m.id === updated.id ? updated : m)
    if (updated.id === selectedId) selected = updated
  }

  // ── Qualifications (fonctions du membre) ───────────────────────────────────
  async function addQualification() {
    if (!addQualFonction) return
    saveError = ''
    try {
      await api.post(`/sp/membres/${selectedId}/qualifications/${addQualFonction}`)
      addQualOpen = false; addQualFonction = ''
      await reloadSelected()
    } catch (e) { saveError = e.message }
  }

  async function removeQualification(fonctionId) {
    saveError = ''
    try {
      await api.delete(`/sp/membres/${selectedId}/qualifications/${fonctionId}`)
      await reloadSelected()
    } catch (e) { saveError = e.message }
  }

  function fonctionLabel(fonctionId) {
    const f = fonctions.find(x => x.id === fonctionId)
    return f ? f.label : '…'
  }

  // ── Création membre ───────────────────────────────────────────────────────
  async function submitNewUser(e) {
    e.preventDefault(); newUserError = ''
    try {
      const created = await api.post('/sp/users', newUserForm)
      users = [...users, created]
      createForm.userId = created.id
      showNewUser = false; newUserForm = { username: '', password: '' }
    } catch (e) { newUserError = e.message }
  }

  async function submitCreate(e) {
    e.preventDefault(); createError = ''
    try {
      const created = await api.post('/sp/membres', createForm)
      membres = [...membres, created]
      showCreate = false
      createForm = { userId: '', gradeId: '', contrat: 'SPV', numeroCasier: freeCasiers[0] ?? 0, nomComplet: '', telephone: '' }
      select(created)
    } catch (e) { createError = e.message }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────
  function openEdit(field) {
    editName = false; editGrade = false; editContrat = false; editCasier = false; editTel = false
    saveError = ''
    if (field === 'name')    { editName = true;    editNameVal  = selected.nomComplet ?? '' }
    if (field === 'tel')     { editTel = true;     editTelVal   = selected.telephone ?? '' }
    if (field === 'grade')   { editGrade = true;   editGradeId  = selected.gradeId }
    if (field === 'contrat') { editContrat = true }
    if (field === 'casier')  { editCasier = true;  editCasierVal = selected.numeroCasier }
  }
</script>

<!-- ═══════════════════════════════════════════════════════════════════════ -->
{#snippet fonctionNode(f)}
  {@const ms = membresPourFonction(f.id)}
  {@const kids = enfantsDe(f.id)}
  <div class="orga-node">
    <div class="orga-node-head">
      <span class="orga-node-ico">{f.icone || '🔹'}</span>
      <span class="orga-node-label">{f.label}</span>
      <span class="orga-node-count">{ms.length}</span>
    </div>
    {#if ms.length > 0}
      <div class="orga-membres">
        {#each ms as m (m.id)}
          <button class="orga-membre" onclick={() => { switchVue('detail'); select(m) }} title={m.nomComplet || m.username}>
            <span class="om-grade">{m.gradeCode}</span>
            <span class="om-name">{m.nomComplet || m.username}</span>
          </button>
        {/each}
      </div>
    {/if}
    {#if kids.length > 0}
      <div class="orga-children">
        {#each kids as c (c.id)}{@render fonctionNode(c)}{/each}
      </div>
    {/if}
  </div>
{/snippet}

<div class="page">

  <!-- ── En-tête ─────────────────────────────────────────────────────────── -->
  <div class="page-header">
    <h2>Effectifs — Sapeurs-Pompiers</h2>
    {#if isAdmin}
      <button class="btn-primary" onclick={() => { createForm = { userId: '', gradeId: '', contrat: 'SPV', numeroCasier: freeCasiers[0] ?? 0, nomComplet: '', telephone: '' }; showCreate = true; createError = ''; showNewUser = false }}>
        + Ajouter un membre
      </button>
    {/if}
  </div>

  <!-- ── Tabs vue ─────────────────────────────────────────────────────────── -->
  <div class="vue-tabs" role="tablist" aria-label="Mode d'affichage des effectifs">
    <button role="tab" aria-selected={vueMode === 'detail'} class:active={vueMode === 'detail'} onclick={() => switchVue('detail')}>👤 Détail</button>
    <button role="tab" aria-selected={vueMode === 'annuaire'} class:active={vueMode === 'annuaire'} onclick={() => switchVue('annuaire')}>📋 Annuaire</button>
    <button role="tab" aria-selected={vueMode === 'organigramme'} class:active={vueMode === 'organigramme'} onclick={() => switchVue('organigramme')}>🌳 Organigramme</button>
  </div>

  {#if loading}
    <Skeleton rows={6} />

  <!-- ═════════ Vue Annuaire (table dense, full-width) ════════════════════════ -->
  {:else if vueMode === 'annuaire'}
    <table class="annuaire">
      <thead>
        <tr>
          <th>Matricule</th><th>Grade</th><th>Nom / Login</th><th>Téléphone</th><th>Contrat</th><th>Fonctions</th><th></th>
        </tr>
      </thead>
      <tbody>
        {#each membresFiltres as m (m.id)}
          <tr>
            <td class="mono" data-label="Matricule">{m.matricule}</td>
            <td data-label="Grade">{m.gradeCode}</td>
            <td data-label="Nom">{m.nomComplet || m.username}</td>
            <td class="mono" data-label="Téléphone">{m.telephone || '—'}</td>
            <td data-label="Contrat">
              <span class="contrat-pill" class:spp={m.contrat === 'SPP'} class:spv={m.contrat === 'SPV'}>{m.contrat}</span>
            </td>
            <td data-label="Fonctions">
              {#each (m.fonctionsOrga ?? []) as f (f.id)}
                <span class="fonc-chip" title={f.label}>{f.icone || '•'} {f.label}</span>
              {/each}
              {#if !(m.fonctionsOrga ?? []).length}<span class="muted small">—</span>{/if}
            </td>
            <td class="actions">
              <button class="btn-ghost-sm" onclick={() => { switchVue('detail'); select(m) }}>Détail →</button>
            </td>
          </tr>
        {/each}
        {#if membresFiltres.length === 0}
          <tr><td colspan="7" class="empty">{membres.length === 0 ? 'Aucun membre' : 'Aucun résultat'}</td></tr>
        {/if}
      </tbody>
    </table>

  <!-- ═════════ Vue Organigramme (arbre par fonction) ═════════════════════════ -->
  {:else if vueMode === 'organigramme'}
    {#if racinesOrga.length === 0}
      <p class="muted">Aucune fonction d'organigramme configurée. Voir Configuration.</p>
    {:else}
      <div class="orga-tree">
        {#each racinesOrga as r (r.id)}
          {@render fonctionNode(r)}
        {/each}
        {#if membresSansFonction().length > 0}
          <div class="orga-node sans-fonction">
            <div class="orga-node-head">
              <span class="orga-node-ico">👥</span>
              <span class="orga-node-label">Sans fonction</span>
              <span class="orga-node-count">{membresSansFonction().length}</span>
            </div>
            <div class="orga-membres">
              {#each membresSansFonction() as m (m.id)}
                <button class="orga-membre" onclick={() => { switchVue('detail'); select(m) }} title={m.nomComplet || m.username}>
                  <span class="om-grade">{m.gradeCode}</span>
                  <span class="om-name">{m.nomComplet || m.username}</span>
                </button>
              {/each}
            </div>
          </div>
        {/if}
      </div>
    {/if}

  <!-- ═════════ Vue Détail (split sélection + détail, comportement v1.5) ═══════ -->
  {:else}
  <div class="split">

    <!-- ── Liste (gauche) ────────────────────────────────────────────────── -->
    <div class="list-pane">
      <input class="list-search" type="search" bind:value={recherche} placeholder="Rechercher…" />
      <div class="list-tools">
        <select bind:value={filtreContrat} title="Filtre contrat">
          <option value="">Tous contrats</option>
          <option value="SPP">SPP</option>
          <option value="SPV">SPV</option>
        </select>
        <select bind:value={triBy} title="Trier par">
          <option value="grade">Tri : grade</option>
          <option value="nom">Tri : nom</option>
          <option value="matricule">Tri : matricule</option>
        </select>
        <span class="list-count">{membresFiltres.length}</span>
      </div>
      {#each membresFiltres as m (m.id)}
        <button
          class="list-item"
          class:active={m.id === selectedId}
          onclick={() => select(m)}
        >
          <span class="li-mat">{m.gradeCode}</span>
          <span class="li-main">
            <span class="li-name">{m.nomComplet || m.username}</span>
            <span class="li-grade">{m.matricule}{#if m.nomComplet} · {m.username}{/if}</span>
          </span>
          <span class="contrat-pill" class:spp={m.contrat === 'SPP'} class:spv={m.contrat === 'SPV'}>
            {m.contrat}
          </span>
          {#if canManageNotation && relanceMembreIds.has(m.id)}
            <span class="relance-flag" class:overdue={relanceOverdueIds.has(m.id)} title="Relance(s) en attente">⚠</span>
          {/if}
        </button>
      {/each}
      {#if membresFiltres.length === 0}
        <p class="muted" style="padding:16px;font-size:13px">{membres.length === 0 ? 'Aucun membre' : 'Aucun résultat'}</p>
      {/if}
    </div>

    <!-- ── Détail (droite) ───────────────────────────────────────────────── -->
    <div class="detail-pane">
      {#if !selected}
        <div class="empty-detail">
          <p>Sélectionnez un effectif dans la liste</p>
        </div>
      {:else}

        <!-- En-tête fiche -------------------------------------------------- -->
        <div class="detail-header">
          <div class="dh-main">
            <span class="dh-title">{selected.nomComplet || selected.username}</span>
            <span class="contrat-pill lg" class:spp={selected.contrat === 'SPP'} class:spv={selected.contrat === 'SPV'}>
              {selected.contrat === 'SPP' ? 'Professionnel' : 'Volontaire'}
            </span>
            {#if isAdmin}
              <button class="radier-btn" class:reintegrer={!selected.actif} onclick={toggleActif}>
                {selected.actif ? 'Radier' : 'Réintégrer'}
              </button>
            {/if}
          </div>
          <div class="dh-sub">
            <span class="dh-grade">{selected.grade}</span>
            <span class="dh-username">@{selected.username}</span>
            <span class="badge" class:badge-actif={selected.actif} class:badge-inactif={!selected.actif}>
              {selected.actif ? 'Actif' : 'Inactif'}
            </span>
          </div>
        </div>

        {#if saveError}
          <p class="inline-error">{saveError}</p>
        {/if}

        <!-- Profil RP en pleine largeur, AU-DESSUS du split informations/qualifs -->
        {#if profilRp}
          <div class="detail-section">
            <div class="detail-section-head">
              <h3>Profil RP</h3>
              {#if isAdmin}
                <button class="btn-ghost-sm" onclick={evaluerBadges} title="Re-évalue tous les membres">↻ Évaluer badges</button>
              {/if}
            </div>
            <div class="rp-body">
              <div class="rp-xp">
                <div class="rp-xp-head">
                  <span class="rp-niveau">Niveau {profilRp.niveau}</span>
                  <span class="rp-xp-val">{profilRp.xp} XP</span>
                </div>
                <div class="rp-bar"><div class="rp-bar-fill" style="width:{profilRp.progressionPct}%"></div></div>
                <div class="rp-xp-foot muted small">
                  {profilRp.xp - profilRp.xpNiveauActuel} / {profilRp.xpNiveauSuivant - profilRp.xpNiveauActuel} XP avant niveau {profilRp.niveau + 1}
                </div>
              </div>

              <div class="rp-stats">
                <div class="rp-stat"><span class="rp-stat-v">{profilRp.compteurs.interventions}</span><span class="rp-stat-l">interventions</span></div>
                <div class="rp-stat"><span class="rp-stat-v">{profilRp.compteurs.heuresGarde}</span><span class="rp-stat-l">h de garde</span></div>
                <div class="rp-stat"><span class="rp-stat-v">{profilRp.compteurs.joursService}</span><span class="rp-stat-l">jours de service</span></div>
                <div class="rp-stat"><span class="rp-stat-v">{profilRp.compteurs.joursGrade}</span><span class="rp-stat-l">jours dans le grade</span></div>
                <div class="rp-stat"><span class="rp-stat-v">{profilRp.compteurs.qualifications}</span><span class="rp-stat-l">qualifications</span></div>
              </div>

              {#if (profilRp.badges ?? []).length > 0}
                {@const isMine = selected?.username && selected.username === $currentUser?.username}
                <div class="rp-badges">
                  {#each profilRp.badges as b (b.badgeId)}
                    {@const hidden = isMine && !b.decouvert}
                    <button class="rp-badge obtenu" class:masque={hidden}
                            class:revealed={revealedBadgeIds.has(b.badgeId)}
                            title={hidden ? 'Clique pour découvrir ce nouveau badge !' : (b.description || b.condition || '')}
                            disabled={!hidden}
                            onclick={() => hidden && decouvrirBadge(b.badgeId)}>
                      <span class="rp-badge-ico">{hidden ? '❓' : (b.icone || '🏅')}</span>
                      <span class="rp-badge-label">{hidden ? '????' : b.label}</span>
                      {#if revealedBadgeIds.has(b.badgeId)}
                        {#each Array(20) as _, i}
                          <span class="confetti" style="--i:{i}; --c:{['#e05c5c','#4caf82','#4f6ef7','#e8a23a','#b450dc'][i % 5]}"></span>
                        {/each}
                      {/if}
                    </button>
                  {/each}
                </div>
              {:else}
                <p class="muted small">Aucun badge obtenu pour le moment.</p>
              {/if}
            </div>
          </div>
        {/if}

        <!-- Informations (gauche) + Qualifications/Fonctions orga (droite, accordéon) -->
        <div class="detail-cols">

        <div class="col-left">
        <!-- Section informations ------------------------------------------- -->
        <div class="detail-section">
          <h3>Informations</h3>

          <div class="info-grid">

            <!-- Matricule -->
            <div class="info-row">
              <span class="info-label">Matricule</span>
              <span class="info-value mono">{selected.matricule}</span>
            </div>

            <!-- Nom / Prénom -->
            <div class="info-row">
              <span class="info-label">Nom / Prénom</span>
              {#if editName && canEditInfos}
                <div class="inline-edit">
                  <input type="text" maxlength="100" bind:value={editNameVal} placeholder="Nom Prénom" />
                  <button class="btn-save" onclick={saveName}>✓</button>
                  <button class="btn-cancel" onclick={() => editName = false}>✕</button>
                </div>
              {:else}
                <span class="info-value">
                  {selected.nomComplet || '—'}
                  {#if canEditInfos}<button class="btn-edit" onclick={() => openEdit('name')} title="Modifier">✎</button>{/if}
                </span>
              {/if}
            </div>

            <!-- Téléphone -->
            <div class="info-row">
              <span class="info-label">Téléphone</span>
              {#if editTel && canEditInfos}
                <div class="inline-edit">
                  <input type="tel" maxlength="10" bind:value={editTelVal} placeholder="0612345678"
                         oninput={e => editTelVal = e.target.value.replace(/\D/g, '').slice(0, 10)} />
                  <button class="btn-save" onclick={saveTelephone}>✓</button>
                  <button class="btn-cancel" onclick={() => editTel = false}>✕</button>
                </div>
              {:else}
                <span class="info-value">
                  {selected.telephone || '—'}
                  {#if canEditInfos}<button class="btn-edit" onclick={() => openEdit('tel')} title="Modifier">✎</button>{/if}
                </span>
              {/if}
            </div>

            <!-- Grade -->
            <div class="info-row">
              <span class="info-label">Grade</span>
              {#if editGrade && canEditInfos}
                <div class="inline-edit">
                  <select bind:value={editGradeId}>
                    {#each grades as g}<option value={g.id}>{g.label}</option>{/each}
                  </select>
                  <button class="btn-save" onclick={saveGrade}>✓</button>
                  <button class="btn-cancel" onclick={() => editGrade = false}>✕</button>
                </div>
              {:else}
                <span class="info-value">
                  {selected.grade}
                  {#if canEditInfos}
                    <button class="btn-edit" onclick={() => openEdit('grade')} title="Modifier">✎</button>
                  {/if}
                </span>
              {/if}
            </div>

            <!-- Contrat -->
            <div class="info-row">
              <span class="info-label">Contrat</span>
              {#if editContrat && canEditInfos}
                <div class="inline-edit">
                  <button
                    class="contrat-btn"
                    class:active={selected.contrat === 'SPP'}
                    onclick={() => saveContrat('SPP')}
                  >SPP</button>
                  <button
                    class="contrat-btn"
                    class:active={selected.contrat === 'SPV'}
                    onclick={() => saveContrat('SPV')}
                  >SPV</button>
                  <button class="btn-cancel" onclick={() => editContrat = false}>✕</button>
                </div>
              {:else}
                <span class="info-value">
                  {selected.contrat === 'SPP' ? 'Sapeur-Pompier Professionnel' : 'Sapeur-Pompier Volontaire'}
                  {#if canEditInfos}
                    <button class="btn-edit" onclick={() => openEdit('contrat')} title="Modifier">✎</button>
                  {/if}
                </span>
              {/if}
            </div>

            <!-- Numéro de casier -->
            <div class="info-row">
              <span class="info-label">N° casier</span>
              {#if editCasier && canEditInfos}
                <div class="inline-edit">
                  <select bind:value={editCasierVal} style="width:80px">
                    {#each casierEditOptions as n}
                      <option value={n}>{n}</option>
                    {/each}
                  </select>
                  <button class="btn-save" onclick={saveCasier}>✓</button>
                  <button class="btn-cancel" onclick={() => editCasier = false}>✕</button>
                </div>
              {:else}
                <span class="info-value">
                  {selected.numeroCasier}
                  {#if canEditInfos}
                    <button class="btn-edit" onclick={() => openEdit('casier')} title="Modifier">✎</button>
                  {/if}
                </span>
              {/if}
            </div>

            <!-- Date d'intégration -->
            <div class="info-row">
              <span class="info-label">Intégration</span>
              <span class="info-value">{fmtDate(selected.dateIntegration)}</span>
            </div>

            <!-- Dernière promotion -->
            <div class="info-row">
              <span class="info-label">Dern. promotion</span>
              <span class="info-value">{fmtDate(selected.dateDernierePromotion)}</span>
            </div>

          </div>
        </div>

        <!-- Section sanctions (RH / admin) ---------------------------------- -->
        {#if canManageNotation}
          <div class="detail-section">
            <div class="detail-section-head">
              <h3>Sanctions <span class="hab-count">({sanctions.length})</span></h3>
            </div>
            {#if sancError}<p class="inline-error" style="margin:8px 16px">{sancError}</p>{/if}
            <div class="rel-list">
              {#each sanctions as s (s.id)}
                <div class="rel-item">
                  <span class="rel-texte">{#if s.type}<strong>{s.type} — </strong>{/if}{s.motif}</span>
                  <span class="rel-ech">{fmtDate(s.dateSanction)}</span>
                  <button class="rm-btn" title="Supprimer" onclick={() => supprimerSanction(s)}>×</button>
                </div>
              {/each}
              {#if sanctions.length === 0}<p class="muted small">Aucune sanction</p>{/if}
              <div class="rel-add">
                <input type="text" bind:value={sancForm.type} placeholder="Type (ex: Avertissement)" style="max-width:170px" />
                <input type="text" bind:value={sancForm.motif} placeholder="Motif" />
                <button class="btn-ghost-sm" onclick={createSanction}>Ajouter</button>
              </div>
            </div>
          </div>
        {/if}
        </div><!-- /col-left -->

        <div class="col-right">
        <!-- Section qualifications (collapsible) ----------------------------- -->
        <div class="detail-section">
          <button
            class="section-toggle"
            onclick={() => qualifsOpen = !qualifsOpen}
          >
            <h3>Qualifications <span class="hab-count">({selectedQuals.length})</span></h3>
            <span class="chevron" class:open={qualifsOpen}>▾</span>
          </button>

          {#if qualifsOpen}
            <div class="hab-list">
              {#each selectedQuals as q (q.fonctionId)}
                <div class="hab-item">
                  <span class="hab-label">
                    {q.fonctionLabel}
                    <span class="hab-meta">délivré le {fmtDate(q.dateDelivrance)}{#if q.delivrePar} par {q.delivrePar}{/if}</span>
                  </span>
                  {#if canManageQuals}
                    <button class="rm-btn" onclick={() => removeQualification(q.fonctionId)} title="Retirer">×</button>
                  {/if}
                </div>
              {/each}
              {#if selectedQuals.length === 0}
                <p class="muted small">Aucune qualification</p>
              {/if}

              <!-- Ajout qualification (RH / ADMIN uniquement) -->
              {#if !canManageQuals}
                <!-- pas de gestion -->
              {:else if addQualOpen}
                <div class="add-hab-form">
                  <select bind:value={addQualFonction}>
                    <option value="">— choisir une fonction —</option>
                    {#each fonctions.filter(f => !selectedQuals.some(q => q.fonctionId === f.id)) as f (f.id)}
                      <option value={f.id}>{f.label}</option>
                    {/each}
                  </select>
                  <button class="btn-save" onclick={addQualification}>Ajouter</button>
                  <button class="btn-cancel" onclick={() => { addQualOpen = false; addQualFonction = '' }}>Annuler</button>
                </div>
              {:else}
                <button class="btn-ghost-sm" onclick={() => addQualOpen = true}>+ Qualification</button>
              {/if}
            </div>
          {/if}
        </div>

        <!-- Section fonctions d'organigramme (rôles caserne — cumul possible) -->
        <div class="detail-section">
          <button class="section-toggle" onclick={() => fctsOrgaOpen = !fctsOrgaOpen}>
            <h3>Fonctions orga <span class="hab-count">({(selected.fonctionsOrga ?? []).length})</span></h3>
            <span class="chevron" class:open={fctsOrgaOpen}>▾</span>
          </button>
          {#if fctsOrgaOpen}
            {#if fonctionsOrga.length === 0}
              <p class="muted small">Aucune fonction d'organigramme configurée. Voir Configuration.</p>
            {:else}
              <div class="orga-grid">
                {#each fonctionsOrga as f (f.id)}
                  {@const checked = (selected.fonctionsOrga ?? []).some(x => x.id === f.id)}
                  <label class="orga-check" class:on={checked} class:locked={!canEditInfos}>
                    <input type="checkbox" {checked} disabled={!canEditInfos}
                           onchange={() => toggleFonctionOrga(f.id)} />
                    {#if f.icone}<span class="orga-ico">{f.icone}</span>{/if}
                    <span>{f.label}</span>
                  </label>
                {/each}
              </div>
            {/if}
          {/if}
        </div>
        </div><!-- /col-right -->

        </div><!-- /detail-cols -->

        <!-- Section notations (RH/admin + l'effectif concerné) --------------- -->
        {#if canSeeNotation(selected)}
          <div class="detail-section">
            <div class="detail-section-head">
              <h3>Notations <span class="hab-count">({notations.length})</span></h3>
              {#if canManageNotation}
                <button class="btn-ghost-sm" onclick={() => { notOpen = !notOpen; notError = '' }}>{notOpen ? 'Annuler' : '+ Noter'}</button>
              {/if}
            </div>

            {#if notOpen && canManageNotation}
              <div class="not-form">
                {#if notError}<p class="inline-error">{notError}</p>{/if}
                <label class="not-mois">Mois <input type="month" bind:value={notForm.mois} /></label>
                {#each NOTATION_CRITERES as [key, label]}
                  <div class="not-critere">
                    <span class="not-label">{label}</span>
                    <input type="range" min="0" max="5" bind:value={notForm[key]} />
                    <span class="not-val">{notForm[key]}/5</span>
                  </div>
                {/each}
                <label class="not-text">Observations du service
                  <textarea rows="2" bind:value={notForm.observations}></textarea>
                </label>
                <label class="not-text">Objectifs pour le prochain mois
                  <textarea rows="2" bind:value={notForm.objectifs}></textarea>
                </label>
                <button class="btn-primary" onclick={submitNotation}>Enregistrer la notation</button>
              </div>
            {/if}

            <div class="not-list">
              {#each notations as n (n.id)}
                <div class="not-item">
                  <div class="not-item-head">
                    <span class="not-mois-badge">{n.mois}</span>
                    {#each NOTATION_CRITERES as [key, label]}
                      <span class="not-score" title={label}>{label.split(' ')[0]} {n[key]}/5</span>
                    {/each}
                    <span class="not-by">{n.evaluateur ?? ''} · {fmtDate(n.creeLe)}</span>
                  </div>
                  {#if n.observations}<p class="not-obs"><strong>Observations :</strong> {n.observations}</p>{/if}
                  {#if n.objectifs}<p class="not-obs"><strong>Objectifs :</strong> {n.objectifs}</p>{/if}
                </div>
              {/each}
              {#if notations.length === 0}<p class="muted small" style="padding:12px 16px">Aucune notation</p>{/if}
            </div>
          </div>
        {/if}

        <!-- Section relances (RH/admin) : recyclages / compétences à prévoir -->
        {#if canManageNotation}
          <div class="detail-section">
            <div class="detail-section-head"><h3>Relances <span class="hab-count">({relances.filter(r => r.statut === 'OUVERT').length} en attente)</span></h3></div>
            {#if relError}<p class="inline-error" style="margin:8px 16px">{relError}</p>{/if}
            <div class="rel-list">
              {#each relances as r (r.id)}
                <div class="rel-item" class:done={r.statut === 'FAIT'}>
                  <span class="rel-texte">{r.texte}</span>
                  {#if r.echeance}<span class="rel-ech" class:overdue={r.statut === 'OUVERT' && r.echeance < todayISO}>échéance {fmtDate(r.echeance)}</span>{/if}
                  {#if r.statut === 'OUVERT'}
                    <button class="btn-ghost-sm" onclick={() => relanceFaite(r)}>Fait</button>
                  {:else}
                    <span class="rel-badge">Fait{#if r.faitPar} · {r.faitPar}{/if}</span>
                  {/if}
                  <button class="rm-btn" title="Supprimer" onclick={() => supprimerRelance(r)}>×</button>
                </div>
              {/each}
              {#if relances.length === 0}<p class="muted small">Aucune relance</p>{/if}
              <div class="rel-add">
                <input type="text" bind:value={relForm.texte} placeholder="Nouvelle relance (ex: recyclage SAP)" />
                <input type="date" bind:value={relForm.echeance} title="Échéance (optionnel)" />
                <button class="btn-ghost-sm" onclick={createRelance}>Ajouter</button>
              </div>
            </div>
          </div>
        {/if}

      {/if}
    </div>
  </div>
  {/if}
</div>

<!-- ═══════════════════════════════════════════════════════════════ Modal ═ -->
{#if showCreate}
  <Modal title="Nouveau membre SP" onclose={() => showCreate = false}>
      {#if createError}<p class="inline-error">{createError}</p>{/if}

      <form onsubmit={submitCreate} style="display:flex;flex-direction:column;gap:14px">
        <!-- Compte -->
        <label class="field-label">Compte utilisateur
          <div class="user-row">
            <select bind:value={createForm.userId} required>
              <option value="">— choisir —</option>
              {#each users as u}<option value={u.id}>{u.username}</option>{/each}
            </select>
            <button type="button" class="btn-ghost-sm" onclick={() => showNewUser = !showNewUser}>
              {showNewUser ? 'Annuler' : '+ Nouveau compte'}
            </button>
          </div>
        </label>

        {#if showNewUser}
          <div class="sub-form">
            <p class="sub-title">Créer un compte SP</p>
            {#if newUserError}<p class="inline-error">{newUserError}</p>{/if}
            <div class="form-row">
              <label class="field-label">Nom d'utilisateur
                <input type="text" bind:value={newUserForm.username} required />
              </label>
              <label class="field-label">Mot de passe
                <input type="password" bind:value={newUserForm.password} required />
              </label>
            </div>
            <button type="button" class="btn-ghost-sm" onclick={submitNewUser}>Créer le compte</button>
          </div>
        {/if}

        <!-- Nom / Prénom -->
        <label class="field-label">Nom / Prénom <span class="muted small">(optionnel)</span>
          <input type="text" maxlength="100" bind:value={createForm.nomComplet} placeholder="ex: Jean Dupont" />
        </label>

        <!-- Téléphone -->
        <label class="field-label">Téléphone <span class="muted small">(optionnel)</span>
          <input type="tel" maxlength="10" bind:value={createForm.telephone} placeholder="0612345678"
                 oninput={e => createForm.telephone = e.target.value.replace(/\D/g, '').slice(0, 10)} />
        </label>

        <!-- Grade -->
        <label class="field-label">Grade
          <select bind:value={createForm.gradeId} required>
            <option value="">— choisir —</option>
            {#each grades as g}<option value={g.id}>{g.label}</option>{/each}
          </select>
        </label>

        <!-- Contrat -->
        <label class="field-label">Contrat
          <div class="contrat-toggle">
            <button
              type="button"
              class="contrat-btn"
              class:active={createForm.contrat === 'SPV'}
              onclick={() => createForm.contrat = 'SPV'}
            >SPV — Volontaire</button>
            <button
              type="button"
              class="contrat-btn"
              class:active={createForm.contrat === 'SPP'}
              onclick={() => createForm.contrat = 'SPP'}
            >SPP — Professionnel</button>
          </div>
        </label>

        <!-- Numéro de casier -->
        <label class="field-label">N° casier
          <select bind:value={createForm.numeroCasier} required>
            {#each freeCasiers as n}
              <option value={n}>{n}</option>
            {/each}
          </select>
        </label>

        <div class="modal-actions">
          <button type="button" class="btn-ghost-sm" onclick={() => showCreate = false}>Annuler</button>
          <button type="submit" class="btn-primary">Créer le membre</button>
        </div>
      </form>
  </Modal>
{/if}

<style>
  /* ── Mise en page (spécifique à l'écran effectifs) ───────────────────── */
  .page  { display: flex; flex-direction: column; gap: 16px; }

  .split {
    display: flex;
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    overflow: hidden;
    min-height: calc(100vh - 130px);
    flex: 1;
    align-items: flex-start;
  }

  /* ── Liste gauche ────────────────────────────────────────────────────── */
  .list-pane {
    width: 250px;
    flex-shrink: 0;
    overflow-y: auto;
    border-right: 1px solid var(--color-border);
    background: var(--color-surface);
    max-height: calc(100vh - 130px);
    align-self: stretch;
  }
  .list-search { width: calc(100% - 16px); margin: 8px 8px 4px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 6px 9px; outline: none; }
  .list-tools { display: flex; align-items: center; gap: 6px; padding: 0 8px 8px; }
  .list-tools select { flex: 1; min-width: 0; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 11px; padding: 4px 6px; }
  .list-count { font-size: 11px; color: var(--color-muted); flex-shrink: 0; min-width: 22px; text-align: right; }
  .list-item {
    display: flex; align-items: center; gap: 8px;
    width: 100%; padding: 10px 12px;
    background: none; border: none; color: var(--color-text);
    border-bottom: 1px solid var(--color-border);
    border-left: 3px solid transparent;
    cursor: pointer; text-align: left; transition: background .12s;
  }
  .list-item:hover  { background: var(--hover); }
  .list-item.active { background: color-mix(in srgb, var(--accent) 10%, transparent); border-left-color: var(--accent); }
  .li-mat  { font-family: monospace; font-size: 10px; color: var(--color-muted); min-width: 52px; }
  .li-main { display: flex; flex-direction: column; flex: 1; min-width: 0; }
  .li-name { font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .li-grade { font-size: 10px; color: var(--color-muted); }

  /* ── Détail droite ───────────────────────────────────────────────────── */
  .detail-pane { flex: 1; padding: 24px; display: flex; flex-direction: column; gap: 20px; }

  /* Informations + Qualifications côte à côte (notations en pleine largeur dessous) */
  .detail-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; align-items: stretch; }
  /* Colonne gauche = Informations + Sanctions empilées ; colonne droite = Qualifications + Fonctions orga (accordéon, hauteur cappée sur la gauche) */
  .col-left  { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
  .col-right { display: flex; flex-direction: column; gap: 20px; min-width: 0; min-height: 0; }
  /* Sections de la colonne droite : prennent l'espace dispo, scroll interne quand le contenu dépasse */
  .col-right > .detail-section { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
  .col-right > .detail-section > .hab-list,
  .col-right > .detail-section > .orga-grid { overflow-y: auto; min-height: 0; flex: 1 1 auto; }
  @media (max-width: 920px) { .detail-cols { grid-template-columns: 1fr; } .col-right { min-height: 0; } .col-right > .detail-section > .hab-list,
  .col-right > .detail-section > .orga-grid { max-height: 280px; } }
  /* Mobile : split-pane vertical (liste compactée en haut, détail dessous). */
  @media (max-width: 768px) {
    .split { flex-direction: column; min-height: 0; }
    .list-pane { width: 100%; max-height: 200px; border-right: none; border-bottom: 1px solid var(--color-border); }
    .detail-pane { padding: 16px; }
  }
  .empty-detail { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--color-muted); font-size: 13px; }

  .detail-header { display: flex; flex-direction: column; gap: 6px; }
  .dh-main { display: flex; align-items: center; gap: 12px; }
  .dh-title { font-size: 26px; font-weight: 700; letter-spacing: .3px; }
  .radier-btn { margin-left: auto; background: none; border: 1px solid var(--color-danger); color: var(--color-danger); border-radius: var(--radius); font-size: 12px; padding: 4px 12px; cursor: pointer; transition: background .12s; }
  .radier-btn:hover { background: color-mix(in srgb, var(--color-danger) 12%, transparent); }
  .radier-btn.reintegrer { border-color: var(--color-success); color: var(--color-success); }
  .radier-btn.reintegrer:hover { background: color-mix(in srgb, var(--color-success) 12%, transparent); }
  .info-value.mono { font-family: monospace; }
  .dh-sub  { display: flex; align-items: center; gap: 10px; }
  .dh-grade { font-size: 14px; font-weight: 600; color: var(--accent); }
  .dh-username { font-size: 13px; color: var(--color-muted); }

  /* Badges contrat (sémantique propre : SPP=bleu, SPV=vert) */
  .contrat-pill { font-size: 10px; font-weight: 700; letter-spacing: .5px; padding: 2px 7px; border-radius: 8px; white-space: nowrap; }
  .contrat-pill.spp { background: rgba(79,110,247,.15); color: var(--color-primary); }
  .contrat-pill.spv { background: rgba(76,175,130,.15); color: var(--color-success); }
  .contrat-pill.lg  { font-size: 12px; padding: 3px 10px; border-radius: 10px; }

  /* Sections */
  .detail-section { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .detail-section h3 { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .6px; color: var(--color-muted); margin: 0; }
  .section-toggle { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 12px 16px; background: none; border: none; cursor: pointer; border-bottom: 1px solid var(--color-border); transition: background .12s; }
  .section-toggle:hover { background: var(--hover); }
  .chevron { color: var(--color-muted); font-size: 16px; transition: transform .2s; }
  .chevron.open { transform: rotate(0deg); }
  .chevron:not(.open) { transform: rotate(-90deg); }

  /* Grille infos */
  .info-grid { padding: 12px 16px; display: flex; flex-direction: column; gap: 0; }
  .detail-section > .info-grid { padding-top: 12px; }
  .detail-section > h3 { padding: 12px 16px; border-bottom: 1px solid var(--color-border); }
  .info-row { display: flex; align-items: center; gap: 12px; min-height: 36px; border-bottom: 1px solid var(--color-border); padding: 4px 0; }
  .info-row:last-child { border-bottom: none; }
  .info-label { font-size: 11px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .4px; min-width: 100px; }
  .info-value { font-size: 13px; display: flex; align-items: center; gap: 8px; }

  /* Édition inline */
  .inline-edit { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .inline-edit select, .inline-edit input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 8px; outline: none; }
  .inline-edit select:focus, .inline-edit input:focus { border-color: var(--accent); }
  .btn-edit   { background: none; border: none; color: var(--color-muted); cursor: pointer; font-size: 13px; padding: 0 2px; line-height: 1; }
  .btn-edit:hover { color: var(--accent); }
  .btn-save   { background: var(--accent); border: none; border-radius: var(--radius); color: #fff; font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .btn-cancel { background: none; border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-muted); font-size: 12px; padding: 4px 8px; cursor: pointer; }
  .btn-cancel:hover { border-color: var(--color-danger); color: var(--color-danger); }

  /* Boutons contrat */
  .contrat-toggle { display: flex; gap: 8px; margin-top: 4px; }
  .contrat-btn { padding: 5px 14px; border: 1px solid var(--color-border); border-radius: var(--radius); background: none; color: var(--color-muted); font-size: 13px; cursor: pointer; transition: all .15s; }
  .contrat-btn.active { border-color: var(--accent); color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, transparent); font-weight: 600; }

  /* Qualifications */
  .hab-list { padding: 12px 16px; display: flex; flex-direction: column; gap: 8px; }
  .hab-item { display: flex; align-items: center; justify-content: space-between; padding: 7px 10px; background: var(--color-bg); border-radius: var(--radius); border: 1px solid var(--color-border); }
  .hab-label { font-size: 13px; display: flex; flex-direction: column; gap: 2px; }
  .hab-meta { font-size: 10px; color: var(--color-muted); }
  .hab-count { font-weight: 400; font-size: 10px; color: var(--color-muted); }

  /* Notations */
  .detail-section-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--color-border); }
  .detail-section-head h3 { margin: 0; }
  .not-form { padding: 12px 16px; display: flex; flex-direction: column; gap: 10px; border-bottom: 1px solid var(--color-border); }
  .not-mois { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--color-muted); }
  .not-mois input, .not-text textarea { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .not-critere { display: flex; align-items: center; gap: 10px; }
  .not-label { flex: 1; font-size: 13px; }
  .not-critere input[type="range"] { width: 140px; }
  .not-val { font-family: monospace; font-size: 12px; color: var(--accent); min-width: 32px; text-align: right; }
  .not-text { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--color-muted); }
  .not-text textarea { resize: vertical; }
  .not-list { padding: 8px 16px 12px; display: flex; flex-direction: column; gap: 10px; }
  .not-item { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 10px 12px; }
  .not-item-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .not-mois-badge { font-family: monospace; font-weight: 700; color: var(--accent); }
  .not-score { font-size: 11px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 8px; padding: 1px 7px; }
  .not-by { margin-left: auto; font-size: 11px; color: var(--color-muted); }
  .not-obs { font-size: 12px; margin: 6px 0 0; }

  /* Relances */
  .relance-flag { font-size: 12px; color: #e0a23c; }
  .relance-flag.overdue { color: var(--color-danger); }
  .rel-list { padding: 12px 16px; display: flex; flex-direction: column; gap: 8px; }
  .rel-item { display: flex; align-items: center; gap: 10px; font-size: 13px; padding: 6px 8px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .rel-item.done { opacity: .6; }
  .rel-texte { flex: 1; }
  .rel-ech { font-size: 11px; color: var(--color-muted); }
  .rel-ech.overdue { color: var(--color-danger); font-weight: 600; }
  .rel-badge { font-size: 10px; font-weight: 700; color: var(--color-success); }
  .rel-add { display: flex; gap: 8px; margin-top: 4px; }
  .rel-add input[type="text"] { flex: 1; }
  .rel-add input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; padding: 0 4px; cursor: pointer; line-height: 1; }
  .rm-btn:hover { color: var(--color-danger); }
  /* Tabs vue annuaire / organigramme / détail */
  .vue-tabs { display: flex; gap: 6px; border-bottom: 1px solid var(--color-border); padding-bottom: 6px; }
  .vue-tabs button { background: none; border: 1px solid transparent; border-radius: var(--radius); color: var(--color-muted); font-size: 13px; padding: 6px 12px; cursor: pointer; transition: background .12s, color .12s, border-color .12s; }
  .vue-tabs button:hover { color: var(--color-text); background: var(--hover); }
  .vue-tabs button.active { color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); background: color-mix(in srgb, var(--accent) 10%, transparent); font-weight: 600; }

  /* Annuaire (table dense) */
  .annuaire .fonc-chip { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; padding: 2px 8px; border-radius: 10px; background: color-mix(in srgb, var(--accent) 12%, transparent); color: var(--accent); margin: 0 4px 2px 0; white-space: nowrap; }
  .contrat-pill { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 8px; }
  .contrat-pill.spp { background: rgba(79, 110, 247, .18); color: var(--color-primary); }
  .contrat-pill.spv { background: rgba(76, 175, 130, .18); color: var(--color-success); }

  /* Organigramme (arbre) */
  .orga-tree { display: flex; flex-direction: column; gap: 12px; }
  .orga-node { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px; }
  .orga-node-head { display: flex; align-items: center; gap: 8px; }
  .orga-node-ico { font-size: 18px; }
  .orga-node-label { font-weight: 600; font-size: 14px; flex: 1; }
  .orga-node-count { font-size: 11px; color: var(--color-muted); background: var(--color-bg); padding: 2px 8px; border-radius: 10px; }
  .orga-membres { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
  .orga-membre { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 16px; padding: 4px 10px; cursor: pointer; transition: border-color .12s, color .12s; }
  .orga-membre:hover { border-color: var(--accent); color: var(--accent); }
  .om-grade { font-family: monospace; font-size: 10px; color: var(--color-muted); }
  .om-name { font-weight: 500; }
  .orga-children { margin-top: 10px; padding-left: 16px; border-left: 2px solid var(--color-border); display: flex; flex-direction: column; gap: 10px; }
  .orga-node.sans-fonction { border-style: dashed; opacity: .85; }

  /* Profil RP : XP, niveau, badges */
  .rp-body { padding: 12px 16px; display: flex; flex-direction: column; gap: 14px; }
  .rp-xp-head { display: flex; justify-content: space-between; align-items: baseline; }
  .rp-niveau { font-size: 16px; font-weight: 700; color: var(--accent); }
  .rp-xp-val { font-family: monospace; font-size: 13px; color: var(--color-muted); }
  .rp-bar { height: 8px; background: var(--color-bg); border-radius: 4px; overflow: hidden; margin: 6px 0 4px; }
  .rp-bar-fill { height: 100%; background: var(--accent); border-radius: 4px; transition: width .3s; }
  .rp-xp-foot { text-align: right; }
  .rp-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); gap: 8px; }
  .rp-stat { display: flex; flex-direction: column; align-items: center; padding: 8px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .rp-stat-v { font-size: 18px; font-weight: 700; color: var(--accent); }
  .rp-stat-l { font-size: 10px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .4px; }
  .rp-badges { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 6px; }
  .rp-badge { position: relative; display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); opacity: .55; transition: opacity .15s, transform .15s, background .15s; cursor: default; font: inherit; color: inherit; text-align: left; overflow: visible; }
  .rp-badge.obtenu { opacity: 1; border-color: color-mix(in srgb, var(--accent) 55%, var(--color-border)); }
  .rp-badge.masque { cursor: pointer; opacity: 1; border-color: color-mix(in srgb, var(--accent) 65%, transparent); background: color-mix(in srgb, var(--accent) 8%, var(--color-bg)); animation: pulse 1.5s ease-in-out infinite; }
  .rp-badge.masque:hover { transform: scale(1.04); background: color-mix(in srgb, var(--accent) 15%, var(--color-bg)); }
  .rp-badge.revealed { animation: reveal .8s ease-out; border-color: var(--accent); background: color-mix(in srgb, var(--accent) 18%, transparent); }
  .rp-badge-ico { font-size: 18px; }
  .rp-badge-label { flex: 1; font-size: 12px; font-weight: 500; }
  @keyframes pulse { 0%, 100% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent) 35%, transparent); } 50% { box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent) 0%, transparent); } }
  @keyframes reveal { 0% { transform: scale(.6) rotate(-15deg); } 60% { transform: scale(1.15) rotate(5deg); } 100% { transform: scale(1) rotate(0); } }
  /* Confetti : 20 particules colorées giclant depuis le centre du badge */
  .confetti { position: absolute; left: 50%; top: 50%; width: 6px; height: 8px; background: var(--c); border-radius: 1px; pointer-events: none;
              transform: translate(-50%, -50%); animation: confetti-fly 1.2s ease-out forwards;
              animation-delay: calc(var(--i) * 12ms); opacity: 0; }
  @keyframes confetti-fly {
    0%   { opacity: 1; transform: translate(-50%, -50%) rotate(0); }
    100% { opacity: 0;
           transform: translate(calc(-50% + (sin(var(--i)) * 80px)), calc(-50% + (cos(var(--i)) * 80px) - 30px))
                      rotate(720deg); }
  }
  @media (prefers-reduced-motion: reduce) {
    .rp-badge.masque { animation: none; }
    .rp-badge.revealed { animation: none; }
    .confetti { display: none; }
  }

  /* Fonctions orga (RH/Chef/Formateur…) — case à cocher en grille compacte */
  .orga-grid { padding: 12px 16px; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 6px; }
  .orga-check { display: flex; align-items: center; gap: 6px; font-size: 13px; padding: 6px 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); cursor: pointer; transition: border-color .12s, background .12s; }
  .orga-check:hover:not(.locked) { border-color: var(--accent); }
  .orga-check.on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }
  .orga-check.locked { cursor: default; opacity: .8; }
  .orga-check input { margin: 0; flex-shrink: 0; }
  .orga-ico { font-size: 14px; }

  .add-hab-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }
  .add-hab-form select { flex: 1; min-width: 200px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 10px; outline: none; }
  .add-hab-form select:focus { border-color: var(--accent); }
</style>
