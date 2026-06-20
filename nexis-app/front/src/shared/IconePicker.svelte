<script>
  import {api} from './api.js'
  import Icone from './Icone.svelte'
  import Modal from './Modal.svelte'

  // Sélecteur d'icône pour les formulaires admin : emoji (saisie) OU image piochée dans la
  // bibliothèque sp_icone via une galerie modale. Lie les deux champs de l'entité.
  let { emoji = $bindable(''), imageId = $bindable(null) } = $props()

  let lib    = $state([])
  let open   = $state(false)
  let loaded = false

  async function openGallery() {
    open = true
    if (!loaded) { lib = await api.get('/sp/icones').catch(() => []); loaded = true }
  }
  function choisir(id) { imageId = id; open = false }
  function retirer()   { imageId = null }
</script>

<div class="ipick">
  <span class="ipick-prev"><Icone {imageId} {emoji} size={26} /></span>
  {#if imageId}
    <button type="button" class="btn-ghost-sm" onclick={openGallery}>Changer</button>
    <button type="button" class="btn-ghost-sm" onclick={retirer} title="Revenir à l'emoji">Retirer</button>
  {:else}
    <input class="ipick-emoji" type="text" bind:value={emoji} maxlength="8" placeholder="🏅" />
    <button type="button" class="btn-ghost-sm" onclick={openGallery}>Image…</button>
  {/if}
</div>

{#if open}
  <Modal title="Choisir une image" onclose={() => open = false}>
    {#if lib.length === 0}
      <p class="muted small">Bibliothèque vide. Ajoutez des images dans le menu « Icônes » (admin).</p>
    {:else}
      <div class="ipick-gal">
        {#each lib as ic (ic.id)}
          <button type="button" class="ipick-cell" class:sel={imageId === ic.id}
                  onclick={() => choisir(ic.id)} title={ic.nom}>
            <Icone imageId={ic.id} size={40} alt={ic.nom} />
          </button>
        {/each}
      </div>
    {/if}
  </Modal>
{/if}

<style>
  .ipick { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .ipick-prev { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center;
                border: 1px solid var(--color-border); border-radius: var(--radius); }
  .ipick-emoji { width: 56px; text-align: center; background: var(--color-bg); border: 1px solid var(--color-border);
                 border-radius: var(--radius); color: var(--color-text); font-size: 16px; padding: 5px 4px; }
  .ipick-gal { display: grid; grid-template-columns: repeat(auto-fill, minmax(60px, 1fr)); gap: 8px;
               max-height: 50vh; overflow-y: auto; }
  .ipick-cell { display: flex; align-items: center; justify-content: center; padding: 10px; cursor: pointer;
                background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); }
  .ipick-cell:hover { border-color: var(--accent); }
  .ipick-cell.sel { border-color: var(--accent);
                    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 40%, transparent); }
</style>
