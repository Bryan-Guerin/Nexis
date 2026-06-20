<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {realtime} from '../shared/realtime.js'
    import {currentUser} from '../shared/stores.js'
    import {toast} from '../shared/toasts.js'
    import Skeleton from '../shared/Skeleton.svelte'
    import Modal from '../shared/Modal.svelte'

    let stats   = $state(null)
  let journal = $state([])
  let statuts = $state([])          // statuts planning (pour la prise de garde rapide)
  let reloadTimer = null

  let isAdmin = $derived($currentUser?.roles?.includes('ROLE_ADMIN_SP') ?? false)

  // Fréquences radio (affichées à tous, gérées par les admins)
  let frequences = $state([])
  let freqForm   = $state({ description: '', frequence: '' })
  let freqError  = $state('')

  // Événements à venir / en cours
  let evenements = $state([])
  function fmtEvt(iso) {
    return new Date(iso).toLocaleString('fr-FR', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
  }

  // Vote intervention de la semaine
  let voteEtat = $state(null)        // { semaineDate, candidates[], monVote, gagnant }
  let voteOpen = $state(false)
  // Candidates toujours triées par numéro d'intervention croissant (tri naturel sur le code).
  const candidatesTriees = $derived(
    voteEtat ? [...voteEtat.candidates].sort((a, b) =>
      (a.code || '').localeCompare(b.code || '', undefined, { numeric: true })) : [])
  function fmtSemaine(iso) {
    if (!iso) return ''
    const d = new Date(iso + 'T12:00:00')
    const fin = new Date(d); fin.setDate(fin.getDate() + 6)
    const opt = { day: '2-digit', month: 'short' }
    return `${d.toLocaleDateString('fr-FR', opt)} → ${fin.toLocaleDateString('fr-FR', opt)}`
  }
  async function voter(interId) {
    try {
      voteEtat = await api.post(`/sp/vote/${interId}`)
      toast.success('Vote enregistré.')
    } catch { /* toast par api.js */ }
  }
  async function retirerVote() {
    try { voteEtat = await api.delete('/sp/vote'); toast.info('Vote retiré.') }
    catch { /* toast par api.js */ }
  }

  // Accordéon dans la modale de vote : 1 intervention détaillée à la fois.
  let expandedInter = $state(null)        // id de l'intervention dépliée
  let interDetails  = $state({})          // cache : interId -> { engins[], journal[] }
  async function toggleExpand(interId) {
    if (expandedInter === interId) { expandedInter = null; return }
    expandedInter = interId
    if (!interDetails[interId]) {
      const [inter, journal] = await Promise.all([
        api.get(`/sp/interventions/${interId}`).catch(() => null),
        api.get(`/sp/interventions/${interId}/journal`).catch(() => []),
      ])
      interDetails = { ...interDetails, [interId]: { inter, journal } }
    }
  }
  function fmtHeure(iso) { return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) }

  // Logo caserne (paramétrable par instance : fichier déposé dans le volume,
  // servi sur /branding/sp-logo.png). Masqué si aucun fichier n'est présent.
  let showLogo = $state(true)

  // Prise de garde rapide
  let gardeDuree = $state(2)        // heures
  let gardeError = $state('')
  let gardeBusy  = $state(false)
  let gardeStatut = $derived(statuts.find(s => s.categorie === 'GARDE'))
  // Catégorie de service en cours du membre connecté : 'GARDE' / 'ASTREINTE' / null
  let monService = $state(null)

  const LIVE = ['AFFECTATION', 'DESAFFECTATION', 'ETAT_VEHICULE', 'INVENTAIRE', 'PLANNING',
                'INTERVENTION_OUVERTE', 'INTERVENTION_RENFORT', 'INTERVENTION_CLOTUREE']

  onMount(() => {
    load()
    const off = realtime.on(ev => {
      if (ev.faction === 'SP' && LIVE.includes(ev.type)) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(load, 500)
      }
    })
    // Mon service (garde/astreinte) se termine à une heure donnée sans événement →
    // rafraîchit l'état pour masquer « Terminer ma garde » une fois le créneau écoulé.
    const tick = setInterval(async () => {
      monService = (await api.get('/sp/planning/me/service-courant').catch(() => ({ categorie: null })))?.categorie ?? null
    }, 60000)
    return () => { off(); clearInterval(tick) }
  })

  async function load() {
    try {
      const [s, j, st, sc, fq, ev, vt] = await Promise.all([
        api.get('/sp/dashboard'),
        api.get('/sp/journal?limit=8'),
        api.get('/sp/planning/statuts').catch(() => []),
        api.get('/sp/planning/me/service-courant').catch(() => ({ categorie: null })),
        api.get('/sp/frequences').catch(() => []),
        api.get('/sp/evenements').catch(() => []),
        api.get('/sp/vote/semaine-courante').catch(() => null),
      ])
      frequences = fq ?? []
      evenements = ev ?? []
      voteEtat   = vt
      // Robustesse : les collections vides peuvent être omises du JSON
      stats = {
        ...s,
        flotte: s.flotte ?? [], garde: s.garde ?? [], enginsEngages: s.enginsEngages ?? [],
        alertes: s.alertes ?? [], interventionsEnCours: s.interventionsEnCours ?? [], activite7j: s.activite7j ?? [],
      }
      journal = j ?? []; statuts = st ?? []; monService = sc?.categorie ?? null
    } catch { /* toast par api.js */ }
  }

  async function declarerPresence(evId, present) {
    try {
      await api.put(`/sp/evenements/${evId}/reponse?present=${present}`)
      evenements = await api.get('/sp/evenements').catch(() => evenements)
    } catch { /* toast par api.js */ }
  }

  async function addFrequence() {
    freqError = ''
    if (!freqForm.description.trim() || !freqForm.frequence.trim()) { freqError = 'Description et fréquence requises'; return }
    try {
      const created = await api.post('/sp/frequences', { description: freqForm.description, frequence: freqForm.frequence })
      frequences = [...frequences, created]
      freqForm = { description: '', frequence: '' }
    } catch (e) { freqError = e.message }
  }
  async function deleteFrequence(id) {
    try { await api.delete(`/sp/frequences/${id}`); frequences = frequences.filter(f => f.id !== id) }
    catch (e) { freqError = e.message }
  }

  async function prendreGarde() {
    gardeError = ''
    if (!gardeStatut) { gardeError = 'Aucun statut de garde configuré'; return }
    gardeBusy = true
    try {
      const debut = new Date()
      const fin = new Date(debut.getTime() + gardeDuree * 3600_000)
      await api.post('/sp/planning/me', {
        statutId: gardeStatut.id, debut: debut.toISOString(), fin: fin.toISOString(), notes: null,
      })
      await load()
    } catch (e) { gardeError = e.message }
    finally { gardeBusy = false }
  }

  async function terminerGarde() { await actionGarde('/sp/planning/me/terminer-garde') }
  async function passerGarde()   { await actionGarde('/sp/planning/me/basculer/garde') }
  async function passerAstreinte() { await actionGarde('/sp/planning/me/basculer/astreinte') }

  async function actionGarde(path) {
    gardeError = ''; gardeBusy = true
    try { await api.put(path); await load() }
    catch (e) { gardeError = e.message }
    finally { gardeBusy = false }
  }

  const TYPE = {
    AFFECTATION: 'Affectation', DESAFFECTATION: 'Retrait', ETAT_VEHICULE: 'Statut',
    INVENTAIRE: 'Inventaire', PLANNING: 'Garde', BIP: 'Bip', INTERVENTION_OUVERTE: 'Inter. ouverte',
    INTERVENTION_RENFORT: 'Renfort', INTERVENTION_CLOTUREE: 'Inter. clôturée',
  }
  function typeLabel(t) { return TYPE[t] ?? t }

  function heure(iso) { return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) }
  function depuis(iso) {
    // @ts-ignore
    const min = Math.floor((Date.now() - new Date(iso)) / 60000)
    if (min < 1) return "à l'instant"
    if (min < 60) return `il y a ${min} min`
    const h = Math.floor(min / 60)
    return `il y a ${h} h ${String(min % 60).padStart(2, '0')}`
  }
</script>

<div class="page">
  <div class="page-header">
    <div style="display:flex;align-items:center;gap:14px">
      {#if showLogo}
        <img src="/branding/sp-logo.png" alt="Logo caserne"
             style="height:46px;width:auto;max-width:160px;object-fit:contain"
             onerror={() => showLogo = false} />
      {/if}
      <h2>Sapeurs-Pompiers — Tableau de bord</h2>
    </div>
    <button class="btn-ghost" onclick={load}>Actualiser</button>
  </div>

  {#if !stats}
    <Skeleton rows={5} />
  {:else}
    <!-- KPIs essentiels (De garde · Interventions en cours) -->
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">De garde</span>
        <span class="stat-value live">{stats.deGarde}</span>
        <span class="stat-sub">/ {stats.effectifsActifs} effectifs actifs</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Interventions en cours</span>
        <span class="stat-value" class:alert={stats.interventionsEnCoursTotal > 0}>{stats.interventionsEnCoursTotal}</span>
      </div>
    </div>

    <!-- Garde rapide -->
    <section class="garde-quick">
      {#if monService === 'GARDE'}
        <span class="gq-label"><span class="garde-dot"></span> Vous êtes de garde</span>
        <button class="btn-ghost-sm" disabled={gardeBusy} onclick={passerAstreinte}>Passer en astreinte</button>
        <button class="btn-primary danger" disabled={gardeBusy} onclick={terminerGarde}>
          {gardeBusy ? '…' : 'Terminer ma garde'}
        </button>
      {:else if monService === 'ASTREINTE'}
        <span class="gq-label"><span class="garde-dot astreinte"></span> Vous êtes d'astreinte</span>
        <button class="btn-primary" disabled={gardeBusy} onclick={passerGarde}>
          {gardeBusy ? '…' : 'Passer en garde'}
        </button>
        <span class="gq-hint">Le reste du créneau passe en garde (min. 1 h)</span>
      {:else}
        <span class="gq-label">Prendre la garde maintenant</span>
        <select bind:value={gardeDuree}>
          <option value={1}>1 h</option>
          <option value={2}>2 h</option>
          <option value={3}>3 h</option>
          <option value={4}>4 h</option>
        </select>
        <button class="btn-primary" disabled={gardeBusy || !gardeStatut} onclick={prendreGarde}>
          {gardeBusy ? '…' : 'Prendre la garde'}
        </button>
        <span class="gq-hint">Quart d'heure entamé = compté (début 15h55 → 15h45)</span>
      {/if}
      {#if gardeError}<span class="inline-error">{gardeError}</span>{/if}

      <!-- Accès rapide aux autres écrans (déplacé depuis le bas de page) -->
      <div class="quick-links">
        <a href="#/sp/dispatch" class="quick-link">Dispatch →</a>
        <a href="#/sp/interventions" class="quick-link">Interventions →</a>
        <a href="#/sp/vehicules" class="quick-link">Véhicules →</a>
        <a href="#/sp/effectifs" class="quick-link">Effectifs →</a>
      </div>
    </section>

    <!-- Vote intervention de la semaine -->
    {#if voteEtat}
      <section class="panel vote-panel">
        <div class="vote-head">
          <h3>⭐ Intervention de la semaine</h3>
          <span class="vote-sem muted small">Semaine du {fmtSemaine(voteEtat.semaineDate)}</span>
        </div>
        {#if voteEtat.gagnant}
          <div class="vote-winner">
            <span class="vw-code mono">{voteEtat.gagnant.code}</span>
            <span class="vw-motif">{voteEtat.gagnant.motif}</span>
            {#if voteEtat.gagnant.natureLabel}<span class="vw-nat">{voteEtat.gagnant.natureLabel}</span>{/if}
            <span class="vw-votes">🗳️ {voteEtat.gagnant.votes} vote{voteEtat.gagnant.votes > 1 ? 's' : ''}</span>
          </div>
        {:else if voteEtat.candidates.length === 0}
          <p class="muted small">Aucune intervention clôturée la semaine précédente.</p>
        {:else}
          <p class="muted small">Aucun vote enregistré pour le moment.</p>
        {/if}
        {#if voteEtat.candidates.length > 0}
          <div class="vote-actions">
            <button class="btn-ghost-sm" onclick={() => voteOpen = true}>
              {voteEtat.monVote ? '✓ Modifier mon vote' : '🗳️ Voter'}
            </button>
            <span class="muted small">{voteEtat.candidates.length} candidate(s)</span>
          </div>
        {/if}
      </section>
    {/if}

    <div class="cols">
        <!-- Événements à venir / en cours -->
        {#if evenements.length > 0}
          <section class="panel">
            <h3>Événements</h3>
            <div class="evt-list">
              {#each evenements as ev (ev.id)}
                <div class="evt">
                  <div class="evt-head">
                    <span class="evt-titre">{ev.titre}</span>
                    <span class="evt-date">{fmtEvt(ev.date)}</span>
                  </div>
                  {#if ev.texte}<p class="evt-texte">{ev.texte}</p>{/if}
                  <div class="evt-foot">
                    <span class="evt-counts" title="Présents · Absents déclarés">✅ {ev.nbPresents} · ❌ {ev.nbAbsents}</span>
                    <div class="evt-actions">
                      <button class="evt-btn oui" class:on={ev.maPresence === true} onclick={() => declarerPresence(ev.id, true)}>Présent</button>
                      <button class="evt-btn non" class:on={ev.maPresence === false} onclick={() => declarerPresence(ev.id, false)}>Absent</button>
                    </div>
                  </div>
                </div>
              {/each}
            </div>
          </section>
        {/if}

        <!-- Fréquences radio -->
        {#if frequences.length > 0 || isAdmin}
          <section class="panel">
            <h3>Fréquences radio</h3>
            <div class="freq-list">
              {#each frequences as f (f.id)}
                <div class="freq-row">
                  <span class="freq-desc">{f.description}</span>
                  <span class="freq-val">{f.frequence}</span>
                  {#if isAdmin}<button class="rm-btn" title="Supprimer" onclick={() => deleteFrequence(f.id)}>×</button>{/if}
                </div>
              {/each}
              {#if frequences.length === 0}<p class="muted small">Aucune fréquence configurée.</p>{/if}
            </div>
            {#if isAdmin}
              <div class="freq-add">
                <input type="text" bind:value={freqForm.description} placeholder="Description (ex: Tactique 1)" />
                <input type="text" bind:value={freqForm.frequence} placeholder="150.1" style="width:90px" />
                <button class="btn-ghost-sm" onclick={addFrequence}>+ Ajouter</button>
              </div>
              {#if freqError}<span class="inline-error">{freqError}</span>{/if}
            {/if}
          </section>
        {/if}
    </div>

    <!-- ── OPÉRATIONNEL ────────────────────────────────────────────────── -->
    <div class="cols">
      <!-- Interventions en cours -->
      <section class="panel">
        <h3>Interventions en cours</h3>
        {#if stats.interventionsEnCours.length === 0}
          <p class="muted small">Aucune intervention en cours.</p>
        {:else}
          <ul class="inter-list">
            {#each stats.interventionsEnCours as i (i.code)}
              <li class="inter">
                <span class="inter-code">{i.code}</span>
                {#if i.nature}<span class="chip-code">{i.nature}</span>{/if}
                <span class="inter-motif">{i.motif}</span>
                <span class="inter-meta">🚒 {i.engins} · {depuis(i.debut)}</span>
              </li>
            {/each}
          </ul>
        {/if}
      </section>

      <!-- Engins engagés -->
      <section class="panel">
        <h3>Engins engagés</h3>
        {#if stats.enginsEngages.length === 0}
          <p class="muted small">Aucun engin engagé.</p>
        {:else}
          <ul class="eng-list">
            {#each stats.enginsEngages as e}
              <li class="eng">
                <span class="eng-dot" style="background:{e.couleur}"></span>
                <span class="eng-lib">{e.libelle}</span>
                <span class="chip-code">{e.typeCode}</span>
                <span class="eng-statut" style="color:{e.couleur}">{e.statut}</span>
                <span class="eng-code">{e.interventionCode}</span>
              </li>
            {/each}
          </ul>
        {/if}
      </section>
    </div>

    <div class="cols">
      <!-- Personnel de garde -->
      <section class="panel">
        <h3>De garde <span class="muted small">— {stats.garde.length}</span></h3>
        {#if stats.garde.length === 0}
          <p class="muted small">Personne de garde actuellement.</p>
        {:else}
          <div class="chips">
            {#each stats.garde as m (m.matricule)}
              <span class="chip"><span class="garde-dot"></span><span class="chip-grade">{m.gradeCode}</span> {m.nomComplet || m.username}</span>
            {/each}
          </div>
        {/if}
      </section>

      <!-- Alertes flotte (véhicules indisponibles hors intervention) -->
      <section class="panel">
        <h3>Alertes flotte</h3>
        {#if stats.alertes.length === 0}
          <p class="muted small">Aucun véhicule indisponible (hors intervention).</p>
        {:else}
          <ul class="alert-list">
            {#each stats.alertes as a}
              <li class="alert-row">
                <span class="eng-lib">{a.libelle}</span>
                <span class="badge" style="background:{a.couleur}22; color:{a.couleur}">{a.etat}</span>
              </li>
            {/each}
          </ul>
        {/if}
      </section>
    </div>

    <!-- Activité récente (main courante) -->
    <section class="panel">
      <div class="panel-head">
        <h3>Activité récente</h3>
        <a href="#/sp/main-courante" class="link">Main courante →</a>
      </div>
      {#if journal.length === 0}
        <p class="muted small">Aucune activité.</p>
      {:else}
        <ul class="act-list">
          {#each journal as e (e.id)}
            <li class="act">
              <span class="act-time">{heure(e.creeLe)}</span>
              <span class="act-type">{typeLabel(e.type)}</span>
              <span class="act-msg">{e.message}</span>
              {#if e.acteurUsername}<span class="act-who">{e.acteurUsername}</span>{/if}
            </li>
          {/each}
        </ul>
      {/if}
    </section>

  {/if}
</div>

<!-- Modale de vote : liste des candidates avec accordéon (détails 1 à la fois) -->
{#if voteOpen && voteEtat}
  <Modal title="Vote intervention de la semaine" width="640px" onclose={() => voteOpen = false}>
    <p class="muted small">Semaine du {fmtSemaine(voteEtat.semaineDate)} · 1 vote par personne. Clique sur une intervention pour voir le détail.</p>
    <div class="vote-list">
      {#each candidatesTriees as c (c.interventionId)}
        {@const mine    = voteEtat.monVote === c.interventionId}
        {@const open    = expandedInter === c.interventionId}
        {@const details = interDetails[c.interventionId]}
        <div class="vote-cand" class:mine class:open>
          <button class="vc-head" onclick={() => toggleExpand(c.interventionId)} aria-expanded={open}>
            <span class="vc-chev" class:open>▾</span>
            <div class="vc-main">
              <span class="vc-code mono">{c.code}</span>
              <span class="vc-motif">{c.motif}</span>
              {#if c.natureLabel}<span class="vc-nat">{c.natureLabel}</span>{/if}
              {#if c.commune}<span class="muted small">· {c.commune}</span>{/if}
            </div>
            <span class="vc-votes">{c.votes}</span>
          </button>
          <div class="vc-foot">
            {#if mine}
              <span class="vc-mine">Mon vote</span>
            {:else}
              <button class="btn-ghost-sm" onclick={() => voter(c.interventionId)}>🗳️ Voter</button>
            {/if}
          </div>
          {#if open}
            <div class="vc-detail">
              {#if !details}
                <p class="muted small">Chargement…</p>
              {:else}
                {@const engins = details.inter?.enginsHisto ?? []}
                {#if engins.length}
                  <div class="vc-section">
                    <span class="vc-sec-l">🚒 Engins &amp; équipage ({engins.length})</span>
                    <div class="vc-histo">
                      {#each engins as e}
                        <div class="vc-histo-engin">
                          <span class="vc-engin">{e.libelle}{#if e.typeCode} · {e.typeCode}{/if}</span>
                          {#if e.equipage.length}
                            <span class="vc-crew">{e.equipage.map(m => `${m.grade} ${m.nom}`).join(', ')}</span>
                          {/if}
                        </div>
                      {/each}
                    </div>
                  </div>
                {/if}
                {#if details.journal?.length}
                  <div class="vc-section">
                    <span class="vc-sec-l">📜 Main courante (extraits)</span>
                    <ul class="vc-journal">
                      {#each details.journal.slice(0, 6) as j (j.id)}
                        <li><span class="vc-h">{fmtHeure(j.creeLe)}</span> <span>{j.message}</span></li>
                      {/each}
                      {#if details.journal.length > 6}<li class="muted small">+ {details.journal.length - 6} autre(s) entrée(s)</li>{/if}
                    </ul>
                  </div>
                {/if}
                {#if !engins.length && !details.journal?.length}
                  <p class="muted small">Pas de détails additionnels.</p>
                {/if}
              {/if}
            </div>
          {/if}
        </div>
      {/each}
    </div>
    {#snippet actions()}
      {#if voteEtat.monVote}
        <button class="btn-ghost-sm" onclick={retirerVote}>Retirer mon vote</button>
      {/if}
      <button class="btn-primary" onclick={() => voteOpen = false}>Fermer</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
  .stat-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; display: flex; flex-direction: column; gap: 4px; }
  .stat-label { font-size: 11px; text-transform: uppercase; letter-spacing: .5px; color: var(--color-muted); }
  .stat-value { font-size: 30px; font-weight: 700; line-height: 1; }
  .stat-value.live { color: var(--color-success); }
  .stat-value.alert { color: var(--color-danger); }
  .stat-sub { font-size: 11px; color: var(--color-muted); }

  .garde-quick { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 12px; padding: 12px 16px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .gq-label { font-weight: 600; font-size: 13px; }
  .gq-hint { font-size: 11px; color: var(--color-muted); }
  .garde-quick .danger { background: var(--color-danger); border-color: var(--color-danger); }
  .garde-quick select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }

  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
  @media (max-width: 768px) { .cols { grid-template-columns: 1fr; } }
  .panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; }
  .panel h3 { margin: 0 0 12px; font-size: 14px; }
  .panel-head { display: flex; align-items: center; justify-content: space-between; }
  .panel-head h3 { margin-bottom: 12px; }
  .link { font-size: 12px; color: var(--accent); text-decoration: none; }

  .chips { display: flex; flex-wrap: wrap; gap: 8px; }
  .chip { font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; padding: 3px 10px; display: inline-flex; gap: 6px; align-items: center; }
  .chip-grade { color: var(--color-muted); font-size: 10px; }
  .garde-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-success); flex-shrink: 0; }
  .garde-dot.astreinte { background: #e0a23c; }

  .eng-list, .alert-list, .inter-list, .act-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
  .eng { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .eng-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
  .eng-lib { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .eng-statut { font-size: 12px; font-weight: 600; }
  .eng-code { font-family: monospace; font-size: 11px; color: var(--color-muted); }
  .alert-row { display: flex; align-items: center; justify-content: space-between; font-size: 13px; }

  .inter { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .inter-code { font-family: monospace; font-weight: 600; color: var(--accent); }
  .inter-motif { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .inter-meta { font-size: 11px; color: var(--color-muted); white-space: nowrap; }

  .act { display: flex; align-items: center; gap: 10px; font-size: 12px; }
  .act-time { font-family: monospace; color: var(--color-muted); white-space: nowrap; }
  .act-type { min-width: 96px; font-weight: 600; color: var(--accent); }
  .act-msg { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .act-who { color: var(--color-muted); white-space: nowrap; }

  /* Dans la barre de garde : poussés à droite */
  .garde-quick .quick-links { display: flex; gap: 12px; margin: 0 0 0 auto; flex-wrap: wrap; }
  .quick-link { color: var(--accent); text-decoration: none; font-size: 13px; white-space: nowrap; }

  /* Événements */
  .evt-list { display: flex; flex-direction: column; gap: 10px; }
  .evt { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 10px 12px; }
  .evt-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; }
  .evt-titre { font-size: 14px; font-weight: 600; }
  .evt-date { font-size: 11px; color: var(--color-muted); white-space: nowrap; text-transform: capitalize; }
  .evt-texte { font-size: 12px; color: var(--color-muted); margin: 6px 0 0; white-space: pre-wrap; }
  .evt-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 8px; }
  .evt-counts { font-size: 12px; color: var(--color-muted); }
  .evt-actions { display: flex; gap: 6px; }
  .evt-btn { font-size: 12px; padding: 4px 12px; border-radius: var(--radius); border: 1px solid var(--color-border); background: none; color: var(--color-muted); cursor: pointer; transition: all .12s; }
  .evt-btn.oui.on { border-color: var(--color-success); color: var(--color-success); background: color-mix(in srgb, var(--color-success) 14%, transparent); font-weight: 600; }
  .evt-btn.non.on { border-color: var(--color-danger); color: var(--color-danger); background: color-mix(in srgb, var(--color-danger) 14%, transparent); font-weight: 600; }

  /* Fréquences radio */
  .freq-list { display: flex; flex-direction: column; gap: 6px; }
  .freq-row { display: flex; align-items: center; gap: 10px; padding: 6px 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .freq-desc { flex: 1; font-size: 13px; }
  .freq-val { font-family: monospace; font-weight: 700; color: var(--accent); font-size: 14px; }
  .freq-add { display: flex; gap: 8px; margin-top: 10px; }
  .freq-add input { flex: 1; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; outline: none; }
  .freq-add input:focus { border-color: var(--accent); }
  .freq-row .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .freq-row .rm-btn:hover { color: var(--color-danger); }

  /* Vote intervention de la semaine */
  .vote-panel { display: flex; flex-direction: column; gap: 10px; }
  .vote-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
  .vote-head h3 { margin: 0; }
  .vote-winner { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 12px; background: color-mix(in srgb, var(--accent) 10%, transparent); border-radius: var(--radius); border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent); }
  .vw-code { font-weight: 700; color: var(--accent); }
  .vw-motif { font-weight: 600; flex: 1; }
  .vw-nat { font-size: 11px; padding: 2px 8px; background: var(--color-bg); border-radius: 10px; color: var(--color-muted); }
  .vw-votes { font-size: 12px; color: var(--accent); font-weight: 600; }
  .vote-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .vote-list { display: flex; flex-direction: column; gap: 6px; max-height: 60vh; overflow-y: auto; }
  .vote-cand { display: flex; flex-direction: column; flex-shrink: 0; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .vote-cand.mine { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 8%, transparent); }
  .vote-cand.open { border-color: color-mix(in srgb, var(--accent) 60%, var(--color-border)); }
  .vc-head { display: flex; align-items: center; gap: 10px; padding: 10px 12px; background: none; border: none; color: inherit; font: inherit; cursor: pointer; text-align: left; width: 100%; }
  .vc-head:hover { background: var(--hover); }
  .vc-chev { color: var(--color-muted); font-size: 11px; transition: transform .15s; }
  .vc-chev.open { transform: rotate(180deg); }
  .vc-foot { display: flex; justify-content: flex-end; gap: 8px; padding: 0 12px 8px; }
  .vc-main { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .vc-code { font-size: 11px; color: var(--color-muted); }
  .vc-motif { font-weight: 500; }
  .vc-nat { font-size: 10px; color: var(--color-muted); }
  .vc-votes { font-family: monospace; font-size: 13px; font-weight: 700; color: var(--accent); min-width: 24px; text-align: right; }
  .vc-mine { font-size: 11px; color: var(--accent); font-weight: 600; align-self: center; }
  .vc-detail { padding: 0 14px 12px; border-top: 1px dashed var(--color-border); display: flex; flex-direction: column; gap: 10px; padding-top: 10px; }
  .vc-section { display: flex; flex-direction: column; gap: 6px; }
  .vc-sec-l { font-size: 11px; font-weight: 700; color: var(--color-muted); text-transform: uppercase; letter-spacing: .4px; }
  .vc-engin { font-size: 12px; font-weight: 600; }
  .vc-histo { display: flex; flex-direction: column; gap: 6px; }
  .vc-histo-engin { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 6px 10px; display: flex; flex-direction: column; gap: 2px; }
  .vc-crew { font-size: 11px; color: var(--color-muted); }
  .vc-journal { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
  .vc-journal li { display: flex; gap: 8px; }
  .vc-h { font-family: monospace; color: var(--color-muted); flex-shrink: 0; min-width: 38px; }
</style>
