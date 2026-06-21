<script>
    import {onMount} from 'svelte'
    import {push} from 'svelte-spa-router'
    import {api} from '../shared/api.js'
    import {toast} from '../shared/toasts.js'
    import {confirm} from '../shared/confirm.js'
    import {realtime} from '../shared/realtime.js'
    import {currentUser} from '../shared/stores.js'
    import {can} from '../shared/roles.js'
    import Icone from '../shared/Icone.svelte'
    import {refNatures, refStatutsVeh, refMe} from '../shared/referentials.js'
    import Pagination from '../shared/Pagination.svelte'
    import Skeleton from '../shared/Skeleton.svelte'
    import EmptyState from '../shared/EmptyState.svelte'
    import SpInterventionCreate from './SpInterventionCreate.svelte'

    let interventions = $state([])
  let vehicules     = $state([])
  let natures       = $state([])
  let loading       = $state(true)

  let isAdmin      = $derived($currentUser?.roles?.includes('ROLE_ADMIN_SP') ?? false)
  let isDispatcher = $derived($can.dispatch)   // dispatch OU admin (source centrale roles.js)

  // Référentiels engagés par la liste (statut engin inline + droits par carte)
  let statuts      = $state([])
  let affectations = $state([])
  let myMembreId   = $state(null)

  // Création (modal extrait → SpInterventionCreate)
  let showCreate  = $state(false)

  // Renfort
  let renfortFor  = $state(null)
  let renfortSel  = $state([])

  let reloadTimer = null

  let sorted = $derived([...interventions].sort((a, b) =>
    a.enCours === b.enCours ? (new Date(b.debut) - new Date(a.debut)) : (a.enCours ? -1 : 1)
  ))

  // Recherche / archive — par défaut, on n'affiche que les interventions en cours.
  // Filtres : TOUTES | EN_COURS | ATTENTE_CRI | ATTENTE_VALIDATION | CLOSE | CLOTUREES (les 3 dernières regroupées).
  let filtreStatut = $state('EN_COURS')
  let filtreNature = $state('')
  let recherche    = $state('')
  let intPage      = $state(1)
  let intPageSize  = $state(25)
  let affichees = $derived(sorted.filter(i => {
    const sc = i.statutCloture ?? (i.enCours ? 'EN_COURS' : 'CLOSE')
    if (filtreStatut === 'EN_COURS' && sc !== 'EN_COURS') return false
    if (filtreStatut === 'ATTENTE_CRI' && sc !== 'ATTENTE_CRI') return false
    if (filtreStatut === 'ATTENTE_VALIDATION' && sc !== 'ATTENTE_VALIDATION') return false
    if (filtreStatut === 'CLOSE' && sc !== 'CLOSE') return false
    if (filtreNature && i.nature?.id !== filtreNature) return false
    const q = recherche.trim().toLowerCase()
    if (!q) return true
    return [i.code, i.motif, i.nature?.code, i.nature?.label, i.commune]
      .filter(Boolean).some(s => s.toLowerCase().includes(q))
  }))

  const STATUT_CLOTURE_LABEL = { EN_COURS: 'En cours', ATTENTE_CRI: 'En attente CRI', ATTENTE_VALIDATION: 'En attente validation', CLOSE: 'Close' }
  let afficheesPage = $derived(affichees.slice((intPage - 1) * intPageSize, intPage * intPageSize))

  // Horloge pour le chrono des interventions en cours (rafraîchi chaque minute).
  let now = $state(Date.now())

  onMount(() => {
    load()
    const unsub = realtime.on(ev => {
      if (ev.faction === 'SP' && (ev.type?.startsWith('INTERVENTION_') || ev.type === 'ETAT_VEHICULE'
          || ev.type === 'AFFECTATION' || ev.type === 'DESAFFECTATION' || ev.type === 'MAIN_COURANTE')) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(load, 300)
      }
    })
    const tick = setInterval(() => now = Date.now(), 60000)
    return () => { unsub(); clearInterval(tick) }
  })

  // Durée écoulée depuis l'ouverture (chrono live).
  function chrono(debutIso) {
    const min = Math.max(0, Math.floor((now - new Date(debutIso).getTime()) / 60000))
    const h = Math.floor(min / 60), m = min % 60
    return h > 0 ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`
  }

  async function load() {
    loading = true
    try {
      // Dynamiques (rechargés à chaque fois) + référentiels mis en cache (natures, statuts, me) :
      // sur les rechargements temps réel, ces 3 derniers sont servis sans réseau.
      let me
      ;[interventions, vehicules, affectations, natures, statuts, me] = await Promise.all([
        api.get('/sp/interventions'),
        api.get('/sp/vehicules/engageables'),   // disponibles + équipage requis
        api.get('/sp/affectations'),             // pour canControl par carte
        refNatures(),
        refStatutsVeh(),                         // pour l'édition de statut depuis la liste
        refMe().catch(() => null),
      ])
      myMembreId = me?.id ?? null
    } catch { /* toast par api.js */ }
    finally { loading = false }
  }

  async function refreshOrLoad() { await load() }

  function fmt(iso) { return iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—' }
  function fmtCoord(c) { return c && c.length === 6 ? c.slice(0, 3) + ' ' + c.slice(3) : (c || '—') }
  // Coordonnées : affichage avec espace après 3 chiffres (réutilisé par l'édition).
  function coordDisplay(raw) { return raw.length > 3 ? raw.slice(0, 3) + ' ' + raw.slice(3) : raw }
  function toggle(list, id) { return list.includes(id) ? list.filter(x => x !== id) : [...list, id] }

  function vehiculesHors(inter) {
    return vehicules.filter(v => !inter.engins.some(e => e.vehiculeId === v.vehiculeId))
  }

  async function avertirNonArmes(ids) {
    const nonArmes = vehicules.filter(v => ids.includes(v.vehiculeId) && !v.arme)
    if (nonArmes.length === 0) return true
    return await confirm({ title: 'Engins non armés', danger: true, confirmLabel: 'Engager quand même',
      message: `${nonArmes.map(v => v.libelle).join(', ')} non armé(s) (poste obligatoire non couvert).` })
  }

  async function submitRenfort(inter) {
    if (renfortSel.length === 0) return
    if (!(await avertirNonArmes(renfortSel))) return
    try {
      await api.post(`/sp/interventions/${inter.id}/engins`, { vehiculeIds: renfortSel })
      renfortFor = null; renfortSel = []
      await load()
    } catch { /* toast par api.js */ }
  }

  async function cloturer(inter) {
    if (!await confirm({ title: 'Clôturer l\'intervention', message: `Clôturer « ${inter.motif} » ?` })) return
    try { await api.put(`/sp/interventions/${inter.id}/cloture`); toast.success('Intervention clôturée.'); await load() }
    catch { /* toast par api.js */ }
  }

  // ── Renforts GN / VINCI (éditable par tous) ────────────────────────────────
  const RENFORT_OPTS = [['NON_PREVENU', 'Non prévenu'], ['PREVENU', 'Prévenu'], ['SUR_PLACE', 'Sur place']]
  function renfortLabel(v) { return (RENFORT_OPTS.find(o => o[0] === v) ?? [, v])[1] }
  async function changeRenfort(interId, cible, statut) {
    try {
      await api.put(`/sp/interventions/${interId}/renfort`,
        cible === 'GN' ? { renfortGn: statut } : { renfortVinci: statut })
      await refreshOrLoad()
    } catch { /* toast par api.js */ }
  }

  // L'utilisateur connecté est-il équipier de cet engin ? (ou admin)
  function canControl(engin) {
    return isAdmin || affectations.some(a => a.vehiculeId === engin.vehiculeId && a.membreId === myMembreId)
  }
  // Transition avant uniquement : statut courant + suivants
  function statutOptions(engin) { return statuts.filter(s => s.position >= engin.statutPosition) }

  async function changeEnginStatut(engin, statutId) {
    if (!statutId || statutId === engin.statutId) return
    try { await api.put(`/sp/vehicules/${engin.vehiculeId}/statut?statutId=${statutId}`); await refreshOrLoad() }
    catch { /* toast par api.js */ }
  }

</script>

<div class="page">
  <div class="page-header">
    <h2>Interventions — Sapeurs-Pompiers</h2>
    <button class="btn-primary" onclick={() => showCreate = !showCreate}>
      {showCreate ? 'Annuler' : 'Nouvelle intervention'}
    </button>
  </div>

  {#if loading}
    <Skeleton rows={6} />
  {:else}
    <div class="filtres">
      <div class="seg">
        <button class="seg-btn" class:on={filtreStatut === 'TOUTES'} onclick={() => filtreStatut = 'TOUTES'}>Toutes</button>
        <button class="seg-btn" class:on={filtreStatut === 'EN_COURS'} onclick={() => filtreStatut = 'EN_COURS'}>En cours</button>
        <button class="seg-btn" class:on={filtreStatut === 'ATTENTE_CRI'} onclick={() => filtreStatut = 'ATTENTE_CRI'}>En attente CRI</button>
        <button class="seg-btn" class:on={filtreStatut === 'ATTENTE_VALIDATION'} onclick={() => filtreStatut = 'ATTENTE_VALIDATION'}>À valider</button>
        <button class="seg-btn" class:on={filtreStatut === 'CLOSE'} onclick={() => filtreStatut = 'CLOSE'}>Closes</button>
      </div>
      <select class="search nature-filtre" bind:value={filtreNature} title="Filtrer par nature">
        <option value="">Toutes natures</option>
        {#each natures as n (n.id)}<option value={n.id}>{n.code} · {n.label}</option>{/each}
      </select>
      <input class="search" type="search" bind:value={recherche} placeholder="Rechercher (code, motif, nature, commune)…" />
    </div>
    <div class="list">
      {#each afficheesPage as i (i.id)}
        <div class="card" class:closed={!i.enCours}>
          <div class="i-head" onclick={() => push(`/sp/interventions/${i.code}`)} role="button" tabindex="0" title="Voir le détail">
            <div class="i-main">
              <span class="badge" class:badge-actif={i.enCours} class:badge-inactif={!i.enCours}
                    class:badge-attente={i.statutCloture === 'ATTENTE_CRI' || i.statutCloture === 'ATTENTE_VALIDATION'}>
                {STATUT_CLOTURE_LABEL[i.statutCloture] ?? (i.enCours ? 'En cours' : 'Clôturée')}
              </span>
              {#if i.enCours}<span class="i-chrono" title="Durée depuis l'ouverture">⏱ {chrono(i.debut)}</span>{/if}
              <span class="i-code">{i.code}</span>
              {#if i.nature}<span class="chip-code">{i.nature.code}</span>{/if}
              <span class="i-motif">{i.motif}</span>
            </div>
            <span class="i-time">{fmt(i.debut)}{#if i.fin} → {fmt(i.fin)}{/if}</span>
          </div>

          <div class="engins">
            {#if i.enCours}
              {#each i.engins as e (e.vehiculeId)}
                <span class="engin" style="border-left:3px solid {e.etatCouleur}">
                  <Icone imageId={e.typeIconeImageId} emoji={e.typeIcone || '🚒'} size={15} />
                  {e.libelle} <span class="chip-code">{e.typeCode}</span>
                  {#if canControl(e)}
                    <select class="eng-statut-inline" value={e.statutId} onchange={ev => changeEnginStatut(e, ev.target.value)} title="Statut de l'engin">
                      {#each statutOptions(e) as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
                    </select>
                  {:else}
                    <span class="eng-statut-mini" style="color:{e.etatCouleur}">{e.etatLabel}</span>
                  {/if}
                </span>
              {/each}
              {#if i.engins.length === 0}<span class="muted small">Aucun engin</span>{/if}
            {:else}
              {#each i.enginsHisto ?? [] as e}
                <span class="engin" title={e.equipage.map(m => `${m.grade} ${m.nom}`).join(', ')}>
                  {e.libelle}{#if e.typeCode} <span class="chip-code">{e.typeCode}</span>{/if}
                  {#if e.equipage.length}<span class="eng-statut-mini">👤 {e.equipage.length}</span>{/if}
                </span>
              {/each}
              {#if (i.enginsHisto ?? []).length === 0}<span class="muted small">Aucun engin</span>{/if}
            {/if}
          </div>

          <div class="i-services">
            {#if i.enCours}
              <label class="svc-edit">GN
                <select value={i.renfortGn} onchange={e => changeRenfort(i.id, 'GN', e.target.value)}>
                  {#each RENFORT_OPTS as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
              </label>
              <label class="svc-edit">Dépanneur
                <select value={i.renfortVinci} onchange={e => changeRenfort(i.id, 'VINCI', e.target.value)}>
                  {#each RENFORT_OPTS as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
              </label>
            {:else}
              <span class="svc-chip svc-{(i.renfortGn ?? 'NON_PREVENU').toLowerCase()}" title="Gendarmerie">GN&nbsp;: {renfortLabel(i.renfortGn)}</span>
              <span class="svc-chip svc-{(i.renfortVinci ?? 'NON_PREVENU').toLowerCase()}" title="Dépanneur (VINCI)">Dépanneur&nbsp;: {renfortLabel(i.renfortVinci)}</span>
            {/if}
          </div>

          {#if (i.dernieresLignes ?? []).length > 0}
            <ul class="i-mc">
              {#each i.dernieresLignes as ev (ev.id)}
                <li><span class="mono">{fmt(ev.creeLe)}</span> <span class="mc-msg">{ev.message}</span></li>
              {/each}
            </ul>
          {/if}

          <div class="i-actions">
            <button class="btn-ghost-sm" onclick={() => push(`/sp/interventions/${i.code}`)}>Détail</button>
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
        <EmptyState icon="🔥"
          title={interventions.length === 0 ? 'Aucune intervention' : 'Aucun résultat'}
          message={interventions.length === 0 ? 'Aucune intervention enregistrée.' : 'Aucune intervention ne correspond aux filtres.'} />
      {/if}
    </div>
    {#if affichees.length > 0}<Pagination bind:page={intPage} bind:pageSize={intPageSize} total={affichees.length} />{/if}
  {/if}
</div>

<!-- ── Modale : nouvelle intervention (composant partagé) ────────────────────── -->
{#if showCreate}
  <SpInterventionCreate onclose={() => showCreate = false} oncreated={load} />
{/if}


<style>
  .filtres { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
  .seg { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .seg-btn { background: var(--color-surface); border: none; color: var(--color-muted); font-size: 12px; padding: 6px 12px; cursor: pointer; border-right: 1px solid var(--color-border); }
  .seg-btn:last-child { border-right: none; }
  .seg-btn.on { background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); font-weight: 600; }
  .search { flex: 1; min-width: 220px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; }
  .nature-filtre { flex: 0 0 auto; min-width: 160px; }

  .list { display: flex; flex-direction: column; gap: 10px; }
  .card.closed { opacity: 0.7; }

  .i-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; cursor: pointer; }
  .i-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .i-code { font-family: monospace; font-size: 12px; color: var(--accent); font-weight: 700; }
  .i-motif { font-size: 15px; font-weight: 600; }
  .i-time { font-size: 12px; color: var(--color-muted); white-space: nowrap; }
  .i-chrono { font-size: 12px; font-weight: 700; color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, transparent); border-radius: 10px; padding: 1px 8px; white-space: nowrap; }

  .engins { display: flex; gap: 8px; flex-wrap: wrap; }
  .engin { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 4px 10px; font-size: 13px; display: inline-flex; gap: 6px; align-items: center; }

  .i-actions { display: flex; gap: 8px; flex-wrap: wrap; }

  .i-services { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
  .svc-edit { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; color: var(--color-muted); }
  .svc-edit select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 3px 6px; }
  .eng-statut-inline { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 11px; padding: 2px 5px; margin-left: 4px; }
  .eng-statut-mini { font-size: 11px; font-weight: 600; margin-left: 4px; }
  .svc-chip { font-size: 11px; font-weight: 600; border-radius: 12px; padding: 2px 9px; border: 1px solid var(--color-border); color: var(--color-muted); }
  .svc-chip.svc-prevenu { color: #e0a23c; border-color: color-mix(in srgb, #e0a23c 45%, transparent); background: color-mix(in srgb, #e0a23c 12%, transparent); }
  .svc-chip.svc-sur_place { color: var(--color-success); border-color: color-mix(in srgb, var(--color-success) 45%, transparent); background: color-mix(in srgb, var(--color-success) 12%, transparent); }

  .i-mc { list-style: none; margin: 0; padding: 6px 10px; display: flex; flex-direction: column; gap: 2px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .i-mc li { font-size: 12px; display: flex; gap: 8px; }
  .i-mc .mono { font-family: monospace; color: var(--color-muted); white-space: nowrap; }
  .i-mc .mc-msg { color: var(--color-text); }

  .renfort { display: flex; flex-direction: column; gap: 6px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 10px 12px; width: 100%; }
  .veh-check { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }
  .renfort-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 6px; }


  .non-arme { font-size: 9px; font-weight: 700; color: var(--color-danger); background: color-mix(in srgb, var(--color-danger) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
</style>
