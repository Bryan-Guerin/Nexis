<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {realtime} from '../shared/realtime.js'
    import {refStatutsVeh} from '../shared/referentials.js'
    import SpInterventionCreate from './SpInterventionCreate.svelte'
    import MapView from '../shared/MapView.svelte'

    let vehicules    = $state([])
  let membres      = $state([])
  let enServiceIds = $state([])
  let loading      = $state(true)
  let error        = $state('')
  let showCreate   = $state(false)   // création rapide d'intervention
  let showMap      = $state(true)    // carte des interventions en cours
  let interCarte   = $state([])      // interventions en cours (pour la carte)
  let centres      = $state([])      // casernes (repères carte permanents)
  let hopitaux     = $state([])      // hôpitaux (repères carte + destinations transport)
  // Transits : le trait dépend de l'action carte du statut courant de l'engin.
  let transits = $derived.by(() => {
    const vmap = new Map(vehicules.map(v => [v.id, v]))
    const out = []
    for (const i of interCarte) {
      if (!i.coordonnees) continue
      for (const e of (i.engins ?? [])) {
        const v = vmap.get(e.vehiculeId); if (!v) continue
        const id = v.id, couleur = v.statut?.couleur, label = v.libelle, icone = v.type?.icone, dep = v.legDepart
        const action = v.statut?.actionCarte, dest = v.hopitalDestinationCoordonnees, caserne = v.centreCoordonnees, pos = v.positionCoordonnees
        if (action === 'TRANSPORT_HOPITAL') {
          if (dest) out.push({ id, from: i.coordonnees, to: dest, couleur, label, icone, depart: dep })
          else out.push({ id, at: i.coordonnees, couleur, label, icone })   // destination non choisie : garé sur l'inter
        } else if (action === 'SUR_PLACE') {
          out.push({ id, at: dest || i.coordonnees, couleur, label, icone })
        } else if (action === 'RETOUR_CASERNE') {
          const from = pos || dest || i.coordonnees   // origine = dernière position (ex. hôpital)
          if (caserne) out.push({ id, from, to: caserne, couleur, label, icone, depart: dep })
          else out.push({ id, at: from, couleur, label, icone })
        } else if (caserne) {
          out.push({ id, from: caserne, to: i.coordonnees, couleur, label, icone, depart: dep || i.debut })   // en route, ETA animée
        } else {
          out.push({ id, at: i.coordonnees, couleur, label, icone })
        }
      }
    }
    return out
  })

  let enServiceSet     = $derived(new Set(enServiceIds))
  let enServiceMembres = $derived(membres.filter(m => enServiceSet.has(m.id)))

  // Regroupement par catégorie principale (nature principale du type), sections repliables.
  let collapsed = $state({})
  let groupes = $derived(grouperVehicules(vehicules))

  // Compteurs d'état de la flotte (vue instantanée).
  let stats = $derived({
    total:    vehicules.length,
    dispo:    vehicules.filter(v => v.etat?.code === 'DISPONIBLE').length,
    engages:  vehicules.filter(v => (v.equipe ?? []).length > 0).length,
    nonArmes: vehicules.filter(v => !v.arme).length,
  })
  function grouperVehicules(list) {
    const map = new Map()
    for (const v of list) {
      const np = v.type?.naturePrincipale
      const key = np?.id ?? '__autres__'
      // position = ordre de la nature dans la config ; « Autres » (sans nature) en dernier.
      if (!map.has(key)) map.set(key, { key, label: np?.label ?? 'Autres', position: np?.position ?? Infinity, items: [] })
      map.get(key).items.push(v)
    }
    return [...map.values()].sort((a, b) => a.position - b.position)
  }
  function toggleGroupe(key) { collapsed = { ...collapsed, [key]: !collapsed[key] } }

  let reloadTimer = null

  onMount(() => {
    load()
    // Mise à jour live : on recharge sur les événements SP pertinents (throttlé)
    return realtime.on(ev => {
      if (ev.faction === 'SP' && ['AFFECTATION', 'DESAFFECTATION', 'ETAT_VEHICULE'].includes(ev.type)) {
        clearTimeout(reloadTimer)
        reloadTimer = setTimeout(load, 400)
      }
    })
  })

  async function load() {
    loading = true; error = ''
    try {
      let inters
      ;[vehicules, membres, enServiceIds, statuts, inters, centres, hopitaux] = await Promise.all([
        api.get('/sp/dispatch'),
        api.get('/sp/membres?actif=true'),
        api.get('/sp/membres/en-service'),
        refStatutsVeh(),   // référentiel en cache
        api.get('/sp/interventions'),
        api.get('/sp/centres'),
        api.get('/sp/hopitaux'),
      ])
      interCarte = (inters ?? []).filter(i => i.enCours)
    } catch (e) { error = e.message }
    finally { loading = false }
  }

  async function bip(v) {
    try { await api.post(`/sp/vehicules/${v.id}/bip`) }
    catch (e) { error = e.message }
  }

  async function affecterAuto(v) {
    error = ''
    try {
      const ajout = await api.post(`/sp/vehicules/${v.id}/affecter-auto`)
      if (!ajout || ajout.length === 0) error = `Aucun effectif de garde disponible pour armer ${v.libelle}.`
      await load()
    } catch (e) { error = e.message }
  }

  async function desaffecterTout() {
    if (!window.confirm('Désaffecter TOUT le personnel embarqué de tous les véhicules ?')) return
    error = ''
    try { await api.put('/sp/affectations/desaffecter-tout'); await load() }
    catch (e) { error = e.message }
  }

  // ── Armement / engagement d'un véhicule ─────────────────────────────────────
  let engageVeh   = $state(null)
  let postes      = $state([])
  let activeAff   = $state([])
  let statuts     = $state([])
  let addSel      = $state({})        // posteId -> membreId sélectionné
  let engageError = $state('')

  let engageCurrent = $derived(engageVeh ? (vehicules.find(x => x.id === engageVeh.id) ?? engageVeh) : null)
  // Double affectation autorisée : on n'exclut que les effectifs déjà sur CE véhicule
  // (un effectif peut être engagé sur un autre véhicule, ex. FSR + VTU).
  let busyMembreIds = $derived(new Set(
    activeAff.filter(a => engageVeh && a.vehiculeId === engageVeh.id).map(a => a.membreId)
  ))

  async function openEngage(v) {
    engageVeh = v; engageError = ''; addSel = {}
    await loadEngageData()
  }
  function closeEngage() { engageVeh = null }

  async function loadEngageData() {
    if (!engageVeh) return
    try {
      ;[postes, activeAff, statuts] = await Promise.all([
        api.get(`/sp/vehicules/types/${engageVeh.type.id}/postes`),
        api.get('/sp/affectations'),
        api.get('/sp/vehicules/statuts'),
      ])
    } catch (e) { engageError = e.message }
  }

  function membreById(id) { return membres.find(m => m.id === id) }
  function occupants(posteId) { return activeAff.filter(a => a.vehiculeId === engageVeh.id && a.posteId === posteId) }
  function eligibles(poste) {
    return membres.filter(m => m.actif && enServiceSet.has(m.id)
        && (m.qualifications ?? []).some(q => q.fonctionId === poste.fonctionId) && !busyMembreIds.has(m.id))
  }

  async function affecterPoste(poste) {
    const membreId = addSel[poste.id]
    if (!membreId) return
    engageError = ''
    try {
      await api.post('/sp/affectations', {
        vehiculeId: engageVeh.id, membreId, posteId: poste.id, debut: new Date().toISOString(),
      })
      addSel = { ...addSel, [poste.id]: '' }
      await Promise.all([loadEngageData(), load()])
    } catch (e) { engageError = e.message }
  }

  async function retirer(aff) {
    engageError = ''
    try {
      await api.put(`/sp/affectations/${aff.id}/cloture?fin=${encodeURIComponent(new Date().toISOString())}`)
      await Promise.all([loadEngageData(), load()])
    } catch (e) { engageError = e.message }
  }

  // Picker d'hôpital affiché quand le statut cible porte l'action TRANSPORT_HOPITAL.
  let hopitalPick = $state(null)   // { vehId, statutId }
  function statutById(id) { return statuts.find(s => s.id === id) }

  // Applique le changement de statut ; ouvre le picker d'hôpital si l'action l'exige.
  async function doChangeStatut(vehId, statutId, hopitalId = null) {
    const s = statutById(statutId)
    if (s?.actionCarte === 'TRANSPORT_HOPITAL' && hopitalId === null && !hopitalPick) {
      hopitalPick = { vehId, statutId }; return
    }
    const q = hopitalId ? `&hopitalId=${hopitalId}` : ''
    await api.put(`/sp/vehicules/${vehId}/statut?statutId=${statutId}${q}`)
    hopitalPick = null
    await load()
  }

  async function changeStatut(statutId) {
    engageError = ''
    try { await doChangeStatut(engageVeh.id, statutId) }
    catch (e) { engageError = e.message }
  }
  // Transition avant uniquement : statut courant + suivants
  let statutOptions = $derived(
    engageCurrent ? statuts.filter(s => s.position >= (engageCurrent.statut?.position ?? 0)) : []
  )

  // Ancienneté de la dernière vérification d'inventaire (badge de péremption).
  function verifJours(iso) { return iso ? Math.floor((Date.now() - new Date(iso).getTime()) / 86400000) : null }
  function verifTxt(iso) { const d = verifJours(iso); return d === null ? 'jamais vérifié' : d <= 0 ? 'vérif. aujourd’hui' : `vérif. il y a ${d} j` }
  function verifVieux(iso) { const d = verifJours(iso); return d === null || d >= 7 }

  // Changement de statut directement depuis la carte (transition avant uniquement).
  function statutsPour(v) { return statuts.filter(s => s.position >= (v.statut?.position ?? 0)) }
  async function changeStatutVeh(v, statutId) {
    if (!statutId || statutId === v.statut.id) return
    try { await doChangeStatut(v.id, statutId) }
    catch (e) { error = e.message }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Dispatch — Sapeurs-Pompiers</h2>
    <div style="display:flex;gap:8px;align-items:center">
      <button class="btn-nouvelle-inter" onclick={() => showCreate = true}>➕ Nouvelle intervention</button>
      <button class="btn-ghost" onclick={desaffecterTout}>Tout désaffecter</button>
      <button class="btn-ghost" onclick={load}>Actualiser</button>
    </div>
  </div>

  {#if showCreate}
    <SpInterventionCreate onclose={() => showCreate = false} oncreated={load} />
  {/if}

  {#if loading}
    <p class="muted">Chargement...</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <!-- Carte des interventions en cours (repliable) -->
    <section class="map-panel">
      <button class="map-head" onclick={() => showMap = !showMap}>
        <span class="caret">{showMap ? '▾' : '▸'}</span> Carte — {interCarte.length} intervention(s) en cours
      </button>
      {#if showMap}
        <MapView interventions={interCarte} transits={transits} centres={centres} hopitaux={hopitaux} height="360px"
                 onveh={(id) => { const v = vehicules.find(x => x.id === id); if (v) openEngage(v) }} />
      {/if}
    </section>

    <!-- Compteurs d'état de la flotte -->
    <div class="stat-bar">
      <span class="stat"><b>{stats.total}</b> engins</span>
      <span class="stat dispo"><b>{stats.dispo}</b> disponibles</span>
      <span class="stat eng"><b>{stats.engages}</b> engagés</span>
      <span class="stat na"><b>{stats.nonArmes}</b> non armés</span>
    </div>

    <!-- Personnel actuellement de garde -->
    <div class="garde-panel">
      <span class="garde-title"><span class="garde-dot"></span> De garde — {enServiceMembres.length}</span>
      {#if enServiceMembres.length > 0}
        <div class="garde-list">
          {#each enServiceMembres as m (m.id)}
            <span class="garde-chip"><span class="g-grade">{m.gradeCode}</span> {m.nomComplet || m.username}</span>
          {/each}
        </div>
      {:else}
        <span class="muted small">Personne de garde actuellement</span>
      {/if}
    </div>

    {#each groupes as g (g.key)}
    <section class="veh-group">
      <button class="group-head" onclick={() => toggleGroupe(g.key)}>
        <span class="group-caret">{collapsed[g.key] ? '▸' : '▾'}</span>
        <span class="group-title">{g.label}</span>
        <span class="group-count">{g.items.length}</span>
      </button>
      {#if !collapsed[g.key]}
      <div class="grid">
      {#each g.items as v (v.id)}
        <div class="card" style="border-top: 3px solid {v.statut.couleur}">
          <div class="card-head">
            <div class="veh-info">
              <span class="veh-type">{v.type.code}</span>
              <span class="veh-lib">{v.libelle}</span>
              {#if v.immatriculation}
                <span class="veh-immat">{v.immatriculation}</span>
              {/if}
              {#if v.centreLabel}<span class="veh-centre">⛑️ {v.centreLabel}</span>{/if}
            </div>
            <div class="badges">
              <select class="statut-sel" title="Changer le statut (transition avant)"
                      style="color:{v.statut.couleur}; border-color:{v.statut.couleur}55; background:{v.statut.couleur}18"
                      value={v.statut.id} onchange={e => changeStatutVeh(v, e.target.value)}>
                {#each statutsPour(v) as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
              </select>
              <span class="sys-badge" style="background:{v.etat.couleur}22; color:{v.etat.couleur}" title="État système">{v.etat.label}</span>
              <span class="arme-badge" class:ok={v.arme}
                    title={v.arme ? 'Postes obligatoires couverts' : 'Postes manquants : ' + ((v.postesManquants ?? []).join(', ') || '—')}>
                {v.arme ? '✓ armé' : '✗ non armé'}{#if !v.arme && (v.postesManquants ?? []).length}<span class="manq-count"> ({v.postesManquants.length})</span>{/if}
              </span>
              <span class="verif-badge" class:vieux={verifVieux(v.derniereVerifLe)} title="Dernière vérification d'inventaire">📋 {verifTxt(v.derniereVerifLe)}</span>
            </div>
          </div>

          {#if (v.equipe ?? []).length > 0}
            <ul class="crew">
              {#each v.equipe as m (m.membreId)}
                <li class="crew-member">
                  {#if enServiceSet.has(m.membreId)}<span class="garde-dot" title="De garde"></span>{/if}
                  <span class="crew-grade">{m.gradeCode}</span>
                  <span class="crew-name">{m.nomComplet || m.username}</span>
                  <div class="crew-right">
                    <span class="crew-matricule">{m.matricule}</span>
                    <span class="crew-fonction">{m.fonctionLabel}</span>
                  </div>
                </li>
              {/each}
            </ul>
          {:else}
            <p class="empty-crew">Aucun personnel embarqué</p>
          {/if}

          <div class="card-actions">
            <button class="btn-ghost-sm" onclick={() => openEngage(v)}>⚙ Armer / Statut</button>
            {#if !v.arme}
              <button class="btn-ghost-sm" onclick={() => affecterAuto(v)} title="Affecter automatiquement l'équipage de garde">⚡ Armer auto</button>
            {/if}
            {#if (v.equipe ?? []).length > 0}
              <button class="btn-ghost-sm" onclick={() => bip(v)}>🔔 Biper</button>
            {/if}
          </div>
        </div>
      {/each}
      </div>
      {/if}
    </section>
    {/each}
    {#if vehicules.length === 0}
      <p class="muted">Aucun véhicule enregistré</p>
    {/if}
  {/if}
</div>

<!-- ── Modal : choix de l'hôpital de destination (transport) ─────────────────── -->
{#if hopitalPick}
  <div class="backdrop" onclick={() => hopitalPick = null}>
    <div class="modal" onclick={e => e.stopPropagation()}>
      <h3>Destination — transport hôpital</h3>
      {#if hopitaux.length === 0}
        <p class="muted small">Aucun hôpital configuré (Config → Centres &amp; hôpitaux).</p>
      {/if}
      <div class="hopital-list">
        {#each hopitaux as h (h.id)}
          <button class="btn-secondary" onclick={() => doChangeStatut(hopitalPick.vehId, hopitalPick.statutId, h.id)}>🏥 {h.label}</button>
        {/each}
      </div>
      <div class="modal-actions">
        <button class="btn-ghost-sm" onclick={() => doChangeStatut(hopitalPick.vehId, hopitalPick.statutId, '')}>Sans destination</button>
        <button class="btn-ghost-sm" onclick={() => hopitalPick = null}>Annuler</button>
      </div>
    </div>
  </div>
{/if}

<!-- ── Modal armement / engagement ──────────────────────────────────────────── -->
{#if engageVeh}
  <div class="backdrop" onclick={closeEngage}>
    <div class="modal wide" onclick={e => e.stopPropagation()}>
      <h3>Armer — {engageCurrent.libelle} <span class="muted small">({engageCurrent.type.code})</span></h3>
      {#if engageError}<p class="inline-error">{engageError}</p>{/if}

      <label class="field-label">Statut du véhicule <span class="muted small">(état système : {engageCurrent.etat.label})</span>
        <select value={engageCurrent.statut.id} onchange={e => changeStatut(e.target.value)}>
          {#each statutOptions as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
        </select>
      </label>

      <div class="postes">
        {#each postes as p (p.id)}
          {@const occ = occupants(p.id)}
          <div class="poste">
            <div class="poste-head">
              <span class="poste-fonction">{p.fonctionLabel}</span>
              <span class="poste-cap">{occ.length}/{p.nbPlaces}</span>
            </div>
            {#if occ.length > 0}
              <ul class="poste-crew">
                {#each occ as a (a.id)}
                  <li>
                    <span class="crew-grade">{membreById(a.membreId)?.gradeCode ?? ''}</span>
                    <span class="crew-name">{membreById(a.membreId)?.nomComplet || membreById(a.membreId)?.username || '—'}</span>
                    <button class="rm-btn" title="Retirer" onclick={() => retirer(a)}>×</button>
                  </li>
                {/each}
              </ul>
            {/if}
            {#if occ.length < p.nbPlaces}
              {@const elig = eligibles(p)}
              <div class="poste-add">
                <select bind:value={addSel[p.id]}>
                  <option value="">— ajouter un effectif —</option>
                  {#each elig as m (m.id)}<option value={m.id}>{m.gradeCode} {m.nomComplet || m.username}</option>{/each}
                </select>
                <button class="btn-primary" disabled={!addSel[p.id]} onclick={() => affecterPoste(p)}>Affecter</button>
              </div>
              {#if elig.length === 0}<span class="muted small">Aucun effectif qualifié <strong>de garde</strong> disponible</span>{/if}
            {/if}
          </div>
        {/each}
        {#if postes.length === 0}
          <p class="muted small">Ce type de véhicule n'a aucun poste configuré.</p>
        {/if}
      </div>

      <div class="modal-actions">
        <button class="btn-ghost-sm" onclick={closeEngage}>Fermer</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .btn-nouvelle-inter {
    background: var(--color-danger); color: #fff; border: none; cursor: pointer;
    font-size: 14px; font-weight: 700; padding: 10px 18px; border-radius: var(--radius);
    box-shadow: 0 1px 4px rgba(0,0,0,.25);
  }
  .btn-nouvelle-inter:hover { filter: brightness(1.08); }

  .veh-centre { font-size: 11px; color: var(--color-muted); }

  .map-panel { margin-bottom: 12px; }
  .map-head { background: none; border: none; color: var(--color-text); font-size: 14px; font-weight: 600; cursor: pointer; padding: 4px 0; }
  .map-head .caret { color: var(--color-muted); font-size: 11px; }

  .stat-bar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
  .stat { font-size: 13px; color: var(--color-muted); background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 6px 12px; }
  .stat b { color: var(--color-text); font-size: 15px; }
  .stat.dispo b { color: var(--color-success); }
  .stat.eng b { color: var(--accent); }
  .stat.na b { color: var(--color-danger); }

  .veh-group { margin-bottom: 16px; }
  .group-head {
    display: flex; align-items: center; gap: 8px; width: 100%;
    background: none; border: none; cursor: pointer; padding: 6px 2px;
    color: var(--color-text); font-size: 14px; font-weight: 600;
    border-bottom: 1px solid var(--color-border); margin-bottom: 10px;
  }
  .group-caret { color: var(--color-muted); font-size: 11px; }
  .group-title { flex: 1; text-align: left; }
  .group-count { color: var(--color-muted); font-weight: 500; font-size: 12px; }

  .badges { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
  .verif-badge { font-size: 10px; color: var(--color-muted); }
  .verif-badge.vieux { color: var(--color-danger); font-weight: 600; }
  .statut-sel { font-size: 11px; font-weight: 700; border-radius: 6px; border: 1px solid; padding: 2px 6px; cursor: pointer; outline: none; max-width: 150px; }
  .statut-sel option { background: var(--color-surface); color: var(--color-text); font-weight: 500; }
  .sys-badge { font-size: 10px; font-weight: 600; border-radius: 6px; padding: 1px 6px; }
  .arme-badge { font-size: 10px; font-weight: 700; border-radius: 6px; padding: 1px 6px; background: color-mix(in srgb, var(--color-danger) 16%, transparent); color: var(--color-danger); }
  .arme-badge.ok { background: color-mix(in srgb, var(--color-success) 16%, transparent); color: var(--color-success); }
  .crew-right { display: flex; flex-direction: column; align-items: flex-end; gap: 1px; }
  .crew-fonction { font-size: 11px; color: var(--accent); font-weight: 500; }

  /* Panneau "de garde" */
  .garde-panel {
    background: var(--color-surface); border: 1px solid var(--color-border);
    border-radius: var(--radius); padding: 12px 16px;
    display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  }
  .garde-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: var(--color-success); display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
  .garde-list { display: flex; gap: 8px; flex-wrap: wrap; }
  .garde-chip { font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; padding: 3px 10px; display: inline-flex; gap: 6px; align-items: center; }
  .garde-chip .g-grade { color: var(--color-muted); font-size: 10px; }
  .garde-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-success); flex-shrink: 0; }
  .card-actions { display: flex; gap: 8px; flex-wrap: wrap; }

  /* Modal armement */
  .modal.wide { width: 560px; }
  .modal select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; outline: none; }
  .postes { display: flex; flex-direction: column; gap: 10px; max-height: 52vh; overflow-y: auto; }
  .poste { border: 1px solid var(--color-border); border-radius: var(--radius); padding: 10px 12px; }
  .poste-head { display: flex; align-items: center; justify-content: space-between; }
  .poste-fonction { font-weight: 600; font-size: 13px; }
  .poste-cap { font-family: monospace; font-size: 12px; color: var(--color-muted); }
  .poste-crew { list-style: none; margin: 6px 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
  .poste-crew li { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .poste-crew .crew-name { flex: 1; }
  .poste-add { display: flex; gap: 8px; align-items: center; margin-top: 4px; }
  .poste-add select { flex: 1; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }
  .hopital-list { display: flex; flex-direction: column; gap: 8px; margin: 8px 0; }
  .hopital-list .btn-secondary { text-align: left; }
</style>
