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
  // Calibration (ajuster ici si décalage) :
  //  - X : pas 100 m → gx 000..102 sur 10240 m.
  //  - Y : origine au milieu ; nord = 000++ (vers le haut), sud = 999-- (vers le bas) ;
  //        pas 10.24 m/unité ; décalage −20 m.
  const CY = IMG / 1000   // 10.24 m par unité de gy
  const Y_OFF = 20        // décalage vertical (m)
  function gridToImg(coord) {
    if (!coord || coord.length < 6) return null
    const gx = +coord.slice(0, 3), gy = +coord.slice(3, 6)
    if (Number.isNaN(gx) || Number.isNaN(gy)) return null
    const s = gy <= 500 ? gy : gy - 1000   // signé : >0 nord, <0 sud
    const px = gx / 102.4 * IMG
    const py = IMG / 2 - s * CY + Y_OFF
    return [px, py]   // [px depuis gauche, py depuis haut]
  }
  // pixel image → latlng CRS.Simple (lat vers le haut → on inverse py)
  function toLatLng(px, py) { return [IMG - py, px] }
  // latlng → coordonnées jeu (pour le clic de saisie)
  function latLngToGrid(latlng) {
    const px = latlng.lng, py = IMG - latlng.lat
    const gx = Math.round(px / IMG * 102.4)
    let gy = Math.round((IMG / 2 + Y_OFF - py) / CY)
    gy = ((gy % 1000) + 1000) % 1000
    return String(Math.max(0, Math.min(102, gx))).padStart(3, '0') + String(gy).padStart(3, '0')
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

  function iconHtml(i) { return i.nature?.icone || (i.incendie ? '🔥' : '🚨') }
  function numero(i) { return (i.code ?? '').replace(/^INT-?/i, '') }

  function render() {
    if (!map || !layer || !window.L) return
    const L = window.L
    layer.clearLayers()
    for (const i of interventions) {
      const p = gridToImg(i.coordonnees); if (!p) continue
      const html = `<div class="itv-marker"><span class="ic">${iconHtml(i)}</span>${numero(i) ? `<span class="num">${numero(i)}</span>` : ''}</div>`
      const icon = L.divIcon({ className: 'itv-pin', html, iconSize: [34, 38], iconAnchor: [17, 19] })
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
  :global(.itv-marker) { display: flex; flex-direction: column; align-items: center; line-height: 1; cursor: pointer; }
  :global(.itv-marker .ic) { font-size: 22px; filter: drop-shadow(0 1px 2px rgba(0,0,0,.7)); }
  :global(.itv-marker .num) { font-size: 11px; font-weight: 800; color: #fff; background: rgba(0,0,0,.65); border-radius: 6px; padding: 0 4px; margin-top: -2px; }
  :global(.leaflet-container) { background: #0b1622; font: inherit; }
</style>
