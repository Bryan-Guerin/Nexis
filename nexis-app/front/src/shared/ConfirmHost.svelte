<script>
  import {confirmState, resolveConfirm} from './confirm.js'
  function onkey(e) { if ($confirmState && e.key === 'Escape') resolveConfirm(false) }
</script>

<svelte:window onkeydown={onkey} />

{#if $confirmState}
  <div class="backdrop" style="z-index:1500" onclick={() => resolveConfirm(false)}>
    <div class="modal confirm-modal" onclick={e => e.stopPropagation()} role="dialog" aria-modal="true">
      <h3>{$confirmState.title}</h3>
      {#if $confirmState.message}<p class="confirm-msg">{$confirmState.message}</p>{/if}
      <div class="modal-actions">
        <button class="btn-ghost-sm" onclick={() => resolveConfirm(false)}>{$confirmState.cancelLabel}</button>
        <button class:btn-danger={$confirmState.danger} class:btn-primary={!$confirmState.danger}
                onclick={() => resolveConfirm(true)}>{$confirmState.confirmLabel}</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .confirm-modal { width: 400px; max-width: 92vw; }
  .confirm-msg { font-size: 13px; color: var(--color-text); white-space: pre-line; margin: 4px 0 0; }
</style>
