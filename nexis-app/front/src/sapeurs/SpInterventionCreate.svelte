<script>
  import {onMount} from 'svelte'
  import {api} from '../shared/api.js'
  import {refNatures} from '../shared/referentials.js'
  import {currentUser} from '../shared/stores.js'
  import MapView from '../shared/MapView.svelte'

  // Modal de création d'intervention, réutilisable (écran interventions + bouton rapide dispatch).
  let { onclose, oncreated } = $props()

  let isDispatch = $derived(($currentUser?.roles ?? []).some(r => r === 'ROLE_SP_DISPATCH' || r === 'ROLE_ADMIN_SP'))
  let affecterAutoApresDepart = $state(true)

  let natures   = $state([])
  let vehicules = $state([])
  let centres   = $state([])
  let hopitaux  = $state([])
  let form      = $state({ motif: '', natureId: '', requerant: '', telephone: '', observation: '', commune: '', coordonnees: '', nbVictimes: '', incendie: false, vehiculeImplique: false })
  let createSel = $state([])
  let createError = $state('')

  onMount(async () => {
    ;[vehicules, natures, centres, hopitaux] = await Promise.all([
      api.get('/sp/vehicules/engageables').catch(() => []),
      refNatures().catch(() => []),
      api.get('/sp/centres').catch(() => []),
      api.get('/sp/hopitaux').catch(() => []),
    ])
  })

  function coordDisplay(raw) { return raw.length > 3 ? raw.slice(0, 3) + ' ' + raw.slice(3) : raw }
  function onCoordInput(e) { form.coordonnees = e.target.value.replace(/\D/g, '').slice(0, 6) }
  function toggle(list, id) { return list.includes(id) ? list.filter(x => x !== id) : [...list, id] }

  // Proposition par pertinence : armé + nature principale = nature de l'intervention, puis
  // armé + nature secondaire, puis armé, puis le reste.
  function matchPrincipal(v)  { return form.natureId && v.arme && v.naturePrincipaleId === form.natureId }
  function matchSecondaire(v) { return form.natureId && v.arme && !matchPrincipal(v) && (v.natureIds ?? []).includes(form.natureId) }
  function rang(v) {
    if (matchPrincipal(v))  return 0
    if (matchSecondaire(v)) return 1
    if (v.arme)             return 2
    return 3
  }
  let engageablesTries = $derived([...vehicules].sort((a, b) =>
    rang(a) - rang(b) || a.libelle.localeCompare(b.libelle)
  ))
  // Engins proposés (nature principale/secondaire) vs autres ; détails repliables.
  let proposes   = $derived(engageablesTries.filter(v => matchPrincipal(v) || matchSecondaire(v)))
  let autres     = $derived(engageablesTries.filter(v => !matchPrincipal(v) && !matchSecondaire(v)))
  let showAutres = $state(false)
  let openAutres = $derived(showAutres || proposes.length === 0)
  // Recherche dans « Autres engins » (la liste peut être longue : tous les engins).
  let vehSearch  = $state('')
  let autresFiltres = $derived(vehSearch.trim()
    ? autres.filter(v => `${v.libelle} ${v.typeCode}`.toLowerCase().includes(vehSearch.trim().toLowerCase()))
    : autres)

  // « Engager le lot » : présélectionne les engins du template de la nature (souple : avertit si manque).
  async function engagerLot() {
    if (!form.natureId) return
    createError = ''
    const tpl = await api.get(`/sp/natures/${form.natureId}/template`).catch(() => [])
    if (!tpl.length) { createError = 'Aucun lot de départ défini pour cette nature.'; return }
    const sel = [...createSel]
    const manques = []
    for (const ligne of tpl) {
      const dispo = engageablesTries.filter(v => v.typeId === ligne.vehiculeTypeId && !sel.includes(v.vehiculeId))
      const pris = dispo.slice(0, ligne.quantite)
      pris.forEach(v => sel.push(v.vehiculeId))
      if (pris.length < ligne.quantite) manques.push(`${ligne.typeLabel} ${pris.length}/${ligne.quantite}`)
    }
    createSel = sel
    if (manques.length) createError = `Lot partiel — indisponible : ${manques.join(', ')}. Tu peux déclencher quand même.`
  }
  function avertirNonArmes(ids) {
    const nonArmes = vehicules.filter(v => ids.includes(v.vehiculeId) && !v.arme)
    if (nonArmes.length === 0) return true
    return window.confirm(`⚠ ${nonArmes.map(v => v.libelle).join(', ')} non armé(s) (poste obligatoire non couvert).\nEngager quand même ?`)
  }
  async function avertirDesaffectation(ids) {
    if (!ids || ids.length === 0) return true
    let preview = []
    try { preview = await api.post('/sp/interventions/preview-desaffectation', { vehiculeIds: ids }) }
    catch { preview = [] }
    if (!preview || preview.length === 0) return true
    const lignes = preview.map(p => `• ${p.gradeCode} ${p.nom} — ${p.fonction} (${p.vehicule})`).join('\n')
    return window.confirm(`Au déclenchement, ces effectifs sur un poste NON obligatoire seront désaffectés :\n\n${lignes}\n\nDéclencher quand même ?`)
  }

  async function submitCreate(e) {
    e.preventDefault(); createError = ''
    if (!form.motif.trim()) { createError = 'Motif requis'; return }
    if (!form.natureId) { createError = 'La nature est obligatoire'; return }
    if (!avertirNonArmes(createSel)) return
    if (!(await avertirDesaffectation(createSel))) return
    try {
      const created = await api.post('/sp/interventions', {
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
      // Affectation auto de l'équipage de garde — uniquement sur les engins NON armés
      // (un engin déjà armé garde son équipage en place).
      if (isDispatch && affecterAutoApresDepart) {
        const nonArmes = new Set(vehicules.filter(v => !v.arme).map(v => v.vehiculeId))
        await Promise.all((created?.engins ?? [])
          .filter(e => nonArmes.has(e.vehiculeId))
          .map(e => api.post(`/sp/vehicules/${e.vehiculeId}/affecter-auto`).catch(() => null)))
      }
      oncreated?.()
      onclose()
    } catch (err) { createError = err.message }
  }
</script>

<div class="backdrop">
  <div class="modal create-modal">
    <button class="modal-x" title="Fermer" onclick={onclose}>✕</button>
    <h3>Nouvelle intervention</h3>
    {#if createError}<p class="inline-error">{createError}</p>{/if}
    {#snippet enginRow(v)}
      <label class="veh-check" class:propose={matchPrincipal(v)} class:propose-sec={matchSecondaire(v)}>
        <input type="checkbox" checked={createSel.includes(v.vehiculeId)} onchange={() => createSel = toggle(createSel, v.vehiculeId)} />
        {v.libelle} <span class="chip-code">{v.typeCode}</span>
        {#if matchPrincipal(v)}<span class="propose-tag">★ proposé</span>
        {:else if matchSecondaire(v)}<span class="propose-tag sec">proposé</span>{/if}
        {#if !v.arme}<span class="non-arme">non armé</span>{/if}
      </label>
    {/snippet}

    <form class="create-modal-form" onsubmit={submitCreate}>
      <div class="cm-cols">
        <!-- Colonne gauche : formulaire -->
        <div class="cm-form">
          <div class="sec-alerte">
            <label class="big">Nature *
              <select bind:value={form.natureId} required>
                <option value="" disabled>— choisir —</option>
                {#each natures as n (n.id)}<option value={n.id}>{n.code} · {n.label}</option>{/each}
              </select>
            </label>
            <label class="big">Motif *<input type="text" bind:value={form.motif} placeholder="ex: Feu d'habitation" required /></label>
          </div>
          {#if form.natureId}
            <button type="button" class="lot-btn" onclick={engagerLot}>⚡ Engager le lot de départ</button>
          {/if}

          <!-- Détails (toujours visibles) -->
          <div class="sec-body">
            <span class="sec-h">Appelant</span>
            <div class="form-row">
              <label>Requérant<input type="text" bind:value={form.requerant} maxlength="40" /></label>
              <label>Téléphone<input type="tel" bind:value={form.telephone} maxlength="10" placeholder="10 chiffres" /></label>
            </div>
            <span class="sec-h">Localisation</span>
            <div class="form-row">
              <label>Commune<input type="text" bind:value={form.commune} maxlength="40" /></label>
              <label>Coordonnées<input type="text" inputmode="numeric" maxlength="7" value={coordDisplay(form.coordonnees)} oninput={onCoordInput} placeholder="ex: 060 150" /></label>
            </div>
            <label class="full">Observation<input type="text" bind:value={form.observation} placeholder="Précisions / description" /></label>
            <span class="sec-h">Qualification</span>
            <div class="qualif-row">
              <label class="q-vic">Nb victimes<input type="number" min="0" bind:value={form.nbVictimes} placeholder="0" /></label>
              <label class="q-chk"><input type="checkbox" bind:checked={form.incendie} /> Incendie</label>
              <label class="q-chk"><input type="checkbox" bind:checked={form.vehiculeImplique} /> Véhicule impliqué</label>
            </div>
          </div>

          <!-- Engins -->
          <div class="veh-pick">
            <span class="sec-h">Engins proposés</span>
            {#if proposes.length > 0}
              <div class="veh-grid">
                {#each proposes as v (v.vehiculeId)}{@render enginRow(v)}{/each}
              </div>
            {:else}
              <span class="muted small">{form.natureId ? 'Aucun engin proposé pour cette nature.' : 'Choisis une nature pour la proposition.'}</span>
            {/if}

            <div class="autres-head">
              <button type="button" class="sec-toggle" onclick={() => showAutres = !showAutres}>
                <span class="caret">{openAutres ? '▾' : '▸'}</span> Autres engins ({autresFiltres.length})
              </button>
              {#if openAutres && autres.length > 6}
                <input class="veh-search" type="text" placeholder="filtrer…" bind:value={vehSearch} />
              {/if}
            </div>
            {#if openAutres}
              <div class="veh-grid">
                {#each autresFiltres as v (v.vehiculeId)}{@render enginRow(v)}{/each}
              </div>
              {#if vehicules.length === 0}<span class="muted small">Aucun véhicule</span>{/if}
              {#if autres.length > 0 && autresFiltres.length === 0}<span class="muted small">Aucun engin ne correspond à « {vehSearch} »</span>{/if}
            {/if}
          </div>
        </div>

        <!-- Colonne droite : carte (repère visuel + clic = coordonnées) -->
        <div class="cm-map">
          <span class="pick-label">Localisation <span class="hint">— clic = coordonnées {#if form.coordonnees.length === 6}({coordDisplay(form.coordonnees)}){/if}</span></span>
          <div class="cm-map-box">
            <MapView height="100%" centres={centres} hopitaux={hopitaux}
                     interventions={form.coordonnees.length === 6 ? [{ coordonnees: form.coordonnees, motif: 'Localisation', nature: { code: '📍' } }] : []}
                     oncoordpick={c => form.coordonnees = c} />
          </div>
        </div>
      </div>

      <div class="modal-actions">
        {#if isDispatch}
          <label class="auto-chk"><input type="checkbox" bind:checked={affecterAutoApresDepart} /> Affecter auto l'équipage de garde</label>
        {/if}
        <button type="submit" class="btn-primary">Déclencher l'intervention</button>
      </div>
    </form>
  </div>
</div>

<style>
  .create-modal { width: 880px; max-width: 94vw; max-height: 90vh; overflow-y: auto; position: relative; }

  /* 2 colonnes : formulaire (gauche) + carte (droite, repère visuel) */
  .cm-cols { display: flex; gap: 16px; align-items: stretch; }
  .cm-form { flex: 1.1; min-width: 0; display: flex; flex-direction: column; gap: 14px; }
  .cm-map  { flex: 0.9; min-width: 0; display: flex; flex-direction: column; gap: 5px; }
  .cm-map-box { flex: 1; min-height: 340px; }
  .autres-head { display: flex; align-items: center; gap: 10px; }
  .veh-search { flex: 1; max-width: 220px; }
  @media (max-width: 760px) {
    .cm-cols { flex-direction: column; }
    .cm-map-box { min-height: 240px; }
  }
  .modal-x { position: absolute; top: 12px; right: 14px; background: none; border: none; color: var(--color-muted); font-size: 18px; cursor: pointer; line-height: 1; }
  .modal-x:hover { color: var(--color-danger); }
  .create-modal-form { display: flex; flex-direction: column; gap: 14px; margin-top: 4px; }
  .create-modal label { display: flex; flex-direction: column; gap: 5px; font-size: 11px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .4px; }
  .create-modal label.full { width: 100%; }
  .create-modal input, .create-modal select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; text-transform: none; letter-spacing: 0; }
  .create-modal .veh-check { text-transform: none; letter-spacing: 0; flex-direction: row; align-items: center; gap: 8px; }
  .form-row { display: flex; gap: 10px; flex-wrap: wrap; }
  .form-row label { flex: 1; min-width: 140px; }
  .veh-pick { display: flex; flex-direction: column; gap: 6px; }
  .veh-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 6px; }
  .veh-check { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }

  .auto-chk { margin-right: auto; display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text); }
  .lot-btn { align-self: flex-start; background: color-mix(in srgb, var(--accent) 14%, transparent); color: var(--accent); border: 1px solid color-mix(in srgb, var(--accent) 45%, transparent); border-radius: var(--radius); font-size: 12px; font-weight: 600; padding: 5px 12px; cursor: pointer; }
  .sec-alerte { display: flex; gap: 10px; flex-wrap: wrap; }
  .sec-alerte .big { flex: 1; min-width: 180px; }
  .create-modal .big { font-size: 12px; font-weight: 700; color: var(--color-text); }
  .create-modal .big input, .create-modal .big select { font-size: 15px; padding: 9px 11px; }
  .sec-toggle { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 12px; font-weight: 600; text-align: left; padding: 2px 0; }
  .caret { display: inline-block; width: 12px; color: var(--color-muted); }
  .sec-body { display: flex; flex-direction: column; gap: 10px; border-left: 2px solid var(--color-border); padding-left: 12px; }
  .sec-h { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: var(--color-muted); }

  .qualif-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
  .q-vic { display: flex; flex-direction: column; gap: 2px; font-size: 12px; color: var(--color-muted); }
  .q-vic input { width: 80px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 8px; }
  .q-chk { display: flex; align-items: center; gap: 6px; font-size: 13px; }

  .veh-check.propose { border-left: 2px solid var(--color-success); padding-left: 5px; border-radius: 2px; }
  .veh-check.propose-sec { border-left: 2px solid var(--accent); padding-left: 5px; border-radius: 2px; }
  .propose-tag { font-size: 9px; font-weight: 700; color: var(--color-success); background: color-mix(in srgb, var(--color-success) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
  .propose-tag.sec { color: var(--accent); background: color-mix(in srgb, var(--accent) 16%, transparent); }
  .non-arme { font-size: 9px; font-weight: 700; color: var(--color-danger); background: color-mix(in srgb, var(--color-danger) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
</style>
