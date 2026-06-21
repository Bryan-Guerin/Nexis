<script>
  import MapView from '../shared/MapView.svelte'

  // Bilan INC — feu de forêt. Reçoit le contenu + les engins de l'intervention (pour les lances)
  // + la coordonnée (centrage carte). Émet le contenu (autosave) via onsave.
  let { contenu = null, engins = [], coord = null, onsave } = $props()

  function emptyInc() {
    return { sinistre: { couvert: [] }, propagation: {}, enjeux: {}, hydraulique: { lances: [], pointsEau: [] }, aeriens: {}, technique: null, polygone: [] }
  }
  function intoInc(c) {
    if (!c) return emptyInc()
    return {
      sinistre:    { couvert: [], ...(c.sinistre ?? {}) },
      propagation: { ...(c.propagation ?? {}) },
      enjeux:      { ...(c.enjeux ?? {}) },
      hydraulique: { lances: [], pointsEau: [], ...(c.hydraulique ?? {}) },
      aeriens:     { ...(c.aeriens ?? {}) },
      technique:   c.technique ?? null,
      polygone:    Array.isArray(c.polygone) ? c.polygone.map(p => [...p]) : [],
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
  const UNITES     = [['L', 'L'], ['M3', 'm³']]
  const TECHNIQUES = [['DIRECTE', 'Attaque directe'], ['INDIRECTE', 'Attaque indirecte'], ['FEU_TACTIQUE', 'Feu tactique'], ['NOYAGE', 'Noyage']]

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

  function toggleCouvert(v) {
    const s = new Set(form.sinistre.couvert ?? [])
    s.has(v) ? s.delete(v) : s.add(v)
    form.sinistre.couvert = [...s]; change()
  }
  function addLance() { form.hydraulique.lances = [...form.hydraulique.lances, { type: 'LDV', enginVehiculeId: null, debit: null }]; change() }
  function delLance(i) { form.hydraulique.lances = form.hydraulique.lances.filter((_, k) => k !== i); change() }
  function addPE() { form.hydraulique.pointsEau = [...form.hydraulique.pointsEau, { type: 'PEI', ref: '' }]; change() }
  function delPE(i) { form.hydraulique.pointsEau = form.hydraulique.pointsEau.filter((_, k) => k !== i); change() }
</script>

<div class="inc">
  {#if saved}<span class="saved">✓ Enregistré</span>{/if}

  <div class="inc-sec">
    <h4>Cartographie — zone brûlée</h4>
    <MapView height="320px" center={coord} centerZoom={2} drawPolygon bind:polygon={form.polygone} onpolygon={onPoly}
             interventions={coord && coord.length === 6 ? [{ coordonnees: coord, motif: 'Feu de forêt', nature: { code: '🔥' } }] : []} />
    <div class="inc-carto-bar">
      <span>Aire tracée : <b>{Math.round(aire).toLocaleString('fr-FR')} m²</b> ({ha(aire)} ha)</span>
      <button class="btn-ghost-sm" disabled={!form.polygone.length} onclick={effacerDernier}>Effacer le dernier</button>
      <button class="btn-ghost-sm" disabled={!form.polygone.length} onclick={effacerTout}>Tout effacer</button>
    </div>
    <p class="muted small">Clique sur la carte pour ajouter des sommets (déplaçables). L'aire pré-remplit la surface brûlée.</p>
  </div>

  <div class="inc-sec">
    <h4>Sinistre</h4>
    <div class="inc-grid">
      <label>Surface brûlée (m²)<input type="number" min="0" bind:value={form.sinistre.surfaceBrulee} oninput={onSurfaceManuelle} /></label>
      <label>Surface menacée (m²)<input type="number" min="0" bind:value={form.sinistre.surfaceMenacee} oninput={change} /></label>
      <label>État<select bind:value={form.sinistre.etat} onchange={change}><option value={null}>—</option>{#each ETATS as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
      <label>Début<input type="text" bind:value={form.sinistre.heureDebut} oninput={change} placeholder="hh:mm" /></label>
      <label>Maîtrise<input type="text" bind:value={form.sinistre.heureMaitrise} oninput={change} placeholder="hh:mm" /></label>
      <label>Extinction<input type="text" bind:value={form.sinistre.heureExtinction} oninput={change} placeholder="hh:mm" /></label>
    </div>
    <div class="inc-multi">
      <span class="inc-lib">Couvert</span>
      {#each COUVERTS as [v, l]}<button class:on={(form.sinistre.couvert ?? []).includes(v)} onclick={() => toggleCouvert(v)}>{l}</button>{/each}
    </div>
    <p class="muted small">Surface brûlée : {ha(form.sinistre.surfaceBrulee)} ha · source : {form.sinistre.surfaceBruleeSource === 'MANUEL' ? 'manuelle' : form.sinistre.surfaceBruleeSource === 'TRACE' ? 'tracé carto' : '—'}</p>
  </div>

  <div class="inc-sec">
    <h4>Propagation</h4>
    <div class="inc-grid">
      <label>Direction<select bind:value={form.propagation.direction} onchange={change}><option value={null}>—</option>{#each DIRS as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
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
      <label>Eau consommée<input type="number" bind:value={form.hydraulique.eauConsommee} oninput={change} /></label>
      <label>Unité<select bind:value={form.hydraulique.eauUnite} onchange={change}><option value={null}>—</option>{#each UNITES as [v, l]}<option value={v}>{l}</option>{/each}</select></label>
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
</div>

<style>
  .inc { display: flex; flex-direction: column; gap: 16px; max-width: 760px; }
  .saved { font-size: 11px; color: var(--color-success); font-weight: 600; align-self: flex-start; }
  .inc-sec { display: flex; flex-direction: column; gap: 10px; }
  .inc-sec h4 { margin: 0; font-size: 14px; font-weight: 600; }
  .inc-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px 14px; }
  .inc-grid label, .inc-full { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .inc-grid input, .inc-grid select, .inc-full input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .inc-multi { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .inc-lib { font-size: 11px; text-transform: uppercase; letter-spacing: .4px; color: var(--color-muted); }
  .inc-multi button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 10px; cursor: pointer; }
  .inc-multi button.on { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 45%, transparent); font-weight: 600; }
  .inc-checks { display: flex; gap: 16px; flex-wrap: wrap; }
  .inc-checks label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
  .inc-carto-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 13px; }
  .inc-list { display: flex; flex-direction: column; gap: 6px; }
  .inc-list-head { display: flex; align-items: center; justify-content: space-between; font-size: 12px; font-weight: 600; color: var(--color-muted); }
  .inc-row { display: flex; gap: 6px; align-items: center; }
  .inc-row select, .inc-row input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 8px; }
  .inc-row input { width: 110px; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }
</style>
