<script>
  import {onMount, onDestroy} from 'svelte'

  // Heatmap simplifiée : satellite UnRealLife + cercles proportionnels au nombre
  // d'interventions par coordonnée. Légère par rapport à MapView (pas de vecteurs,
  // pas d'animation, juste sat + heat).
  let { points = [], height = '460px' } = $props()

  const TILE = 2560, NTILE = 4
  const IMG = TILE * NTILE
  const CX = IMG / 102.4
  const CY = CX
  const Y_OFF = 20

  function gridToImg(coord) {
    if (!coord || coord.length < 6) return null
    const gx = +coord.slice(0, 3), gy = +coord.slice(3, 6)
    if (Number.isNaN(gx) || Number.isNaN(gy)) return null
    const s = gy <= 500 ? gy : gy - 1000
    return [gx * CX, IMG / 2 - s * CY + Y_OFF]
  }
  function toLatLng(px, py) { return [IMG - py, px] }

  let el = $state(null)
  let map = null
  let heatLayer = null

  onMount(() => {
    const L = window.L
    if (!L) return
    map = L.map(el, { crs: L.CRS.Simple, minZoom: -5, maxZoom: 3, attributionControl: false, zoomSnap: 0.25, preferCanvas: true })
    map.fitBounds([[0, 0], [IMG, IMG]])
    for (let x = 0; x < NTILE; x++) for (let y = 0; y < NTILE; y++) {
      L.imageOverlay(`/map/unreallife/sat/${x}/${y}.png`,
        [[IMG - (y + 1) * TILE, x * TILE], [IMG - y * TILE, (x + 1) * TILE]],
        { pane: 'tilePane' }).addTo(map)
    }
    heatLayer = L.layerGroup().addTo(map)
    render()
  })
  onDestroy(() => { if (map) { map.remove(); map = null } })

  // Re-rend quand `points` change.
  let lastPoints = []
  $effect(() => {
    if (points !== lastPoints) { lastPoints = points; render() }
  })

  function render() {
    if (!heatLayer) return
    const L = window.L
    heatLayer.clearLayers()
    if (!points || points.length === 0) return
    const maxCount = Math.max(1, ...points.map(p => p.count))
    for (const p of points) {
      const xy = gridToImg(p.coordonnees)
      if (!xy) continue
      const intensite = p.count / maxCount   // 0..1
      // rayon (px image) : 40 → 200 selon intensité
      const r = 40 + intensite * 160
      // couleur : vert (faible) → jaune → orange → rouge (fort)
      const c = heatColor(intensite)
      L.circle(toLatLng(xy[0], xy[1]), {
        radius: r, color: c, weight: 1, fillColor: c, fillOpacity: 0.35,
      }).bindTooltip(`${p.count} intervention${p.count > 1 ? 's' : ''}`, { sticky: true })
       .addTo(heatLayer)
    }
  }

  function heatColor(t) {
    // Interpolation simple sur 4 stops.
    const stops = [
      [0.0, [76, 175, 130]],   // vert
      [0.4, [232, 218, 80]],   // jaune
      [0.7, [232, 162, 58]],   // orange
      [1.0, [224, 92, 92]],    // rouge
    ]
    for (let i = 0; i < stops.length - 1; i++) {
      const [t0, c0] = stops[i], [t1, c1] = stops[i + 1]
      if (t >= t0 && t <= t1) {
        const k = (t - t0) / (t1 - t0)
        const r = Math.round(c0[0] + (c1[0] - c0[0]) * k)
        const g = Math.round(c0[1] + (c1[1] - c0[1]) * k)
        const b = Math.round(c0[2] + (c1[2] - c0[2]) * k)
        return `rgb(${r},${g},${b})`
      }
    }
    return 'rgb(224,92,92)'
  }
</script>

<div class="heatmap" style="height:{height}" bind:this={el}></div>

<style>
  .heatmap { width: 100%; border: 1px solid var(--color-border); border-radius: var(--radius); background: #0a0d14; }
  :global(.leaflet-tooltip) { background: var(--color-surface); color: var(--color-text); border: 1px solid var(--color-border); font-size: 12px; }
</style>
