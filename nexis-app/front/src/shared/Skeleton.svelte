<script>
  // Squelette de chargement (barres shimmer). Remplace les « Chargement… ».
  let { rows = 4, height = '40px', gap = '10px' } = $props()
</script>

<div class="sk" style="gap:{gap}" aria-busy="true" aria-label="Chargement">
  {#each Array(rows) as _, i (i)}<div class="sk-bar" style="height:{height}"></div>{/each}
</div>

<style>
  .sk { display: flex; flex-direction: column; }
  .sk-bar {
    border-radius: var(--radius);
    background: linear-gradient(90deg,
      var(--color-surface) 25%,
      color-mix(in srgb, var(--color-surface) 55%, var(--color-border)) 37%,
      var(--color-surface) 63%);
    background-size: 400% 100%;
    animation: sk 1.3s ease-in-out infinite;
  }
  @keyframes sk { 0% { background-position: 100% 0; } 100% { background-position: 0 0; } }
  @media (prefers-reduced-motion: reduce) { .sk-bar { animation: none; } }
</style>
