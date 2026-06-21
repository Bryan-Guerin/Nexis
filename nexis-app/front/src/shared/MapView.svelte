<script>
  import {onMount, onDestroy} from 'svelte'

  // Carte (Leaflet, CRS.Simple sur le terrain UnRealLife). Fond satellite (mosaïque) ou
  // vectoriel (geojson : forêts, routes, bâtiments-repères, noms). Réutilisable.
  let { interventions = [], transits = [], centres = [], hopitaux = [], height = '380px', oncoordpick = null, onveh = null,
        drawPolygon = false, polygon = $bindable(null), onpolygon = null, center = null, centerZoom = 1,
        windowRadius = 0, markers = null, onmarker = null, oncenterready = null, arrows = null } = $props()

  const TILE = 2560, NTILE = 4
  const IMG = TILE * NTILE   // 10240 = worldSize, 1 px = 1 m

  // ── Conversion coordonnées jeu (6 chiffres) → pixel image ────────────────────
  const CX = IMG / 102.4     // pas X (100 m / unité)
  const CY = CX              // taux Y = taux X
  const Y_OFF = 20           // décalage vertical (m)
  function gridToImg(coord) {
    if (!coord || coord.length < 6) return null
    const gx = +coord.slice(0, 3), gy = +coord.slice(3, 6)
    if (Number.isNaN(gx) || Number.isNaN(gy)) return null
    const s = gy <= 500 ? gy : gy - 1000   // nord >0, sud <0
    return [gx * CX, IMG / 2 - s * CY + Y_OFF]   // [px gauche, py haut]
  }
  function toLatLng(px, py) { return [IMG - py, px] }
  function latLngToGrid(latlng) {
    const px = latlng.lng, py = IMG - latlng.lat
    const gx = Math.round(px / CX)
    let gy = Math.round((IMG / 2 + Y_OFF - py) / CY)
    gy = ((gy % 1000) + 1000) % 1000
    return String(Math.max(0, Math.min(102, gx))).padStart(3, '0') + String(gy).padStart(3, '0')
  }
  // coords geojson (mètres monde [x,y]) → latlng CRS.Simple
  function geoToLatLng(c) { return window.L.latLng(c[1], c[0]) }

  // Fenêtre (perf) : bbox latlng autour de l'intervention (rayon en mètres) ; null = carte entière.
  function bboxIntersect(a, b) { return a[0][0] <= b[1][0] && a[1][0] >= b[0][0] && a[0][1] <= b[1][1] && a[1][1] >= b[0][1] }
  function windowLatLngBounds() {
    if (!center || !windowRadius) return null
    const ll = llOf(center); if (!ll) return null
    const r = windowRadius
    return [[Math.max(0, ll[0] - r), Math.max(0, ll[1] - r)], [Math.min(IMG, ll[0] + r), Math.min(IMG, ll[1] + r)]]
  }

  // Couches vectorielles (geojson). style = lignes/polygones ; emoji/color = points.
  // detail:true = repère ponctuel affiché seulement zoomé (avec les bâtiments), sinon clutter.
  const VECTOR_LAYERS = [
    // Surfaces / lignes (toujours visibles en vecteur)
    { file: 'forest',           label: 'Forêt',            style: { stroke: false, fillColor: '#2f6d43', fillOpacity: 0.55 } },
    { file: 'mounts',           label: 'Relief',           style: { color: '#7d6b4f', weight: 0.5, opacity: 0.5 } },
    { file: 'track',            label: 'Piste',            style: { color: '#8a8f96', weight: 0.7, dashArray: '3' } },
    { file: 'road',             label: 'Route',            style: { color: '#aab0b6', weight: 1.2 } },
    { file: 'road-bridge',      label: 'Pont',             style: { color: '#c3c8cd', weight: 1.4 } },
    { file: 'main_road',        label: 'Route principale', style: { color: '#e0c24a', weight: 2.2 } },
    { file: 'main_road-bridge', label: 'Pont (princ.)',    style: { color: '#ecd06a', weight: 2.4 } },
    { file: 'railway',          label: 'Voie ferrée',      style: { color: '#5a6066', weight: 1.2, dashArray: '4 3' } },
    { file: 'powerline',        label: 'Ligne HT',         style: { color: '#caa83c', weight: 0.6, opacity: 0.55, dashArray: '2 4' } },
    // Repères ponctuels (detail : apparaissent au zoom des bâtiments)
    { file: 'church',      label: 'Église',          detail: true, style: { color: '#9aa0a6', weight: 1, fillColor: '#b9bfc6', fillOpacity: 0.6 } },
    { file: 'hospital',    label: 'Hôpital (bât.)',  detail: true, style: { color: '#e05c5c', weight: 1, fillColor: '#e05c5c', fillOpacity: 0.5 } },
    { file: 'fuelstation', label: 'Station-service', detail: true, emoji: '⛽' },
    { file: 'watertower',  label: "Château d'eau",   detail: true, emoji: '🚰' },
    { file: 'lighthouse',  label: 'Phare',           detail: true, emoji: '🗼' },
    { file: 'view-tower',  label: "Tour d'observation", detail: true, emoji: '🔭' },
    { file: 'transmitter', label: 'Antenne',         detail: true, emoji: '📡' },
    { file: 'chapel',      label: 'Chapelle',        detail: true, emoji: '⛪' },
    { file: 'cross',       label: 'Croix',           detail: true, emoji: '✝️' },
    { file: 'busstop',     label: 'Arrêt de bus',    detail: true, emoji: '🚏' },
    { file: 'fountain',    label: 'Fontaine',        detail: true, emoji: '⛲' },
    { file: 'ruin',        label: 'Ruine',           detail: true, emoji: '🏚️' },
    { file: 'shipwreck',   label: 'Épave',           detail: true, emoji: '🚢' },
    { file: 'tourism',     label: 'Point touristique', detail: true, emoji: '📷' },
    { file: 'airport',     label: 'Aéroport',        detail: true, emoji: '✈️' },
    { file: 'hill',        label: 'Colline',         detail: true, emoji: '⛰️' },
    { file: 'powersolar',  label: 'Centrale solaire', detail: true, emoji: '☀️' },
    { file: 'powerwind',   label: 'Éolienne',        detail: true, emoji: '🌬️' },
    { file: 'bunker',      label: 'Bunker',          detail: true, color: '#9aa0a6' },
    { file: 'citycenter',  label: 'Centre-ville',    detail: true, color: '#c9b06a' },
  ]
  const NAME_LAYERS = ['namecity', 'namevillage', 'namemarine']

  // Couches lourdes (house/tree) : tuilées (scripts/split-map-layers.mjs), chargées à la
  // demande au zoom (seules les cellules visibles sont fetchées). Mode vecteur uniquement.
  const HEAVY_N = 32, HEAVY_CS = IMG / HEAVY_N
  const HEAVY_MAX_CELLS = 100   // garde-fou : trop dézoomé = trop de tuiles → on saute (sinon on charge ~toute la map)
  const HEAVY_LAYERS = [
    { file: 'house', minZoom: -2, style: { stroke: false, fillColor: '#5b554f', fillOpacity: 0.65 } },
    { file: 'tree',  minZoom: -1, color: '#3c7a4a' },
  ]
  let heavyGroup, heavyIndex = {}, heavyCells = new Map()   // 'file/cx/cy' → couche Leaflet (ou null = en cours)
  const DETAIL_MIN = -2   // zoom à partir duquel les repères ponctuels apparaissent (= bâtiments)

  let el, map, satGroup, vectorGroup, detailGroup, layer, interLayer, baseGroup, clockTimer, vectorBuilt = false
  let drawGroup, polyLayer, vtxMarkers = []   // tracé polygone (zone brûlée) : sommets déplaçables
  let markerGroup, mkMarkers = []             // marqueurs engins déplaçables (carto FDF)
  let arrowsGroup                              // flèches vent/propagation (carto FDF)
  // Vecteur par défaut (mémorisé ensuite via localStorage).
  let mode = $state((typeof localStorage !== 'undefined' && localStorage.getItem('nexis.mapMode') === 'sat') ? 'sat' : 'vecteur')
  let tick = $state(0)       // horloge (1 s) pilotant l'animation des véhicules
  let showLegend = $state(false)

  onMount(() => {
    const L = window.L
    if (!L) return
    map = L.map(el, { crs: L.CRS.Simple, minZoom: -5, maxZoom: 3, attributionControl: false, zoomSnap: 0.25, preferCanvas: true })
    // Fenêtre restreinte (perf) : on cadre autour de l'intervention. windowRadius n'enferme PAS le
    // user — il peut dézoomer pour voir le contexte (mode vecteur autour, sat seul dans la fenêtre).
    const win = windowLatLngBounds()
    if (win) map.fitBounds(win)
    else map.fitBounds([[0, 0], [IMG, IMG]])

    // Pane satellite prioritaire (au-dessus du vecteur) quand windowRadius : sat reste visible
    // par-dessus le vecteur dans la fenêtre → minimap locale + contexte vecteur dézoomé.
    // Et un pane polyTop par-dessus le sat pour le tracé polygone + flèches (sinon le sat les recouvre).
    const satPane = win ? 'satTop' : 'tilePane'
    if (win) {
      map.createPane(satPane); map.getPane(satPane).style.zIndex = 410
      map.createPane('polyTop'); map.getPane('polyTop').style.zIndex = 420
    }

    // Fond satellite (mosaïque sat/{x}/{y}.png). Toujours présent : pleine opacité en mode sat,
    // atténué en fond du mode vecteur (eau + sol). Si windowRadius : seules les tuiles intersectant
    // la fenêtre sont chargées (perf).
    satGroup = L.layerGroup()
    for (let x = 0; x < NTILE; x++)
      for (let y = 0; y < NTILE; y++) {
        const tb = [[IMG - (y + 1) * TILE, x * TILE], [IMG - y * TILE, (x + 1) * TILE]]
        if (win && !bboxIntersect(tb, win)) continue   // hors fenêtre → tuile non chargée
        L.imageOverlay(`/map/unreallife/sat/${x}/${y}.png`, tb, { pane: satPane }).addTo(satGroup)
      }

    vectorGroup = L.layerGroup()   // construit à la demande (1er passage en vecteur)
    detailGroup = L.layerGroup()   // repères ponctuels, affichés seulement zoomé

    satGroup.addTo(map)
    baseGroup = L.layerGroup().addTo(map)   // casernes + hôpitaux (permanents, les 2 modes)
    heavyGroup = L.layerGroup()             // house/tree tuilés (vecteur, à la demande)
    interLayer = L.layerGroup().addTo(map)  // pins d'intervention (ne bougent pas → rendus à part)
    layer = L.layerGroup().addTo(map)       // engins/transits animés (re-rendus chaque seconde)
    if (drawPolygon) map.on('click', e => addVertex(e.latlng))
    else if (oncoordpick) map.on('click', e => oncoordpick(latLngToGrid(e.latlng)))
    for (const h of HEAVY_LAYERS)
      fetch(`/map/unreallife/tiles/${h.file}/index.json`).then(r => r.json())
        .then(keys => { heavyIndex[h.file] = new Set(keys); updateHeavy() }).catch(() => {})
    map.on('moveend zoomend', updateHeavy)
    renderBase()
    renderInterventions()
    render()
    renderPolygon()
    renderMarkers()
    if (center && !win) { const ll = llOf(center); if (ll) map.setView(ll, centerZoom) }
    if (center) oncenterready?.(llOf(center))
    if (mode === 'vecteur') setMode('vecteur')   // applique le choix mémorisé (G)
    clockTimer = setInterval(() => tick++, 1000)
  })
  onDestroy(() => { clearInterval(clockTimer); if (map) { map.remove(); map = null } })

  function emojiIcon(e) { return window.L.divIcon({ className: 'poi-pt', html: `<span>${e}</span>`, iconSize: [16, 16], iconAnchor: [8, 8] }) }

  async function buildVector() {
    const L = window.L
    for (const lyr of VECTOR_LAYERS) {
      const arr = await fetch(`/map/unreallife/geojson/${lyr.file}.geojson`).then(r => r.json()).catch(() => null)
      if (!arr) continue
      L.geoJSON({ type: 'FeatureCollection', features: arr }, {
        coordsToLatLng: geoToLatLng,
        style: lyr.style,
        pointToLayer: (f, latlng) => lyr.emoji
          ? L.marker(latlng, { icon: emojiIcon(lyr.emoji), interactive: false })
          : L.circleMarker(latlng, { radius: 2, color: lyr.color || '#9aa0a6', weight: 1, fillOpacity: 0.8 }),
      }).addTo(lyr.detail ? detailGroup : vectorGroup)
    }
    for (const nf of NAME_LAYERS) {
      const arr = await fetch(`/map/unreallife/geojson/${nf}.geojson`).then(r => r.json()).catch(() => null)
      if (!arr) continue
      for (const f of arr) {
        const c = f.geometry?.coordinates; if (!c) continue
        L.marker(geoToLatLng(c), { icon: L.divIcon({ className: 'map-label', html: f.properties?.name ?? '', iconSize: [80, 0], iconAnchor: [40, 0] }), interactive: false }).addTo(vectorGroup)
      }
    }
  }

  const SAT_DIM = 0.4   // opacité du sat en fond du mode vecteur
  function setMode(m) {
    if (!map) return
    mode = m
    try { localStorage.setItem('nexis.mapMode', m) } catch { /* indispo */ }
    if (m === 'vecteur' && !vectorBuilt) { buildVector(); vectorBuilt = true }   // lazy-load (C)
    // Le sat reste toujours affiché ; on baisse juste son opacité en vecteur (sauf en minimap
    // locale : windowRadius = sat plein dans la fenêtre, vecteur autour).
    const dimInVec = windowRadius ? 1 : SAT_DIM
    satGroup.eachLayer(l => l.setOpacity(m === 'vecteur' ? dimInVec : 1))
    if (m === 'vecteur') { vectorGroup.addTo(map); heavyGroup.addTo(map) }
    else { map.removeLayer(vectorGroup); map.removeLayer(heavyGroup); if (detailGroup) map.removeLayer(detailGroup) }
    updateHeavy()
  }

  // Charge/décharge les cellules house/tree visibles + repères détail selon le zoom (vecteur seul).
  function updateHeavy() {
    if (!map || !heavyGroup) return
    if (mode !== 'vecteur') { heavyGroup.clearLayers(); heavyCells.clear(); return }
    const z = map.getZoom(), b = map.getBounds()
    // Repères ponctuels : visibles seulement zoomé (= avec les bâtiments).
    if (detailGroup) { if (z >= DETAIL_MIN) detailGroup.addTo(map); else map.removeLayer(detailGroup) }
    const wanted = new Set()
    for (const h of HEAVY_LAYERS) {
      const idx = heavyIndex[h.file]
      if (!idx || z < h.minZoom) continue
      const cxA = Math.max(0, Math.floor(b.getWest() / HEAVY_CS)), cxB = Math.min(HEAVY_N - 1, Math.floor(b.getEast() / HEAVY_CS))
      const cyA = Math.max(0, Math.floor(b.getSouth() / HEAVY_CS)), cyB = Math.min(HEAVY_N - 1, Math.floor(b.getNorth() / HEAVY_CS))
      if ((cxB - cxA + 1) * (cyB - cyA + 1) > HEAVY_MAX_CELLS) continue   // trop dézoomé
      for (let cx = cxA; cx <= cxB; cx++) for (let cy = cyA; cy <= cyB; cy++) {
        const ck = `${cx}/${cy}`; if (!idx.has(ck)) continue
        const fk = `${h.file}/${ck}`; wanted.add(fk)
        if (!heavyCells.has(fk)) { heavyCells.set(fk, null); loadHeavyCell(h, ck, fk) }
      }
    }
    for (const fk of [...heavyCells.keys()]) {
      if (!wanted.has(fk)) { const c = heavyCells.get(fk); if (c) heavyGroup.removeLayer(c); heavyCells.delete(fk) }
    }
  }

  async function loadHeavyCell(h, ck, fk) {
    const arr = await fetch(`/map/unreallife/tiles/${h.file}/${ck}.geojson`).then(r => r.json()).catch(() => null)
    if (!arr || !heavyCells.has(fk)) return   // cellule sortie de la vue entre-temps
    const gj = window.L.geoJSON({ type: 'FeatureCollection', features: arr }, {
      coordsToLatLng: geoToLatLng, style: h.style,
      pointToLayer: (f, ll) => window.L.circleMarker(ll, { radius: 1.4, weight: 0, fillColor: h.color || '#3c7a4a', fillOpacity: 0.85 }),
    })
    if (!heavyCells.has(fk)) return            // retirée pendant le fetch
    heavyCells.set(fk, gj); gj.addTo(heavyGroup)
  }

  function iconHtml(i) {
    if (i.nature?.iconeImageId) return `<img class="itv-img" src="/api/sp/icones/${i.nature.iconeImageId}/contenu" alt="">`
    return i.nature?.icone || (i.incendie ? '🔥' : '🚨')
  }
  function numero(i) { return (i.code ?? '').replace(/^INT-?/i, '') }
  function llOf(coord) { const p = gridToImg(coord); return p ? toLatLng(p[0], p[1]) : null }

  // Casernes (🚒) + hôpitaux (🏥) : repères permanents, depuis la config (les 2 modes).
  function renderBase() {
    if (!map || !baseGroup || !window.L) return
    const L = window.L
    baseGroup.clearLayers()
    for (const c of centres) {
      const ll = llOf(c.coordonnees); if (!ll) continue
      const icon = L.divIcon({ className: 'itv-pin', html: `<div class="poi caserne">⛑️</div>`, iconSize: [22, 22], iconAnchor: [11, 11] })
      L.marker(ll, { icon }).addTo(baseGroup).bindPopup(`⛑️ <b>${c.label ?? ''}</b>`)
    }
    for (const h of hopitaux) {
      const ll = llOf(h.coordonnees); if (!ll) continue
      const icon = L.divIcon({ className: 'itv-pin', html: `<div class="poi hopital">🏥</div>`, iconSize: [22, 22], iconAnchor: [11, 11] })
      L.marker(ll, { icon }).addTo(baseGroup).bindPopup(`🏥 <b>${h.label ?? ''}</b>`)
    }
  }

  const MIN_PER_KM = 2   // vitesse retenue (≈ 2 min/km), à peaufiner
  function fmtClock(d) { return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }
  function vehDiv(t) {
    const inner = t.imageId
      ? `<img class="veh-img" src="/api/sp/icones/${t.imageId}/contenu" alt="">`
      : (t.icone || '🚒')
    return `<div class="veh-marker" style="--c:${t.couleur || '#4f6ef7'}">${inner}</div>`
  }
  // Marqueur véhicule : tooltip au survol + clic → callback parent (changer le statut depuis la carte).
  function vehMarker(ll, icon, t, tip) {
    const m = window.L.marker(ll, { icon }).addTo(layer).bindTooltip(tip, { direction: 'top' })
    if (onveh && t.id) m.on('click', () => onveh(t.id))
    return m
  }

  function render() {
    if (!map || !layer || !window.L) return
    const L = window.L
    layer.clearLayers()
    const now = Date.now()
    const atMap = new Map()   // engins garés à un point → barre horizontale sous le point
    for (const t of transits) {
      if (t.at) {
        const ll = llOf(t.at); if (!ll) continue
        const key = `${Math.round(ll[0] / 8)}_${Math.round(ll[1] / 8)}`
        ;(atMap.get(key) ?? atMap.set(key, { ll, items: [] }).get(key)).items.push(t)
        continue
      }
      const a = llOf(t.from), b = llOf(t.to)
      if (!a || !b) continue
      L.polyline([a, b], { color: t.couleur || '#4f6ef7', weight: 2, dashArray: '6 5', opacity: 0.9 }).addTo(layer)
      // Distance (1 px = 1 m) + ETA ; le 🚒 progresse le long du trait selon le temps écoulé.
      const distKm = Math.hypot(a[0] - b[0], a[1] - b[1]) / 1000
      const etaMin = distKm * MIN_PER_KM
      let frac = 0, eta = `~${etaMin.toFixed(0)} min`
      if (t.depart && etaMin > 0) {
        const d0 = new Date(t.depart).getTime()
        frac = Math.max(0, Math.min(1, (now - d0) / 60000 / etaMin))
        eta = frac >= 1 ? 'arrivé' : `arrivée ~${fmtClock(new Date(d0 + etaMin * 60000))}`
      }
      const pos = [a[0] + (b[0] - a[0]) * frac, a[1] + (b[1] - a[1]) * frac]
      const icon = L.divIcon({ className: 'itv-pin', html: vehDiv(t), iconSize: [24, 24], iconAnchor: [12, 12] })
      vehMarker(pos, icon, t, `🚒 ${t.label ?? ''} · ${eta} · ${distKm.toFixed(2)} km`)
    }
    // Barre d'engins sous chaque point (sur place) : côte à côte, jamais empilés.
    for (const grp of atMap.values()) {
      grp.items.forEach((t, k) => {
        const dx = (k - (grp.items.length - 1) / 2) * 24
        const icon = L.divIcon({ className: 'itv-pin', html: vehDiv(t), iconSize: [24, 24], iconAnchor: [12 - dx, -12] })
        vehMarker(grp.ll, icon, t, `🚒 ${t.label ?? ''}`)
      })
    }
  }

  // Pins d'intervention : ne bougent pas → calque séparé, re-rendu seulement quand
  // la liste change (pas à chaque tick d'animation, évite le clignotement).
  function renderInterventions() {
    if (!map || !interLayer || !window.L) return
    const L = window.L
    interLayer.clearLayers()
    for (const i of interventions) {
      const ll = llOf(i.coordonnees); if (!ll) continue
      const html = `<div class="itv-marker"><span class="ic">${iconHtml(i)}</span>${numero(i) ? `<span class="num">${numero(i)}</span>` : ''}</div>`
      const icon = L.divIcon({ className: 'itv-pin', html, iconSize: [34, 38], iconAnchor: [17, 19] })
      L.marker(ll, { icon }).addTo(interLayer)
        .bindPopup(`<b>${i.code ?? ''}</b><br>${(i.motif ?? '').replace(/</g, '&lt;')}`)
    }
  }

  // ── Tracé polygone (zone brûlée) ─────────────────────────────────────────────
  const POLY_STYLE = { color: '#ef9f27', weight: 2, fillColor: '#ef9f27', fillOpacity: 0.22 }
  function vtxIcon() { return window.L.divIcon({ className: 'poly-vtx', html: '', iconSize: [12, 12], iconAnchor: [6, 6] }) }
  function addVertex(latlng) {
    polygon = [...(polygon ?? []), [Math.round(latlng.lat), Math.round(latlng.lng)]]
    onpolygon?.(polygon)
  }
  function commitVertices() {
    polygon = vtxMarkers.map(m => { const ll = m.getLatLng(); return [Math.round(ll.lat), Math.round(ll.lng)] })
    onpolygon?.(polygon)
  }
  function renderPolygon() {
    if (!map || !window.L) return
    if (!drawGroup) drawGroup = window.L.layerGroup().addTo(map)
    drawGroup.clearLayers(); vtxMarkers = []
    const pts = (polygon ?? []).map(p => [p[0], p[1]])
    const styleP = windowRadius ? { ...POLY_STYLE, pane: 'polyTop' } : POLY_STYLE
    polyLayer = pts.length >= 2 ? window.L.polygon(pts, styleP).addTo(drawGroup) : null
    if (!drawPolygon) return
    for (const p of pts) {
      const m = window.L.marker(p, { draggable: true, icon: vtxIcon() }).addTo(drawGroup)
      m.on('drag', () => { if (polyLayer) polyLayer.setLatLngs(vtxMarkers.map(x => x.getLatLng())) })
      m.on('dragend', commitVertices)
      vtxMarkers.push(m)
    }
  }
  $effect(() => { polygon; if (map) renderPolygon() })        // re-rendu du tracé (ajout/effacement parent)

  // ── Marqueurs engins déplaçables (carto FDF) ─────────────────────────────────
  function enginIcon(label) {
    return window.L.divIcon({ className: 'engin-mk', html: `<span class="em-veh">🚒</span><span class="em-lib">${label ?? ''}</span>`, iconSize: [80, 20], iconAnchor: [10, 10] })
  }
  function renderMarkers() {
    if (!map || !window.L) return
    if (!markerGroup) markerGroup = window.L.layerGroup().addTo(map)
    markerGroup.clearLayers(); mkMarkers = []
    for (const mk of (markers ?? [])) {
      if (mk.lat == null || mk.lng == null) continue
      const m = window.L.marker([mk.lat, mk.lng], { draggable: true, icon: enginIcon(mk.label) }).addTo(markerGroup)
      m.on('dragend', commitMarkers)
      mkMarkers.push({ id: mk.id, m })
    }
  }
  function commitMarkers() {
    onmarker?.(mkMarkers.map(({ id, m }) => { const ll = m.getLatLng(); return { id, lat: Math.round(ll.lat), lng: Math.round(ll.lng) } }))
  }
  $effect(() => { markers; if (map) renderMarkers() })

  // ── Flèches (vent / propagation, etc.) ───────────────────────────────────────
  // arrows = [{ lat0, lng0, lat1, lng1, color, label }]
  function renderArrows() {
    if (!map || !window.L) return
    if (!arrowsGroup) arrowsGroup = window.L.layerGroup().addTo(map)
    arrowsGroup.clearLayers()
    const linePane = windowRadius ? { pane: 'polyTop' } : {}
    for (const a of (arrows ?? [])) {
      if (a.lat0 == null || a.lng0 == null || a.lat1 == null || a.lng1 == null) continue
      window.L.polyline([[a.lat0, a.lng0], [a.lat1, a.lng1]], { color: a.color || '#fff', weight: 3, opacity: 0.9, ...linePane }).addTo(arrowsGroup)
      // CRS.Simple : lat = nord ; angle 0 = nord, sens horaire. Rotation SVG : 0 = haut (=nord).
      const dy = a.lat1 - a.lat0, dx = a.lng1 - a.lng0
      const angle = Math.atan2(dx, dy) * 180 / Math.PI
      const head = window.L.divIcon({ className: 'arr-head', html: `<div class="arr-rot" style="--ang:${angle}deg;--c:${a.color || '#fff'}"><svg width="18" height="18" viewBox="0 0 18 18"><polygon points="9,0 2,16 9,12 16,16" /></svg></div>`, iconSize: [18, 18], iconAnchor: [9, 9] })
      window.L.marker([a.lat1, a.lng1], { icon: head, interactive: false }).addTo(arrowsGroup)
      if (a.label) {
        const mid = [a.lat0 + (a.lat1 - a.lat0) / 2, a.lng0 + (a.lng1 - a.lng0) / 2]
        const lab = window.L.divIcon({ className: 'arr-lab', html: `<span style="--c:${a.color || '#fff'}">${a.label}</span>`, iconSize: [80, 14], iconAnchor: [40, -6] })
        window.L.marker(mid, { icon: lab, interactive: false }).addTo(arrowsGroup)
      }
    }
  }
  $effect(() => { arrows; if (map) renderArrows() })

  $effect(() => { transits; tick; render() })                 // engins animés (chaque seconde)
  // Pins d'intervention : re-rendus seulement si la liste change RÉELLEMENT (code/position/icône).
  // Un simple changement de statut véhicule recharge la liste des interventions à l'identique →
  // sans cette garde, les pins clignoteraient à chaque maj live.
  let interSig = ''
  $effect(() => {
    const sig = interventions.map(i => `${i.code}|${i.coordonnees}|${i.nature?.icone ?? ''}|${i.nature?.iconeImageId ?? ''}|${i.incendie ? 1 : 0}`).join(';')
    if (sig === interSig) return
    interSig = sig
    renderInterventions()
  })
  $effect(() => { centres; hopitaux; renderBase() })          // repères permanents (rares maj)
</script>

<div class="mapwrap" style="height:{height}">
  <div bind:this={el} class="mapview"></div>
  <div class="map-toggle">
    <button type="button" class:on={mode === 'sat'} onclick={() => setMode('sat')}>Satellite</button>
    <button type="button" class:on={mode === 'vecteur'} onclick={() => setMode('vecteur')}>Vecteur</button>
  </div>
  <div class="map-legend">
    <button type="button" class="lg-toggle" onclick={() => showLegend = !showLegend}>{showLegend ? '✕ Légende' : 'ⓘ Légende'}</button>
    {#if showLegend}
      <ul>
        <li><span class="lg-sym">⛑️</span> Caserne</li>
        <li><span class="lg-sym">🏥</span> Hôpital</li>
        <li><span class="lg-sym">🚒</span> Engin <span class="muted">(couleur = statut)</span></li>
        <li><span class="lg-dash"></span> Trajet</li>
        <li><span class="lg-sym">🔥</span> Intervention</li>
        <li class="lg-sep">Carte</li>
        {#each VECTOR_LAYERS as l}
          <li>
            {#if l.emoji}<span class="lg-sym">{l.emoji}</span>
            {:else if l.color}<span class="lg-dot" style="background:{l.color}"></span>
            {:else if l.style?.fillColor && l.style?.fillOpacity}<span class="lg-box" style="background:{l.style.fillColor}"></span>
            {:else}<span class="lg-line" style="border-color:{l.style?.color}"></span>{/if}
            {l.label}
          </li>
        {/each}
        <li><span class="lg-box" style="background:#5b554f"></span> Bâtiment</li>
        <li><span class="lg-dot" style="background:#3c7a4a"></span> Arbre</li>
      </ul>
    {/if}
  </div>
</div>

<style>
  .mapwrap { position: relative; width: 100%; }
  .mapview { width: 100%; height: 100%; border-radius: var(--radius); overflow: hidden; background: #0b1622; }
  .map-toggle { position: absolute; top: 8px; right: 8px; z-index: 500; display: flex; border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .map-toggle button { background: var(--color-surface); color: var(--color-muted); border: none; font-size: 11px; padding: 4px 10px; cursor: pointer; }
  .map-toggle button.on { background: color-mix(in srgb, var(--accent) 20%, transparent); color: var(--accent); font-weight: 600; }
  :global(.leaflet-container) { background: #0b1622; font: inherit; }
  :global(.itv-pin) { background: none; border: none; }
  :global(.itv-marker) { display: flex; flex-direction: column; align-items: center; line-height: 1; cursor: pointer; }
  :global(.itv-marker .ic) { font-size: 22px; filter: drop-shadow(0 1px 2px rgba(0,0,0,.7)); }
  :global(.itv-marker .ic .itv-img) { width: 24px; height: 24px; object-fit: contain; display: block; }
  :global(.itv-marker .num) { font-size: 11px; font-weight: 800; color: #fff; background: rgba(0,0,0,.65); border-radius: 6px; padding: 0 4px; margin-top: -2px; }
  :global(.map-label) { color: #e8eef5; font-size: 11px; font-weight: 700; text-shadow: 0 1px 2px #000, 0 0 3px #000; white-space: nowrap; text-align: center; pointer-events: none; }
  :global(.veh-marker) { font-size: 18px; line-height: 1; filter: drop-shadow(0 0 2px var(--c)) drop-shadow(0 1px 2px rgba(0,0,0,.7)); cursor: pointer; }
  :global(.veh-marker .veh-img) { width: 22px; height: 22px; object-fit: contain; display: block; }
  :global(.poi) { font-size: 15px; line-height: 18px; width: 22px; height: 22px; text-align: center; border-radius: 5px; cursor: pointer; box-shadow: 0 1px 3px rgba(0,0,0,.6); }
  :global(.poi.caserne) { background: rgba(79,110,247,.28); border: 1px solid #4f6ef7; }
  :global(.poi.hopital) { background: rgba(224,92,92,.28); border: 1px solid #e05c5c; }
  :global(.poi-pt) { background: none; border: none; font-size: 12px; line-height: 16px; text-align: center; filter: drop-shadow(0 1px 1px rgba(0,0,0,.6)); }
  :global(.poly-vtx) { background: #ef9f27; border: 2px solid #fff; border-radius: 50%; box-shadow: 0 1px 3px rgba(0,0,0,.6); cursor: grab; }
  :global(.engin-mk) { background: none; border: none; display: flex; align-items: center; gap: 3px; cursor: grab; white-space: nowrap; }
  :global(.engin-mk .em-veh) { font-size: 16px; filter: drop-shadow(0 1px 2px rgba(0,0,0,.8)); }
  :global(.engin-mk .em-lib) { font-size: 10px; font-weight: 700; color: #fff; background: rgba(0,0,0,.6); border-radius: 4px; padding: 0 4px; }
  :global(.arr-head) { background: none; border: none; }
  :global(.arr-head .arr-rot) { width: 18px; height: 18px; transform: rotate(var(--ang)); filter: drop-shadow(0 1px 2px rgba(0,0,0,.7)); }
  :global(.arr-head .arr-rot svg polygon) { fill: var(--c); }
  :global(.arr-lab) { background: none; border: none; }
  :global(.arr-lab span) { font-size: 10px; font-weight: 700; color: var(--c); background: rgba(0,0,0,.65); border-radius: 4px; padding: 1px 5px; white-space: nowrap; }
  .map-legend { position: absolute; bottom: 8px; left: 8px; z-index: 500; font-size: 11px; }
  .lg-toggle { background: var(--color-surface); color: var(--color-muted); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 3px 8px; cursor: pointer; font-size: 11px; }
  .map-legend ul { list-style: none; margin: 4px 0 0; padding: 8px 10px; background: color-mix(in srgb, var(--color-surface) 94%, transparent); border: 1px solid var(--color-border); border-radius: var(--radius); display: flex; flex-direction: column; gap: 3px; max-height: 60vh; overflow-y: auto; }
  .map-legend li { display: flex; align-items: center; gap: 6px; color: var(--color-text); white-space: nowrap; }
  .map-legend .muted { color: var(--color-muted); }
  .lg-sep { margin-top: 4px; font-size: 9px; text-transform: uppercase; letter-spacing: .5px; color: var(--color-muted); border-top: 1px solid var(--color-border); padding-top: 4px; }
  .lg-sym { display: inline-block; width: 16px; text-align: center; }
  .lg-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
  .lg-box { display: inline-block; width: 12px; height: 9px; border-radius: 2px; flex-shrink: 0; }
  .lg-line { display: inline-block; width: 16px; border-top: 2px solid #aab0b6; }
  .lg-dash { display: inline-block; width: 18px; border-top: 2px dashed #aab0b6; }
</style>
