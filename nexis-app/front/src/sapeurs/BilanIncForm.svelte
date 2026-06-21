<script>
  import MapView from '../shared/MapView.svelte'

  // Bilan INC — feu de forêt (catégorie MAISON à venir). Reçoit le contenu + engins de l'intervention
  // (pour les lances + position carto) + la coordonnée (centrage carte). Émet le contenu (autosave) via onsave.
  let { contenu = null, engins = [], coord = null, onsave } = $props()

  function emptyInc() {
    return { typeFeu: 'FORET', sinistre: { couvert: [] }, propagation: {}, enjeux: {}, hydraulique: { lances: [], pointsEau: [] }, aeriens: {}, technique: null, polygone: [], enginsPositions: [] }
  }
  function intoInc(c) {
    if (!c) return emptyInc()
    return {
      typeFeu:     c.typeFeu ?? 'FORET',
      sinistre:    { couvert: [], ...(c.sinistre ?? {}) },
      propagation: { ...(c.propagation ?? {}) },
      enjeux:      { ...(c.enjeux ?? {}) },
      hydraulique: { lances: [], pointsEau: [], ...(c.hydraulique ?? {}) },
      aeriens:     { ...(c.aeriens ?? {}) },
      technique:   c.technique ?? null,
      polygone:    Array.isArray(c.polygone) ? c.polygone.map(p => [...p]) : [],
      enginsPositions: Array.isArray(c.enginsPositions) ? c.enginsPositions.map(p => ({ ...p })) : [],
    }
  }
  let form    = $state(intoInc(contenu))
  let saved   = $state(false)
  let timer   = null
  let editing = false
  function change() {
    saved = false; editing = true
    clearTimeout(timer)
    timer = setTimeout(() => { editing = false; timer = null; onsave?.(form); saved = true; setTimeout(() => { saved = false }, 2000) }, 600)
  }
  // Resync sur maj temps réel d'un autre équipier, sauf si saisie locale en cours.
  $effect(() => { contenu; if (!editing && !timer) form = intoInc(contenu) })

  const COUVERTS   = [['HERBE', 'Herbe'], ['BROUSSAILLES', 'Broussailles / garrigue'], ['RESINEUX', 'Résineux'], ['FEUILLUS', 'Feuillus'], ['CULTURES', 'Cultures']]
  const ETATS      = [['EN_COURS', 'En cours'], ['MAITRISE', 'Maîtrisé'], ['ETEINT', 'Éteint'], ['SOUS_SURVEILLANCE', 'Sous surveillance']]
  const DIRS       = [['N', 'N'], ['NE', 'NE'], ['E', 'E'], ['SE', 'SE'], ['S', 'S'], ['SO', 'SO'], ['O', 'O'], ['NO', 'NO']]
  const TOPOS      = [['PLAT', 'À plat'], ['MONTANT', 'Montant'], ['DESCENDANT', 'Descendant']]
  const TYPES_LANCE = [['LDV', 'LDV'], ['LDT', 'LDT'], ['AUTRE', 'Autre']]
  const TYPES_PE   = [['PEI', 'PEI'], ['CITERNE', 'Citerne'], ['NATUREL', 'Point naturel']]
  const TECHNIQUES = [['DIRECTE', 'Attaque directe'], ['INDIRECTE', 'Attaque indirecte'], ['FEU_TACTIQUE', 'Feu tactique'], ['NOYAGE', 'Noyage']]
  // Direction → vecteur (dlat=nord, dlng=est). Convention monde : lat = mètres depuis le sud.
  const DIR_VEC = { N: [1, 0], NE: [0.707, 0.707], E: [0, 1], SE: [-0.707, 0.707], S: [-1, 0], SO: [-0.707, -0.707], O: [0, -1], NO: [0.707, -0.707] }
  const ARROW_LEN = 300   // m

  // Aire du polygone (sommets [lat,lng] = mètres monde) par la formule du lacet → m².
  function aireM2(poly) {
    if (!poly || poly.length < 3) return 0
    let a = 0
    for (let i = 0; i < poly.length; i++) {
      const [lat1, lng1] = poly[i], [lat2, lng2] = poly[(i + 1) % poly.length]
      a += lng1 * lat2 - lng2 * lat1
    }
    return Math.abs(a) / 2
  }
  let aire = $derived(aireM2(form.polygone))
  const ha = m2 => ((m2 ?? 0) / 10000).toFixed(2)

  function onPoly() {
    if (form.sinistre.surfaceBruleeSource !== 'MANUEL') {
      form.sinistre.surfaceBrulee = Math.round(aireM2(form.polygone))
      form.sinistre.surfaceBruleeSource = 'TRACE'
    }
    change()
  }
  function onSurfaceManuelle() { form.sinistre.surfaceBruleeSource = 'MANUEL'; change() }
  function effacerDernier() { form.polygone = form.polygone.slice(0, -1); onPoly() }
  function effacerTout() { form.polygone = []; onPoly() }

  // Engins engagés positionnés sur la carte (historisés). Marqueurs déplaçables.
  let centerLL = $state(null)
  let incMarkers = $derived((form.enginsPositions ?? []).map(p => ({ id: p.vehiculeId, label: enginLabel(p.vehiculeId), lat: p.lat, lng: p.lng })))
  function enginLabel(id) { return engins.find(e => e.vehiculeId === id)?.libelle ?? '?' }
  function estPlace(id) { return (form.enginsPositions ?? []).some(p => p.vehiculeId === id) }
  function centroideOuCentre() {
    const poly = form.polygone ?? []
    if (poly.length) { let la = 0, ln = 0; for (const [a, b] of poly) { la += a; ln += b } return [Math.round(la / poly.length), Math.round(ln / poly.length)] }
    return centerLL ?? [5000, 5000]
  }
  function placerEngin(id) {
    const c = centroideOuCentre()
    form.enginsPositions = [...(form.enginsPositions ?? []), { vehiculeId: id, lat: c[0], lng: c[1] }]
    change()
  }
  function retirerEnginPos(id) { form.enginsPositions = (form.enginsPositions ?? []).filter(p => p.vehiculeId !== id); change() }
  function onMarker(updated) { form.enginsPositions = updated.map(m => ({ vehiculeId: m.id, lat: m.lat, lng: m.lng })); change() }

  // Flèches vent / propagation centrées sur le centroïde de la zone (ou centre fenêtre).
  let arrows = $derived.by(() => {
    const arr = []
    const [la, ln] = centroideOuCentre()
    const vDir = form.propagation?.ventDirection, pDir = form.propagation?.direction
    if (vDir && DIR_VEC[vDir]) {
      const [dy, dx] = DIR_VEC[vDir]
      arr.push({ lat0: la, lng0: ln, lat1: la + dy * ARROW_LEN, lng1: ln + dx * ARROW_LEN, color: '#5ab8ff', label: '🪁 Vent' })
    }
    if (pDir && DIR_VEC[pDir]) {
      const [dy, dx] = DIR_VEC[pDir]
      arr.push({ lat0: la, lng0: ln, lat1: la + dy * ARROW_LEN, lng1: ln + dx * ARROW_LEN, color: '#ff6b35', label: '🔥 Propagation' })
    }
    return arr
  })

  function toggleCouvert(v) {
    const s = new Set(form.sinistre.couvert ?? [])
    s.has(v) ? s.delete(v) : s.add(v)
    form.sinistre.couvert = [...s]; change()
  }
  function setTypeFeu(t) { form.typeFeu = t; change() }
  function addLance() { form.hydraulique.lances = [...form.hydraulique.lances, { type: 'LDV', enginVehiculeId: null, debit: null }]; change() }
  function delLance(i) { form.hydraulique.lances = form.hydraulique.lances.filter((_, k) => k !== i); change() }
  function addPE() { form.hydraulique.pointsEau = [...form.hydraulique.pointsEau, { type: 'PEI', ref: '' }]; change() }
  function delPE(i) { form.hydraulique.pointsEau = form.hydraulique.pointsEau.filter((_, k) => k !== i); change() }
</script>

<div class="inc">
  {#if saved}<span class="saved">✓ Enregistré</span>{/if}

  <div class="inc-typefeu">
    <button class:on={form.typeFeu === 'FORET'} onclick={() => setTypeFeu('FORET')}>🌲 Feu de forêt</button>
    <button class:on={form.typeFeu === 'MAISON'} onclick={() => setTypeFeu('MAISON')}>🏠 Feu de maison</button>
  </div>

  {#if form.typeFeu === 'MAISON'}
    <div class="inc-vide">
      <p>🚧 <b>Feu de maison</b> — formulaire à venir.</p>
    </div>
  {:else}
  <div class="inc-sec">
    <h4>Cartographie — zone brûlée</h4>
    <MapView height="320px" center={coord} windowRadius={1500} drawPolygon bind:polygon={form.polygone} onpolygon={onPoly}
             markers={incMarkers} onmarker={onMarker} oncenterready={ll => centerLL = ll} {arrows}
             interventions={coord && coord.length === 6 ? [{ coordonnees: coord, motif: 'Feu de forêt', nature: { code: '🔥' } }] : []} />
    <div class="inc-carto-bar">
      <span>Aire tracée : <b>{Math.round(aire).toLocaleString('fr-FR')} m²</b> ({ha(aire)} ha)</span>
      <button class="btn-ghost-sm" disabled={!form.polygone.length} onclick={effacerDernier}>Effacer le dernier</button>
      <button class="btn-ghost-sm" disabled={!form.polygone.length} onclick={effacerTout}>Tout effacer</button>
    </div>
    {#if engins.length}
      <div class="inc-engins">
        <span class="muted small">Engins sur la carte :</span>
        {#each engins as e (e.vehiculeId)}
          {#if estPlace(e.vehiculeId)}
            <button class="on" onclick={() => retirerEnginPos(e.vehiculeId)} title="Retirer de la carte">{e.libelle} ✓</button>
          {:else}
            <button onclick={() => placerEngin(e.vehiculeId)}>+ {e.libelle}</button>
          {/if}
        {/each}
      </div>
    {/if}
    <p class="muted small">Clique sur la carte pour tracer la zone (sommets déplaçables). Place les engins ci-dessus puis glisse-les. Les flèches vent / propagation apparaissent quand les directions sont saisies. L'aire pré-remplit la surface brûlée.</p>
  </div>

  <div class="inc-sec">
    <h4>Sinistre</h4>
    <div class="inc-grid">
      <label>Surface brûlée (m²)<input type="number" min="0" bind:value={form.sinistre.surfaceBrulee} oninput={onSurfaceManuelle} /></label>
      <label>Surface menacée (m²)<input type="number" min="0" bind:value={form.sinistre.surfaceMenacee} oninput={change} /></label>
      <label>État<select bind:value={form.sinistre.etat} onchange={change}><option value={null}>—</option>{#each ETATS as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
    </div>
    <p class="muted small inc-heures">
      ⏱ Heures tamponnées automatiquement au changement d'état (et notées en main courante) ·
      Début : <b>{form.sinistre.heureDebut ?? '—'}</b> · Maîtrise : <b>{form.sinistre.heureMaitrise ?? '—'}</b> · Extinction : <b>{form.sinistre.heureExtinction ?? '—'}</b>
    </p>
    <div class="inc-multi">
      <span class="inc-lib">Couvert</span>
      {#each COUVERTS as [v, l]}<button class:on={(form.sinistre.couvert ?? []).includes(v)} onclick={() => toggleCouvert(v)}>{l}</button>{/each}
    </div>
    <p class="muted small">Surface brûlée : {ha(form.sinistre.surfaceBrulee)} ha · source : {form.sinistre.surfaceBruleeSource === 'MANUEL' ? 'manuelle' : form.sinistre.surfaceBruleeSource === 'TRACE' ? 'tracé carto' : '—'}</p>
  </div>

  <div class="inc-sec">
    <h4>Propagation</h4>
    <div class="inc-grid">
      <label>Direction (propagation)<select bind:value={form.propagation.direction} onchange={change}><option value={null}>—</option>{#each DIRS as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
      <label>Vitesse du front (m/min)<input type="number" bind:value={form.propagation.vitesse} oninput={change} /></label>
      <label>Longueur du front (m)<input type="number" bind:value={form.propagation.longueurFront} oninput={change} /></label>
      <label>Vent — direction<select bind:value={form.propagation.ventDirection} onchange={change}><option value={null}>—</option>{#each DIRS as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
      <label>Vent — force<input type="text" bind:value={form.propagation.ventForce} oninput={change} placeholder="km/h / Beaufort" /></label>
      <label>Topographie<select bind:value={form.propagation.topographie} onchange={change}><option value={null}>—</option>{#each TOPOS as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
      <label>Pente (%)<input type="number" bind:value={form.propagation.pentePct} oninput={change} /></label>
    </div>
  </div>

  <div class="inc-sec">
    <h4>Enjeux menacés</h4>
    <div class="inc-checks">
      <label><input type="checkbox" bind:checked={form.enjeux.habitations} onchange={change} /> Habitations</label>
      <label><input type="checkbox" bind:checked={form.enjeux.route} onchange={change} /> Route / axe</label>
      <label><input type="checkbox" bind:checked={form.enjeux.ligneElec} onchange={change} /> Ligne électrique</label>
      <label><input type="checkbox" bind:checked={form.enjeux.autreSite} onchange={change} /> Autre site sensible</label>
    </div>
    {#if form.enjeux.autreSite}<label class="inc-full">Préciser<input type="text" bind:value={form.enjeux.autreSiteRef} oninput={change} /></label>{/if}
  </div>

  <div class="inc-sec">
    <h4>Moyens hydrauliques</h4>
    <div class="inc-list">
      <div class="inc-list-head"><span>Lances établies</span><button class="btn-ghost-sm" onclick={addLance}>+ Lance</button></div>
      {#each form.hydraulique.lances as l, i}
        <div class="inc-row">
          <select bind:value={l.type} onchange={change}>{#each TYPES_LANCE as [v, t]}<option value={v}>{t}</option>{/each}</select>
          <select bind:value={l.enginVehiculeId} onchange={change}>
            <option value={null}>— engin —</option>
            {#each engins as e (e.vehiculeId)}<option value={e.vehiculeId}>{e.libelle}</option>{/each}
          </select>
          <input type="number" placeholder="débit" bind:value={l.debit} oninput={change} />
          <button class="rm-btn" onclick={() => delLance(i)} title="Retirer">×</button>
        </div>
      {/each}
    </div>
    <div class="inc-grid">
      <label>Eau consommée (L)<input type="number" min="0" bind:value={form.hydraulique.eauConsommee} oninput={change} /></label>
    </div>
    <div class="inc-list">
      <div class="inc-list-head"><span>Points d'eau</span><button class="btn-ghost-sm" onclick={addPE}>+ Point d'eau</button></div>
      {#each form.hydraulique.pointsEau as pe, i}
        <div class="inc-row">
          <select bind:value={pe.type} onchange={change}>{#each TYPES_PE as [v, t]}<option value={v}>{t}</option>{/each}</select>
          <input type="text" placeholder="référence" bind:value={pe.ref} oninput={change} />
          <button class="rm-btn" onclick={() => delPE(i)} title="Retirer">×</button>
        </div>
      {/each}
    </div>
  </div>

  <div class="inc-sec">
    <h4>Moyens aériens</h4>
    <div class="inc-checks">
      <label><input type="checkbox" bind:checked={form.aeriens.engages} onchange={change} /> Engagés</label>
    </div>
    {#if form.aeriens.engages}<label class="inc-full">Nombre de largages<input type="number" bind:value={form.aeriens.nbLargages} oninput={change} /></label>{/if}
  </div>

  <div class="inc-sec">
    <h4>Technique</h4>
    <select bind:value={form.technique} onchange={change}><option value={null}>—</option>{#each TECHNIQUES as [v, l]}<option value={v}>{l}</option>{/each}</select>
  </div>
  {/if}
</div>

<style>
  .inc { display: flex; flex-direction: column; gap: 16px; max-width: 760px; }
  /* Toast fixé : ne décale plus le contenu (donc plus la carte pendant le tracé). */
  .saved { position: fixed; bottom: 16px; right: 16px; z-index: 9999; background: color-mix(in srgb, var(--color-success) 18%, var(--color-surface)); border: 1px solid color-mix(in srgb, var(--color-success) 45%, transparent); color: var(--color-success); font-size: 12px; font-weight: 600; padding: 6px 12px; border-radius: var(--radius); box-shadow: 0 2px 8px rgba(0,0,0,.3); pointer-events: none; }
  .inc-typefeu { display: flex; gap: 6px; }
  .inc-typefeu button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 14px; cursor: pointer; }
  .inc-typefeu button.on { background: color-mix(in srgb, var(--accent) 22%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 50%, transparent); font-weight: 600; }
  .inc-vide { padding: 24px; border: 1px dashed var(--color-border); border-radius: var(--radius); color: var(--color-muted); text-align: center; }
  .inc-sec { display: flex; flex-direction: column; gap: 10px; }
  .inc-sec h4 { margin: 0; font-size: 14px; font-weight: 600; }
  .inc-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px 14px; }
  .inc-grid label, .inc-full { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .inc-grid input, .inc-grid select, .inc-full input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .inc-heures { font-size: 11px; color: var(--color-muted); }
  .inc-heures b { color: var(--color-text); font-weight: 600; }
  .inc-multi { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .inc-lib { font-size: 11px; text-transform: uppercase; letter-spacing: .4px; color: var(--color-muted); }
  .inc-multi button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .inc-multi button.on { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .inc-checks { display: flex; gap: 16px; flex-wrap: wrap; }
  .inc-checks label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
  .inc-carto-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 13px; }
  .inc-engins { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .inc-engins button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .inc-engins button.on { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .inc-list { display: flex; flex-direction: column; gap: 6px; }
  .inc-list-head { display: flex; align-items: center; justify-content: space-between; font-size: 12px; font-weight: 600; color: var(--color-muted); }
  .inc-row { display: flex; gap: 6px; align-items: center; }
  .inc-row select, .inc-row input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 8px; }
  .inc-row input { width: 110px; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }
</style>
