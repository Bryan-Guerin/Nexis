<script>
  // En-tête de colonne triable. Usage : <SortableTh {col} label="…" {sort} onsort={c => sort = nextSort(sort, c)} />
  let { col, label, sort, onsort, style = '' } = $props()
</script>

<th class="sortable" {style} onclick={() => onsort(col)} title="Trier">
  {label}<span class="arr">{sort.col === col ? (sort.dir === 'asc' ? '▲' : '▼') : ''}</span>
</th>

<style>
  .sortable { cursor: pointer; user-select: none; white-space: nowrap; }
  .sortable:hover { color: var(--color-text); }
  .arr { font-size: 9px; margin-left: 4px; color: var(--accent); display: inline-block; width: 8px; }
</style>
