<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {realtime} from '../shared/realtime.js'
    import {currentUser} from '../shared/stores.js'
    import Skeleton from '../shared/Skeleton.svelte'

    let stats   = $state(null)
  let journal = $state([])
  let statuts = $state([])          // statuts planning (pour la prise de garde rapide)
  let error   = $state('')
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
    return realtime.on(ev => {
      if (ev.faction === 'SP' && LIVE.includes(ev.type)) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(load, 500)
      }
    })
  })

  async function load() {
    error = ''
    try {
      const [s, j, st, sc, fq, ev] = await Promise.all([
        api.get('/sp/dashboard'),
        api.get('/sp/journal?limit=8'),
        api.get('/sp/planning/statuts').catch(() => []),
        api.get('/sp/planning/me/service-courant').catch(() => ({ categorie: null })),
        api.get('/sp/frequences').catch(() => []),
        api.get('/sp/evenements').catch(() => []),
      ])
      frequences = fq ?? []
      evenements = ev ?? []
      // Robustesse : les collections vides peuvent être omises du JSON
      stats = {
        ...s,
        flotte: s.flotte ?? [], garde: s.garde ?? [], enginsEngages: s.enginsEngages ?? [],
        alertes: s.alertes ?? [], interventionsEnCours: s.interventionsEnCours ?? [], activite7j: s.activite7j ?? [],
      }
      journal = j ?? []; statuts = st ?? []; monService = sc?.categorie ?? null
    } catch (e) { error = e.message }
  }

  async function declarerPresence(evId, present) {
    try {
      await api.put(`/sp/evenements/${evId}/reponse?present=${present}`)
      evenements = await api.get('/sp/evenements').catch(() => evenements)
    } catch (e) { error = e.message }
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
  function dureeTexte(min) {
    if (!min) return '—'
    const h = Math.floor(min / 60), m = min % 60
    return h > 0 ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`
  }
  function jourCourt(iso) {
    return new Date(iso + 'T12:00:00').toLocaleDateString('fr-FR', { weekday: 'short', day: '2-digit' })
  }
  function pct(n) { return stats && stats.vehiculesTotal > 0 ? (n / stats.vehiculesTotal) * 100 : 0 }
  let maxJour = $derived(stats ? Math.max(1, ...stats.activite7j.map(j => j.count)) : 1)
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

  {#if error}<p class="inline-error">{error}</p>{/if}

  {#if !stats}
    <Skeleton rows={5} />
  {:else}
    <!-- KPIs -->
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">De garde</span>
        <span class="stat-value live">{stats.deGarde}</span>
        <span class="stat-sub">/ {stats.effectifsActifs} effectifs actifs</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Véhicules disponibles</span>
        <span class="stat-value">{stats.vehiculesDisponibles}<span class="frac">/ {stats.vehiculesTotal}</span></span>
        <span class="stat-sub">{Math.round(pct(stats.vehiculesDisponibles))}% de la flotte</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Interventions en cours</span>
        <span class="stat-value" class:alert={stats.interventionsEnCoursTotal > 0}>{stats.interventionsEnCoursTotal}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Durée moy. (7 j)</span>
        <span class="stat-value">{dureeTexte(stats.dureeMoyenneMinutes)}</span>
        <span class="stat-sub">interventions clôturées</span>
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

    <div class="cols">
      <!-- Flotte par état -->
      <section class="panel">
        <h3>Disponibilité de la flotte</h3>
        {#if stats.vehiculesTotal === 0}
          <p class="muted small">Aucun véhicule enregistré.</p>
        {:else}
          <div class="fleet">
            {#each stats.flotte as e (e.code)}
              <div class="fleet-row">
                <span class="fleet-dot" style="background:{e.couleur}"></span>
                <span class="fleet-label">{e.label}</span>
                <div class="fleet-bar"><div class="fleet-fill" style="width:{pct(e.count)}%; background:{e.couleur}"></div></div>
                <span class="fleet-count">{e.count}</span>
              </div>
            {/each}
          </div>
        {/if}
      </section>

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
    </div>

    <div class="cols">
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

      <!-- Alertes flotte -->
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

      <!-- Tendance 7 jours -->
      <section class="panel">
        <h3>Interventions — 7 derniers jours</h3>
        <div class="trend">
          {#each stats.activite7j as j (j.jour)}
            <div class="trend-col">
              <span class="trend-n">{j.count}</span>
              <div class="trend-bar" style="height:{Math.round((j.count / maxJour) * 60)}px"></div>
              <span class="trend-day">{jourCourt(j.jour)}</span>
            </div>
          {/each}
        </div>
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

<style>
  .stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
  .stat-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; display: flex; flex-direction: column; gap: 4px; }
  .stat-label { font-size: 11px; text-transform: uppercase; letter-spacing: .5px; color: var(--color-muted); }
  .stat-value { font-size: 30px; font-weight: 700; line-height: 1; }
  .stat-value.live { color: var(--color-success); }
  .stat-value.alert { color: var(--color-danger); }
  .stat-value .frac { font-size: 15px; font-weight: 500; color: var(--color-muted); margin-left: 4px; }
  .stat-sub { font-size: 11px; color: var(--color-muted); }

  .garde-quick { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 12px; padding: 12px 16px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .gq-label { font-weight: 600; font-size: 13px; }
  .gq-hint { font-size: 11px; color: var(--color-muted); }
  .garde-quick .danger { background: var(--color-danger); border-color: var(--color-danger); }
  .garde-quick select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }

  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
  .panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; }
  .panel h3 { margin: 0 0 12px; font-size: 14px; }
  .panel-head { display: flex; align-items: center; justify-content: space-between; }
  .panel-head h3 { margin-bottom: 12px; }
  .link { font-size: 12px; color: var(--accent); text-decoration: none; }

  .fleet { display: flex; flex-direction: column; gap: 8px; }
  .fleet-row { display: flex; align-items: center; gap: 10px; font-size: 13px; }
  .fleet-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
  .fleet-label { width: 110px; flex-shrink: 0; }
  .fleet-bar { flex: 1; height: 8px; background: var(--color-bg); border-radius: 4px; overflow: hidden; }
  .fleet-fill { height: 100%; border-radius: 4px; transition: width .3s; }
  .fleet-count { width: 24px; text-align: right; font-family: monospace; color: var(--color-muted); }

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

  .trend { display: flex; align-items: flex-end; justify-content: space-between; gap: 6px; height: 100px; }
  .trend-col { display: flex; flex-direction: column; align-items: center; gap: 3px; flex: 1; justify-content: flex-end; }
  .trend-n { font-size: 11px; color: var(--color-muted); }
  .trend-bar { width: 70%; min-height: 2px; background: var(--accent); border-radius: 3px 3px 0 0; }
  .trend-day { font-size: 10px; color: var(--color-muted); }

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
</style>
