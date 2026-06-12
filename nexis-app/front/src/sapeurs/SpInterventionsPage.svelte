<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {realtime} from '../shared/realtime.js'
    import {currentUser} from '../shared/stores.js'

    let interventions = $state([])
  let vehicules     = $state([])
  let natures       = $state([])
  let loading       = $state(true)
  let error         = $state('')

  let isAdmin      = $derived($currentUser?.roles?.includes('ROLE_ADMIN_SP') ?? false)
  let isDispatcher = $derived($currentUser?.roles?.includes('ROLE_SP_DISPATCH') ?? false)

  // Contexte du détail (changement de statut + note par l'équipage + CRI)
  let statuts      = $state([])
  let affectations = $state([])
  let myMembreId   = $state(null)
  let noteText     = $state('')
  let cris         = $state([])
  let editing      = $state(false)
  let editForm     = $state({})

  // Création
  let showCreate  = $state(false)
  let form        = $state({ motif: '', natureId: '', requerant: '', telephone: '', observation: '', commune: '', coordonnees: '', nbVictimes: '', incendie: false, vehiculeImplique: false })
  let createSel   = $state([])
  let createError = $state('')

  // Renfort
  let renfortFor  = $state(null)
  let renfortSel  = $state([])

  // Détail (double-clic)
  let detailInter   = $state(null)
  let detailJournal = $state([])

  let reloadTimer = null

  let sorted = $derived([...interventions].sort((a, b) =>
    a.enCours === b.enCours ? (new Date(b.debut) - new Date(a.debut)) : (a.enCours ? -1 : 1)
  ))

  // Recherche / archive — par défaut, on n'affiche que les interventions en cours
  let filtreStatut = $state('EN_COURS')   // TOUTES | EN_COURS | CLOTUREES
  let recherche    = $state('')
  let affichees = $derived(sorted.filter(i => {
    if (filtreStatut === 'EN_COURS' && !i.enCours) return false
    if (filtreStatut === 'CLOTUREES' && i.enCours) return false
    const q = recherche.trim().toLowerCase()
    if (!q) return true
    return [i.code, i.motif, i.nature?.code, i.nature?.label, i.commune]
      .filter(Boolean).some(s => s.toLowerCase().includes(q))
  }))

  onMount(() => {
    load()
    return realtime.on(ev => {
      if (ev.faction === 'SP' && (ev.type?.startsWith('INTERVENTION_') || ev.type === 'ETAT_VEHICULE'
          || ev.type === 'AFFECTATION' || ev.type === 'DESAFFECTATION' || ev.type === 'MAIN_COURANTE')) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(() => detailInter ? refreshDetail() : load(), 300)
      }
    })
  })

  async function load() {
    loading = true; error = ''
    try {
      ;[interventions, vehicules, natures] = await Promise.all([
        api.get('/sp/interventions'),
        api.get('/sp/vehicules/engageables'),   // disponibles + équipage requis
        api.get('/sp/natures'),
      ])
    } catch (e) { error = e.message }
    finally { loading = false }
  }

  function fmt(iso) { return iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—' }
  function fmtCoord(c) { return c && c.length === 6 ? c.slice(0, 3) + ' ' + c.slice(3) : (c || '—') }
  // Saisie coordonnées : chiffres uniquement (max 6), espace ajouté après 3 chiffres pour la lecture
  function coordDisplay(raw) { return raw.length > 3 ? raw.slice(0, 3) + ' ' + raw.slice(3) : raw }
  function onCoordInput(e) { form.coordonnees = e.target.value.replace(/\D/g, '').slice(0, 6) }
  function toggle(list, id) { return list.includes(id) ? list.filter(x => x !== id) : [...list, id] }

  async function submitCreate(e) {
    e.preventDefault(); createError = ''
    if (!form.motif.trim()) { createError = 'Motif requis'; return }
    if (!form.natureId) { createError = 'La nature est obligatoire'; return }
    if (!avertirNonArmes(createSel)) return
    if (!(await avertirDesaffectation(createSel))) return
    try {
      await api.post('/sp/interventions', {
        motif: form.motif,
        natureId: form.natureId,
        requerant: form.requerant || null,
        telephone: form.telephone || null,
        observation: form.observation || null,
        commune: form.commune || null,
        coordonnees: form.coordonnees || null,
        nbVictimes: form.nbVictimes !== '' ? Number(form.nbVictimes) : null,
        incendie: form.incendie,
        vehiculeImplique: form.vehiculeImplique,
        vehiculeIds: createSel,
      })
      form = { motif: '', natureId: '', requerant: '', telephone: '', observation: '', commune: '', coordonnees: '', nbVictimes: '', incendie: false, vehiculeImplique: false }
      createSel = []; showCreate = false
      await load()
    } catch (e) { createError = e.message }
  }

  function vehiculesHors(inter) {
    return vehicules.filter(v => !inter.engins.some(e => e.vehiculeId === v.vehiculeId))
  }

  // Proposition : véhicule armé ET dont le type est tagué pour la nature choisie
  function estPropose(v) { return v.arme && (v.natureIds ?? []).includes(form.natureId) }
  let engageablesTries = $derived([...vehicules].sort((a, b) =>
    (estPropose(b) ? 1 : 0) - (estPropose(a) ? 1 : 0) ||
    (b.arme ? 1 : 0) - (a.arme ? 1 : 0) ||
    a.libelle.localeCompare(b.libelle)
  ))
  function avertirNonArmes(ids) {
    const nonArmes = vehicules.filter(v => ids.includes(v.vehiculeId) && !v.arme)
    if (nonArmes.length === 0) return true
    return window.confirm(`⚠ ${nonArmes.map(v => v.libelle).join(', ')} non armé(s) (poste obligatoire non couvert).\nEngager quand même ?`)
  }

  // Au déclenchement, les effectifs sur poste NON obligatoire des engins seront désaffectés :
  // on prévient le dispatcher avec la liste avant de valider.
  async function avertirDesaffectation(ids) {
    if (!ids || ids.length === 0) return true
    let preview = []
    try { preview = await api.post('/sp/interventions/preview-desaffectation', { vehiculeIds: ids }) }
    catch { preview = [] }
    if (!preview || preview.length === 0) return true
    const lignes = preview.map(p => `• ${p.gradeCode} ${p.nom} — ${p.fonction} (${p.vehicule})`).join('\n')
    return window.confirm(`Au déclenchement, ces effectifs sur un poste NON obligatoire seront désaffectés :\n\n${lignes}\n\nDéclencher quand même ?`)
  }

  async function submitRenfort(inter) {
    if (renfortSel.length === 0) return
    if (!avertirNonArmes(renfortSel)) return
    try {
      await api.post(`/sp/interventions/${inter.id}/engins`, { vehiculeIds: renfortSel })
      renfortFor = null; renfortSel = []
      await load()
    } catch (e) { error = e.message }
  }

  async function cloturer(inter) {
    if (!window.confirm(`Clôturer l'intervention « ${inter.motif} » ?`)) return
    try { await api.put(`/sp/interventions/${inter.id}/cloture`); await load() }
    catch (e) { error = e.message }
  }

  async function openDetail(inter) {
    detailInter = inter; noteText = ''; editing = false
    const [j, st, aff, me, c] = await Promise.all([
      api.get(`/sp/interventions/${inter.id}/journal`).catch(() => []),
      api.get('/sp/vehicules/statuts').catch(() => []),
      api.get('/sp/affectations').catch(() => []),
      api.get('/sp/membres/me').catch(() => null),
      api.get(`/sp/interventions/${inter.id}/cri`).catch(() => []),
    ])
    detailJournal = j; statuts = st; affectations = aff; myMembreId = me?.id ?? null; cris = c
  }

  async function refreshDetail() {
    if (!detailInter) return
    const id = detailInter.id
    await load()
    detailInter = interventions.find(i => i.id === id) ?? null
    if (detailInter) {
      ;[detailJournal, cris] = await Promise.all([
        api.get(`/sp/interventions/${id}/journal`).catch(() => []),
        api.get(`/sp/interventions/${id}/cri`).catch(() => []),
      ])
    }
  }

  // ── Édition de l'intervention (dispatcher/admin) ───────────────────────────
  function startEdit() {
    editForm = {
      motif: detailInter.motif, natureId: detailInter.nature?.id ?? '',
      requerant: detailInter.requerant ?? '', telephone: detailInter.telephone ?? '',
      observation: detailInter.observation ?? '', commune: detailInter.commune ?? '',
      coordonnees: detailInter.coordonnees ?? '',
    }
    editing = true
  }
  async function submitEdit() {
    try {
      await api.patch(`/sp/interventions/${detailInter.id}`, {
        motif: editForm.motif, natureId: editForm.natureId || null,
        requerant: editForm.requerant, telephone: editForm.telephone,
        observation: editForm.observation, commune: editForm.commune, coordonnees: editForm.coordonnees,
      })
      editing = false
      await refreshDetail()
    } catch (e) { error = e.message }
  }

  async function retirerEngin(engin) {
    if (!window.confirm(`Retirer ${engin.libelle} de l'intervention ?`)) return
    try { await api.delete(`/sp/interventions/${detailInter.id}/engins/${engin.vehiculeId}`); await refreshDetail() }
    catch (e) { error = e.message }
  }

  // ── CRI (compte rendu) ─────────────────────────────────────────────────────
  function canEditCri(cri) {
    return cri.statut !== 'VALIDE'
        && (isAdmin || affectations.some(a => a.vehiculeId === cri.vehiculeId && a.membreId === myMembreId))
  }
  async function criSave(cri) {
    try { await api.put(`/sp/cri/${cri.id}`, { contenu: cri.contenu }); await refreshDetail() }
    catch (e) { error = e.message }
  }
  async function criSoumettre(cri) {
    try { await api.put(`/sp/cri/${cri.id}/soumettre`); await refreshDetail() }
    catch (e) { error = e.message }
  }
  async function criValider(cri) {
    try { await api.put(`/sp/cri/${cri.id}/valider`); await refreshDetail() }
    catch (e) { error = e.message }
  }
  const CRI_LABEL = { BROUILLON: 'Brouillon', SOUMIS: 'Soumis', VALIDE: 'Validé' }

  // ── Renforts GN / VINCI (éditable par tous) ────────────────────────────────
  const RENFORT_OPTS = [['NON_PREVENU', 'Non prévenu'], ['PREVENU', 'Prévenu'], ['SUR_PLACE', 'Sur place']]
  function renfortLabel(v) { return (RENFORT_OPTS.find(o => o[0] === v) ?? [, v])[1] }
  async function changeRenfort(cible, statut) {
    try {
      await api.put(`/sp/interventions/${detailInter.id}/renfort`,
        cible === 'GN' ? { renfortGn: statut } : { renfortVinci: statut })
      await refreshDetail()
    } catch (e) { error = e.message }
  }

  // ── Export PDF (impression navigateur) ─────────────────────────────────────
  function exportPdf() {
    const i = detailInter
    const esc = s => (s ?? '').toString().replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))
    const row = (k, v) => `<tr><th>${k}</th><td>${esc(v) || '—'}</td></tr>`
    const engins = i.engins.map(e => `${esc(e.libelle)} <span class="muted">(${esc(e.etatLabel)})</span>`).join(' · ') || '—'
    const mc = detailJournal.map(ev =>
      `<tr><td class="mono">${fmt(ev.creeLe)}</td><td>${esc(ev.message)}</td><td class="muted">${esc(ev.acteurUsername)}</td></tr>`).join('')
    const crisH = cris.map(c =>
      `<div class="cri"><h4>${esc(c.vehiculeLibelle)} — ${CRI_LABEL[c.statut] ?? c.statut}</h4>
        <p>${esc(c.contenu) || '<span class="muted">(vide)</span>'}</p>
        ${c.validePar ? `<p class="muted">Validé par ${esc(c.validePar)}</p>` : ''}</div>`).join('') || '<p class="muted">Aucun</p>'

    const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${esc(i.code)}</title>
      <style>
        * { font-family: Arial, Helvetica, sans-serif; color: #1a1a1a; }
        body { margin: 28px; font-size: 12px; }
        h1 { font-size: 20px; margin: 0 0 4px; }
        .sub { color: #666; margin: 0 0 16px; }
        h3 { font-size: 13px; border-bottom: 1px solid #ccc; padding-bottom: 3px; margin: 18px 0 8px; }
        table { width: 100%; border-collapse: collapse; }
        .meta th { text-align: left; width: 130px; color: #666; font-weight: 600; padding: 3px 8px 3px 0; vertical-align: top; }
        .meta td { padding: 3px 0; }
        .mc th, .mc td { text-align: left; border-bottom: 1px solid #eee; padding: 4px 6px; font-size: 11px; }
        .mono { font-family: monospace; white-space: nowrap; }
        .muted { color: #888; }
        .cri { border: 1px solid #ddd; border-radius: 6px; padding: 8px 10px; margin-bottom: 8px; }
        .cri h4 { margin: 0 0 4px; font-size: 12px; }
        .cri p { margin: 0; white-space: pre-wrap; }
        footer { margin-top: 24px; color: #999; font-size: 10px; }
        @media print { body { margin: 12mm; } }
      </style></head><body>
      <h1>${esc(i.code)} — ${esc(i.motif)}</h1>
      <p class="sub">${i.enCours ? 'En cours' : 'Clôturée'} · ${i.nature ? esc(i.nature.label) : ''}</p>
      <table class="meta">
        ${row('Début', fmt(i.debut))}
        ${row('Fin', i.fin ? fmt(i.fin) : '—')}
        ${row('Requérant', i.requerant)}
        ${row('Téléphone', i.telephone)}
        ${row('Commune', i.commune)}
        ${row('Coordonnées', fmtCoord(i.coordonnees))}
        ${row('Victimes', i.nbVictimes ?? '—')}
        ${row('Incendie', i.incendie ? 'Oui' : 'Non')}
        ${row('Véhicule impliqué', i.vehiculeImplique ? 'Oui' : 'Non')}
        ${row('Renfort GN', renfortLabel(i.renfortGn))}
        ${row('Renfort VINCI', renfortLabel(i.renfortVinci))}
        ${row('Observation', i.observation)}
        ${row('Créée par', i.creePar)}
      </table>
      <h3>Engins</h3><p>${engins}</p>
      <h3>Main courante</h3>
      <table class="mc"><tbody>${mc || '<tr><td class="muted">Aucun événement</td></tr>'}</tbody></table>
      <h3>Comptes rendus (CRI)</h3>${crisH}
      <footer>Nexis — fiche d'intervention ${esc(i.code)} · exportée le ${new Date().toLocaleString('fr-FR')}</footer>
      </body></html>`

    const w = window.open('', '_blank')
    if (!w) { error = 'Autorisez les pop-ups pour exporter en PDF.'; return }
    w.document.write(html); w.document.close(); w.focus()
    setTimeout(() => w.print(), 300)
  }

  // L'utilisateur connecté est-il équipier de cet engin ? (ou admin)
  function canControl(engin) {
    return isAdmin || affectations.some(a => a.vehiculeId === engin.vehiculeId && a.membreId === myMembreId)
  }
  // Peut ajouter une note : équipier d'au moins un engin de l'intervention (ou admin)
  let canNote = $derived(detailInter && (isAdmin
      || detailInter.engins.some(e => affectations.some(a => a.vehiculeId === e.vehiculeId && a.membreId === myMembreId))))
  // Transition avant uniquement : statut courant + suivants
  function statutOptions(engin) { return statuts.filter(s => s.position >= engin.statutPosition) }

  async function changeEnginStatut(engin, statutId) {
    if (!statutId || statutId === engin.statutId) return
    try { await api.put(`/sp/vehicules/${engin.vehiculeId}/statut?statutId=${statutId}`); await refreshDetail() }
    catch (e) { error = e.message }
  }

  async function addNote() {
    if (!noteText.trim()) return
    try { await api.post(`/sp/interventions/${detailInter.id}/journal`, { message: noteText }); noteText = ''; await refreshDetail() }
    catch (e) { error = e.message }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Interventions — Sapeurs-Pompiers</h2>
    <button class="btn-primary" onclick={() => { showCreate = !showCreate; createError = '' }}>
      {showCreate ? 'Annuler' : 'Nouvelle intervention'}
    </button>
  </div>

  {#if loading}
    <p class="muted">Chargement…</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <div class="filtres">
      <div class="seg">
        <button class="seg-btn" class:on={filtreStatut === 'TOUTES'} onclick={() => filtreStatut = 'TOUTES'}>Toutes</button>
        <button class="seg-btn" class:on={filtreStatut === 'EN_COURS'} onclick={() => filtreStatut = 'EN_COURS'}>En cours</button>
        <button class="seg-btn" class:on={filtreStatut === 'CLOTUREES'} onclick={() => filtreStatut = 'CLOTUREES'}>Clôturées</button>
      </div>
      <input class="search" type="search" bind:value={recherche} placeholder="Rechercher (code, motif, nature, commune)…" />
    </div>
    <div class="list">
      {#each affichees as i (i.id)}
        <div class="card" class:closed={!i.enCours}>
          <div class="i-head" ondblclick={() => openDetail(i)} role="button" tabindex="0" title="Double-clic pour le détail">
            <div class="i-main">
              <span class="badge" class:badge-actif={i.enCours} class:badge-inactif={!i.enCours}>
                {i.enCours ? 'En cours' : 'Clôturée'}
              </span>
              <span class="i-code">{i.code}</span>
              {#if i.nature}<span class="chip-code">{i.nature.code}</span>{/if}
              <span class="i-motif">{i.motif}</span>
            </div>
            <span class="i-time">{fmt(i.debut)}{#if i.fin} → {fmt(i.fin)}{/if}</span>
          </div>

          <div class="engins">
            {#each i.engins as e (e.vehiculeId)}
              <span class="engin" style="border-left:3px solid {e.etatCouleur}">
                {e.libelle} <span class="chip-code">{e.typeCode}</span>
              </span>
            {/each}
            {#if i.engins.length === 0}<span class="muted small">Aucun engin</span>{/if}
          </div>

          <div class="i-actions">
            <button class="btn-ghost-sm" onclick={() => openDetail(i)}>Détail</button>
            {#if i.enCours}
              {#if renfortFor === i.id}
                <div class="renfort">
                  {#each vehiculesHors(i) as v (v.vehiculeId)}
                    <label class="veh-check">
                      <input type="checkbox" checked={renfortSel.includes(v.vehiculeId)} onchange={() => renfortSel = toggle(renfortSel, v.vehiculeId)} />
                      {v.libelle} <span class="chip-code">{v.typeCode}</span>
                      {#if !v.arme}<span class="non-arme">non armé</span>{/if}
                    </label>
                  {/each}
                  {#if vehiculesHors(i).length === 0}<span class="muted small">Tous les engins sont déjà engagés</span>{/if}
                  <div class="renfort-actions">
                    <button class="btn-ghost-sm" onclick={() => { renfortFor = null; renfortSel = [] }}>Annuler</button>
                    <button class="btn-primary" disabled={renfortSel.length === 0} onclick={() => submitRenfort(i)}>Engager (bip)</button>
                  </div>
                </div>
              {:else}
                <button class="btn-ghost-sm" onclick={() => { renfortFor = i.id; renfortSel = [] }}>+ Renfort</button>
                {#if isDispatcher}
                  <button class="btn-ghost-sm" onclick={() => cloturer(i)} title="Clôture forcée (dispatcher)">Clôturer (forcer)</button>
                {/if}
              {/if}
            {/if}
          </div>
        </div>
      {/each}
      {#if affichees.length === 0}
        <p class="muted">{interventions.length === 0 ? 'Aucune intervention' : 'Aucun résultat'}</p>
      {/if}
    </div>
  {/if}
</div>

<!-- ── Modale : nouvelle intervention (fermeture par la croix uniquement) ────── -->
{#if showCreate}
  <div class="backdrop">
    <div class="modal create-modal">
      <button class="modal-x" title="Fermer" onclick={() => showCreate = false}>✕</button>
      <h3>Nouvelle intervention</h3>
      {#if createError}<p class="inline-error">{createError}</p>{/if}
      <form class="create-modal-form" onsubmit={submitCreate}>
        <div class="form-row">
          <label>Nature *
            <select bind:value={form.natureId} required>
              <option value="" disabled>— choisir —</option>
              {#each natures as n (n.id)}<option value={n.id}>{n.code} · {n.label}</option>{/each}
            </select>
          </label>
          <label>Motif<input type="text" bind:value={form.motif} placeholder="ex: Feu d'habitation" required /></label>
        </div>
        <div class="form-row">
          <label>Requérant<input type="text" bind:value={form.requerant} maxlength="40" /></label>
          <label>Téléphone<input type="tel" bind:value={form.telephone} maxlength="10" placeholder="10 chiffres" /></label>
          <label>Commune<input type="text" bind:value={form.commune} maxlength="40" /></label>
          <label>Coordonnées<input type="text" inputmode="numeric" maxlength="7" value={coordDisplay(form.coordonnees)} oninput={onCoordInput} placeholder="ex: 060 150" /></label>
        </div>
        <label class="full">Observation<input type="text" bind:value={form.observation} placeholder="Précisions / description" /></label>

        <!-- Arbre décision / qualification de l'appel -->
        <div class="qualif">
          <span class="pick-label">Qualification</span>
          <div class="qualif-row">
            <label class="q-vic">Nb victimes<input type="number" min="0" bind:value={form.nbVictimes} placeholder="0" /></label>
            <label class="q-chk"><input type="checkbox" bind:checked={form.incendie} /> Incendie</label>
            <label class="q-chk"><input type="checkbox" bind:checked={form.vehiculeImplique} /> Véhicule impliqué</label>
          </div>
        </div>

        <div class="veh-pick">
          <span class="pick-label">Engins engagés</span>
          <div class="veh-grid">
            {#each engageablesTries as v (v.vehiculeId)}
              <label class="veh-check" class:propose={estPropose(v)}>
                <input type="checkbox" checked={createSel.includes(v.vehiculeId)} onchange={() => createSel = toggle(createSel, v.vehiculeId)} />
                {v.libelle} <span class="chip-code">{v.typeCode}</span>
                {#if estPropose(v)}<span class="propose-tag">proposé</span>{/if}
                {#if !v.arme}<span class="non-arme">non armé</span>{/if}
              </label>
            {/each}
          </div>
          {#if vehicules.length === 0}<span class="muted small">Aucun véhicule</span>{/if}
        </div>
        <div class="modal-actions">
          <button type="submit" class="btn-primary">Déclencher l'intervention</button>
        </div>
      </form>
    </div>
  </div>
{/if}

<!-- ── Détail intervention + main courante ──────────────────────────────────── -->
{#if detailInter}
  <div class="backdrop" onclick={() => detailInter = null}>
    <div class="modal wide" onclick={e => e.stopPropagation()}>
      <div class="detail-head">
        <h3>{detailInter.code} — {detailInter.motif}</h3>
        {#if isDispatcher && detailInter.enCours && !editing}
          <button class="btn-ghost-sm" onclick={startEdit}>Éditer</button>
        {/if}
      </div>

      {#if editing}
        <div class="edit-form">
          <div class="form-row">
            <label>Nature
              <select bind:value={editForm.natureId} required>
                {#each natures as n (n.id)}<option value={n.id}>{n.code} · {n.label}</option>{/each}
              </select>
            </label>
            <label>Motif<input type="text" bind:value={editForm.motif} required /></label>
          </div>
          <div class="form-row">
            <label>Requérant<input type="text" bind:value={editForm.requerant} maxlength="40" /></label>
            <label>Téléphone<input type="tel" bind:value={editForm.telephone} maxlength="10" /></label>
            <label>Commune<input type="text" bind:value={editForm.commune} maxlength="40" /></label>
            <label>Coordonnées<input type="text" inputmode="numeric" maxlength="7" value={coordDisplay(editForm.coordonnees)} oninput={e => editForm.coordonnees = e.target.value.replace(/\D/g, '').slice(0, 6)} /></label>
          </div>
          <label class="full">Observation<input type="text" bind:value={editForm.observation} /></label>
          <div class="modal-actions">
            <button class="btn-ghost-sm" onclick={() => editing = false}>Annuler</button>
            <button class="btn-primary" onclick={submitEdit}>Enregistrer</button>
          </div>
        </div>
      {:else}
      <div class="detail-grid">
        <div><span class="dl">Statut</span> {detailInter.enCours ? 'En cours' : 'Clôturée'}</div>
        <div><span class="dl">Nature</span> {detailInter.nature ? detailInter.nature.label : '—'}</div>
        <div><span class="dl">Début</span> {fmt(detailInter.debut)}</div>
        <div><span class="dl">Fin</span> {fmt(detailInter.fin)}</div>
        <div><span class="dl">Requérant</span> {detailInter.requerant ?? '—'}</div>
        <div><span class="dl">Téléphone</span> {detailInter.telephone ?? '—'}</div>
        <div><span class="dl">Commune</span> {detailInter.commune ?? '—'}</div>
        <div><span class="dl">Coordonnées</span> {fmtCoord(detailInter.coordonnees)}</div>
        <div><span class="dl">Victimes</span> {detailInter.nbVictimes ?? '—'}</div>
        <div><span class="dl">Incendie</span> {detailInter.incendie ? 'Oui' : 'Non'}</div>
        <div><span class="dl">Véhicule impliqué</span> {detailInter.vehiculeImplique ? 'Oui' : 'Non'}</div>
        <div class="full"><span class="dl">Observation</span> {detailInter.observation ?? '—'}</div>
        <div class="full"><span class="dl">Créée par</span> {detailInter.creePar ?? '—'}</div>
      </div>
      {/if}

      <!-- Renforts GN / VINCI (éditable par tous) -->
      <div class="mc">
        <span class="dl">Renforts</span>
        <div class="renfort-rows">
          <div class="renfort-row">
            <span class="renfort-cible">Gendarmerie</span>
            {#if detailInter.enCours}
              <select value={detailInter.renfortGn} onchange={e => changeRenfort('GN', e.target.value)}>
                {#each RENFORT_OPTS as [v, l]}<option value={v}>{l}</option>{/each}
              </select>
            {:else}<span>{renfortLabel(detailInter.renfortGn)}</span>{/if}
          </div>
          <div class="renfort-row">
            <span class="renfort-cible">VINCI</span>
            {#if detailInter.enCours}
              <select value={detailInter.renfortVinci} onchange={e => changeRenfort('VINCI', e.target.value)}>
                {#each RENFORT_OPTS as [v, l]}<option value={v}>{l}</option>{/each}
              </select>
            {:else}<span>{renfortLabel(detailInter.renfortVinci)}</span>{/if}
          </div>
        </div>
      </div>

      <!-- Engins + statut (modifiable par l'équipage) -->
      <div class="mc">
        <span class="dl">Engins</span>
        <div class="eng-rows">
          {#each detailInter.engins as e (e.vehiculeId)}
            <div class="eng-row">
              <span class="eng-dot" style="background:{e.etatCouleur}"></span>
              <span class="eng-name">{e.libelle}</span>
              {#if detailInter.enCours && canControl(e)}
                <select class="eng-statut-sel" value={e.statutId} onchange={ev => changeEnginStatut(e, ev.target.value)}>
                  {#each statutOptions(e) as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
                </select>
              {:else}
                <span class="eng-statut" style="color:{e.etatCouleur}">{e.etatLabel}</span>
              {/if}
              {#if detailInter.enCours && isDispatcher && detailInter.engins.length > 1}
                <button class="rm-btn" title="Retirer l'engin" onclick={() => retirerEngin(e)}>×</button>
              {/if}
            </div>
          {/each}
          {#if detailInter.engins.length === 0}<span class="muted small">Aucun engin</span>{/if}
        </div>
      </div>

      <div class="mc">
        <span class="dl">Main courante</span>
        <div class="mc-list">
          {#each detailJournal as ev (ev.id)}
            <div class="mc-line"><span class="mono">{fmt(ev.creeLe)}</span><span>{ev.message}</span>{#if ev.acteurUsername}<span class="muted">{ev.acteurUsername}</span>{/if}</div>
          {/each}
          {#if detailJournal.length === 0}<span class="muted small">Aucun événement</span>{/if}
        </div>
        {#if detailInter.enCours && canNote}
          <div class="mc-add">
            <input type="text" bind:value={noteText} placeholder="Ajouter une note…"
                   onkeydown={e => { if (e.key === 'Enter') { e.preventDefault(); addNote() } }} />
            <button class="btn-ghost-sm" disabled={!noteText.trim()} onclick={addNote}>Ajouter</button>
          </div>
        {/if}
      </div>

      <!-- Comptes rendus (CRI) : 1 par engin, soumis par l'équipage, validés par l'admin -->
      <div class="mc">
        <span class="dl">Comptes rendus (CRI)</span>
        <div class="cri-list">
          {#each cris as cri (cri.id)}
            <div class="cri-item">
              <div class="cri-head">
                <span class="eng-name">{cri.vehiculeLibelle}</span>
                <span class="cri-badge {cri.statut.toLowerCase()}">{CRI_LABEL[cri.statut] ?? cri.statut}</span>
                {#if cri.statut === 'SOUMIS' && isAdmin}
                  <button class="btn-primary cri-validate" onclick={() => criValider(cri)}>Valider</button>
                {/if}
                {#if cri.validePar}<span class="muted small">par {cri.validePar}</span>{/if}
              </div>
              {#if canEditCri(cri)}
                <textarea rows="2" bind:value={cri.contenu} placeholder="Compte rendu du véhicule…"></textarea>
                <div class="cri-actions">
                  <button class="btn-ghost-sm" onclick={() => criSave(cri)}>Enregistrer</button>
                  {#if cri.statut === 'BROUILLON'}<button class="btn-ghost-sm" onclick={() => criSoumettre(cri)}>Soumettre</button>{/if}
                </div>
              {:else}
                <p class="cri-contenu">{cri.contenu || '—'}</p>
              {/if}
            </div>
          {/each}
          {#if cris.length === 0}<span class="muted small">Aucun engin</span>{/if}
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn-ghost-sm" onclick={exportPdf}>⤓ Exporter PDF</button>
        <button class="btn-ghost-sm" onclick={() => detailInter = null}>Fermer</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .filtres { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
  .seg { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .seg-btn { background: var(--color-surface); border: none; color: var(--color-muted); font-size: 12px; padding: 6px 12px; cursor: pointer; border-right: 1px solid var(--color-border); }
  .seg-btn:last-child { border-right: none; }
  .seg-btn.on { background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); font-weight: 600; }
  .search { flex: 1; min-width: 220px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; }

  .list { display: flex; flex-direction: column; gap: 10px; }
  .card.closed { opacity: 0.7; }

  .i-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; cursor: pointer; }
  .i-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .i-code { font-family: monospace; font-size: 12px; color: var(--accent); font-weight: 700; }
  .i-motif { font-size: 15px; font-weight: 600; }
  .i-time { font-size: 12px; color: var(--color-muted); white-space: nowrap; }

  .engins { display: flex; gap: 8px; flex-wrap: wrap; }
  .engin { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 4px 10px; font-size: 13px; display: inline-flex; gap: 6px; align-items: center; }

  .i-actions { display: flex; gap: 8px; flex-wrap: wrap; }

  .veh-pick, .renfort { display: flex; flex-direction: column; gap: 6px; }
  .pick-label { font-size: 11px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .5px; }
  .veh-check { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }

  /* Modale création */
  .create-modal { width: 640px; max-height: 88vh; overflow-y: auto; position: relative; }
  .modal-x { position: absolute; top: 12px; right: 14px; background: none; border: none; color: var(--color-muted); font-size: 18px; cursor: pointer; line-height: 1; }
  .modal-x:hover { color: var(--color-danger); }
  .create-modal-form { display: flex; flex-direction: column; gap: 14px; margin-top: 4px; }
  .create-modal label { display: flex; flex-direction: column; gap: 5px; font-size: 11px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .4px; }
  .create-modal label.full { width: 100%; }
  .create-modal input, .create-modal select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; text-transform: none; letter-spacing: 0; }
  .create-modal .veh-check { text-transform: none; letter-spacing: 0; flex-direction: row; }
  .veh-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 6px; }
  .renfort { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 10px 12px; width: 100%; }
  .renfort-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 6px; }

  /* Détail */
  .modal.wide { width: 600px; }
  .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; font-size: 13px; }
  .detail-grid .full { grid-column: 1 / -1; }
  .dl { display: block; font-size: 10px; text-transform: uppercase; letter-spacing: .4px; color: var(--color-muted); }
  .mc { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
  .mc-list { display: flex; flex-direction: column; gap: 4px; max-height: 30vh; overflow-y: auto; border: 1px solid var(--color-border); border-radius: var(--radius); padding: 8px; }
  .mc-line { display: flex; gap: 10px; font-size: 12px; align-items: center; }
  .mc-line .mono { font-family: monospace; color: var(--color-muted); white-space: nowrap; }

  .eng-rows { display: flex; flex-direction: column; gap: 6px; }
  .eng-row { display: flex; align-items: center; gap: 10px; font-size: 13px; }
  .eng-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
  .eng-name { flex: 1; }
  .eng-statut { font-size: 12px; font-weight: 600; }
  .eng-statut-sel { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 8px; }

  .mc-add { display: flex; gap: 8px; }
  .mc-add input { flex: 1; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 10px; outline: none; }

  .detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .detail-head h3 { margin: 0; }
  .edit-form { display: flex; flex-direction: column; gap: 10px; margin: 8px 0; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }

  .cri-list { display: flex; flex-direction: column; gap: 8px; }
  .cri-item { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 8px 10px; display: flex; flex-direction: column; gap: 6px; }
  .cri-head { display: flex; align-items: center; gap: 8px; }
  .cri-badge { font-size: 10px; font-weight: 700; border-radius: 8px; padding: 1px 7px; }
  .cri-badge.brouillon { background: color-mix(in srgb, var(--color-muted) 22%, transparent); color: var(--color-muted); }
  .cri-badge.soumis { background: color-mix(in srgb, #e0a23c 22%, transparent); color: #e0a23c; }
  .cri-badge.valide { background: color-mix(in srgb, var(--color-success) 22%, transparent); color: var(--color-success); }
  .cri-validate { margin-left: auto; padding: 2px 10px; font-size: 12px; }
  .cri-item textarea { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; resize: vertical; }
  .cri-actions { display: flex; gap: 8px; }
  .cri-contenu { font-size: 13px; margin: 0; white-space: pre-wrap; }

  .qualif { display: flex; flex-direction: column; gap: 6px; }
  .qualif-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
  .q-vic { display: flex; flex-direction: column; gap: 2px; font-size: 12px; color: var(--color-muted); }
  .q-vic input { width: 80px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 8px; }
  .q-chk { display: flex; align-items: center; gap: 6px; font-size: 13px; }

  .renfort-rows { display: flex; gap: 24px; flex-wrap: wrap; }
  .renfort-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .renfort-cible { font-weight: 600; }
  .renfort-row select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 8px; }

  .veh-check.propose { border-left: 2px solid var(--color-success); padding-left: 5px; border-radius: 2px; }
  .propose-tag { font-size: 9px; font-weight: 700; color: var(--color-success); background: color-mix(in srgb, var(--color-success) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
  .non-arme { font-size: 9px; font-weight: 700; color: var(--color-danger); background: color-mix(in srgb, var(--color-danger) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
</style>
