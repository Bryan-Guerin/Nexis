<script>
  import { toasts } from './toasts.js'
</script>

<div class="toasts">
  {#each $toasts as t (t.id)}
    <div class="toast {t.kind}">{t.message}</div>
  {/each}
</div>

<style>
  .toasts {
    position: fixed; bottom: 16px; right: 16px; z-index: 300;
    display: flex; flex-direction: column; gap: 8px;
  }
  .toast {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-left: 3px solid var(--color-success);
    border-radius: var(--radius);
    padding: 8px 14px; font-size: 13px;
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35);
    animation: t-in 0.15s ease;
  }
  .toast.error { border-left-color: var(--color-danger); }
  @keyframes t-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
</style>
