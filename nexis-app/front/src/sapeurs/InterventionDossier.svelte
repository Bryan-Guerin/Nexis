<script>
  import {onMount} from 'svelte'
  import {push} from 'svelte-spa-router'
  import {api} from '../shared/api.js'
  import {confirm} from '../shared/confirm.js'
  import {realtime} from '../shared/realtime.js'
  import {currentUser, criAValiderCount} from '../shared/stores.js'
  import {can} from '../shared/roles.js'
  import {refNatures, refStatutsVeh, refMe} from '../shared/referentials.js'
  import {exportInterventionPdf} from './interventionsPdf.js'
  import Modal from '../shared/Modal.svelte'
  import BilanIncForm from './BilanIncForm.svelte'

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
  // L'URL porte le code (ex. INT-0035) pour le partage ; l'UUID est l'identifiant interne utilisé
  // par toutes les API. Résolu au premier load via /by-code.
  let interventionId = $state(null)

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

  // ── Bilan SR (scène + véhicules vue de haut) ──
  let srForm     = $state({ routeType: 'AUTOROUTE_3V', vehicules: [] })
  let srVehSel   = $state(null)
  let srSaved    = $state(false)
  let srDraft    = $state(null)   // véhicule en cours de création (validé → posé sur la scène)
  let srDraftLink = null          // id victime à relier après création (depuis le SAP)
  let srTimer    = null
  let dragId     = null
  let srSvgEl

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
  function emptySap() { return { x: {}, a: {}, b: {}, c: {}, d: {}, e: {}, avp: {}, sample: {}, lesions: [], vehiculeSrId: null, triage: null } }
  function intoSap(contenu) {
    const s = emptySap()
    if (contenu) {
      for (const k of ['x', 'a', 'b', 'c', 'd', 'e', 'avp', 'sample']) if (contenu[k]) s[k] = { ...contenu[k] }
      if (Array.isArray(contenu.lesions)) s.lesions = contenu.lesions.map(l => ({ ...l }))
      if (contenu.vehiculeSrId != null) s.vehiculeSrId = contenu.vehiculeSrId
      if (contenu.triage != null) s.triage = contenu.triage
    }
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
  // Triage (urgence) : vert → jaune → rouge → noir. Pastille de victime colorée pour le récap.
  const TRIAGES = [['INDEMNE', 'Indemne'], ['UR', 'Urgence relative'], ['UA', 'Urgence absolue'], ['DECEDE', 'Décédé']]
  const COULEUR_TRIAGE = { INDEMNE: '#1d9e75', UR: '#e0a23c', UA: '#e24b4a', DECEDE: '#1e1e1e' }
  function triageDe(victimeId) { return bilanSapDe(victimeId)?.contenu?.triage ?? null }
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
  let peutValiderCri = $state(false)   // admin OU grade autorisé (sergent et +) — fetché au load

  // Statut clôture dérivé : EN_COURS → ATTENTE_CRI → ATTENTE_VALIDATION → CLOSE.
  let statutCloture = $derived.by(() => {
    if (!inter) return { label: '—', titre: '' }
    if (inter.enCours) return { label: 'En cours', titre: 'Intervention en cours' }
    const liste = cris ?? []
    if (liste.some(c => c.statut !== 'SOUMIS' && c.statut !== 'VALIDE'))
      return { label: 'En attente CRI', titre: 'Au moins un CRI reste à soumettre par son équipage' }
    if (liste.some(c => c.statut === 'SOUMIS'))
      return { label: 'En attente validation', titre: 'Tous les CRI soumis — en attente de validation par un grade autorisé' }
    return { label: 'Close', titre: 'Tous les CRI ont été validés' }
  })
  let isDispatcher = $derived($can.dispatch)

  const RENFORT_OPTS = [['NON_PREVENU', 'Non prévenu'], ['PREVENU', 'Prévenu'], ['SUR_PLACE', 'Sur place']]
  const CRI_LABEL = { BROUILLON: 'Brouillon', SOUMIS: 'Soumis', VALIDE: 'Validé' }
  function renfortLabel(v) { return (RENFORT_OPTS.find(o => o[0] === v) ?? [, v])[1] }
  function fmt(iso) { return iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—' }
  function fmtCoord(c) { return c && c.length === 6 ? c.slice(0, 3) + ' ' + c.slice(3) : (c || '—') }

  async function load() {
    loading = true
    try {
      // L'URL porte le code (INT-0035). On résout d'abord l'UUID, puis on charge le reste en parallèle.
      inter = await api.get(`/sp/interventions/by-code/${params.code}`)
      interventionId = inter.id
      let me
      ;[journal, statuts, affectations, me, cris, natures] = await Promise.all([
        api.get(`/sp/interventions/${interventionId}/journal`).catch(() => []),
        refStatutsVeh().catch(() => []),
        api.get('/sp/affectations').catch(() => []),
        refMe().catch(() => null),
        api.get(`/sp/interventions/${interventionId}/cri`).catch(() => []),
        refNatures().catch(() => []),
      ])
      myMembreId = me?.id ?? null
      // Resync du badge "à valider" à l'ouverture du dossier (filet de sécurité au-dessus du WS).
      try {
        const r = await api.get('/sp/cri/a-valider/count')
        peutValiderCri = r.peutValider
        criAValiderCount.set(r.peutValider ? r.count : 0)
      } catch { peutValiderCri = false }
      await loadBilansVictimes()
    } catch { /* toast par api.js */ }
    finally { loading = false }
  }

  async function loadBilansVictimes(rafraichirSaisie = false) {
    if (!interventionId) return
    ;[victimes, bilans] = await Promise.all([
      api.get(`/sp/interventions/${interventionId}/victimes`).catch(() => []),
      api.get(`/sp/interventions/${interventionId}/bilans`).catch(() => []),
    ])
    if (!victimeSel && victimes.length) selectVictime(victimes[0])
    // MAJ par un autre équipier : rafraîchit les champs de la victime affichée, sauf si une saisie
    // locale est en cours (timer débounce actif) — pour ne pas écraser ce qu'on tape.
    else if (rafraichirSaisie && victimeSel && !sapTimer) {
      sapForm = intoSap(bilanSapDe(victimeSel)?.contenu)
    }
    if (!srTimer) srForm = intoSr(bilanSrDe()?.contenu)   // SR : pas écrasé si saisie en cours
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
        const c = await api.post(`/sp/interventions/${interventionId}/victimes`, payload)
        victimes = [...victimes, c]; selectVictime(c)
      }
      showVictime = false
    } catch { /* toast par api.js */ }
  }

  // ── Bilan SR ──
  const ROUTES_SR = [['AUTOROUTE_3V', 'Autoroute (3 voies)'], ['BIDIRECTIONNEL_2V', 'Bidirectionnel (2 voies)']]
  const TYPES_SR  = [['VOITURE', 'Voiture'], ['CAMION', 'Camion'], ['UTILITAIRE', 'Utilitaire'], ['MOTO', 'Moto']]
  const CHOCS_SR  = [['FRONTAL', 'Frontal'], ['LATERAL_GAUCHE', 'Latéral G'], ['LATERAL_DROIT', 'Latéral D'], ['ARRIERE', 'Arrière'], ['AUTRE', 'Autre']]
  const CARBURATIONS = ['Essence', 'Diesel', 'GPL', 'Électrique', 'Hybride']
  const VEH_DIM   = { VOITURE: [28, 52], CAMION: [34, 84], UTILITAIRE: [30, 66], MOTO: [14, 34] }
  function chocXY(choc, d) {
    const [w, h] = d
    if (choc === 'FRONTAL') return [0, -h / 2]
    if (choc === 'ARRIERE') return [0, h / 2]
    if (choc === 'LATERAL_GAUCHE') return [-w / 2, 0]
    if (choc === 'LATERAL_DROIT') return [w / 2, 0]
    if (choc === 'AUTRE') return [0, 0]
    return null
  }
  // Sièges (vue de haut, avant = haut). France : conducteur = avant gauche.
  function seatXY(position, d) {
    const [w, h] = d
    const F = { CONDUCTEUR: [-0.26, -0.28], PASSAGER_AVANT: [0.26, -0.28], PASSAGER_ARRIERE: [0, 0.30] }
    const f = F[position] ?? [0, 0.05]
    return [f[0] * w, f[1] * h]
  }
  // Victimes (SAP) reliées à ce véhicule + leur position (avp.position).
  function victimesDuVehicule(vehId) {
    return victimes
      .map(vic => { const c = bilanSapDe(vic.id)?.contenu; return c?.vehiculeSrId === vehId ? { vic, position: c?.avp?.position ?? null, triage: c?.triage ?? null } : null })
      .filter(Boolean)
  }
  function bilanSrDe() { return bilans.find(b => b.famille === 'SR') }
  function bilanIncDe() { return bilans.find(b => b.famille === 'INC') }
  async function saveInc(contenu) {
    try { const b = await api.put(`/sp/interventions/${interventionId}/bilan-inc`, contenu); bilans = [...bilans.filter(x => x.id !== b.id), b] }
    catch { /* toast par api.js */ }
  }
  function intoSr(c) { return { routeType: c?.routeType ?? 'AUTOROUTE_3V', vehicules: (c?.vehicules ?? []).map(v => ({ ...v })) } }
  function vehSel() { return srForm.vehicules.find(v => v.id === srVehSel) }
  function onSrChange() { srSaved = false; clearTimeout(srTimer); srTimer = setTimeout(saveSr, 600) }
  async function saveSr() {
    srTimer = null
    try {
      const b = await api.put(`/sp/interventions/${interventionId}/bilan-sr`, srForm)
      bilans = [...bilans.filter(x => x.id !== b.id), b]
      srSaved = true; setTimeout(() => { srSaved = false }, 2000)
    } catch { /* toast par api.js */ }
  }
  function ouvrirCreation(type) {
    srVehSel = null
    srDraft = { type, modele: '', plaque: '', carburation: '', choc: null, incendie: false, desincarcere: false, stabilise: false }
  }
  function validerCreation() {
    if (!srDraft.modele?.trim() || !srDraft.carburation) return
    const v = { ...srDraft, id: crypto.randomUUID(), x: 0.5, y: 0.5 }
    srForm.vehicules = [...srForm.vehicules, v]; srVehSel = v.id; onSrChange()
    if (srDraftLink) { sapForm.vehiculeSrId = v.id; onSapChange() }
    const link = srDraftLink
    srDraft = null; srDraftLink = null
    if (link) famille = 'SAP'   // créé depuis le SAP : on revient à la victime
  }
  function annulerCreation() { srDraft = null; srDraftLink = null }
  function deleteVeh(id) {
    srForm.vehicules = srForm.vehicules.filter(v => v.id !== id)
    if (srVehSel === id) srVehSel = null
    onSrChange()
  }
  function vehPointerDown(e, id) {
    e.stopPropagation(); dragId = id; srVehSel = id
    window.addEventListener('pointermove', vehPointerMove); window.addEventListener('pointerup', vehPointerUp)
  }
  function vehPointerMove(e) {
    if (!dragId || !srSvgEl) return
    const r = srSvgEl.getBoundingClientRect()
    const v = srForm.vehicules.find(x => x.id === dragId)
    if (v) { v.x = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)); v.y = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height)) }
  }
  function vehPointerUp() {
    window.removeEventListener('pointermove', vehPointerMove); window.removeEventListener('pointerup', vehPointerUp)
    if (dragId) { dragId = null; onSrChange() }
  }
  function plaqueVeh(id) { return srForm.vehicules.find(v => v.id === id)?.plaque || srForm.vehicules.find(v => v.id === id)?.modele || 'Véhicule' }
  // Création depuis le bilan SAP : bascule sur SR, ouvre le formulaire ; au Valider, relie la victime.
  function creerVehiculeDepuisSap() {
    srDraftLink = victimeSel
    famille = 'SR'
    ouvrirCreation('VOITURE')
  }

  async function refresh() {
    if (!interventionId) return
    const [i, j, c] = await Promise.all([
      api.get(`/sp/interventions/${interventionId}`).catch(() => inter),
      api.get(`/sp/interventions/${interventionId}/journal`).catch(() => []),
      api.get(`/sp/interventions/${interventionId}/cri`).catch(() => []),
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
      // CRI soumis / validé sur CETTE intervention → recharge les CRIs locaux (le badge nav est géré par Layout).
      if (ev.faction === 'SP' && ev.type === 'CRI_MAJ' && ev.payload?.interventionId === interventionId) {
        clearTimeout(reloadTimer); reloadTimer = setTimeout(refresh, 300)
      }
      // Bilan modifié par un AUTRE équipier → recharge + rafraîchit les champs affichés.
      // (Mon propre event est ignoré : j'ai déjà l'état à jour, pas de faux « ✓ Enregistré ».)
      if (ev.faction === 'SP' && ev.type === 'BILAN_MAJ' && ev.payload?.interventionId === interventionId
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
  function fmtVal(val, type, opts) {
    if (type === 'bool') return val ? 'Oui' : 'Non'
    if (type === 'enum') return opts?.find(o => o[0] === val)?.[1] ?? val
    return String(val)
  }
  function srPlaque(id) { const v = srForm.vehicules.find(x => x.id === id); return v ? (v.plaque || v.modele || v.type) : '?' }
  function incPdfLignes(c) {
    const L = [], s = c.sinistre ?? {}, p = c.propagation ?? {}, h = c.hydraulique ?? {}
    const ETAT = { EN_COURS: 'En cours', MAITRISE: 'Maîtrisé', ETEINT: 'Éteint', SOUS_SURVEILLANCE: 'Sous surveillance' }
    const TECH = { DIRECTE: 'Attaque directe', INDIRECTE: 'Attaque indirecte', FEU_TACTIQUE: 'Feu tactique', NOYAGE: 'Noyage' }
    if (s.surfaceBrulee) L.push(['Surface brûlée', (s.surfaceBrulee / 10000).toFixed(2) + ' ha'])
    if (s.surfaceMenacee) L.push(['Surface menacée', (s.surfaceMenacee / 10000).toFixed(2) + ' ha'])
    if (s.etat) L.push(['État', ETAT[s.etat] ?? s.etat])
    if ((s.couvert ?? []).length) L.push(['Couvert', s.couvert.join(', ').toLowerCase()])
    for (const [k, lab] of [['heureDebut', 'Début'], ['heureMaitrise', 'Maîtrise'], ['heureExtinction', 'Extinction']]) if (s[k]) L.push([lab, s[k]])
    if (p.direction) L.push(['Propagation', p.direction])
    if (p.vitesse) L.push(['Vitesse front', p.vitesse + ' m/min'])
    if (p.longueurFront) L.push(['Longueur front', p.longueurFront + ' m'])
    if (p.ventDirection || p.ventForce) L.push(['Vent', [p.ventDirection, p.ventForce].filter(Boolean).join(' ')])
    if (c.technique) L.push(['Technique', TECH[c.technique] ?? c.technique])
    if ((h.lances ?? []).length) L.push(['Lances établies', String(h.lances.length)])
    if (h.eauConsommee) L.push(['Eau consommée', h.eauConsommee + ' L'])
    if (c.aeriens?.engages) L.push(['Moyens aériens', (c.aeriens.nbLargages ?? 0) + ' largage(s)'])
    return L
  }
  // PDF détaillé : n'inclut que les bilans/sections réellement remplis (pas de blocs vides).
  function exportPdfDetaille() {
    const bilansPdf = []
    for (const vic of victimes) {
      const c = bilanSapDe(vic.id)?.contenu
      if (!c) continue
      const lignes = []
      if (c.triage) lignes.push(['Triage', TRIAGES.find(t => t[0] === c.triage)?.[1] ?? c.triage])
      for (const [sk, , champs] of SECTIONS) {
        if (sk === 'schema') continue
        for (const [k, label, type, opts] of champs) {
          const val = c[sk]?.[k]
          if (val === undefined || val === null || val === '' || val === false) continue
          lignes.push([label, fmtVal(val, type, opts)])
        }
      }
      if ((c.lesions ?? []).length) lignes.push(['Lésions marquées', String(c.lesions.length)])
      if (c.vehiculeSrId) lignes.push(['Véhicule (SR)', srPlaque(c.vehiculeSrId)])
      if (lignes.length) bilansPdf.push({ titre: 'SAP — ' + victimeNom(vic) + (vic.sexe ? ` (${vic.sexe})` : ''), lignes })
    }
    if (srForm.vehicules.length) {
      const lignes = srForm.vehicules.map(v => {
        const det = [TYPES_SR.find(t => t[0] === v.type)?.[1], v.modele, v.carburation,
          v.choc && ('choc ' + (CHOCS_SR.find(x => x[0] === v.choc)?.[1])),
          v.incendie && 'incendié', v.desincarcere && 'désincarcéré', v.stabilise && 'stabilisé'].filter(Boolean).join(' · ')
        return [v.plaque || v.modele || 'Véhicule', det]
      })
      bilansPdf.push({ titre: 'Secours routier — ' + srForm.vehicules.length + ' véhicule(s)', lignes })
    }
    const inc = bilanIncDe()?.contenu
    if (inc) { const l = incPdfLignes(inc); if (l.length) bilansPdf.push({ titre: 'Feu de forêt', lignes: l }) }
    exportInterventionPdf(inter, journal, cris, bilansPdf)
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
        <span class="status-chip" class:closed={!inter.enCours} title={statutCloture.titre}>{statutCloture.label}</span>
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
                  {#if cri.statut === 'SOUMIS' && peutValiderCri}
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
                {#if triageDe(v.id)}<span class="tri-dot" style="background:{COULEUR_TRIAGE[triageDe(v.id)]}"></span>{/if}
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
            <div class="triage">
              <span class="triage-lib">Triage</span>
              {#each TRIAGES as [tv, tl]}
                <button class:on={sapForm.triage === tv} style="--tc:{COULEUR_TRIAGE[tv]}" onclick={() => { sapForm.triage = tv; onSapChange() }}>{tl}</button>
              {/each}
            </div>
            <div class="sap-stepper">
              {#each navSections as [sk], idx}
                <button class:on={sectionIdx === idx} onclick={() => sectionIdx = idx}>{sk === 'sample' ? 'SAMPLE' : sk === 'avp' ? 'AVP' : sk === 'schema' ? 'Schéma' : sk.toUpperCase()}</button>
              {/each}
            </div>
            <div class="sap-2col" class:wide>
              {#if wide}<div class="sap-left">{@render schemaView()}</div>{/if}
              <div class="sap-right">
                {#if currentSec[0] === 'schema'}
              {@render schemaView()}
            {:else}
              <div class="sap-form">
                {#if currentSec[0] === 'avp'}
                  <div class="sap-row">
                    <span class="sap-lib">Véhicule (SR)</span>
                    <span class="sap-veh-pick">
                      <select bind:value={sapForm.vehiculeSrId} onchange={onSapChange}>
                        <option value={null}>—</option>
                        {#each srForm.vehicules as v (v.id)}<option value={v.id}>{v.plaque || v.modele || (TYPES_SR.find(t => t[0] === v.type)?.[1])}</option>{/each}
                      </select>
                      <button type="button" class="btn-ghost-sm" onclick={creerVehiculeDepuisSap}>+ créer</button>
                    </span>
                  </div>
                {/if}
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
        {:else if famille === 'SR'}
          <div class="sr">
            <div class="sr-routes">
              {#each ROUTES_SR as [v, l]}
                <button class:on={srForm.routeType === v} onclick={() => { srForm.routeType = v; onSrChange() }}>{l}</button>
              {/each}
              {#if srSaved}<span class="saved">✓ Enregistré</span>{/if}
            </div>
            <div class="sr-body">
              <svg bind:this={srSvgEl} class="sr-scene" viewBox="0 0 200 360" role="img" aria-label="Scène secours routier">
                <rect x="40" y="0" width="120" height="360" fill="var(--color-surface)" />
                {#if srForm.routeType === 'AUTOROUTE_3V'}
                  <line x1="80" y1="0" x2="80" y2="360" stroke="var(--color-muted)" stroke-width="1" stroke-dasharray="10 10" />
                  <line x1="120" y1="0" x2="120" y2="360" stroke="var(--color-muted)" stroke-width="1" stroke-dasharray="10 10" />
                {:else}
                  <line x1="100" y1="0" x2="100" y2="360" stroke="#e0a23c" stroke-width="2" />
                {/if}
                <line x1="40" y1="0" x2="40" y2="360" stroke="var(--color-border)" stroke-width="2" />
                <line x1="160" y1="0" x2="160" y2="360" stroke="var(--color-border)" stroke-width="2" />
                {#each srForm.vehicules as v (v.id)}
                  {@const d = VEH_DIM[v.type] ?? VEH_DIM.VOITURE}
                  <g class="veh" class:sel={srVehSel === v.id} transform="translate({v.x * 200},{v.y * 360})"
                     onpointerdown={e => vehPointerDown(e, v.id)} role="button" tabindex="0" aria-label={v.plaque || v.type}>
                    <rect x={-d[0] / 2} y={-d[1] / 2} width={d[0]} height={d[1]} rx="3"
                          fill={v.incendie ? '#e24b4a' : '#378add'} stroke="#fff" stroke-width="1" />
                    <rect x={-d[0] / 2 + 2} y={-d[1] / 2 + 3} width={d[0] - 4} height={Math.max(3, d[1] / 4)} rx="1" fill="rgba(255,255,255,0.55)" />
                    {#if v.choc}
                      {@const cp = chocXY(v.choc, d)}
                      {#if cp}<polygon points="0,-7 1.8,-2.2 7,-2 2.6,1.2 4.2,7 0,3.2 -4.2,7 -2.6,1.2 -7,-2 -1.8,-2.2" transform="translate({cp[0]},{cp[1]})" fill="#ef9f27" stroke="#7a3e00" stroke-width="0.5" />{/if}
                    {/if}
                    {#each victimesDuVehicule(v.id) as occ}
                      {@const sxy = seatXY(occ.position, d)}
                      <g class="seat" transform="translate({sxy[0]},{sxy[1]})">
                        <title>{victimeNom(occ.vic)}{occ.triage ? ' · ' + (TRIAGES.find(t => t[0] === occ.triage)?.[1]) : ''}</title>
                        <circle r="7" fill={COULEUR_TRIAGE[occ.triage] ?? 'var(--accent)'} stroke="#fff" stroke-width="0.9" />
                        <text text-anchor="middle" dy="3" font-size="8.5" font-weight="700" fill="#fff">{occ.vic.numero}</text>
                      </g>
                    {/each}
                  </g>
                {/each}
              </svg>
              <div class="sr-side">
                <div class="sr-add">
                  <span class="muted small">Ajouter :</span>
                  {#each TYPES_SR as [v, l]}<button class="btn-ghost-sm" onclick={() => ouvrirCreation(v)}>+ {l}</button>{/each}
                </div>
                {#if srDraft}
                  <div class="sr-edit">
                    <div class="sr-edit-head"><span>Nouveau {TYPES_SR.find(t => t[0] === srDraft.type)?.[1]}</span></div>
                    <label>Modèle *<input type="text" bind:value={srDraft.modele} /></label>
                    <label>Carburation *
                      <select bind:value={srDraft.carburation}>
                        <option value="">—</option>
                        {#each CARBURATIONS as c}<option value={c}>{c}</option>{/each}
                      </select>
                    </label>
                    <label>Plaque (optionnel)<input type="text" bind:value={srDraft.plaque} /></label>
                    <label>Position du choc
                      <select bind:value={srDraft.choc}>
                        <option value={null}>—</option>
                        {#each CHOCS_SR as [cv, cl]}<option value={cv}>{cl}</option>{/each}
                      </select>
                    </label>
                    <label class="sr-flag"><input type="checkbox" bind:checked={srDraft.incendie} /> Incendié</label>
                    <label class="sr-flag"><input type="checkbox" bind:checked={srDraft.desincarcere} /> Désincarcéré</label>
                    <label class="sr-flag"><input type="checkbox" bind:checked={srDraft.stabilise} /> Stabilisé</label>
                    <div class="sr-actions">
                      <button class="btn-ghost-sm" onclick={annulerCreation}>Annuler</button>
                      <button class="btn-primary" disabled={!srDraft.modele?.trim() || !srDraft.carburation} onclick={validerCreation}>Valider</button>
                    </div>
                  </div>
                {:else if vehSel()}
                  {@const v = vehSel()}
                  <div class="sr-edit">
                    <div class="sr-edit-head"><span>{TYPES_SR.find(t => t[0] === v.type)?.[1]} — {v.plaque || v.modele}</span></div>
                    <label>Modèle<input type="text" bind:value={v.modele} oninput={onSrChange} /></label>
                    <label>Carburation
                      <select bind:value={v.carburation} onchange={onSrChange}>
                        <option value="">—</option>
                        {#each CARBURATIONS as c}<option value={c}>{c}</option>{/each}
                      </select>
                    </label>
                    <label>Plaque<input type="text" bind:value={v.plaque} oninput={onSrChange} /></label>
                    <label>Position du choc
                      <select bind:value={v.choc} onchange={onSrChange}>
                        <option value={null}>—</option>
                        {#each CHOCS_SR as [cv, cl]}<option value={cv}>{cl}</option>{/each}
                      </select>
                    </label>
                    <label class="sr-flag"><input type="checkbox" bind:checked={v.incendie} onchange={onSrChange} /> Incendié</label>
                    <label class="sr-flag"><input type="checkbox" bind:checked={v.desincarcere} onchange={onSrChange} /> Désincarcéré</label>
                    <label class="sr-flag"><input type="checkbox" bind:checked={v.stabilise} onchange={onSrChange} /> Stabilisé</label>
                    <button class="btn-ghost-sm sr-del" onclick={() => deleteVeh(v.id)}>🗑 Supprimer le véhicule</button>
                  </div>
                {:else}
                  <p class="muted small">« Ajouter » un véhicule (modèle + carburation requis) puis glisse-le sur la scène. Clic = éditer.</p>
                {/if}
              </div>
            </div>
          </div>
        {:else}
          <BilanIncForm contenu={bilanIncDe()?.contenu} engins={inter.engins} coord={inter.coordonnees} onsave={saveInc} />
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
  .sap-2col.wide { grid-template-columns: 230px minmax(0, 1fr); align-items: start; }
  .sap-left { position: sticky; top: 8px; width: 230px; }
  .sap-right { min-width: 0; display: flex; flex-direction: column; gap: 12px; }
  .sap-2col.wide .sap-form { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 8px 18px; }
  .sap-2col.wide .sap-form .sap-row.full { grid-column: 1 / -1; }
  .famille-chips { display: flex; gap: 8px; flex-wrap: wrap; }
  .famille-chips button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-muted); font-size: 13px; padding: 6px 12px; cursor: pointer; }
  .famille-chips button.on { background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .victimes-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  .vic { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 15px; padding: 10px 18px; cursor: pointer; display: inline-flex; gap: 8px; align-items: center; font-weight: 500; min-height: 40px; }
  .vic.on { border-color: var(--accent); color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, var(--color-bg)); }
  .vic-sexe { font-size: 11px; font-weight: 700; color: var(--color-muted); border: 1px solid var(--color-border); border-radius: 6px; padding: 1px 5px; }
  .tri-dot { width: 14px; height: 14px; border-radius: 50%; display: inline-block; flex-shrink: 0; border: 1px solid rgba(255,255,255,0.4); }
  .triage { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  .triage-lib { font-size: 10px; text-transform: uppercase; letter-spacing: .4px; color: var(--color-muted); }
  .triage button { background: var(--color-surface); border: 1px solid var(--color-border); border-left: 4px solid var(--tc); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .triage button.on { background: color-mix(in srgb, var(--tc) 22%, transparent); font-weight: 600; }
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
  .sap-veh-pick { display: flex; gap: 6px; align-items: center; }

  .schema { display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
  .lesion-palette { display: flex; gap: 6px; flex-wrap: wrap; }
  .lesion-palette button { background: var(--color-surface); border: 1px solid var(--color-border); border-left: 4px solid var(--lc); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .lesion-palette button.on { background: color-mix(in srgb, var(--lc) 18%, transparent); font-weight: 600; }
  .silhouette { width: 170px; height: 368px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); cursor: crosshair; }
  .silhouette .body { fill: var(--color-surface); stroke: var(--color-border); stroke-width: 1.5; }
  .lesion-pt { cursor: pointer; stroke: #fff; stroke-width: 1; }

  .sr { display: flex; flex-direction: column; gap: 12px; }
  .sr-routes { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  .sr-routes button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-muted); font-size: 13px; padding: 6px 12px; cursor: pointer; }
  .sr-routes button.on { background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .sr-body { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
  .sr-scene { width: 280px; height: 504px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); touch-action: none; flex-shrink: 0; }
  .veh { cursor: grab; }
  .veh.sel rect:first-child { stroke: var(--accent); stroke-width: 2; }
  .sr-side { flex: 1; min-width: 220px; display: flex; flex-direction: column; gap: 12px; }
  .sr-add { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
  .sr-edit { display: flex; flex-direction: column; gap: 8px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px; }
  .sr-edit-head { display: flex; align-items: center; justify-content: space-between; font-weight: 600; font-size: 14px; }
  .sr-edit label { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .sr-edit input, .sr-edit select { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .sr-flag { flex-direction: row !important; align-items: center; gap: 8px; font-size: 13px !important; color: var(--color-text) !important; }
  .sr-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 4px; }
  .sr-del { color: var(--color-danger); align-self: flex-start; }
  .vic-form { display: flex; flex-direction: column; gap: 10px; }
  .vic-form label { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .vic-form input, .vic-form select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
</style>
