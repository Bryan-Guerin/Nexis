<script>
  import {onMount} from 'svelte'
  import {api} from '../shared/api.js'
  import {currentUser} from '../shared/stores.js'
  import {realtime} from '../shared/realtime.js'

  // Chip « mon affectation » dans la top-bar (toutes factions). Indique poste + véhicule
  // de l'utilisateur courant. Cliquable → dispatch. SP pour l'instant (GN quand crews prêts).
  let affs = $state([])
  let roles = $derived($currentUser?.roles ?? [])
  let isSp  = $derived(roles.some(r => r === 'ROLE_SP' || r === 'ROLE_SP_DISPATCH' || r === 'ROLE_ADMIN_SP'))

  async function load() {
    if (!isSp) { affs = []; return }
    try { affs = await api.get('/sp/affectations/moi') } catch { affs = [] }
  }

  onMount(() => {
    load()
    return realtime.on(ev => {
      if (ev.faction === 'SP' && ['AFFECTATION', 'DESAFFECTATION'].includes(ev.type)) load()
    })
  })

  let principal = $derived(affs[0] ?? null)
  function go() { window.location.hash = '#/sp/dispatch' }
</script>

{#if principal}
  <button class="mon-aff" onclick={go} title="Aller au dispatch">
    <span class="ic">{principal.typeIcone || '🚒'}</span>
    <span class="lib">{principal.vehiculeLibelle}</span>
    {#if principal.fonctionLabel}<span class="fct">· {principal.fonctionLabel}</span>{/if}
    {#if affs.length > 1}<span class="more">+{affs.length - 1}</span>{/if}
  </button>
{/if}

<style>
  .mon-aff {
    display: inline-flex; align-items: center; gap: 6px;
    background: color-mix(in srgb, var(--color-success) 14%, transparent);
    border: 1px solid color-mix(in srgb, var(--color-success) 45%, transparent);
    color: var(--color-text); border-radius: 20px;
    padding: 4px 12px; font-size: 13px; cursor: pointer; white-space: nowrap;
  }
  .mon-aff:hover { border-color: var(--color-success); }
  .mon-aff .ic { font-size: 15px; line-height: 1; }
  .mon-aff .lib { font-weight: 600; }
  .mon-aff .fct { color: var(--color-muted); }
  .mon-aff .more { font-size: 11px; color: var(--color-muted); background: var(--color-bg); border-radius: 8px; padding: 0 5px; }
  @media (max-width: 768px) { .mon-aff .fct { display: none; } }
</style>
