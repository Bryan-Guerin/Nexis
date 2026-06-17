<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {toast} from '../shared/toasts.js'
    import {confirm} from '../shared/confirm.js'
    import {realtime} from '../shared/realtime.js'
    import {refStatutsVeh} from '../shared/referentials.js'
    import SpInterventionCreate from './SpInterventionCreate.svelte'
    import MapView from '../shared/MapView.svelte'
    import Modal from '../shared/Modal.svelte'
    import Skeleton from '../shared/Skeleton.svelte'

    let vehicules    = $state([])
  let membres      = $state([])
  let enServiceIds = $state([])
  let loading      = $state(true)
  let showCreate   = $state(false)   // création rapide d'intervention
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
        } else if (action === 'EN_ROUTE' && caserne) {
          out.push({ id, from: caserne, to: i.coordonnees, couleur, label, icone, depart: dep || i.debut })   // en route, ETA animée
        }
        // AUCUNE / DEPANNEUR : aucun tracé — l'engin ne bouge pas tant qu'il n'est pas « en route »
      }
    }
    return out
  })

  let enServiceSet     = $derived(new Set(enServiceIds))
  let enServiceMembres = $derived(membres.filter(m => enServiceSet.has(m.id)))

  // Regroupement par catégorie principale (nature principale du type) → onglets du panneau.
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
    loading = true
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
    } catch { /* toast par api.js */ }
    finally { loading = false }
  }

  async function bip(v) {
    try { await api.post(`/sp/vehicules/${v.id}/bip`) }
    catch { /* toast par api.js */ }
  }

  async function affecterAuto(v) {
    try {
      const ajout = await api.post(`/sp/vehicules/${v.id}/affecter-auto`)
      if (!ajout || ajout.length === 0) toast.info(`Aucun effectif de garde disponible pour armer ${v.libelle}.`)
      else toast.success(`${ajout.length} affecté(s) sur ${v.libelle}.`)
      await load()
    } catch { /* toast */ }
  }

  async function desaffecterTout() {
    if (!await confirm({ title: 'Tout désaffecter', message: 'Désaffecter TOUT le personnel embarqué de tous les véhicules ?', danger: true })) return
    try { const n = await api.put('/sp/affectations/desaffecter-tout'); toast.success(`${n} effectif(s) désaffecté(s).`); await load() }
    catch { /* erreur déjà signalée (toast) */ }
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
    catch { /* toast par api.js */ }
  }

  // ── Layout split : onglets par type d'intervention + lignes dépliables ──────
  let activeTab = $state('__all__')
  let gardeOpen = $state(false)
  let expanded  = $state(new Set())
  function toggleRow(id) { const s = new Set(expanded); s.has(id) ? s.delete(id) : s.add(id); expanded = s }
  let filtered = $derived(activeTab === '__all__' ? vehicules : (groupes.find(g => g.key === activeTab)?.items ?? []))
</script>

<div class="dispatch">
  <!-- Bandeau compact : titre + stats + de garde + actions -->
  <div class="d-strip">
    <div class="d-title">
      <h2>Dispatch — SP</h2>
      <div class="d-stats">
        <span class="stat"><b>{stats.total}</b> engins</span>
        <span class="stat dispo"><b>{stats.dispo}</b> dispo</span>
        <span class="stat eng"><b>{stats.engages}</b> engagés</span>
        <span class="stat na"><b>{stats.nonArmes}</b> non armés</span>
      </div>
    </div>
    <div class="d-strip-right">
      <div class="garde-wrap">
        <button class="garde-btn" onclick={() => gardeOpen = !gardeOpen}>
          <span class="garde-dot"></span> {enServiceMembres.length} de garde ▾
        </button>
        {#if gardeOpen}
          <button class="garde-overlay" aria-label="Fermer" onclick={() => gardeOpen = false}></button>
          <div class="garde-pop">
            {#each enServiceMembres as m (m.id)}
              <span class="garde-chip"><span class="g-grade">{m.gradeCode}</span> {m.nomComplet || m.username}</span>
            {/each}
            {#if enServiceMembres.length === 0}<span class="muted small">Personne de garde</span>{/if}
          </div>
        {/if}
      </div>
      <button class="btn-nouvelle-inter" onclick={() => showCreate = true}>➕ Nouvelle</button>
      <button class="btn-ghost" onclick={desaffecterTout}>Tout désaffecter</button>
      <button class="btn-ghost" onclick={load} title="Actualiser">↻</button>
    </div>
  </div>

  {#if showCreate}
    <SpInterventionCreate onclose={() => showCreate = false} oncreated={load} />
  {/if}

  {#if loading}
    <Skeleton rows={6} />
  {:else}
    <div class="d-body">
      <!-- Carte (grande, à gauche) -->
      <div class="d-map">
        <MapView interventions={interCarte} transits={transits} centres={centres} hopitaux={hopitaux} height="100%"
                 onveh={(id) => { const v = vehicules.find(x => x.id === id); if (v) openEngage(v) }} />
      </div>

      <!-- Panneau véhicules (droite) : onglets par type, un seul à la fois -->
      <div class="d-panel">
        <div class="d-tabs">
          <button class:on={activeTab === '__all__'} onclick={() => activeTab = '__all__'}>Tous <b>{vehicules.length}</b></button>
          {#each groupes as g (g.key)}
            <button class:on={activeTab === g.key} onclick={() => activeTab = g.key}>{g.label} <b>{g.items.length}</b></button>
          {/each}
        </div>

        <div class="d-list">
          {#each filtered as v (v.id)}
            <div class="vrow" style="border-left:3px solid {v.statut.couleur}">
              <div class="vr-main" role="button" tabindex="0" onclick={() => toggleRow(v.id)}>
                <span class="vr-ic">{v.type?.icone || '🚒'}</span>
                <div class="vr-id">
                  <span class="vr-lib">{v.libelle}</span>
                  <span class="vr-sub">{v.type.code}{#if v.centreLabel} · ⛑️ {v.centreLabel}{/if}</span>
                </div>
                <span class="vr-crewcount" title="Personnel embarqué">👤 {(v.equipe ?? []).length}</span>
                <span class="vr-arme" class:ok={v.arme}
                      title={v.arme ? 'Postes obligatoires couverts' : 'Manque : ' + ((v.postesManquants ?? []).join(', ') || '—')}>
                  {v.arme ? '✓' : '✗'}
                </span>
              </div>
              <div class="vr-actions">
                <select class="statut-sel" title="Changer le statut (transition avant)"
                        style="color:{v.statut.couleur}; border-color:{v.statut.couleur}55; background:{v.statut.couleur}18"
                        value={v.statut.id} onchange={e => changeStatutVeh(v, e.target.value)}>
                  {#each statutsPour(v) as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
                </select>
                {#if !v.arme}<button class="ico-btn" title="Armer auto" onclick={() => affecterAuto(v)}>⚡</button>{/if}
                {#if (v.equipe ?? []).length > 0}<button class="ico-btn" title="Biper" onclick={() => bip(v)}>🔔</button>{/if}
                <button class="ico-btn" title="Armer / postes" onclick={() => openEngage(v)}>⚙</button>
              </div>

              {#if expanded.has(v.id)}
                <div class="vr-crew">
                  {#if (v.equipe ?? []).length > 0}
                    <ul class="crew">
                      {#each v.equipe as m (m.membreId)}
                        <li class="crew-member">
                          {#if enServiceSet.has(m.membreId)}<span class="garde-dot" title="De garde"></span>{/if}
                          <span class="crew-grade">{m.gradeCode}</span>
                          <span class="crew-name">{m.nomComplet || m.username}</span>
                          <span class="crew-fonction">{m.fonctionLabel}</span>
                        </li>
                      {/each}
                    </ul>
                  {:else}
                    <p class="empty-crew">Aucun personnel embarqué</p>
                  {/if}
                  {#if !v.arme && (v.postesManquants ?? []).length}
                    <div class="manq">Manque : {#each v.postesManquants as p}<span class="manq-chip">{p}</span>{/each}</div>
                  {/if}
                  <div class="vr-foot">
                    <span class="sys-badge" style="background:{v.etat.couleur}22; color:{v.etat.couleur}">{v.etat.label}</span>
                    <span class="verif-badge" class:vieux={verifVieux(v.derniereVerifLe)}>📋 {verifTxt(v.derniereVerifLe)}</span>
                  </div>
                </div>
              {/if}
            </div>
          {/each}
          {#if filtered.length === 0}<p class="muted small" style="padding:12px">Aucun engin</p>{/if}
        </div>
      </div>
    </div>
  {/if}
</div>

<!-- ── Modal : choix de l'hôpital de destination (transport) ─────────────────── -->
{#if hopitalPick}
  <Modal title="Destination — transport hôpital" z={1100} onclose={() => hopitalPick = null}>
    {#if hopitaux.length === 0}
      <p class="muted small">Aucun hôpital configuré (Config → Centres &amp; hôpitaux).</p>
    {/if}
    <div class="hopital-list">
      {#each hopitaux as h (h.id)}
        <button class="btn-ghost" onclick={() => doChangeStatut(hopitalPick.vehId, hopitalPick.statutId, h.id)}>🏥 {h.label}</button>
      {/each}
    </div>
    {#snippet actions()}
      <button class="btn-ghost-sm" onclick={() => doChangeStatut(hopitalPick.vehId, hopitalPick.statutId, '')}>Sans destination</button>
      <button class="btn-ghost-sm" onclick={() => hopitalPick = null}>Annuler</button>
    {/snippet}
  </Modal>
{/if}

<!-- ── Modal armement / engagement ──────────────────────────────────────────── -->
{#if engageVeh}
  <Modal wide title={`Armer — ${engageCurrent.libelle} (${engageCurrent.type.code})`} onclose={closeEngage}>
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

    {#snippet actions()}
      <button class="btn-ghost-sm" onclick={closeEngage}>Fermer</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .btn-nouvelle-inter {
    background: var(--color-danger); color: #fff; border: none; cursor: pointer;
    font-size: 14px; font-weight: 700; padding: 10px 18px; border-radius: var(--radius);
    box-shadow: 0 1px 4px rgba(0,0,0,.25);
  }
  .btn-nouvelle-inter:hover { filter: brightness(1.08); }

  .stat { font-size: 13px; color: var(--color-muted); background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 6px 12px; }
  .stat b { color: var(--color-text); font-size: 15px; }
  .stat.dispo b { color: var(--color-success); }
  .stat.eng b { color: var(--accent); }
  .stat.na b { color: var(--color-danger); }

  .crew-fonction { font-size: 11px; color: var(--accent); font-weight: 500; }

  .garde-chip { font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; padding: 3px 10px; display: inline-flex; gap: 6px; align-items: center; }
  .garde-chip .g-grade { color: var(--color-muted); font-size: 10px; }
  .garde-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-success); flex-shrink: 0; }

  /* Modal armement */
  .field-label select, .postes select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; outline: none; }
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
  .hopital-list .btn-ghost { text-align: left; }

  /* ── Layout split (carte + panneau véhicules) ──────────────────────────── */
  .dispatch { display: flex; flex-direction: column; gap: 10px; height: calc(100vh - var(--header-h) - 48px); }
  .d-strip { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
  .d-title { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
  .d-title h2 { font-size: 18px; font-weight: 600; }
  .d-stats { display: flex; gap: 6px; flex-wrap: wrap; }
  .d-strip-right { display: flex; align-items: center; gap: 8px; }

  .garde-wrap { position: relative; }
  .garde-btn { display: inline-flex; align-items: center; gap: 6px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 6px 10px; cursor: pointer; }
  .garde-btn:hover { border-color: var(--color-success); }
  .garde-overlay { position: fixed; inset: 0; background: transparent; border: none; z-index: 49; }
  .garde-pop { position: absolute; right: 0; top: calc(100% + 6px); z-index: 50; width: 250px; max-height: 50vh; overflow-y: auto; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); box-shadow: 0 8px 24px rgba(0,0,0,.3); padding: 10px; display: flex; flex-wrap: wrap; gap: 6px; }

  .d-body { flex: 1; min-height: 0; display: flex; gap: 10px; }
  .d-map { flex: 1.7; min-width: 0; border-radius: var(--radius); overflow: hidden; }
  .d-panel { flex: 1; min-width: 300px; max-width: 460px; display: flex; flex-direction: column; gap: 8px; min-height: 0; }

  .d-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
  .d-tabs button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-muted); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .d-tabs button b { color: var(--color-text); font-weight: 700; }
  .d-tabs button.on { background: color-mix(in srgb, var(--accent) 16%, transparent); border-color: var(--accent); color: var(--accent); }
  .d-tabs button.on b { color: var(--accent); }

  .d-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; padding-right: 2px; }
  .vrow { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .vr-main { display: flex; align-items: center; gap: 8px; padding: 8px 10px; cursor: pointer; }
  .vr-ic { font-size: 18px; line-height: 1; }
  .vr-id { display: flex; flex-direction: column; flex: 1; min-width: 0; }
  .vr-lib { font-weight: 600; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .vr-sub { font-size: 11px; color: var(--color-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .vr-crewcount { font-size: 11px; color: var(--color-muted); white-space: nowrap; }
  .vr-arme { font-size: 11px; font-weight: 800; border-radius: 6px; padding: 1px 7px; background: color-mix(in srgb, var(--color-danger) 16%, transparent); color: var(--color-danger); }
  .vr-arme.ok { background: color-mix(in srgb, var(--color-success) 16%, transparent); color: var(--color-success); }
  .vr-actions { display: flex; align-items: center; gap: 6px; padding: 0 10px 8px; }
  .vr-actions .statut-sel { flex: 1; max-width: none; }
  .ico-btn { background: none; border: 1px solid var(--color-border); border-radius: 6px; padding: 3px 8px; cursor: pointer; font-size: 13px; line-height: 1; }
  .ico-btn:hover { border-color: var(--accent); }
  .vr-crew { border-top: 1px solid var(--color-border); padding: 8px 10px; }
  .manq { margin-top: 6px; font-size: 11px; color: var(--color-muted); display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
  .manq-chip { background: color-mix(in srgb, var(--color-danger) 14%, transparent); color: var(--color-danger); border-radius: 6px; padding: 1px 6px; font-weight: 600; }
  .vr-foot { display: flex; gap: 8px; align-items: center; margin-top: 8px; }

  @media (max-width: 900px) {
    .dispatch { height: auto; }
    .d-body { flex-direction: column; }
    .d-map { height: 320px; flex: none; }
    .d-panel { max-width: none; }
    .d-list { overflow-y: visible; }
  }
</style>
