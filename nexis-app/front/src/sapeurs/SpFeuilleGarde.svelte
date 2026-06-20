<script>
  import {onMount} from 'svelte'
  import {get} from 'svelte/store'
  import {api} from '../shared/api.js'
  import {toast} from '../shared/toasts.js'
  import {confirm} from '../shared/confirm.js'
  import {realtime} from '../shared/realtime.js'
  import {refStatutsVeh} from '../shared/referentials.js'
  import {currentUser, feuilleFiltreDemande} from '../shared/stores.js'
  import Modal from '../shared/Modal.svelte'
  import Icone from '../shared/Icone.svelte'
  import Skeleton from '../shared/Skeleton.svelte'
  import EmptyState from '../shared/EmptyState.svelte'

  // Feuille de garde : flotte groupée par type principal (comme l'ancien dispatch),
  // triée armé → statut, pour armer/affecter facilement. Pas de carte (→ Dispatch).
  let vehicules    = $state([])
  let membres      = $state([])
  let enServiceIds = $state([])
  let hopitaux     = $state([])      // pour le picker de transport hôpital
  let loading      = $state(true)

  let enServiceSet     = $derived(new Set(enServiceIds))
  let enServiceMembres = $derived(membres.filter(m => enServiceSet.has(m.id)))

  // Filtre par effectif : clic sur un nom (équipage / de garde) → n'affiche que ses engins.
  let filtreMembres = $state(new Set())   // membreId
  function toggleFiltreMembre(id) {
    const s = new Set(filtreMembres)
    s.has(id) ? s.delete(id) : s.add(id)
    filtreMembres = s
  }
  function clearFiltre() { filtreMembres = new Set() }
  function membreLabel(id) {
    const m = membres.find(x => x.id === id)
    return m ? `${m.gradeCode} ${m.nomComplet || m.username}` : '—'
  }
  let vehiculesFiltres = $derived(
    filtreMembres.size === 0 ? vehicules
      : vehicules.filter(v => (v.equipe ?? []).some(m => filtreMembres.has(m.membreId)))
  )

  // Regroupement par catégorie principale, sections repliables ; items triés armé → statut.
  let collapsed = $state({})
  let groupes = $derived(grouperVehicules(vehiculesFiltres))
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
      if (!map.has(key)) map.set(key, { key, label: np?.label ?? 'Autres', position: np?.position ?? Infinity, items: [] })
      map.get(key).items.push(v)
    }
    for (const g of map.values()) {
      // armé d'abord, puis statut (ordre RP croissant)
      g.items.sort((a, b) => (b.arme === a.arme ? 0 : b.arme ? 1 : -1) || (a.statut?.position ?? 0) - (b.statut?.position ?? 0))
    }
    return [...map.values()].sort((a, b) => a.position - b.position)
  }
  function toggleGroupe(key) { collapsed = { ...collapsed, [key]: !collapsed[key] } }

  let reloadTimer = null
  onMount(() => {
    load()
    // Sur événement : on ne recharge QUE le dynamique (engins, équipages, en-service).
    // Les hôpitaux (picker) restent figés → moins de remous pendant qu'on édite son statut.
    const off = realtime.on(ev => {
      if (ev.faction === 'SP' && ['AFFECTATION', 'DESAFFECTATION', 'ETAT_VEHICULE'].includes(ev.type)) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(loadDynamic, 400)
      }
    })
    // Fins de garde temporelles (sans événement) → rafraîchit « de garde » périodiquement.
    const tick = setInterval(async () => {
      try { enServiceIds = await api.get('/sp/membres/en-service') } catch { /* silencieux */ }
    }, 60000)
    return () => { off(); clearInterval(tick) }
  })

  // Chargement complet (statique hôpitaux + dynamique). Une fois au montage.
  async function load() {
    loading = true
    try {
      hopitaux = await api.get('/sp/hopitaux')
      await loadDynamic()
      consommerFiltreDemande()
    } catch { /* toast par api.js */ }
    finally { loading = false }
  }

  // Données temps réel uniquement (sans toucher au picker hôpitaux).
  async function loadDynamic() {
    try {
      ;[vehicules, membres, enServiceIds, statuts] = await Promise.all([
        api.get('/sp/dispatch'),
        api.get('/sp/membres?actif=true'),
        api.get('/sp/membres/en-service'),
        refStatutsVeh(),
      ])
    } catch { /* toast par api.js */ }
  }

  // Filtre demandé via la navigation (clic sur « mes affectations ») : 'moi' = utilisateur courant.
  function consommerFiltreDemande() {
    const dem = get(feuilleFiltreDemande)
    if (!dem) return
    feuilleFiltreDemande.set(null)
    if (dem === 'moi') {
      const moi = membres.find(m => m.username === $currentUser?.username)
      if (moi) filtreMembres = new Set([moi.id])
    } else if (Array.isArray(dem)) {
      filtreMembres = new Set(dem)
    }
  }

  async function bip(v) {
    try { await api.post(`/sp/vehicules/${v.id}/bip`) } catch { /* toast par api.js */ }
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
    catch { /* toast */ }
  }

  // ── Armement / engagement d'un véhicule ─────────────────────────────────────
  let engageVeh   = $state(null)
  let postes      = $state([])
  let activeAff   = $state([])
  let statuts     = $state([])
  let addSel      = $state({})
  let engageError = $state('')

  let engageCurrent = $derived(engageVeh ? (vehicules.find(x => x.id === engageVeh.id) ?? engageVeh) : null)
  let busyMembreIds = $derived(new Set(
    activeAff.filter(a => engageVeh && a.vehiculeId === engageVeh.id).map(a => a.membreId)
  ))

  async function openEngage(v) { engageVeh = v; engageError = ''; addSel = {}; await loadEngageData() }
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
    const membreId = addSel[poste.id]; if (!membreId) return
    engageError = ''
    try {
      await api.post('/sp/affectations', { vehiculeId: engageVeh.id, membreId, posteId: poste.id, debut: new Date().toISOString() })
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

  // Picker d'hôpital quand le statut cible porte l'action TRANSPORT_HOPITAL.
  let hopitalPick = $state(null)
  function statutById(id) { return statuts.find(s => s.id === id) }
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
    try { await doChangeStatut(engageVeh.id, statutId) } catch (e) { engageError = e.message }
  }
  let statutOptions = $derived(engageCurrent ? statuts.filter(s => s.position >= (engageCurrent.statut?.position ?? 0)) : [])

  function verifJours(iso) { return iso ? Math.floor((Date.now() - new Date(iso).getTime()) / 86400000) : null }
  function verifTxt(iso) { const d = verifJours(iso); return d === null ? 'jamais vérifié' : d <= 0 ? 'vérif. aujourd’hui' : `vérif. il y a ${d} j` }
  function verifVieux(iso) { const d = verifJours(iso); return d === null || d >= 7 }

  function statutsPour(v) { return statuts.filter(s => s.position >= (v.statut?.position ?? 0)) }
  async function changeStatutVeh(v, statutId) {
    if (!statutId || statutId === v.statut.id) return
    try { await doChangeStatut(v.id, statutId) } catch { /* toast par api.js */ }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>Feuille de garde — Sapeurs-Pompiers</h2>
    <div style="display:flex;gap:8px;align-items:center">
      <button class="btn-ghost" onclick={desaffecterTout}>Tout désaffecter</button>
      <button class="btn-ghost" onclick={load}>Actualiser</button>
    </div>
  </div>

  {#if loading}
    <Skeleton rows={6} />
  {:else}
    <div class="stat-bar">
      <span class="stat"><b>{stats.total}</b> engins</span>
      <span class="stat dispo"><b>{stats.dispo}</b> disponibles</span>
      <span class="stat eng"><b>{stats.engages}</b> engagés</span>
      <span class="stat na"><b>{stats.nonArmes}</b> non armés</span>
    </div>

    <div class="garde-panel">
      <span class="garde-title"><span class="garde-dot"></span> De garde — {enServiceMembres.length}</span>
      {#if enServiceMembres.length > 0}
        <div class="garde-list">
          {#each enServiceMembres as m (m.id)}
            <button class="garde-chip" class:on={filtreMembres.has(m.id)} title="Filtrer ses engins"
                    onclick={() => toggleFiltreMembre(m.id)}>
              <span class="g-grade">{m.gradeCode}</span> {m.nomComplet || m.username}
            </button>
          {/each}
        </div>
      {:else}
        <span class="muted small">Personne de garde actuellement</span>
      {/if}
    </div>

    {#if filtreMembres.size > 0}
      <div class="filtre-bar">
        <span class="muted small">Filtré sur :</span>
        {#each [...filtreMembres] as id (id)}
          <button class="filtre-chip" onclick={() => toggleFiltreMembre(id)} title="Retirer">{membreLabel(id)} ✕</button>
        {/each}
        <button class="btn-ghost-sm" onclick={clearFiltre}>Tout afficher</button>
      </div>
    {/if}

    {#if groupes.length === 0}
      <EmptyState icon="🔍" title="Aucun engin" message="Aucun engin ne correspond au filtre." />
    {/if}

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
              <span class="veh-type"><Icone imageId={v.type?.iconeImageId} emoji={v.type?.icone || ''} size={14} /> {v.type.code}</span>
              <span class="veh-lib">{v.libelle}</span>
              {#if v.immatriculation}<span class="veh-immat">{v.immatriculation}</span>{/if}
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
                <li class="crew-member" class:filtre-on={filtreMembres.has(m.membreId)}>
                  {#if enServiceSet.has(m.membreId)}<span class="garde-dot" title="De garde"></span>{/if}
                  <span class="crew-grade">{m.gradeCode}</span>
                  <button class="crew-name crew-name-btn" title="Filtrer ses engins"
                          onclick={() => toggleFiltreMembre(m.membreId)}>{m.nomComplet || m.username}</button>
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
      <EmptyState icon="🚒" title="Aucun véhicule" message="Aucun engin n'est enregistré dans la flotte." />
    {/if}
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

  .veh-centre { font-size: 11px; color: var(--color-muted); }
  .badges { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
  .arme-badge { font-size: 10px; font-weight: 700; border-radius: 6px; padding: 1px 6px; background: color-mix(in srgb, var(--color-danger) 16%, transparent); color: var(--color-danger); }
  .arme-badge.ok { background: color-mix(in srgb, var(--color-success) 16%, transparent); color: var(--color-success); }
  .crew-right { display: flex; flex-direction: column; align-items: flex-end; gap: 1px; }
  .crew-fonction { font-size: 11px; color: var(--accent); font-weight: 500; }

  .garde-panel {
    background: var(--color-surface); border: 1px solid var(--color-border);
    border-radius: var(--radius); padding: 12px 16px;
    display: flex; align-items: center; gap: 14px; flex-wrap: wrap; margin-bottom: 12px;
  }
  .garde-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: var(--color-success); display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
  .garde-list { display: flex; gap: 8px; flex-wrap: wrap; }
  .garde-chip { font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; padding: 3px 10px; display: inline-flex; gap: 6px; align-items: center; color: var(--color-text); cursor: pointer; }
  .garde-chip:hover { border-color: var(--accent); }
  .garde-chip.on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, transparent); color: var(--accent); }
  .garde-chip .g-grade { color: var(--color-muted); font-size: 10px; }
  .garde-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-success); flex-shrink: 0; }
  .card-actions { display: flex; gap: 8px; flex-wrap: wrap; }

  .filtre-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
  .filtre-chip { font-size: 12px; background: color-mix(in srgb, var(--accent) 14%, transparent); border: 1px solid var(--accent); color: var(--accent); border-radius: 16px; padding: 2px 10px; cursor: pointer; }
  .crew-name-btn { background: none; border: none; padding: 0; color: inherit; font: inherit; text-align: left; cursor: pointer; }
  .crew-name-btn:hover { color: var(--accent); text-decoration: underline; }
  .crew-member.filtre-on { background: color-mix(in srgb, var(--accent) 10%, transparent); border-radius: var(--radius); }

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
</style>
