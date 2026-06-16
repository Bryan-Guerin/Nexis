<script>
  import {onMount, onDestroy} from 'svelte'

  // Carte (Leaflet, CRS.Simple sur le fond UnRealLife). Réutilisable : dispatch + création.
  // - interventions : [{ code, motif, coordonnees, incendie, nature }]
  // - oncoordpick : callback(coord6) quand on clique la carte (mode saisie)
  let { interventions = [], height = '380px', oncoordpick = null } = $props()

  const TILE = 2560, NTILE = 4
  const IMG = TILE * NTILE   // mosaïque sat 4×4 de 2560 → 10240×10240 (= worldSize, 1 px = 1 m)

  // ── Conversion coordonnées jeu (6 chiffres) → pixel image ────────────────────
  // Calibré sur points connus : X au pas 100 m (000→102.4), Y au pas 10 m (000→1000).
  // Si décalage, ajuster UNIQUEMENT ces deux lignes.
  // Origine jeu (0,0) = milieu du bord gauche → Y décalé d'une demi-hauteur (puis wrap).
  const Y_OFFSET = IMG / 2
  function gridToImg(coord) {
    if (!coord || coord.length < 6) return null
    const gx = +coord.slice(0, 3), gy = +coord.slice(3, 6)
    if (Number.isNaN(gx) || Number.isNaN(gy)) return null
    const px = gx / 102.4 * IMG
    const py = ((gy / 1000 * IMG) + Y_OFFSET) % IMG
    return [px, py]   // [px depuis gauche, py depuis haut]
  }
  // pixel image → latlng CRS.Simple (lat vers le haut → on inverse py)
  function toLatLng(px, py) { return [IMG - py, px] }
  // latlng → coordonnées jeu (pour le clic de saisie)
  function latLngToGrid(latlng) {
    const px = latlng.lng, py = IMG - latlng.lat
    const gx = Math.round(px / IMG * 102.4)
    const gy = Math.round((((py - Y_OFFSET + IMG) % IMG) / IMG) * 1000)
    return String(Math.max(0, Math.min(102, gx))).padStart(3, '0')
         + String(((gy % 1000) + 1000) % 1000).padStart(3, '0')
  }

  let el, map, layer

  onMount(() => {
    const L = window.L
    if (!L) return
    map = L.map(el, { crs: L.CRS.Simple, minZoom: -5, maxZoom: 3, attributionControl: false, zoomSnap: 0.25 })
    const bounds = [[0, 0], [IMG, IMG]]
    // Mosaïque sat : tuile sat/{x}/{y}.png → colonne x (gauche→droite), ligne y (haut→bas).
    for (let x = 0; x < NTILE; x++) {
      for (let y = 0; y < NTILE; y++) {
        const sw = [IMG - (y + 1) * TILE, x * TILE]
        const ne = [IMG - y * TILE, (x + 1) * TILE]
        L.imageOverlay(`/map/unreallife/sat/${x}/${y}.png`, [sw, ne]).addTo(map)
      }
    }
    map.fitBounds(bounds)
    layer = L.layerGroup().addTo(map)
    if (oncoordpick) map.on('click', e => oncoordpick(latLngToGrid(e.latlng)))
    render()
  })
  onDestroy(() => { if (map) { map.remove(); map = null } })

  function iconHtml(i) { return i.incendie ? '🔥' : (i.nature?.code ?? '🚨') }

  function render() {
    if (!map || !layer || !window.L) return
    const L = window.L
    layer.clearLayers()
    for (const i of interventions) {
      const p = gridToImg(i.coordonnees); if (!p) continue
      const icon = L.divIcon({ className: 'itv-pin', html: `<span class="pin">${iconHtml(i)}</span>`, iconSize: [28, 28], iconAnchor: [14, 14] })
      L.marker(toLatLng(p[0], p[1]), { icon }).addTo(layer)
        .bindPopup(`<b>${i.code ?? ''}</b><br>${(i.motif ?? '').replace(/</g, '&lt;')}`)
    }
  }

  // Re-render quand la liste change.
  $effect(() => { interventions; render() })
</script>

<div bind:this={el} class="mapview" style="height:{height}"></div>

<style>
  .mapview { width: 100%; border-radius: var(--radius); overflow: hidden; background: #0b1622; }
  :global(.itv-pin) { background: none; border: none; }
  :global(.itv-pin .pin) { font-size: 22px; line-height: 1; filter: drop-shadow(0 1px 2px rgba(0,0,0,.6)); cursor: pointer; }
  :global(.leaflet-container) { background: #0b1622; font: inherit; }
</style>
