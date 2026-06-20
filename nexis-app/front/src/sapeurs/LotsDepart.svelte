<script>
  import {api} from '../shared/api.js'
  import Icone from '../shared/Icone.svelte'
  import IconePicker from '../shared/IconePicker.svelte'

  // Lots de départ (types de véhicule à engager par nature). Panneau autonome (admin) :
  // gère sa propre sélection de nature + ses lignes. Reçoit les référentiels en props.
  let { natures = [], types = [] } = $props()

  let showLots       = $state(false)
  let templateNat    = $state('')
  let templateLignes = $state([])
  let tplForm        = $state({ vehiculeTypeId: '', quantite: 1, description: '', iconeImageId: null })

  async function loadTemplate(natureId) {
    templateNat = natureId
    templateLignes = natureId ? await api.get(`/sp/natures/${natureId}/template`).catch(() => []) : []
  }
  async function addTemplate() {
    if (!tplForm.vehiculeTypeId || !templateNat) return
    try {
      const created = await api.post(`/sp/natures/${templateNat}/template`,
        { vehiculeTypeId: tplForm.vehiculeTypeId, quantite: Number(tplForm.quantite) || 1,
          description: tplForm.description || null, iconeImageId: tplForm.iconeImageId || null })
      templateLignes = [...templateLignes, created]
      tplForm = { vehiculeTypeId: '', quantite: 1, description: '', iconeImageId: null }
    } catch { /* toast par api.js */ }
  }
  async function deleteTemplate(id) {
    try { await api.delete(`/sp/templates/${id}`); templateLignes = templateLignes.filter(l => l.id !== id) }
    catch { /* toast par api.js */ }
  }
</script>

<section class="lots-panel">
  <button class="lots-head" onclick={() => showLots = !showLots}>
    <span class="caret">{showLots ? '▾' : '▸'}</span> Lots de départ (par nature)
  </button>
  {#if showLots}
    <div class="lots-body">
      <label class="lots-nat">Nature
        <select value={templateNat} onchange={e => loadTemplate(e.target.value)}>
          <option value="">— choisir une nature —</option>
          {#each natures as n (n.id)}<option value={n.id}>{n.code} · {n.label}</option>{/each}
        </select>
      </label>
      {#if templateNat}
        <ul class="lots-list">
          {#each templateLignes as l (l.id)}
            <li>
              {#if l.iconeImageId}<Icone imageId={l.iconeImageId} size={16} />{/if}
              {l.typeLabel} <span class="qty">×{l.quantite}</span>
              {#if l.description}<span class="lot-desc muted small">— {l.description}</span>{/if}
              <button class="rm-btn" title="Retirer" onclick={() => deleteTemplate(l.id)}>×</button>
            </li>
          {/each}
          {#if templateLignes.length === 0}<li class="muted small">Aucun engin dans ce lot</li>{/if}
        </ul>
        <div class="lots-add">
          <select bind:value={tplForm.vehiculeTypeId}>
            <option value="">— type d'engin —</option>
            {#each types as t (t.id)}<option value={t.id}>{t.label}</option>{/each}
          </select>
          <input type="number" min="1" bind:value={tplForm.quantite} title="Quantité" style="width:70px" />
          <input type="text" bind:value={tplForm.description} placeholder="note (optionnel)" title="Note sur la ligne" />
          <IconePicker imageOnly bind:imageId={tplForm.iconeImageId} />
          <button class="btn-ghost-sm" onclick={addTemplate}>Ajouter</button>
        </div>
      {/if}
    </div>
  {/if}
</section>

<style>
  .lots-panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 10px 14px; margin-bottom: 12px; }
  .lots-head { background: none; border: none; color: var(--color-text); font-size: 14px; font-weight: 600; cursor: pointer; padding: 0; }
  .lots-head .caret { color: var(--color-muted); font-size: 11px; }
  .lots-body { display: flex; flex-direction: column; gap: 10px; margin-top: 10px; }
  .lots-nat { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--color-muted); max-width: 320px; }
  .lots-nat select, .lots-add select, .lots-add input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .lots-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
  .lots-list li { display: flex; align-items: center; gap: 8px; font-size: 13px; }
  .lots-add { display: flex; gap: 8px; align-items: center; }
  .qty { color: var(--accent); font-weight: 600; font-size: 12px; }
  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; cursor: pointer; padding: 0 4px; }
  .rm-btn:hover { color: var(--color-danger); }
</style>
