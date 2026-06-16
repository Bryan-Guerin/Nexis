<script>
  import {toasts, dismiss} from './toasts.js'
</script>

<div class="toasts">
  {#each $toasts as t (t.id)}
    <button class="toast {t.kind}" onclick={() => dismiss(t.id)} title="Masquer">{t.message}</button>
  {/each}
</div>

<style>
  .toasts {
    position: fixed; bottom: 16px; right: 16px; z-index: 2000;
    display: flex; flex-direction: column; gap: 8px; max-width: min(360px, 90vw);
  }
  .toast {
    text-align: left; background: var(--color-surface); color: var(--color-text);
    border: 1px solid var(--color-border);
    border-left: 3px solid var(--color-success);
    border-radius: var(--radius);
    padding: 9px 14px; font-size: 13px; cursor: pointer;
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35);
    animation: t-in 0.15s ease;
  }
  .toast.error { border-left-color: var(--color-danger); }
  .toast.info  { border-left-color: var(--color-primary); }
  @keyframes t-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
</style>
