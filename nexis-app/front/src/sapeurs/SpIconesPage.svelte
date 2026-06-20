<script>
  import {onMount} from 'svelte'
  import {get} from 'svelte/store'
  import {api} from '../shared/api.js'
  import {authToken} from '../shared/stores.js'
  import {toast} from '../shared/toasts.js'
  import {confirm} from '../shared/confirm.js'
  import Icone from '../shared/Icone.svelte'
  import IconePicker from '../shared/IconePicker.svelte'

  // Bibliothèque centrale d'images-icônes (admin SP). Les formulaires (badge, nature,
  // fonction orga, type de véhicule) y piochent via le sélecteur d'icône.
  let icones = $state([])
  let nom    = $state('')
  let file   = $state(null)
  let busy   = $state(false)
  let logoId = $state(null)   // logo de la caserne (icône choisie comme logo)

  async function load() { icones = await api.get('/sp/icones').catch(() => []) }
  async function loadLogo() { const b = await api.get('/sp/branding').catch(() => null); logoId = b?.logoIconeId ?? null }
  async function saveLogo() {
    try { const b = await api.put('/sp/branding/logo', { iconeImageId: logoId }); logoId = b?.logoIconeId ?? null; toast.success('Logo mis à jour.') }
    catch { /* toast par api.js */ }
  }
  onMount(() => { load(); loadLogo() })

  function onFile(e) { file = e.currentTarget.files?.[0] ?? null }

  async function upload() {
    if (!file) { toast.error('Choisissez une image.'); return }
    busy = true
    try {
      const fd = new FormData()
      fd.append('nom', nom ?? '')
      fd.append('fichier', file)
      const res = await fetch('/api/sp/icones', {
        method: 'POST',
        headers: { Authorization: `Bearer ${get(authToken)}` },
        body: fd,
      })
      if (!res.ok) {
        if (res.status === 401) { window.location.assign('/'); return }
        let msg = 'Échec de l\'envoi'
        try { const j = await res.json(); msg = j?.message ?? j?._embedded?.errors?.[0]?.message ?? msg } catch { /* ignore */ }
        throw new Error(msg)
      }
      toast.success('Icône ajoutée.')
      nom = ''; file = null
      const input = document.getElementById('icone-file'); if (input) input.value = ''
      await load()
    } catch (e) { toast.error(e.message) } finally { busy = false }
  }

  async function supprimer(ic) {
    if (!await confirm({ title: 'Supprimer l\'icône',
        message: `Supprimer « ${ic.nom} » ?\nLes éléments qui l'utilisent reviendront à leur emoji.`, danger: true })) return
    try { await api.delete(`/sp/icones/${ic.id}`); icones = icones.filter(x => x.id !== ic.id) }
    catch { /* toast par api.js */ }
  }
</script>

<div class="page">
  <h2>🖼️ Bibliothèque d'icônes</h2>
  <p class="muted small">Images réutilisables (PNG, JPG, WebP, GIF, SVG — max 1 Mo) en remplacement
    des emojis sur les badges, natures, fonctions et types de véhicule.</p>

  <div class="logo-block">
    <span class="logo-label">Logo de la caserne</span>
    <IconePicker imageOnly bind:imageId={logoId} onchange={saveLogo} />
    <span class="muted small">Affiché sur le tableau de bord SP. Vide → logo du volume (/branding/sp-logo.png).</span>
  </div>

  <div class="upload">
    <input type="text" bind:value={nom} placeholder="Nom (optionnel)" maxlength="120" />
    <input id="icone-file" type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" onchange={onFile} />
    <button class="btn-primary" disabled={busy || !file} onclick={upload}>{busy ? 'Envoi…' : '+ Ajouter'}</button>
  </div>

  {#if icones.length === 0}
    <p class="muted small">Aucune icône. Ajoutez-en une pour démarrer la bibliothèque.</p>
  {:else}
    <ul class="grid">
      {#each icones as ic (ic.id)}
        <li class="cell">
          <div class="thumb"><Icone imageId={ic.id} size={48} alt={ic.nom} /></div>
          <span class="nm" title={ic.nom}>{ic.nom}</span>
          <button class="rm-btn" title="Supprimer" onclick={() => supprimer(ic)}>×</button>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .page { padding: 4px 0; }
  .page h2 { margin: 0 0 4px; }
  .logo-block { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 14px 0 4px;
                background: var(--color-surface); border: 1px solid var(--color-border);
                border-radius: var(--radius); padding: 12px; }
  .logo-label { font-weight: 600; font-size: 13px; }
  .upload { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 12px 0 18px;
            border: 1px dashed var(--color-border); border-radius: var(--radius); padding: 12px; }
  .upload input[type="text"] { background: var(--color-bg); border: 1px solid var(--color-border);
            border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 8px; }
  .upload input[type="file"] { font-size: 12px; color: var(--color-muted); }
  .grid { list-style: none; margin: 0; padding: 0; display: grid;
          grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; }
  .cell { position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px;
          background: var(--color-surface); border: 1px solid var(--color-border);
          border-radius: var(--radius); padding: 12px 8px; }
  .thumb { height: 48px; display: flex; align-items: center; justify-content: center; }
  .nm { font-size: 11px; color: var(--color-muted); max-width: 100%; text-align: center;
        overflow-wrap: anywhere; line-height: 1.2; }
  .rm-btn { position: absolute; top: 2px; right: 4px; background: none; border: none;
            color: var(--color-muted); font-size: 16px; line-height: 1; cursor: pointer; padding: 0 4px; }
  .rm-btn:hover { color: var(--color-danger); }
</style>
