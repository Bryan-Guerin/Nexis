<script>
    import {onMount} from 'svelte'
    import {get} from 'svelte/store'
    import {api} from '../shared/api.js'
    import {authToken, currentUser} from '../shared/stores.js'
    import {pushToast} from '../shared/toasts.js'
    import {confirm} from '../shared/confirm.js'

    let categories = $state([])
  let error      = $state('')
  let newCat     = $state('')
  let uploads    = $state({})   // categorieId -> { nom, file, busy }

  let isAdmin = $derived(($currentUser?.roles ?? []).includes('ROLE_ADMIN_SP'))

  onMount(load)

  async function load() {
    error = ''
    try { categories = await api.get('/sp/documents') }
    catch (e) { error = e.message }
  }

  async function createCat(e) {
    e.preventDefault()
    if (!newCat.trim()) return
    try { await api.post('/sp/documents/categories', { nom: newCat.trim() }); newCat = ''; await load() }
    catch (e) { error = e.message }
  }

  async function deleteCat(c) {
    if (!await confirm({ title: 'Supprimer le dossier', message: `Supprimer « ${c.nom} » et tous ses documents ?`, danger: true })) return
    try { await api.delete(`/sp/documents/categories/${c.id}`); await load() }
    catch (e) { error = e.message }
  }

  function up(catId) { return uploads[catId] ?? { nom: '', file: null, busy: false } }
  function setUp(catId, patch) { uploads = { ...uploads, [catId]: { ...up(catId), ...patch } } }

  function onFile(catId, e) {
    const f = e.target.files?.[0] ?? null
    if (f && f.type !== 'application/pdf' && !f.name.toLowerCase().endsWith('.pdf')) {
      error = 'Seuls les fichiers PDF sont acceptés.'; e.target.value = ''; return
    }
    error = ''
    setUp(catId, { file: f })
  }

  async function upload(catId) {
    const u = up(catId)
    if (!u.file) { error = 'Choisissez un fichier PDF.'; return }
    setUp(catId, { busy: true }); error = ''
    try {
      const fd = new FormData()
      fd.append('categorieId', catId)
      fd.append('nom', u.nom ?? '')
      fd.append('fichier', u.file)
      const res = await fetch('/api/sp/documents', {
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
      pushToast('Document ajouté')
      uploads = { ...uploads, [catId]: { nom: '', file: null, busy: false } }
      await load()
    } catch (e) { error = e.message; setUp(catId, { busy: false }) }
  }

  async function openDoc(d) {
    error = ''
    try {
      const res = await fetch(`/api/sp/documents/${d.id}/fichier`, {
        headers: { Authorization: `Bearer ${get(authToken)}` },
      })
      if (!res.ok) throw new Error('Téléchargement impossible')
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch (e) { error = e.message }
  }

  async function deleteDoc(catId, d) {
    if (!await confirm({ title: 'Supprimer le document', message: `Supprimer « ${d.nom} » ?`, danger: true })) return
    try { await api.delete(`/sp/documents/${d.id}`); await load() }
    catch (e) { error = e.message }
  }

  function taille(n) {
    if (n < 1024) return n + ' o'
    if (n < 1024 * 1024) return (n / 1024).toFixed(0) + ' Ko'
    return (n / 1024 / 1024).toFixed(1) + ' Mo'
  }
  function fmt(iso) { return new Date(iso).toLocaleDateString('fr-FR', { dateStyle: 'short' }) }
</script>

<div class="page">
  <div class="page-header">
    <h2>Documents — Sapeurs-Pompiers</h2>
  </div>

  {#if error}<p class="inline-error">{error}</p>{/if}

  {#if isAdmin}
    <form class="new-cat" onsubmit={createCat}>
      <input type="text" bind:value={newCat} placeholder="Nouveau dossier (ex: Formations, Notes de service)" />
      <button type="submit" class="btn-primary">Créer un dossier</button>
    </form>
  {/if}

  {#if categories.length === 0}
    <p class="muted">Aucun dossier.</p>
  {:else}
    {#each categories as c (c.id)}
      <section class="cat">
        <div class="cat-head">
          <h3>📁 {c.nom} <span class="muted small">— {c.documents.length}</span></h3>
          {#if isAdmin}<button class="rm-btn" title="Supprimer le dossier" onclick={() => deleteCat(c)}>×</button>{/if}
        </div>

        {#if c.documents.length === 0}
          <p class="muted small">Aucun document.</p>
        {:else}
          <ul class="doc-list">
            {#each c.documents as d (d.id)}
              <li class="doc">
                <button class="doc-open" onclick={() => openDoc(d)} title="Ouvrir le PDF">📄 {d.nom}</button>
                <span class="doc-meta">{taille(d.taille)} · {fmt(d.creeLe)}{#if d.creePar} · {d.creePar}{/if}</span>
                {#if isAdmin}<button class="rm-btn" title="Supprimer" onclick={() => deleteDoc(c.id, d)}>×</button>{/if}
              </li>
            {/each}
          </ul>
        {/if}

        {#if isAdmin}
          <div class="upload">
            <input type="file" accept="application/pdf,.pdf" onchange={(e) => onFile(c.id, e)} />
            <input type="text" placeholder="Nom (optionnel)" value={up(c.id).nom} oninput={(e) => setUp(c.id, { nom: e.target.value })} />
            <button class="btn-ghost-sm" disabled={up(c.id).busy || !up(c.id).file} onclick={() => upload(c.id)}>
              {up(c.id).busy ? '…' : 'Déposer le PDF'}
            </button>
          </div>
        {/if}
      </section>
    {/each}
  {/if}
</div>

<style>
  .new-cat { display: flex; gap: 10px; margin-bottom: 16px; }
  .new-cat input { flex: 1; max-width: 360px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; }

  .cat { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 14px 16px; margin-bottom: 12px; }
  .cat-head { display: flex; align-items: center; justify-content: space-between; }
  .cat-head h3 { margin: 0 0 8px; font-size: 14px; }

  .doc-list { list-style: none; margin: 0 0 10px; padding: 0; display: flex; flex-direction: column; gap: 4px; }
  .doc { display: flex; align-items: center; gap: 10px; font-size: 13px; padding: 4px 0; }
  .doc-open { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 13px; padding: 0; text-align: left; }
  .doc-open:hover { text-decoration: underline; }
  .doc-meta { flex: 1; font-size: 11px; color: var(--color-muted); }

  .upload { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; border-top: 1px dashed var(--color-border); padding-top: 10px; }
  .upload input[type="text"] { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 5px 8px; }
  .upload input[type="file"] { font-size: 12px; color: var(--color-muted); }

  .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; cursor: pointer; padding: 0 4px; }
  .rm-btn:hover { color: var(--color-danger); }
</style>
