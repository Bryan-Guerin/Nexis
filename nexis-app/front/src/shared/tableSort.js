// Comparateur de tri générique (numérique naturel + texte FR, null en fin).
export function compareBy(keyFn, dir = 'asc') {
  const s = dir === 'desc' ? -1 : 1
  return (a, b) => {
    const av = keyFn(a), bv = keyFn(b)
    if (av == null && bv == null) return 0
    if (av == null) return 1
    if (bv == null) return -1
    if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * s
    return String(av).localeCompare(String(bv), 'fr', { numeric: true }) * s
  }
}

// Bascule l'état de tri { col, dir } sur une colonne (asc → desc → asc).
export function nextSort(sort, col) {
  if (sort.col === col) return { col, dir: sort.dir === 'asc' ? 'desc' : 'asc' }
  return { col, dir: 'asc' }
}
