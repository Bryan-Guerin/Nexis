<script>
  import { onMount } from 'svelte'
  import { api } from '../shared/api.js'
  import { realtime } from '../shared/realtime.js'

  let vehicules    = $state([])
  let membres      = $state([])
  let enServiceIds = $state([])
  let loading      = $state(true)
  let error        = $state('')

  let enServiceSet     = $derived(new Set(enServiceIds))
  let enServiceMembres = $derived(membres.filter(m => enServiceSet.has(m.id)))

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
      ;[vehicules, membres, enServiceIds] = await Promise.all([
        api.get('/sp/dispatch'),
        api.get('/sp/membres?actif=true'),
        api.get('/sp/membres/en-service'),
      ])
    } catch (e) { error = e.message }
    finally { loading = false }
  }

  async function bip(v) {
    try { await api.post(`/sp/vehicules/${v.id}/bip`) }
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
  let busyMembreIds = $derived(new Set(activeAff.map(a => a.membreId)))

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

  async function changeStatut(statutId) {
    engageError = ''
    try {
      await api.put(`/sp/vehicules/${engageVeh.id}/statut?statutId=${statutId}`)
      await load()
    } catch (e) { engageError = e.message }
  }
  // Transition avant uniquement : statut courant + suivants
  let statutOptions = $derived(
    engageCurrent ? statuts.filter(s => s.position >= (engageCurrent.statut?.position ?? 0)) : []
  )
</script>

<div class="page">
  <div class="page-header">
    <h2>Dispatch — Sapeurs-Pompiers</h2>
    <button class="btn-ghost" onclick={load}>Actualiser</button>
  </div>

  {#if loading}
    <p class="muted">Chargement...</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}
    <!-- Personnel actuellement de garde -->
    <div class="garde-panel">
      <span class="garde-title"><span class="garde-dot"></span> De garde — {enServiceMembres.length}</span>
      {#if enServiceMembres.length > 0}
        <div class="garde-list">
          {#each enServiceMembres as m (m.id)}
            <span class="garde-chip">{m.matricule} · {m.username} <span class="g-grade">{m.grade}</span></span>
          {/each}
        </div>
      {:else}
        <span class="muted small">Personne de garde actuellement</span>
      {/if}
    </div>

    <div class="grid">
      {#each vehicules as v (v.id)}
        <div class="card" style="border-top: 3px solid {v.statut.couleur}">
          <div class="card-head">
            <div class="veh-info">
              <span class="veh-type">{v.type.code}</span>
              <span class="veh-lib">{v.libelle}</span>
              {#if v.immatriculation}
                <span class="veh-immat">{v.immatriculation}</span>
              {/if}
            </div>
            <div class="badges">
              <span class="etat-badge" style="background:{v.statut.couleur}22; color:{v.statut.couleur}; border:1px solid {v.statut.couleur}55">
                <span class="etat-dot" style="background:{v.statut.couleur}"></span>
                {v.statut.label}
              </span>
              <span class="sys-badge" style="background:{v.etat.couleur}22; color:{v.etat.couleur}" title="État système">{v.etat.label}</span>
              <span class="arme-badge" class:ok={v.arme} title="Postes obligatoires couverts">{v.arme ? '✓ armé' : '✗ non armé'}</span>
            </div>
          </div>

          {#if (v.equipe ?? []).length > 0}
            <ul class="crew">
              {#each v.equipe as m (m.membreId)}
                <li class="crew-member">
                  {#if enServiceSet.has(m.membreId)}<span class="garde-dot" title="De garde"></span>{/if}
                  <span class="crew-matricule">{m.matricule}</span>
                  <span class="crew-name">{m.username}</span>
                  <div class="crew-right">
                    <span class="crew-grade">{m.grade}</span>
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
            {#if (v.equipe ?? []).length > 0}
              <button class="btn-ghost-sm" onclick={() => bip(v)}>🔔 Biper</button>
            {/if}
          </div>
        </div>
      {/each}
      {#if vehicules.length === 0}
        <p class="muted">Aucun véhicule enregistré</p>
      {/if}
    </div>
  {/if}
</div>

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
                    <span class="crew-matricule">{membreById(a.membreId)?.matricule ?? '—'}</span>
                    <span class="crew-name">{membreById(a.membreId)?.username ?? ''}</span>
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
                  {#each elig as m (m.id)}<option value={m.id}>{m.matricule} · {m.username}</option>{/each}
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
  .etat-dot { width: 6px; height: 6px; }
  .badges { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
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
</style>
