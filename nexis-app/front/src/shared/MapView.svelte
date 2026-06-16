<script>
  import {onMount, onDestroy} from 'svelte'

  // Carte (Leaflet, CRS.Simple sur le terrain UnRealLife). Fond satellite (mosaïque) ou
  // vectoriel (geojson : forêts, routes, bâtiments-repères, noms). Réutilisable.
  let { interventions = [], transits = [], centres = [], hopitaux = [], height = '380px', oncoordpick = null } = $props()

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

  // Couches vectorielles (geojson) : polygones/lignes + style.
  const VECTOR_LAYERS = [
    { file: 'forest',      style: { stroke: false, fillColor: '#2f6d43', fillOpacity: 0.55 } },
    { file: 'track',       style: { color: '#8a8f96', weight: 0.7, dashArray: '3' } },
    { file: 'road',        style: { color: '#aab0b6', weight: 1.2 } },
    { file: 'main_road',   style: { color: '#e0c24a', weight: 2.2 } },
    { file: 'church',      style: { color: '#9aa0a6', weight: 1, fillColor: '#b9bfc6', fillOpacity: 0.6 } },
    { file: 'fuelstation', style: { color: '#e0a23c', weight: 1, fillColor: '#e0a23c', fillOpacity: 0.5 } },
    { file: 'hospital',    style: { color: '#e05c5c', weight: 1, fillColor: '#e05c5c', fillOpacity: 0.6 } },
  ]
  const NAME_LAYERS = ['namecity', 'namevillage', 'namemarine']

  let el, map, satGroup, vectorGroup, layer, baseGroup, clockTimer
  let mode = $state('sat')   // 'sat' | 'vecteur'
  let tick = $state(0)       // horloge (1 s) pilotant l'animation des véhicules

  onMount(() => {
    const L = window.L
    if (!L) return
    map = L.map(el, { crs: L.CRS.Simple, minZoom: -5, maxZoom: 3, attributionControl: false, zoomSnap: 0.25, preferCanvas: true })
    map.fitBounds([[0, 0], [IMG, IMG]])

    // Fond satellite (mosaïque sat/{x}/{y}.png)
    satGroup = L.layerGroup()
    for (let x = 0; x < NTILE; x++)
      for (let y = 0; y < NTILE; y++)
        L.imageOverlay(`/map/unreallife/sat/${x}/${y}.png`, [[IMG - (y + 1) * TILE, x * TILE], [IMG - y * TILE, (x + 1) * TILE]]).addTo(satGroup)

    vectorGroup = L.layerGroup()
    buildVector()

    satGroup.addTo(map)
    baseGroup = L.layerGroup().addTo(map)   // casernes + hôpitaux (permanents, les 2 modes)
    layer = L.layerGroup().addTo(map)
    if (oncoordpick) map.on('click', e => oncoordpick(latLngToGrid(e.latlng)))
    renderBase()
    render()
    clockTimer = setInterval(() => tick++, 1000)
  })
  onDestroy(() => { clearInterval(clockTimer); if (map) { map.remove(); map = null } })

  async function buildVector() {
    const L = window.L
    for (const lyr of VECTOR_LAYERS) {
      const arr = await fetch(`/map/unreallife/geojson/${lyr.file}.geojson`).then(r => r.json()).catch(() => null)
      if (!arr) continue
      L.geoJSON({ type: 'FeatureCollection', features: arr }, { coordsToLatLng: geoToLatLng, style: lyr.style }).addTo(vectorGroup)
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

  function setMode(m) {
    if (!map) return
    mode = m
    if (m === 'vecteur') { map.removeLayer(satGroup); vectorGroup.addTo(map) }
    else { map.removeLayer(vectorGroup); satGroup.addTo(map) }
  }

  function iconHtml(i) { return i.nature?.icone || (i.incendie ? '🔥' : '🚨') }
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
  function vehDiv(t) { return `<div class="veh-marker" style="--c:${t.couleur || '#4f6ef7'}">${t.icone || '🚒'}</div>` }

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
      L.marker(pos, { icon }).addTo(layer).bindPopup(`🚒 ${t.label ?? ''}<br>${eta} · ${distKm.toFixed(2)} km`)
    }
    // Barre d'engins sous chaque point (sur place) : côte à côte, jamais empilés.
    for (const grp of atMap.values()) {
      grp.items.forEach((t, k) => {
        const dx = (k - (grp.items.length - 1) / 2) * 24
        const icon = L.divIcon({ className: 'itv-pin', html: vehDiv(t), iconSize: [24, 24], iconAnchor: [12 - dx, -12] })
        L.marker(grp.ll, { icon }).addTo(layer).bindPopup(`🚒 ${t.label ?? ''}`)
      })
    }
    for (const i of interventions) {
      const ll = llOf(i.coordonnees); if (!ll) continue
      const html = `<div class="itv-marker"><span class="ic">${iconHtml(i)}</span>${numero(i) ? `<span class="num">${numero(i)}</span>` : ''}</div>`
      const icon = L.divIcon({ className: 'itv-pin', html, iconSize: [34, 38], iconAnchor: [17, 19] })
      L.marker(ll, { icon }).addTo(layer)
        .bindPopup(`<b>${i.code ?? ''}</b><br>${(i.motif ?? '').replace(/</g, '&lt;')}`)
    }
  }
  $effect(() => { interventions; transits; tick; render() })
  $effect(() => { centres; hopitaux; renderBase() })
</script>

<div class="mapwrap" style="height:{height}">
  <div bind:this={el} class="mapview"></div>
  <div class="map-toggle">
    <button type="button" class:on={mode === 'sat'} onclick={() => setMode('sat')}>Satellite</button>
    <button type="button" class:on={mode === 'vecteur'} onclick={() => setMode('vecteur')}>Vecteur</button>
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
  :global(.itv-marker .num) { font-size: 11px; font-weight: 800; color: #fff; background: rgba(0,0,0,.65); border-radius: 6px; padding: 0 4px; margin-top: -2px; }
  :global(.map-label) { color: #e8eef5; font-size: 11px; font-weight: 700; text-shadow: 0 1px 2px #000, 0 0 3px #000; white-space: nowrap; text-align: center; pointer-events: none; }
  :global(.veh-marker) { font-size: 18px; line-height: 1; filter: drop-shadow(0 0 2px var(--c)) drop-shadow(0 1px 2px rgba(0,0,0,.7)); cursor: pointer; }
  :global(.poi) { font-size: 15px; line-height: 18px; width: 22px; height: 22px; text-align: center; border-radius: 5px; cursor: pointer; box-shadow: 0 1px 3px rgba(0,0,0,.6); }
  :global(.poi.caserne) { background: rgba(79,110,247,.28); border: 1px solid #4f6ef7; }
  :global(.poi.hopital) { background: rgba(224,92,92,.28); border: 1px solid #e05c5c; }
</style>
