<script>
  // Pagination côté client. Le parent découpe : rows.slice((page-1)*pageSize, page*pageSize).
  let { page = $bindable(1), pageSize = $bindable(25), total = 0, sizes = [25, 50, 100] } = $props()
  let pages = $derived(Math.max(1, Math.ceil(total / pageSize)))
  $effect(() => { if (page > pages) page = pages })
  function go(p) { page = Math.min(pages, Math.max(1, p)) }
</script>

<div class="pager">
  <span class="muted small">{total} résultat{total > 1 ? 's' : ''}</span>
  <label class="pg-size">Par page
    <select bind:value={pageSize}>{#each sizes as s}<option value={s}>{s}</option>{/each}</select>
  </label>
  <div class="pg-btns">
    <button type="button" disabled={page <= 1} onclick={() => go(page - 1)} aria-label="Page précédente">‹</button>
    <span class="small">{page} / {pages}</span>
    <button type="button" disabled={page >= pages} onclick={() => go(page + 1)} aria-label="Page suivante">›</button>
  </div>
</div>

<style>
  .pager { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; padding: 8px 2px; }
  .pg-size { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-muted); }
  .pg-size select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 3px 6px; }
  .pg-btns { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }
  .pg-btns button { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 14px; line-height: 1; padding: 3px 10px; cursor: pointer; }
  .pg-btns button:disabled { opacity: .4; cursor: default; }
</style>
