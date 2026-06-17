<script module>
  // Pile des modales ouvertes : l'Échap ne ferme que celle du dessus.
  let stack = []
  let uidSeq = 0
</script>

<script>
  import {onMount, onDestroy} from 'svelte'
  import {get} from 'svelte/store'
  import {confirmState} from './confirm.js'

  // Modale réutilisable : backdrop, Échap (couche du dessus), focus initial, ✕ systématique.
  // dismissible=false : fermable uniquement par la croix (pas de clic-fond ni Échap)
  let { title = '', onclose, width = null, z = null, dismissible = true, children, actions } = $props()

  const uid = ++uidSeq
  const titleId = `modal-title-${uid}`
  let panel
  onMount(() => { stack.push(uid); panel?.focus() })
  onDestroy(() => { stack = stack.filter(x => x !== uid) })

  function onkey(e) {
    if (e.key !== 'Escape' || !dismissible) return
    if (get(confirmState)) return                 // un confirm est au-dessus
    if (stack[stack.length - 1] !== uid) return   // pas la modale du dessus
    onclose?.()
  }
</script>

<svelte:window onkeydown={onkey} />

<div class="backdrop" style={z ? `z-index:${z}` : ''} onclick={() => dismissible && onclose?.()}>
  <div class="modal" style={width ? `width:${width}; max-width:94vw` : ''} tabindex="-1"
       bind:this={panel} onclick={e => e.stopPropagation()}
       role="dialog" aria-modal="true" aria-labelledby={title ? titleId : undefined}>
    <button class="modal-x" title="Fermer" aria-label="Fermer" onclick={() => onclose?.()}>✕</button>
    {#if title}<h3 id={titleId}>{title}</h3>{/if}
    {@render children?.()}
    {#if actions}<div class="modal-actions">{@render actions()}</div>{/if}
  </div>
</div>

<style>
  .modal { position: relative; }
  .modal-x { position: absolute; top: 12px; right: 14px; background: none; border: none; color: var(--color-muted); font-size: 18px; cursor: pointer; line-height: 1; }
  .modal-x:hover { color: var(--color-danger); }
</style>
