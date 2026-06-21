<script>
  import {onMount} from 'svelte'
  import {push} from 'svelte-spa-router'
  import {api} from '../shared/api.js'
  import {confirm} from '../shared/confirm.js'
  import {realtime} from '../shared/realtime.js'
  import {currentUser} from '../shared/stores.js'
  import {can} from '../shared/roles.js'
  import {refNatures, refStatutsVeh, refMe} from '../shared/referentials.js'
  import {exportInterventionPdf} from './interventionsPdf.js'
  import Modal from '../shared/Modal.svelte'

  // Dossier d'intervention plein-cadre (route /sp/interventions/:id). Onglet Synthèse = l'ancien
  // panneau détail ; onglet Bilans à venir (Front B). Charge l'intervention par son id.
  let { params } = $props()

  let inter        = $state(null)
  let journal      = $state([])
  let statuts      = $state([])
  let affectations = $state([])
  let myMembreId   = $state(null)
  let natures      = $state([])
  let cris         = $state([])
  let criSaved     = $state({})
  let editing      = $state(false)
  let editForm     = $state({})
  let noteText     = $state('')
  let tab          = $state('synthese')
  let loading      = $state(true)
  let reloadTimer  = null

  // ── Bilans ──
  let victimes      = $state([])
  let bilans        = $state([])
  let famille       = $state('SAP')
  let victimeSel    = $state(null)
  let sapForm       = $state({})
  let sapSaved      = $state(false)
  let sapTimer      = null
  let showVictime   = $state(false)
  let victimeForm   = $state({})
  let editVictimeId = $state(null)
  let sectionIdx    = $state(0)

  // Méthode XABCDE — config de rendu (le modèle reste typé côté back ; ceci n'est que l'UI).
  // [clé, label, type, options?] ; type = bool | enum | num | text | textarea.
  const PERTE = [['FAIBLE', 'Faible'], ['IMPORTANTE', 'Importante']]
  const PUPILLE = [['MYDRIASE', 'Mydriase'], ['NORMALE', 'Normale'], ['MYOSIS', 'Myosis']]
  const SECTIONS = [
    ['x', 'X — Hémorragie', [
      ['presente', 'Hémorragie', 'bool'], ['active', 'Active', 'bool'], ['perte', 'Perte estimée', 'enum', PERTE],
      ['positionAllongee', 'Position allongée', 'bool'], ['compressionManuelle', 'Compression manuelle', 'bool'],
      ['pansementCompressif', 'Pansement compressif', 'bool'], ['garrot', 'Garrot', 'bool']]],
    ['a', 'A — Voies aériennes', [
      ['obstruction', 'Obstruction VA', 'bool'], ['extractionDigitale', 'Extraction digitale', 'bool'],
      ['basculeTete', 'Bascule de la tête', 'bool'], ['elevationMenton', 'Élévation du menton', 'bool'],
      ['canuleOroPharyngee', 'Canule oro-pharyngée', 'bool'], ['aspirationBuccale', 'Aspiration buccale', 'bool']]],
    ['b', 'B — Ventilation', [
      ['absenceOuFrFaible', 'Absence ventilation ou FR<6', 'bool'], ['irreguliere', 'Irrégulière / pause', 'bool'],
      ['superficielle', 'Superficielle', 'bool'], ['signesLutte', 'Signes de lutte', 'bool'], ['cyanose', 'Cyanose', 'bool'],
      ['difficultesParole', 'Difficultés parole', 'bool'], ['frequenceRespiratoire', 'Fréquence respiratoire', 'num'],
      ['spo2AirAmbiant', 'SpO2 air ambiant (%)', 'num'], ['spo2SousO2', 'SpO2 sous O2 (%)', 'num'],
      ['insufflations', 'Insufflations', 'bool'], ['inhalation', 'Inhalation', 'bool']]],
    ['c', 'C — Circulation', [
      ['arretCirculatoire', 'Arrêt circulatoire', 'bool'], ['malFrappe', 'Mal frappé', 'bool'],
      ['poulsRadialNonPercu', 'Pouls radial non perçu', 'bool'], ['irregulier', 'Irrégulier', 'bool'],
      ['froideurExtremites', 'Froideur des extrémités', 'bool'], ['paleurCutanee', 'Pâleur cutanée', 'bool'],
      ['trcSup3', 'TRC > 3s', 'bool'], ['frequenceCardiaque', 'Fréquence cardiaque', 'num'],
      ['pniD', 'PNI D', 'text'], ['pniG', 'PNI G', 'text'], ['pniRef', 'PNI Ref', 'text']]],
    ['d', 'D — Neurologique', [
      ['avpu', 'AVPU', 'enum', [['ALERT', 'Alert'], ['VERBAL', 'Verbal'], ['PAIN', 'Pain'], ['UNRESPONSIVE', 'Unresponsive']]],
      ['pci', 'PCI', 'bool'], ['convulsion', 'Convulsion', 'bool'],
      ['pupilleDroite', 'Pupille droite', 'enum', PUPILLE], ['pupilleGauche', 'Pupille gauche', 'enum', PUPILLE],
      ['pupillesReactives', 'Pupilles réactives', 'bool'], ['troubleSensitif', 'Trouble sensitif', 'bool'],
      ['troubleMoteur', 'Trouble moteur', 'bool'], ['resucrage', 'Resucrage', 'bool']]],
    ['e', 'E — Exposition', [
      ['chuteSup2m', 'Chute > 2m', 'bool'], ['traumatismePenetrant', 'Traumatisme pénétrant', 'bool'],
      ['sectionMembreTotale', 'Section de membre totale', 'bool'], ['plaieProfonde', 'Plaie profonde', 'bool'],
      ['brulureSup5pct', 'Brûlure surface > 5%', 'bool'], ['brulureElectrique', 'Brûlure électrique', 'bool'],
      ['brulureChimique', 'Brûlure chimique', 'bool'], ['localisationBrulureAggravante', 'Localisation brûlure aggravante', 'bool'],
      ['temperature', 'Température (°C)', 'num']]],
    ['avp', 'AVP', [
      ['situation', 'Situation à l\'arrivée', 'enum', [['EJECTEE', 'Éjectée'], ['INCARCEREE', 'Incarcérée'], ['PIEGEE', 'Piégée'], ['SORTIE_VEHICULE', 'Sortie du véhicule']]],
      ['cinetique', 'Cinétique (km/h)', 'num'],
      ['position', 'Position dans le véhicule', 'enum', [['CONDUCTEUR', 'Conducteur'], ['PASSAGER_AVANT', 'Passager avant'], ['PASSAGER_ARRIERE', 'Passager arrière']]],
      ['localisationChoc', 'Localisation du choc', 'enum', [['FRONTAL', 'Frontal'], ['LATERAL_GAUCHE', 'Latéral gauche'], ['LATERAL_DROIT', 'Latéral droit'], ['ARRIERE', 'Arrière'], ['AUTRE', 'Autre']]],
      ['casquee', 'Casquée', 'bool'], ['ceinturee', 'Ceinturée', 'bool'], ['tonneaux', 'Tonneaux', 'bool']]],
    ['sample', 'SAMPLE', [
      ['symptomes', 'S — Symptômes', 'textarea'], ['allergies', 'A — Allergies', 'textarea'],
      ['medicaments', 'M — Médicaments', 'textarea'], ['dernierRepas', 'L — Dernier repas (heure)', 'text'],
      ['evenements', 'Événements avant l\'urgence', 'textarea'], ['observations', 'Observations particulières', 'textarea']]],
    ['schema', 'Schéma corporel', []],
  ]
  function emptySap() { return { x: {}, a: {}, b: {}, c: {}, d: {}, e: {}, avp: {}, sample: {}, lesions: [] } }
  function intoSap(contenu) {
    const s = emptySap()
    if (contenu) for (const k of Object.keys(s)) if (contenu[k] != null) s[k] = k === 'lesions' ? contenu[k] : { ...contenu[k] }
    return s
  }

  // Schéma corporel : on marque des points de lésion (x,y normalisés 0..1) sur une silhouette.
  const LESION_TYPES = [
    ['DEFORMATION', 'Déformation'], ['CONTUSION', 'Contusion'], ['ABRASION', 'Abrasion'],
    ['HEMORRAGIE', 'Hémorragie'], ['PLAIE', 'Plaie'], ['BRULURE', 'Brûlure'],
    ['TUMEFACTION', 'Tuméfaction'], ['LACERATION', 'Lacération'], ['DOULEUR', 'Sensibilité / douleur'],
  ]
  const COULEUR_LESION = {
    DEFORMATION: '#a259e2', CONTUSION: '#378add', ABRASION: '#1d9e75', HEMORRAGIE: '#e24b4a',
    PLAIE: '#d8492f', BRULURE: '#ef9f27', TUMEFACTION: '#d4537e', LACERATION: '#b07a17', DOULEUR: '#888888',
  }
  let lesionType = $state('PLAIE')
  let svgEl
  function ajouterLesion(e) {
    const r = svgEl.getBoundingClientRect()
    const x = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width))
    const y = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height))
    sapForm.lesions = [...(sapForm.lesions ?? []), { x, y, type: lesionType }]
    onSapChange()
  }
  function retirerLesion(i) {
    sapForm.lesions = (sapForm.lesions ?? []).filter((_, idx) => idx !== i)
    onSapChange()
  }

  // Grand écran : schéma figé à gauche + questions à droite (schéma hors stepper). Petit écran :
  // schéma redevient une section du stepper. (schema est en dernier → simple slice.)
  let wide = $state(false)
  const navSections = $derived(wide ? SECTIONS.filter(s => s[0] !== 'schema') : SECTIONS)
  $effect(() => { if (sectionIdx >= navSections.length) sectionIdx = navSections.length - 1 })
  const currentSec = $derived(navSections[sectionIdx] ?? navSections[0])

  let isAdmin      = $derived($currentUser?.roles?.includes('ROLE_ADMIN_SP') ?? false)
  let isDispatcher = $derived($can.dispatch)

  const RENFORT_OPTS = [['NON_PREVENU', 'Non prévenu'], ['PREVENU', 'Prévenu'], ['SUR_PLACE', 'Sur place']]
  const CRI_LABEL = { BROUILLON: 'Brouillon', SOUMIS: 'Soumis', VALIDE: 'Validé' }
  function renfortLabel(v) { return (RENFORT_OPTS.find(o => o[0] === v) ?? [, v])[1] }
  function fmt(iso) { return iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—' }
  function fmtCoord(c) { return c && c.length === 6 ? c.slice(0, 3) + ' ' + c.slice(3) : (c || '—') }

  async function load() {
    loading = true
    const id = params.id
    try {
      let me
      ;[inter, journal, statuts, affectations, me, cris, natures] = await Promise.all([
        api.get(`/sp/interventions/${id}`),
        api.get(`/sp/interventions/${id}/journal`).catch(() => []),
        refStatutsVeh().catch(() => []),
        api.get('/sp/affectations').catch(() => []),
        refMe().catch(() => null),
        api.get(`/sp/interventions/${id}/cri`).catch(() => []),
        refNatures().catch(() => []),
      ])
      myMembreId = me?.id ?? null
      await loadBilansVictimes()
    } catch { /* toast par api.js */ }
    finally { loading = false }
  }

  async function loadBilansVictimes(rafraichirSaisie = false) {
    const id = params.id
    ;[victimes, bilans] = await Promise.all([
      api.get(`/sp/interventions/${id}/victimes`).catch(() => []),
      api.get(`/sp/interventions/${id}/bilans`).catch(() => []),
    ])
    if (!victimeSel && victimes.length) selectVictime(victimes[0])
    // MAJ par un autre équipier : rafraîchit les champs de la victime affichée, sauf si une saisie
    // locale est en cours (timer débounce actif) — pour ne pas écraser ce qu'on tape.
    else if (rafraichirSaisie && victimeSel && !sapTimer) {
      sapForm = intoSap(bilanSapDe(victimeSel)?.contenu)
    }
  }

  function bilanSapDe(victimeId) { return bilans.find(b => b.famille === 'SAP' && b.victimeId === victimeId) }
  function victimeNom(v) { return [v.nom, v.prenom].filter(Boolean).join(' ') || v.libelle || `Victime ${v.numero}` }

  function selectVictime(v) {
    victimeSel = v.id
    sapForm = intoSap(bilanSapDe(v.id)?.contenu)
    sectionIdx = 0
    sapSaved = false
  }
  function onSapChange() {
    sapSaved = false
    clearTimeout(sapTimer)
    sapTimer = setTimeout(saveSap, 600)
  }
  async function saveSap() {
    if (!victimeSel) return
    sapTimer = null
    try {
      const b = await api.put(`/sp/victimes/${victimeSel}/bilan-sap`, sapForm)
      bilans = [...bilans.filter(x => x.id !== b.id), b]
      sapSaved = true
      setTimeout(() => { sapSaved = false }, 2000)
    } catch { /* toast par api.js */ }
  }

  function openAjoutVictime() { editVictimeId = null; victimeForm = { libelle: '', nom: '', prenom: '', sexe: '' }; showVictime = true }
  function openEditVictime(v) { editVictimeId = v.id; victimeForm = { libelle: v.libelle ?? '', nom: v.nom ?? '', prenom: v.prenom ?? '', sexe: v.sexe ?? '' }; showVictime = true }
  async function submitVictime() {
    const payload = { libelle: victimeForm.libelle || null, nom: victimeForm.nom || null, prenom: victimeForm.prenom || null, sexe: victimeForm.sexe || null }
    try {
      if (editVictimeId) {
        const u = await api.put(`/sp/victimes/${editVictimeId}`, payload)
        victimes = victimes.map(v => v.id === u.id ? u : v)
      } else {
        const c = await api.post(`/sp/interventions/${params.id}/victimes`, payload)
        victimes = [...victimes, c]; selectVictime(c)
      }
      showVictime = false
    } catch { /* toast par api.js */ }
  }

  async function refresh() {
    const id = params.id
    const [i, j, c] = await Promise.all([
      api.get(`/sp/interventions/${id}`).catch(() => inter),
      api.get(`/sp/interventions/${id}/journal`).catch(() => []),
      api.get(`/sp/interventions/${id}/cri`).catch(() => []),
    ])
    inter = i; journal = j; cris = c
  }

  onMount(() => {
    load()
    const mq = window.matchMedia('(min-width: 900px)')
    wide = mq.matches
    const onMq = e => wide = e.matches
    mq.addEventListener('change', onMq)
    const unsub = realtime.on(ev => {
      if (ev.faction === 'SP' && (ev.type?.startsWith('INTERVENTION_') || ev.type === 'ETAT_VEHICULE'
          || ev.type === 'AFFECTATION' || ev.type === 'DESAFFECTATION' || ev.type === 'MAIN_COURANTE')) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(refresh, 300)
      }
      // Bilan modifié par un AUTRE équipier → recharge + rafraîchit les champs affichés.
      // (Mon propre event est ignoré : j'ai déjà l'état à jour, pas de faux « ✓ Enregistré ».)
      if (ev.faction === 'SP' && ev.type === 'BILAN_MAJ' && ev.payload?.interventionId === params.id
          && ev.actorUsername !== $currentUser?.username) {
        loadBilansVictimes(true)
      }
    })
    return () => { unsub(); clearTimeout(reloadTimer); mq.removeEventListener('change', onMq) }
  })

  function startEdit() {
    editForm = { motif: inter.motif, natureId: inter.nature?.id ?? '', requerant: inter.requerant ?? '',
      telephone: inter.telephone ?? '', observation: inter.observation ?? '', commune: inter.commune ?? '',
      coordonnees: inter.coordonnees ?? '' }
    editing = true
  }
  async function submitEdit() {
    try {
      await api.patch(`/sp/interventions/${inter.id}`, {
        motif: editForm.motif, natureId: editForm.natureId || null, requerant: editForm.requerant,
        telephone: editForm.telephone, observation: editForm.observation, commune: editForm.commune, coordonnees: editForm.coordonnees,
      })
      editing = false; await refresh()
    } catch { /* toast par api.js */ }
  }
  async function retirerEngin(engin) {
    if (!await confirm({ title: 'Retirer l\'engin', message: `Retirer ${engin.libelle} de l'intervention ?`, danger: true })) return
    try { await api.delete(`/sp/interventions/${inter.id}/engins/${engin.vehiculeId}`); await refresh() }
    catch { /* toast par api.js */ }
  }

  function canEditCri(cri) {
    return cri.statut !== 'VALIDE' && (isAdmin || affectations.some(a => a.vehiculeId === cri.vehiculeId && a.membreId === myMembreId))
  }
  async function criAutoSave(cri) {
    try {
      await api.put(`/sp/cri/${cri.id}`, { contenu: cri.contenu })
      criSaved = { ...criSaved, [cri.id]: true }
      setTimeout(() => { criSaved = { ...criSaved, [cri.id]: false } }, 1800)
    } catch { /* silencieux */ }
  }
  async function criSoumettre(cri) {
    try { await api.put(`/sp/cri/${cri.id}`, { contenu: cri.contenu }); await api.put(`/sp/cri/${cri.id}/soumettre`); await refresh() }
    catch { /* toast par api.js */ }
  }
  async function criValider(cri) {
    try { await api.put(`/sp/cri/${cri.id}/valider`); await refresh() }
    catch { /* toast par api.js */ }
  }

  async function changeRenfort(cible, statut) {
    try { await api.put(`/sp/interventions/${inter.id}/renfort`, cible === 'GN' ? { renfortGn: statut } : { renfortVinci: statut }); await refresh() }
    catch { /* toast par api.js */ }
  }

  function canControl(engin) { return isAdmin || affectations.some(a => a.vehiculeId === engin.vehiculeId && a.membreId === myMembreId) }
  let canNote = $derived(inter && (isAdmin || inter.engins.some(e => affectations.some(a => a.vehiculeId === e.vehiculeId && a.membreId === myMembreId))))
  function statutOptions(engin) { return statuts.filter(s => s.position >= engin.statutPosition) }
  async function changeEnginStatut(engin, statutId) {
    if (!statutId || statutId === engin.statutId) return
    try { await api.put(`/sp/vehicules/${engin.vehiculeId}/statut?statutId=${statutId}`); await refresh() }
    catch { /* toast par api.js */ }
  }
  async function addNote() {
    if (!noteText.trim()) return
    try { await api.post(`/sp/interventions/${inter.id}/journal`, { message: noteText }); noteText = ''; await refresh() }
    catch { /* toast par api.js */ }
  }
  function exportPdf() { exportInterventionPdf(inter, journal, cris) }
  function exportPdfDetaille() {
    const detail = victimes.map(v => {
      const c = bilanSapDe(v.id)?.contenu ?? {}
      return {
        titre: victimeNom(v) + (v.sexe ? ` (${v.sexe})` : ''),
        lignes: [
          ['Hémorragie', c.hemorragie == null ? '—' : (c.hemorragie ? 'Oui' : 'Non')],
          ['Perte estimée', c.perteEstimee || '—'],
          ['Fréquence respiratoire', c.frequenceRespiratoire ?? '—'],
          ['Observations', c.observations || '—'],
        ],
      }
    })
    exportInterventionPdf(inter, journal, cris, detail)
  }
</script>

<div class="dossier">
  {#if loading && !inter}
    <p class="muted small">Chargement…</p>
  {:else if !inter}
    <p class="muted small">Intervention introuvable.</p>
    <button class="btn-ghost-sm" onclick={() => push('/sp/interventions')}>← Retour</button>
  {:else}
    <div class="topbar">
      <button class="btn-ghost-sm" onclick={() => push('/sp/interventions')}>← Retour</button>
      <div class="topbar-title">
        <span class="i-code">{inter.code}</span>
        <span class="i-motif">{inter.motif}</span>
        <span class="status-chip" class:closed={!inter.enCours}>{inter.enCours ? 'En cours' : 'Clôturée'}</span>
      </div>
      <div class="topbar-actions">
        {#if isDispatcher && inter.enCours && !editing}
          <button class="btn-ghost-sm" onclick={startEdit}>Éditer</button>
        {/if}
        <button class="btn-ghost-sm" onclick={exportPdf}>⤓ PDF synthèse</button>
        <button class="btn-ghost-sm" onclick={exportPdfDetaille}>⤓ PDF détaillé</button>
      </div>
    </div>

    <div class="tabs" role="tablist">
      <button role="tab" aria-selected={tab === 'synthese'} class:on={tab === 'synthese'} onclick={() => tab = 'synthese'}>Synthèse</button>
      <button role="tab" aria-selected={tab === 'bilans'} class:on={tab === 'bilans'} onclick={() => tab = 'bilans'}>Bilans</button>
    </div>

    {#if tab === 'synthese'}
      <div class="synthese">
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
              <label>Coordonnées<input type="text" inputmode="numeric" maxlength="7" value={fmtCoord(editForm.coordonnees)} oninput={e => editForm.coordonnees = e.target.value.replace(/\D/g, '').slice(0, 6)} /></label>
            </div>
            <label class="full">Observation<input type="text" bind:value={editForm.observation} /></label>
            <div class="modal-actions">
              <button class="btn-ghost-sm" onclick={() => editing = false}>Annuler</button>
              <button class="btn-primary" onclick={submitEdit}>Enregistrer</button>
            </div>
          </div>
        {:else}
          <div class="detail-grid">
            <div><span class="dl">Nature</span> {inter.nature ? inter.nature.label : '—'}</div>
            <div><span class="dl">Début</span> {fmt(inter.debut)}</div>
            <div><span class="dl">Fin</span> {fmt(inter.fin)}</div>
            <div><span class="dl">Requérant</span> {inter.requerant ?? '—'}</div>
            <div><span class="dl">Téléphone</span> {inter.telephone ?? '—'}</div>
            <div><span class="dl">Commune</span> {inter.commune ?? '—'}</div>
            <div><span class="dl">Coordonnées</span> {fmtCoord(inter.coordonnees)}</div>
            <div><span class="dl">Victimes</span> {inter.nbVictimes ?? '—'}</div>
            <div><span class="dl">Incendie</span> {inter.incendie ? 'Oui' : 'Non'}</div>
            <div><span class="dl">Secours routier</span> {inter.sr ? 'Oui' : 'Non'}</div>
            <div><span class="dl">Véhicule impliqué</span> {inter.vehiculeImplique ? 'Oui' : 'Non'}</div>
            <div class="full"><span class="dl">Observation</span> {inter.observation ?? '—'}</div>
            <div class="full"><span class="dl">Créée par</span> {inter.creePar ?? '—'}</div>
          </div>
        {/if}

        <div class="mc">
          <span class="dl">Renforts</span>
          <div class="renfort-rows">
            <div class="renfort-row">
              <span class="renfort-cible">Gendarmerie</span>
              {#if inter.enCours}
                <select value={inter.renfortGn} onchange={e => changeRenfort('GN', e.target.value)}>
                  {#each RENFORT_OPTS as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
              {:else}<span>{renfortLabel(inter.renfortGn)}</span>{/if}
            </div>
            <div class="renfort-row">
              <span class="renfort-cible">VINCI</span>
              {#if inter.enCours}
                <select value={inter.renfortVinci} onchange={e => changeRenfort('VINCI', e.target.value)}>
                  {#each RENFORT_OPTS as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
              {:else}<span>{renfortLabel(inter.renfortVinci)}</span>{/if}
            </div>
          </div>
        </div>

        <div class="mc">
          <span class="dl">Engins{#if !inter.enCours} &amp; équipage{/if}</span>
          {#if inter.enCours}
            <div class="eng-rows">
              {#each inter.engins as e (e.vehiculeId)}
                <div class="eng-row">
                  <span class="eng-dot" style="background:{e.etatCouleur}"></span>
                  <span class="eng-name">{e.libelle}</span>
                  {#if canControl(e)}
                    <select class="eng-statut-sel" value={e.statutId} onchange={ev => changeEnginStatut(e, ev.target.value)}>
                      {#each statutOptions(e) as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
                    </select>
                  {:else}
                    <span class="eng-statut" style="color:{e.etatCouleur}">{e.etatLabel}</span>
                  {/if}
                  {#if isDispatcher && inter.engins.length > 1}
                    <button class="rm-btn" title="Retirer l'engin" onclick={() => retirerEngin(e)}>×</button>
                  {/if}
                </div>
              {/each}
              {#if inter.engins.length === 0}<span class="muted small">Aucun engin</span>{/if}
            </div>
          {:else}
            <div class="histo-engins">
              {#each inter.enginsHisto ?? [] as e}
                <div class="histo-engin">
                  <div class="histo-engin-head">
                    <span class="eng-name">{e.libelle}</span>
                    {#if e.typeCode}<span class="chip-code">{e.typeCode}</span>{/if}
                  </div>
                  {#if e.equipage.length > 0}
                    <ul class="histo-crew">
                      {#each e.equipage as m}
                        <li>
                          <span class="hc-grade">{m.grade}</span>
                          <span class="hc-nom">{m.nom}</span>
                          {#if m.matricule}<span class="mono">{m.matricule}</span>{/if}
                          {#if m.poste}<span class="hc-poste">· {m.poste}</span>{/if}
                        </li>
                      {/each}
                    </ul>
                  {:else}
                    <span class="muted small">Équipage non historisé</span>
                  {/if}
                </div>
              {/each}
              {#if (inter.enginsHisto ?? []).length === 0}<span class="muted small">Aucun engin historisé</span>{/if}
            </div>
          {/if}
        </div>

        <div class="mc">
          <span class="dl">Main courante</span>
          <div class="mc-list">
            {#each journal as ev (ev.id)}
              <div class="mc-line"><span class="mono">{fmt(ev.creeLe)}</span><span>{ev.message}</span>{#if ev.acteurUsername}<span class="muted">{ev.acteurUsername}</span>{/if}</div>
            {/each}
            {#if journal.length === 0}<span class="muted small">Aucun événement</span>{/if}
          </div>
          {#if inter.enCours && canNote}
            <div class="mc-add">
              <input type="text" bind:value={noteText} placeholder="Ajouter une note…"
                     onkeydown={e => { if (e.key === 'Enter') { e.preventDefault(); addNote() } }} />
              <button class="btn-ghost-sm" disabled={!noteText.trim()} onclick={addNote}>Ajouter</button>
            </div>
          {/if}
        </div>

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
                  <textarea rows="2" bind:value={cri.contenu} placeholder="Compte rendu du véhicule… (enregistré automatiquement)"
                            onblur={() => criAutoSave(cri)}></textarea>
                  <div class="cri-actions">
                    {#if criSaved[cri.id]}<span class="cri-saved">✓ Enregistré</span>{/if}
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
      </div>
    {:else}
      <div class="bilans">
        <div class="famille-chips">
          <button class:on={famille === 'SAP'} onclick={() => famille = 'SAP'}>SAP · secours à personne</button>
          <button class:on={famille === 'SR'} onclick={() => famille = 'SR'}>SR · secours routier</button>
          <button class:on={famille === 'INC'} onclick={() => famille = 'INC'}>INC · incendie</button>
        </div>

        {#if famille === 'SAP'}
          <div class="victimes-bar">
            {#each victimes as v (v.id)}
              <button class="vic" class:on={victimeSel === v.id} onclick={() => selectVictime(v)}>
                {victimeNom(v)}{#if v.sexe}<span class="vic-sexe">{v.sexe}</span>{/if}
              </button>
            {/each}
            <button class="btn-ghost-sm" onclick={openAjoutVictime}>+ Victime</button>
          </div>

          {#if victimeSel}
            {@const vsel = victimes.find(v => v.id === victimeSel)}
            {#snippet schemaView()}
              <div class="schema">
                <div class="lesion-palette">
                  {#each LESION_TYPES as [v, l]}
                    <button class:on={lesionType === v} style="--lc:{COULEUR_LESION[v]}" onclick={() => lesionType = v}>{l}</button>
                  {/each}
                </div>
                <p class="muted small">Clic sur la silhouette = poser une lésion. Clic sur un point = le retirer.</p>
                <svg bind:this={svgEl} class="silhouette" viewBox="0 0 120 260" onclick={ajouterLesion} role="img" aria-label="Schéma corporel">
                  <g class="body">
                    <circle cx="60" cy="26" r="18" />
                    <rect x="53" y="42" width="14" height="9" rx="3" />
                    <path d="M38 51 h44 a10 10 0 0 1 10 10 v48 a8 8 0 0 1 -8 8 h-48 a8 8 0 0 1 -8 -8 v-48 a10 10 0 0 1 10 -10 z" />
                    <rect x="20" y="55" width="12" height="62" rx="6" />
                    <rect x="88" y="55" width="12" height="62" rx="6" />
                    <rect x="45" y="116" width="13" height="84" rx="6" />
                    <rect x="62" y="116" width="13" height="84" rx="6" />
                  </g>
                  {#each (sapForm.lesions ?? []) as les, i}
                    <circle class="lesion-pt" cx={les.x * 120} cy={les.y * 260} r="5"
                            fill={COULEUR_LESION[les.type] ?? '#e24b4a'}
                            onclick={(e) => { e.stopPropagation(); retirerLesion(i) }} />
                  {/each}
                </svg>
                <span class="muted small">{(sapForm.lesions ?? []).length} lésion(s) marquée(s).</span>
              </div>
            {/snippet}
            <div class="sap-head">
              <span class="sap-titre">Bilan SAP — {victimeNom(vsel)}</span>
              {#if inter.enCours}<button class="btn-ghost-sm" onclick={() => openEditVictime(vsel)}>Éditer la victime</button>{/if}
              {#if sapSaved}<span class="saved">✓ Enregistré</span>{/if}
            </div>
            <div class="sap-2col" class:wide>
              {#if wide}<div class="sap-left">{@render schemaView()}</div>{/if}
              <div class="sap-right">
                <div class="sap-stepper">
                  {#each navSections as [sk], idx}
                    <button class:on={sectionIdx === idx} onclick={() => sectionIdx = idx}>{sk === 'sample' ? 'SAMPLE' : sk === 'avp' ? 'AVP' : sk === 'schema' ? 'Schéma' : sk.toUpperCase()}</button>
                  {/each}
                </div>
                {#if currentSec[0] === 'schema'}
              {@render schemaView()}
            {:else}
              <div class="sap-form">
                {#each currentSec[2] as [k, label, type, opts]}
                  <div class="sap-row" class:full={type === 'textarea'}>
                    <span class="sap-lib">{label}</span>
                    {#if type === 'bool'}
                      <span class="yn">
                        <button class:on={sapForm[currentSec[0]][k] === true} onclick={() => { sapForm[currentSec[0]][k] = true; onSapChange() }}>Oui</button>
                        <button class:on={sapForm[currentSec[0]][k] === false} onclick={() => { sapForm[currentSec[0]][k] = false; onSapChange() }}>Non</button>
                      </span>
                    {:else if type === 'enum'}
                      <select bind:value={sapForm[currentSec[0]][k]} onchange={onSapChange}>
                        <option value={null}>—</option>
                        {#each opts as [v, l]}<option value={v}>{l}</option>{/each}
                      </select>
                    {:else if type === 'num'}
                      <input type="number" bind:value={sapForm[currentSec[0]][k]} oninput={onSapChange} />
                    {:else if type === 'textarea'}
                      <textarea rows="2" bind:value={sapForm[currentSec[0]][k]} oninput={onSapChange}></textarea>
                    {:else}
                      <input type="text" bind:value={sapForm[currentSec[0]][k]} oninput={onSapChange} />
                    {/if}
                  </div>
                {/each}
              </div>
            {/if}
                <div class="sap-nav">
                  <button class="btn-ghost-sm" disabled={sectionIdx === 0} onclick={() => sectionIdx--}>← Précédent</button>
                  <span class="sap-progress">{sectionIdx + 1} / {navSections.length}</span>
                  <button class="btn-ghost-sm" disabled={sectionIdx === navSections.length - 1} onclick={() => sectionIdx++}>Suivant →</button>
                </div>
              </div>
            </div>
          {:else}
            <p class="muted small">Aucune victime. Ajoute-en une pour saisir un bilan SAP.</p>
          {/if}
        {:else}
          <p class="muted small">Bilan {famille} — structure à définir (coquille).</p>
        {/if}
      </div>
    {/if}
  {/if}
</div>

{#if showVictime}
  <Modal width="440px" title={editVictimeId ? 'Modifier la victime' : 'Nouvelle victime'} onclose={() => showVictime = false}>
    <div class="vic-form">
      <label>Position / rôle<input type="text" bind:value={victimeForm.libelle} placeholder="ex: Conducteur" maxlength="120" /></label>
      <div class="form-row">
        <label>Nom<input type="text" bind:value={victimeForm.nom} maxlength="80" /></label>
        <label>Prénom<input type="text" bind:value={victimeForm.prenom} maxlength="80" /></label>
      </div>
      <label>Sexe
        <select bind:value={victimeForm.sexe}>
          <option value="">— inconnu —</option>
          <option value="H">Homme</option>
          <option value="F">Femme</option>
        </select>
      </label>
    </div>
    {#snippet actions()}
      <button class="btn-ghost" onclick={() => showVictime = false}>Annuler</button>
      <button class="btn-primary" onclick={submitVictime}>{editVictimeId ? 'Enregistrer' : 'Ajouter'}</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .dossier { display: flex; flex-direction: column; gap: 14px; }
  .topbar { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
  .topbar-title { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; flex-wrap: wrap; }
  .topbar-actions { display: flex; gap: 8px; }
  .i-code { font-family: monospace; font-size: 13px; color: var(--accent); font-weight: 700; }
  .i-motif { font-size: 17px; font-weight: 600; }
  .status-chip { font-size: 11px; font-weight: 600; border-radius: 12px; padding: 2px 10px; background: color-mix(in srgb, var(--color-success) 16%, transparent); color: var(--color-success); }
  .status-chip.closed { background: color-mix(in srgb, var(--color-muted) 20%, transparent); color: var(--color-muted); }

  .tabs { display: flex; gap: 18px; border-bottom: 1px solid var(--color-border); }
  .tabs button { background: none; border: none; color: var(--color-muted); font-size: 14px; padding: 8px 2px; cursor: pointer; border-bottom: 2px solid transparent; }
  .tabs button.on { color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }

  .synthese { display: flex; flex-direction: column; gap: 8px; max-width: 900px; }
  .bilans-stub { padding: 24px 0; }

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

  .histo-engins { display: flex; flex-direction: column; gap: 8px; }
  .histo-engin { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 8px 10px; }
  .histo-engin-head { display: flex; align-items: center; gap: 8px; }
  .histo-crew { list-style: none; margin: 6px 0 0; padding: 0; display: flex; flex-direction: column; gap: 3px; }
  .histo-crew li { display: flex; align-items: center; gap: 8px; font-size: 12px; }
  .hc-grade { font-size: 10px; font-weight: 700; color: var(--color-muted); text-transform: uppercase; letter-spacing: .3px; min-width: 80px; }
  .hc-nom { font-weight: 500; }
  .hc-poste { color: var(--accent); font-size: 11px; }

  .mc-add { display: flex; gap: 8px; }
  .mc-add input { flex: 1; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 10px; outline: none; }

  .edit-form { display: flex; flex-direction: column; gap: 10px; margin: 8px 0; }
  .edit-form label { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .edit-form input, .edit-form select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .form-row { display: flex; gap: 10px; flex-wrap: wrap; }
  .form-row label { flex: 1; min-width: 140px; }
  .full { width: 100%; }
  .modal-actions { display: flex; gap: 8px; justify-content: flex-end; }
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
  .cri-actions { display: flex; gap: 8px; align-items: center; }
  .cri-saved { font-size: 11px; color: var(--color-success); font-weight: 600; }
  .cri-contenu { font-size: 13px; margin: 0; white-space: pre-wrap; }

  .renfort-rows { display: flex; gap: 24px; flex-wrap: wrap; }
  .renfort-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .renfort-cible { font-weight: 600; }
  .renfort-row select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 8px; }

  /* Bilans */
  .bilans { display: flex; flex-direction: column; gap: 12px; max-width: 960px; }
  .sap-2col { display: grid; gap: 18px; }
  .sap-2col.wide { grid-template-columns: max-content minmax(0, 1fr); align-items: start; }
  .sap-left { position: sticky; top: 8px; }
  .sap-right { min-width: 0; display: flex; flex-direction: column; gap: 12px; }
  .famille-chips { display: flex; gap: 8px; flex-wrap: wrap; }
  .famille-chips button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-muted); font-size: 13px; padding: 6px 12px; cursor: pointer; }
  .famille-chips button.on { background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .victimes-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  .vic { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 12px; cursor: pointer; display: inline-flex; gap: 6px; align-items: center; }
  .vic.on { border-color: var(--accent); color: var(--accent); }
  .vic-sexe { font-size: 10px; font-weight: 700; color: var(--color-muted); border: 1px solid var(--color-border); border-radius: 6px; padding: 0 4px; }
  .sap-head { display: flex; align-items: center; gap: 12px; }
  .sap-titre { font-weight: 600; font-size: 14px; }
  .saved { font-size: 11px; color: var(--color-success); font-weight: 600; }
  .sap-form { display: flex; flex-direction: column; gap: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px 14px; }
  .sap-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .sap-lib { font-size: 13px; }
  .sap-row select, .sap-row input { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 9px; width: 170px; }
  .yn { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .yn button { background: var(--color-surface); border: none; color: var(--color-muted); font-size: 12px; padding: 4px 14px; cursor: pointer; }
  .yn button.on { background: color-mix(in srgb, var(--accent) 20%, transparent); color: var(--accent); font-weight: 600; }
  .sap-stepper { display: flex; gap: 6px; flex-wrap: wrap; }
  .sap-stepper button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-muted); font-size: 12px; min-width: 34px; padding: 5px 8px; cursor: pointer; }
  .sap-stepper button.on { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .sap-row.full { flex-direction: column; align-items: stretch; gap: 5px; }
  .sap-row textarea { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; resize: vertical; width: 100%; }
  .sap-nav { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .sap-progress { font-size: 12px; color: var(--color-muted); }

  .schema { display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
  .lesion-palette { display: flex; gap: 6px; flex-wrap: wrap; }
  .lesion-palette button { background: var(--color-surface); border: 1px solid var(--color-border); border-left: 4px solid var(--lc); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .lesion-palette button.on { background: color-mix(in srgb, var(--lc) 18%, transparent); font-weight: 600; }
  .silhouette { width: 170px; height: 368px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); cursor: crosshair; }
  .silhouette .body { fill: var(--color-surface); stroke: var(--color-border); stroke-width: 1.5; }
  .lesion-pt { cursor: pointer; stroke: #fff; stroke-width: 1; }
  .vic-form { display: flex; flex-direction: column; gap: 10px; }
  .vic-form label { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .vic-form input, .vic-form select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
</style>
