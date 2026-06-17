<script>
  // État vide cohérent : icône + titre + message optionnel + action (snippet).
  let { icon = '📭', title = 'Rien à afficher', message = '', children } = $props()
</script>

<div class="empty-state">
  <div class="es-ic">{icon}</div>
  <div class="es-title">{title}</div>
  {#if message}<div class="es-msg">{message}</div>{/if}
  {#if children}<div class="es-action">{@render children()}</div>{/if}
</div>

<style>
  .empty-state { display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; padding: 32px 16px; color: var(--color-muted); }
  .es-ic { font-size: 34px; opacity: .7; line-height: 1; }
  .es-title { font-size: 14px; font-weight: 600; color: var(--color-text); }
  .es-msg { font-size: 13px; max-width: 380px; }
  .es-action { margin-top: 8px; }
</style>
