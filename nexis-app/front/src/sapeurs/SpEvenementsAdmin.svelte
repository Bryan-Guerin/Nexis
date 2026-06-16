<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {confirm} from '../shared/confirm.js'

    // Gestion admin des événements (intégrée à l'écran Configuration).
    let evenements = $state([])
    let error      = $state('')
    let masquerPasses = $state(true)

    let form      = $state({ titre: '', texte: '', date: '' })
    let formError = $state('')

    let expandedId = $state(null)
    let reponses   = $state({})    // evId -> { presents, absents }

    // Un événement est "passé" 6 h après sa date (cohérent avec le tableau de bord).
    function estPasse(iso) { return new Date(iso).getTime() < Date.now() - 6 * 3600 * 1000 }

    let affichees = $derived(masquerPasses ? evenements.filter(e => !estPasse(e.date)) : evenements)

    onMount(load)
    async function load() {
      error = ''
      try { evenements = await api.get('/sp/evenements/tous') }
      catch (e) { error = e.message; evenements = [] }
    }

    async function create() {
      formError = ''
      if (!form.titre.trim()) { formError = 'Titre requis'; return }
      if (!form.date)         { formError = 'Date requise'; return }
      try {
        await api.post('/sp/evenements', {
          titre: form.titre.trim(),
          texte: form.texte.trim() || null,
          date:  new Date(form.date).toISOString(),
        })
        form = { titre: '', texte: '', date: '' }
        await load()
      } catch (e) { formError = e.message }
    }

    async function remove(ev) {
      if (!await confirm({ title: 'Supprimer l\'événement', message: `Supprimer « ${ev.titre} » ?`, danger: true })) return
      try { await api.delete(`/sp/evenements/${ev.id}`); evenements = evenements.filter(e => e.id !== ev.id) }
      catch (e) { error = e.message }
    }

    async function toggle(ev) {
      if (expandedId === ev.id) { expandedId = null; return }
      expandedId = ev.id
      if (!reponses[ev.id]) {
        const r = await api.get(`/sp/evenements/${ev.id}/reponses`).catch(() => ({ presents: [], absents: [] }))
        reponses = { ...reponses, [ev.id]: r }
      }
    }

    function fmt(iso) { return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }) }
    function nom(m)  { return `${m.gradeCode} ${m.nomComplet || m.username}` }
</script>

<h3>Événements</h3>
{#if error}<p class="inline-error">{error}</p>{/if}

<!-- Création -->
<div class="evt-create">
  {#if formError}<p class="inline-error">{formError}</p>{/if}
  <div class="row">
    <input type="text" bind:value={form.titre} placeholder="Titre" maxlength="120" />
    <input type="datetime-local" bind:value={form.date} />
  </div>
  <textarea rows="2" bind:value={form.texte} placeholder="Description (optionnel)"></textarea>
  <button class="btn-primary" onclick={create}>+ Créer l'événement</button>
</div>

<!-- Liste chronologique -->
<label class="masquer">
  <input type="checkbox" bind:checked={masquerPasses} /> Masquer les événements passés
</label>

<ul class="evt-admin-list">
  {#each affichees as ev (ev.id)}
    <li class="evt-admin" class:past={estPasse(ev.date)}>
      <button class="evt-row" onclick={() => toggle(ev)}>
        <span class="chevron" class:open={expandedId === ev.id}>▾</span>
        <span class="evt-t">{ev.titre}</span>
        <span class="evt-d">{fmt(ev.date)}</span>
        <span class="evt-c">✅ {ev.nbPresents} · ❌ {ev.nbAbsents}</span>
      </button>
      <button class="rm-btn" title="Supprimer" onclick={() => remove(ev)}>×</button>

      {#if expandedId === ev.id}
        <div class="evt-detail">
          {#if ev.texte}<p class="evt-texte">{ev.texte}</p>{/if}
          <div class="pa-cols">
            <div class="pa">
              <span class="pa-h pres">Présents ({reponses[ev.id]?.presents?.length ?? 0})</span>
              {#each reponses[ev.id]?.presents ?? [] as m (m.membreId)}<span class="pa-chip">{nom(m)}</span>{/each}
              {#if (reponses[ev.id]?.presents?.length ?? 0) === 0}<span class="muted small">—</span>{/if}
            </div>
            <div class="pa">
              <span class="pa-h abs">Absents ({reponses[ev.id]?.absents?.length ?? 0})</span>
              {#each reponses[ev.id]?.absents ?? [] as m (m.membreId)}<span class="pa-chip">{nom(m)}</span>{/each}
              {#if (reponses[ev.id]?.absents?.length ?? 0) === 0}<span class="muted small">—</span>{/if}
            </div>
          </div>
        </div>
      {/if}
    </li>
  {/each}
  {#if affichees.length === 0}<p class="muted small">Aucun événement{masquerPasses ? ' à venir' : ''}.</p>{/if}
</ul>

<style>
  h3 { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: .6px; color: var(--color-muted); margin: 0 0 4px; }
  .evt-create { display: flex; flex-direction: column; gap: 8px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px; }
  .evt-create .row { display: flex; gap: 8px; }
  .evt-create .row input[type="text"] { flex: 1; }
  .evt-create input, .evt-create textarea { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; outline: none; }
  .evt-create input:focus, .evt-create textarea:focus { border-color: var(--accent); }
  .evt-create textarea { resize: vertical; }

  .masquer { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-muted); margin: 12px 0 4px; }

  .evt-admin-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
  .evt-admin { border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-surface); position: relative; }
  .evt-admin.past { opacity: .6; }
  .evt-row { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px 38px 10px 12px; background: none; border: none; color: var(--color-text); cursor: pointer; text-align: left; }
  .chevron { font-size: 10px; color: var(--color-muted); transition: transform .15s; }
  .chevron:not(.open) { transform: rotate(-90deg); }
  .evt-t { flex: 1; font-weight: 600; font-size: 13px; }
  .evt-d { font-size: 12px; color: var(--color-muted); white-space: nowrap; }
  .evt-c { font-size: 12px; color: var(--color-muted); white-space: nowrap; }
  .rm-btn { position: absolute; top: 8px; right: 8px; background: none; border: none; color: var(--color-muted); font-size: 16px; line-height: 1; padding: 0 4px; cursor: pointer; }
  .rm-btn:hover { color: var(--color-danger); }

  .evt-detail { padding: 0 12px 12px; }
  .evt-texte { font-size: 12px; color: var(--color-muted); white-space: pre-wrap; margin: 0 0 10px; }
  .pa-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .pa { display: flex; flex-wrap: wrap; gap: 6px; align-content: flex-start; }
  .pa-h { width: 100%; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .4px; }
  .pa-h.pres { color: var(--color-success); }
  .pa-h.abs  { color: var(--color-danger); }
  .pa-chip { font-size: 12px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 20px; padding: 2px 9px; }
</style>
