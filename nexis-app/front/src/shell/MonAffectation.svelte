<script>
  import {onMount} from 'svelte'
  import {api} from '../shared/api.js'
  import {currentUser, feuilleFiltreDemande} from '../shared/stores.js'
  import {realtime} from '../shared/realtime.js'

  // Chip « mes affectations » dans la top-bar. Liste tous mes véhicules (poste = code,
  // compact). Cliquable → Feuille de garde filtrée sur moi.
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

  function go() {
    feuilleFiltreDemande.set('moi')   // la Feuille filtrera sur l'utilisateur courant
    window.location.hash = '#/sp/feuille-garde'
  }
</script>

{#if affs.length > 0}
  <button class="mon-aff" onclick={go} title="Voir mes engins (feuille de garde)">
    {#each affs as a (a.vehiculeId)}
      <span class="aff-chip">
        <span class="ic">{a.typeIcone || '🚒'}</span>
        <span class="lib">{a.vehiculeLibelle}</span>
        {#if a.fonctionCode}<span class="fct">{a.fonctionCode}</span>{/if}
      </span>
    {/each}
  </button>
{/if}

<style>
  .mon-aff {
    display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap;
    background: none; border: none; color: var(--color-text);
    padding: 0; cursor: pointer; max-width: 46vw;
  }
  .aff-chip {
    display: inline-flex; align-items: center; gap: 5px;
    background: color-mix(in srgb, var(--color-success) 14%, transparent);
    border: 1px solid color-mix(in srgb, var(--color-success) 45%, transparent);
    border-radius: 20px; padding: 3px 10px; font-size: 13px; white-space: nowrap;
  }
  .mon-aff:hover .aff-chip { border-color: var(--color-success); }
  .aff-chip .ic { font-size: 15px; line-height: 1; }
  .aff-chip .lib { font-weight: 600; }
  .aff-chip .fct { font-family: monospace; font-size: 11px; color: var(--color-muted); }
  @media (max-width: 768px) { .aff-chip .fct { display: none; } }
  @media (max-width: 480px) { .mon-aff { display: none; } }   /* libère la barre top mobile */
</style>
