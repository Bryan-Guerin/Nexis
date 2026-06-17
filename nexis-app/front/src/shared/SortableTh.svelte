<script>
  // En-tête de colonne triable. Usage : <SortableTh {col} label="…" {sort} onsort={c => sort = nextSort(sort, c)} />
  let { col, label, sort, onsort, style = '' } = $props()
  let ariaSort = $derived(sort.col === col ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none')
</script>

<th class="sortable" {style} aria-sort={ariaSort}>
  <button type="button" class="th-btn" onclick={() => onsort(col)} aria-label={`Trier par ${label}`}>
    {label}<span class="arr" aria-hidden="true">{sort.col === col ? (sort.dir === 'asc' ? '▲' : '▼') : ''}</span>
  </button>
</th>

<style>
  .sortable { user-select: none; white-space: nowrap; padding: 0 !important; }
  .th-btn { background: none; border: none; color: inherit; font: inherit; text-transform: inherit; letter-spacing: inherit; padding: 10px 12px; width: 100%; text-align: left; cursor: pointer; }
  .th-btn:hover { color: var(--color-text); }
  .arr { font-size: 9px; margin-left: 4px; color: var(--accent); display: inline-block; width: 8px; }
</style>
