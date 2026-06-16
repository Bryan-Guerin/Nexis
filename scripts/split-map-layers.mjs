// Découpe les grosses couches geojson (house, tree…) en tuiles spatiales pour un
// chargement « à la demande » côté carte (on ne fetch que les cellules visibles).
//
// Usage :
//   node scripts/split-map-layers.mjs <srcGeojsonDir> <outTilesDir> [N=32] [layers=house,tree]
// Ex :
//   node scripts/split-map-layers.mjs \
//     ops/grad_meh/unreallife_map/geojson \
//     nexis-app/front/public/map/unreallife/tiles 32 house,tree
//
// Source : chaque couche est ops/.../<layer>.geojson/<layer>.geojson (tableau de Features,
// coords en mètres monde [x,y], monde 0..10240). Sortie : <out>/<layer>/{cx}/{cy}.geojson
// (tableau de Features) + <out>/<layer>/index.json (liste "cx/cy" existants).
import fs from 'node:fs'
import path from 'node:path'

const SRC = process.argv[2] ?? 'ops/grad_meh/unreallife_map/geojson'
const OUT = process.argv[3] ?? 'nexis-app/front/public/map/unreallife/tiles'
const N   = +(process.argv[4] ?? 32)
const LAYERS = (process.argv[5] ?? 'house,tree').split(',').map(s => s.trim()).filter(Boolean)
const IMG = 10240, CS = IMG / N   // taille de cellule (m)

// Premier couple [x,y] d'une géométrie (Point/Line/Polygon/Multi…).
function firstCoord(g) {
  let c = g?.coordinates
  while (Array.isArray(c) && Array.isArray(c[0])) c = c[0]
  return Array.isArray(c) && typeof c[0] === 'number' ? c : null
}

for (const layer of LAYERS) {
  const file = path.join(SRC, `${layer}.geojson`, `${layer}.geojson`)
  if (!fs.existsSync(file)) { console.warn(`skip ${layer} (absent: ${file})`); continue }
  const arr = JSON.parse(fs.readFileSync(file, 'utf8'))
  const cells = new Map()
  for (const f of arr) {
    const c = firstCoord(f.geometry); if (!c) continue
    const cx = Math.max(0, Math.min(N - 1, Math.floor(c[0] / CS)))
    const cy = Math.max(0, Math.min(N - 1, Math.floor(c[1] / CS)))
    const key = `${cx}/${cy}`
    ;(cells.get(key) ?? cells.set(key, []).get(key)).push(f)
  }
  const base = path.join(OUT, layer)
  fs.rmSync(base, { recursive: true, force: true })
  fs.mkdirSync(base, { recursive: true })
  const index = []
  for (const [key, feats] of cells) {
    const out = path.join(base, `${key}.geojson`)
    fs.mkdirSync(path.dirname(out), { recursive: true })
    fs.writeFileSync(out, JSON.stringify(feats))
    index.push(key)
  }
  fs.writeFileSync(path.join(base, 'index.json'), JSON.stringify(index))
  console.log(`${layer}: ${arr.length} features → ${cells.size} tuiles (cellule ${CS} m)`)
}
