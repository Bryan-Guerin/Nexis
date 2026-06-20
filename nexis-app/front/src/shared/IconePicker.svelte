<script>
  import {api} from './api.js'
  import Icone from './Icone.svelte'
  import Modal from './Modal.svelte'

  // Sélecteur d'icône pour les formulaires admin : emoji (saisie) OU image piochée dans la
  // bibliothèque sp_icone via une galerie modale. Lie les deux champs de l'entité.
  // onchange (optionnel) : appelé après un choix/retrait d'image ou au blur de l'emoji
  // (édition inline qui persiste aussitôt). Les formulaires modaux l'ignorent (lecture au submit).
  // imageOnly : masque la saisie d'emoji (champ purement image, ex. logo).
  // Pas de valeur par défaut sur les $bindable : les champs liés peuvent être undefined
  // (serde omet les null), et bind: sur un $bindable avec fallback interdit undefined.
  let { emoji = $bindable(), imageId = $bindable(), onchange, imageOnly = false } = $props()

  let lib  = $state([])
  let open = $state(false)

  // Charge la bibliothèque à chaque ouverture (reflète les images ajoutées entre-temps).
  async function openGallery() {
    open = true
    lib = await api.get('/sp/icones').catch(() => [])
  }
  function choisir(id) { imageId = id; open = false; onchange?.() }
  function retirer()   { imageId = null; onchange?.() }
</script>

<div class="ipick">
  <span class="ipick-prev"><Icone {imageId} {emoji} size={26} /></span>
  {#if imageId}
    <button type="button" class="btn-ghost-sm" onclick={openGallery}>Changer</button>
    <button type="button" class="btn-ghost-sm" onclick={retirer} title={imageOnly ? 'Retirer l’image' : 'Revenir à l’emoji'}>Retirer</button>
  {:else if imageOnly}
    <button type="button" class="btn-ghost-sm" onclick={openGallery}>Image…</button>
  {:else}
    <input class="ipick-emoji" type="text" bind:value={emoji} maxlength="8" placeholder="🏅"
           onchange={() => onchange?.()} />
    <button type="button" class="btn-ghost-sm" onclick={openGallery}>Image…</button>
  {/if}
</div>

{#if open}
  <Modal title="Choisir une image" width="720px" onclose={() => open = false}>
    {#if lib.length === 0}
      <p class="muted small">Bibliothèque vide. Ajoutez des images dans Configuration › « Icônes &amp; logo ».</p>
    {:else}
      <div class="ipick-gal">
        {#each lib as ic (ic.id)}
          <button type="button" class="ipick-cell" class:sel={imageId === ic.id}
                  onclick={() => choisir(ic.id)} title={ic.nom}>
            <Icone imageId={ic.id} size={44} alt={ic.nom} />
            <span class="ipick-nm">{ic.nom}</span>
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
  .ipick-gal { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 10px;
               max-height: 62vh; overflow-y: auto; }
  .ipick-cell { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 10px 8px;
                cursor: pointer; background: var(--color-surface); border: 1px solid var(--color-border);
                border-radius: var(--radius); font: inherit; color: inherit; }
  .ipick-cell:hover { border-color: var(--accent); }
  .ipick-cell.sel { border-color: var(--accent);
                    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 40%, transparent); }
  .ipick-nm { font-size: 11px; color: var(--color-muted); text-align: center; line-height: 1.2;
              overflow-wrap: anywhere; max-width: 100%; }
</style>
