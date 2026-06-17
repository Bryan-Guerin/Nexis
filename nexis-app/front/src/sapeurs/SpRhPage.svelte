<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {confirm} from '../shared/confirm.js'
    import {toast} from '../shared/toasts.js'
    import {compareBy, nextSort} from '../shared/tableSort.js'
    import SortableTh from '../shared/SortableTh.svelte'
    import Pagination from '../shared/Pagination.svelte'
    import Skeleton from '../shared/Skeleton.svelte'

    let paie    = $state(null)
  let grades  = $state([])
  let savingId = $state(null)
  let reglant = $state(false)

  // ── Trésorerie ───────────────────────────────────────────────────────────
  let finance    = $state(null)
  let compteForm = $state({ libelle: '', soldeInitial: 0 })
  let editCompte = $state(false)
  let mvtForm    = $state({ type: 'DEPENSE', montant: '', libelle: '', date: new Date().toISOString().slice(0, 10), categorieId: '' })
  let newCat     = $state('')

  onMount(() => { load(); loadGrades(); loadFinance() })

  async function loadFinance() {
    try {
      finance = await api.get('/sp/rh/finance')
      compteForm = { libelle: finance.libelle, soldeInitial: finance.soldeInitial }
    } catch { /* toast par api.js */ }
  }
  async function saveCompte() {
    try {
      finance = await api.put('/sp/rh/finance/compte', { libelle: compteForm.libelle, soldeInitial: Number(compteForm.soldeInitial) })
      editCompte = false
    } catch { /* toast par api.js */ }
  }
  async function addMouvement() {
    if (!mvtForm.libelle.trim() || !(Number(mvtForm.montant) > 0)) { toast.error('Libellé et montant (> 0) requis'); return }
    try {
      finance = await api.post('/sp/rh/finance/mouvements', {
        type: mvtForm.type, montant: Number(mvtForm.montant), libelle: mvtForm.libelle,
        date: mvtForm.date || null, categorieId: mvtForm.categorieId || null,
      })
      mvtForm = { type: mvtForm.type, montant: '', libelle: '', date: mvtForm.date, categorieId: '' }
    } catch { /* toast par api.js */ }
  }
  async function addCategorie() {
    if (!newCat.trim()) return
    try { finance = await api.post('/sp/rh/finance/categories', { libelle: newCat }); newCat = '' }
    catch { /* toast par api.js */ }
  }
  async function deleteCategorie(id) {
    if (!await confirm({ title: 'Supprimer la catégorie', message: 'Supprimer cette catégorie ? Les mouvements liés la perdront.', danger: true })) return
    try { finance = await api.delete(`/sp/rh/finance/categories/${id}`) }
    catch { /* toast par api.js */ }
  }

  async function load(lundi) {
    try { paie = await api.get('/sp/rh/paie' + (lundi ? `?lundi=${lundi}` : '')) }
    catch { /* toast par api.js */ }
  }
  async function loadGrades() {
    try { grades = await api.get('/sp/rh/grades') } catch { /* toast par api.js */ }
  }

  function shiftWeek(days) {
    if (!paie) return
    const d = new Date(paie.debut + 'T12:00:00')
    d.setDate(d.getDate() + days)
    load(d.toISOString().slice(0, 10))
  }

  async function saveTaux(g) {
    savingId = g.id
    try {
      await api.put(`/sp/rh/grades/${g.id}/taux`, { tauxHoraire: Number(g.tauxHoraire), tauxAstreinte: Number(g.tauxAstreinte) })
      await load(paie?.debut)   // recalcule la paie avec le nouveau taux
    } catch { /* toast par api.js */ }
    finally { savingId = null }
  }

  async function regler() {
    if (!paie || paie.payee) return
    if (!await confirm({ title: 'Régler la paie', message: `Marquer la paie de la semaine du ${jour(paie.debut)} comme réglée ?\nAction irréversible — chaque membre sera notifié de son versement.`, danger: true })) return
    reglant = true
    try { paie = await api.post(`/sp/rh/paie/regler?lundi=${paie.debut}`); await loadFinance() }
    catch { /* toast par api.js */ }
    finally { reglant = false }
  }

  function jour(iso) { return new Date(iso + 'T12:00:00').toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }) }
  function dateHeure(iso) { return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) }
  function eur(n) { return (n ?? 0).toFixed(2) + ' €' }

  // ── Trésorerie : filtre, totaux par catégorie, export CSV, contre-passation ──
  let mvtFiltre = $state('')   // '' = toutes ; 'SANS' = sans catégorie ; sinon id catégorie
  let mvtSearch = $state('')
  let mvtSort   = $state({ col: 'date', dir: 'desc' })
  let mvtPage   = $state(1)
  let mvtPageSize = $state(25)
  const MVT_KEYS = {
    date: m => m.date, libelle: m => m.libelle, categorie: m => m.categorieLibelle,
    montant: m => (m.type === 'GAIN' ? 1 : -1) * Number(m.montant), par: m => m.creePar,
  }
  let mvtsFiltres = $derived(!finance ? [] : finance.mouvements.filter(m => {
    const okCat = mvtFiltre === '' ? true : mvtFiltre === 'SANS' ? !m.categorieId : m.categorieId === mvtFiltre
    const q = mvtSearch.trim().toLowerCase()
    const okQ = !q || `${m.libelle} ${m.categorieLibelle ?? ''} ${m.creePar ?? ''}`.toLowerCase().includes(q)
    return okCat && okQ
  }))
  let mvtsSorted = $derived([...mvtsFiltres].sort(compareBy(MVT_KEYS[mvtSort.col], mvtSort.dir)))
  let mvtsPage = $derived(mvtsSorted.slice((mvtPage - 1) * mvtPageSize, mvtPage * mvtPageSize))
  let totauxCat = $derived.by(() => {
    if (!finance) return []
    const map = new Map()
    for (const m of finance.mouvements) {
      const key = m.categorieId ?? 'SANS'
      const cur = map.get(key) ?? { libelle: m.categorieLibelle ?? 'Sans catégorie', net: 0 }
      cur.net += (m.type === 'GAIN' ? 1 : -1) * Number(m.montant)
      map.set(key, cur)
    }
    return [...map.values()].sort((a, b) => a.libelle.localeCompare(b.libelle))
  })

  function downloadCsv(rows, filename) {
    const esc = v => `"${String(v ?? '').replace(/"/g, '""')}"`
    const csv = rows.map(r => r.map(esc).join(';')).join('\r\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob); a.download = filename; a.click()
    URL.revokeObjectURL(a.href)
  }
  function exportTresoCsv() {
    const rows = [['Date', 'Type', 'Libellé', 'Catégorie', 'Montant', 'Par']]
    for (const m of mvtsFiltres) rows.push([m.date, m.type, m.libelle, m.categorieLibelle ?? '', m.montant, m.creePar ?? ''])
    downloadCsv(rows, `tresorerie-${finance.libelle.replace(/\s+/g, '_')}.csv`)
  }
  function exportPaieCsv() {
    const rows = [['Matricule', 'Agent', 'Grade', 'Heures garde', 'Heures astreinte', 'Montant']]
    for (const l of paie.lignes) rows.push([l.matricule, l.username, l.grade, l.heuresGarde, l.heuresAstreinte, l.montant])
    rows.push(['', '', '', '', 'Total', paie.total])
    downloadCsv(rows, `paie-${paie.debut}.csv`)
  }
  async function contrePasser(m) {
    if (!await confirm({ title: 'Contre-passer', message: `Contre-passer « ${m.libelle} » (${eur(m.montant)}) ?\nCrée l'écriture inverse.` })) return
    try {
      finance = await api.post('/sp/rh/finance/mouvements', {
        type: m.type === 'GAIN' ? 'DEPENSE' : 'GAIN', montant: Number(m.montant),
        libelle: `Annulation : ${m.libelle}`, date: new Date().toISOString().slice(0, 10),
        categorieId: m.categorieId || null,
      })
    } catch { /* toast par api.js */ }
  }
</script>

<div class="page">
  <div class="page-header">
    <h2>RH / Paie — Sapeurs-Pompiers</h2>
  </div>

  <!-- Trésorerie (RH / admin uniquement) -->
  <section class="panel">
    <div class="treso-head">
      <h3>Trésorerie</h3>
      {#if finance && !editCompte}
        <button class="btn-ghost-sm" onclick={() => editCompte = true}>Modifier le compte</button>
      {/if}
    </div>

    {#if !finance}
      <Skeleton rows={5} />
    {:else}
      {#if editCompte}
        <div class="compte-edit">
          <label>Libellé du compte<input type="text" bind:value={compteForm.libelle} maxlength="100" /></label>
          <label>Solde initial (€)<input type="number" step="0.01" bind:value={compteForm.soldeInitial} /></label>
          <div class="treso-actions">
            <button class="btn-ghost-sm" onclick={() => { editCompte = false; compteForm = { libelle: finance.libelle, soldeInitial: finance.soldeInitial } }}>Annuler</button>
            <button class="btn-primary" onclick={saveCompte}>Enregistrer</button>
          </div>
        </div>
      {:else}
        <div class="solde-row">
          <div class="solde-main">
            <span class="solde-lib">{finance.libelle}</span>
            <span class="solde-val" class:neg={finance.solde < 0}>{eur(finance.solde)}</span>
          </div>
          <div class="solde-sub">
            <span>Initial : {eur(finance.soldeInitial)}</span>
            <span class="gain">+ {eur(finance.totalGains)} gains</span>
            <span class="dep">− {eur(finance.totalDepenses)} dépenses</span>
          </div>
        </div>
      {/if}

      <!-- Catégories -->
      <div class="treso-cats">
        <span class="dl">Catégories</span>
        <div class="chips-row">
          {#each finance.categories as c (c.id)}
            <span class="cat-chip">{c.libelle}<button class="cat-x" title="Supprimer" onclick={() => deleteCategorie(c.id)}>×</button></span>
          {/each}
          <span class="cat-add">
            <input type="text" bind:value={newCat} placeholder="Nouvelle catégorie"
                   onkeydown={e => { if (e.key === 'Enter') { e.preventDefault(); addCategorie() } }} />
            <button class="btn-ghost-sm" disabled={!newCat.trim()} onclick={addCategorie}>+</button>
          </span>
        </div>
      </div>

      <!-- Nouveau mouvement -->
      <div class="mvt-form">
        <div class="seg">
          <button class="seg-btn" class:on={mvtForm.type === 'DEPENSE'} onclick={() => mvtForm.type = 'DEPENSE'}>Dépense</button>
          <button class="seg-btn" class:on={mvtForm.type === 'GAIN'} onclick={() => mvtForm.type = 'GAIN'}>Gain</button>
        </div>
        <input class="m-lib" type="text" bind:value={mvtForm.libelle} placeholder="Libellé" maxlength="150" />
        <input class="m-num" type="number" step="0.01" min="0" bind:value={mvtForm.montant} placeholder="Montant €" />
        <input class="m-date" type="date" bind:value={mvtForm.date} />
        <select class="m-cat" bind:value={mvtForm.categorieId}>
          <option value="">— catégorie —</option>
          {#each finance.categories as c (c.id)}<option value={c.id}>{c.libelle}</option>{/each}
        </select>
        <button class="btn-primary" onclick={addMouvement}>Ajouter</button>
      </div>

      <!-- Totaux par catégorie -->
      {#if totauxCat.length > 0}
        <div class="cat-totaux">
          {#each totauxCat as t}
            <span class="cat-total" class:neg={t.net < 0}>{t.libelle} : {t.net >= 0 ? '+' : '−'}{eur(Math.abs(t.net))}</span>
          {/each}
        </div>
      {/if}

      <!-- Historique -->
      <div class="mvt-bar">
        <input class="mvt-search" type="text" placeholder="Rechercher (libellé, catégorie, auteur)…" bind:value={mvtSearch} />
        <select bind:value={mvtFiltre} class="mvt-filtre">
          <option value="">Toutes catégories</option>
          {#each finance.categories as c (c.id)}<option value={c.id}>{c.libelle}</option>{/each}
          <option value="SANS">Sans catégorie</option>
        </select>
        <button class="btn-ghost-sm" disabled={mvtsFiltres.length === 0} onclick={exportTresoCsv}>⤓ Export CSV</button>
      </div>
      <table class="mvt-table">
        <thead>
          <tr>
            <SortableTh col="date" label="Date" sort={mvtSort} onsort={c => mvtSort = nextSort(mvtSort, c)} />
            <SortableTh col="libelle" label="Libellé" sort={mvtSort} onsort={c => mvtSort = nextSort(mvtSort, c)} />
            <SortableTh col="categorie" label="Catégorie" sort={mvtSort} onsort={c => mvtSort = nextSort(mvtSort, c)} />
            <SortableTh col="montant" label="Montant" sort={mvtSort} onsort={c => mvtSort = nextSort(mvtSort, c)} style="text-align:right" />
            <SortableTh col="par" label="Par" sort={mvtSort} onsort={c => mvtSort = nextSort(mvtSort, c)} />
            <th></th>
          </tr>
        </thead>
        <tbody>
          {#each mvtsPage as m (m.id)}
            <tr>
              <td class="mono">{jour(m.date)}</td>
              <td>{m.libelle}</td>
              <td class="muted">{m.categorieLibelle ?? '—'}</td>
              <td class="r mono strong" class:gain={m.type === 'GAIN'} class:dep={m.type === 'DEPENSE'}>
                {m.type === 'GAIN' ? '+' : '−'}{eur(m.montant)}
              </td>
              <td class="muted small">{m.creePar ?? '—'}</td>
              <td><button class="cp-btn" title="Contre-passer (écriture inverse)" onclick={() => contrePasser(m)}>↺</button></td>
            </tr>
          {/each}
        </tbody>
      </table>
      {#if mvtsFiltres.length === 0}<p class="muted small">Aucun mouvement{mvtFiltre || mvtSearch ? ' ne correspond' : ' enregistré'}.</p>
      {:else}<Pagination bind:page={mvtPage} bind:pageSize={mvtPageSize} total={mvtsFiltres.length} />{/if}
    {/if}
  </section>

  <!-- Paie hebdomadaire -->
  <section class="panel">
    <div class="week-nav">
      <button class="btn-ghost-sm" onclick={() => shiftWeek(-7)}>← Semaine préc.</button>
      {#if paie}<span class="week-label">Semaine du {jour(paie.debut)} au {jour(paie.fin)}</span>{/if}
      <button class="btn-ghost-sm" onclick={() => shiftWeek(7)}>Semaine suiv. →</button>
      <button class="btn-ghost-sm" onclick={() => load()}>Cette semaine</button>
      {#if paie && paie.lignes.length > 0}
        <button class="btn-ghost-sm" style="margin-left:auto" onclick={exportPaieCsv}>⤓ Export CSV</button>
      {/if}
    </div>

    {#if !paie}
      <p class="muted">Chargement…</p>
    {:else if paie.lignes.length === 0}
      <p class="muted small">Aucune heure de garde sur cette semaine.</p>
    {:else}
      <table>
        <thead>
          <tr><th>Matricule</th><th>Agent</th><th>Grade</th><th class="r">Garde</th><th class="r">Astreinte</th><th class="r">Montant</th></tr>
        </thead>
        <tbody>
          {#each paie.lignes as l (l.membreId)}
            <tr>
              <td class="mono">{l.matricule}</td>
              <td>{l.username}</td>
              <td class="muted">{l.grade}</td>
              <td class="r mono">{l.heuresGarde.toFixed(2)} h <span class="muted">· {eur(l.tauxHoraire)}</span></td>
              <td class="r mono">{l.heuresAstreinte.toFixed(2)} h <span class="muted">· {eur(l.tauxAstreinte)}</span></td>
              <td class="r mono strong">{eur(l.montant)}</td>
            </tr>
          {/each}
        </tbody>
        <tfoot>
          <tr><td colspan="5" class="r strong">Total</td><td class="r mono strong">{eur(paie.total)}</td></tr>
        </tfoot>
      </table>

      <div class="pay-action">
        {#if paie.payee}
          <span class="paid-badge">✓ Payée le {dateHeure(paie.regleLe)}{#if paie.reglePar} par {paie.reglePar}{/if}</span>
        {:else}
          <button class="btn-primary" onclick={regler} disabled={reglant}>
            {reglant ? 'Enregistrement…' : 'Marquer la semaine payée'}
          </button>
        {/if}
      </div>
    {/if}
  </section>

  <!-- Taux par grade -->
  <section class="panel">
    <h3>Taux par grade</h3>
    <p class="muted small">Paie = heures de garde × taux garde + heures d'astreinte × taux astreinte.</p>
    <div class="taux-list">
      <div class="taux-row taux-head">
        <span class="taux-grade"></span><span class="unit">Garde €/h</span><span class="unit">Astreinte €/h</span><span></span>
      </div>
      {#each grades as g (g.id)}
        <div class="taux-row">
          <span class="taux-grade">{g.label}</span>
          <input type="number" step="0.5" min="0" bind:value={g.tauxHoraire} />
          <input type="number" step="0.5" min="0" bind:value={g.tauxAstreinte} />
          <button class="btn-ghost-sm" disabled={savingId === g.id} onclick={() => saveTaux(g)}>
            {savingId === g.id ? '…' : 'Enregistrer'}
          </button>
        </div>
      {/each}
      {#if grades.length === 0}<p class="muted small">Aucun grade configuré.</p>{/if}
    </div>
  </section>
</div>

<style>
  .panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; margin-bottom: 12px; }
  .panel h3 { margin: 0 0 6px; font-size: 14px; }

  .week-nav { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
  .week-label { font-weight: 600; font-size: 14px; }

  table { width: 100%; }
  th.r, td.r { text-align: right; }
  .strong { font-weight: 700; }
  tfoot td { border-top: 2px solid var(--color-border); padding-top: 8px; }

  .dl { display: block; font-size: 10px; text-transform: uppercase; letter-spacing: .4px; color: var(--color-muted); }
  .seg { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
  .seg-btn { background: var(--color-surface); border: none; color: var(--color-muted); font-size: 12px; padding: 6px 12px; cursor: pointer; border-right: 1px solid var(--color-border); }
  .seg-btn:last-child { border-right: none; }
  .seg-btn.on { background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); font-weight: 600; }

  .treso-head { display: flex; align-items: center; justify-content: space-between; }
  .compte-edit { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; margin: 8px 0; }
  .compte-edit label { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--color-muted); }
  .compte-edit input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .treso-actions { display: flex; gap: 8px; }

  .solde-row { display: flex; flex-direction: column; gap: 4px; margin: 8px 0 14px; }
  .solde-main { display: flex; align-items: baseline; gap: 14px; }
  .solde-lib { font-size: 13px; color: var(--color-muted); }
  .solde-val { font-size: 26px; font-weight: 800; color: var(--color-success); }
  .solde-val.neg { color: var(--color-danger); }
  .solde-sub { display: flex; gap: 16px; font-size: 12px; color: var(--color-muted); }
  .solde-sub .gain { color: var(--color-success); }
  .solde-sub .dep { color: var(--color-danger); }

  .treso-cats { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
  .chips-row { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
  .cat-chip { display: inline-flex; align-items: center; gap: 4px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 14px; font-size: 12px; padding: 3px 10px; }
  .cat-x { background: none; border: none; color: var(--color-muted); cursor: pointer; font-size: 14px; line-height: 1; padding: 0; }
  .cat-x:hover { color: var(--color-danger); }
  .cat-add { display: inline-flex; gap: 4px; align-items: center; }
  .cat-add input { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 12px; padding: 4px 8px; width: 150px; }

  .mvt-form { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 14px; }
  .mvt-form input, .mvt-form select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .mvt-form .m-lib { flex: 1; min-width: 160px; }
  .mvt-form .m-num { width: 110px; }

  .cat-totaux { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
  .cat-total { font-size: 12px; border: 1px solid var(--color-border); border-radius: 12px; padding: 3px 10px; color: var(--color-success); }
  .cat-total.neg { color: var(--color-danger); }
  .mvt-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
  .mvt-search { flex: 1; min-width: 200px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; outline: none; }
  .mvt-search:focus { border-color: var(--accent); }
  .mvt-filtre { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .cp-btn { background: none; border: none; color: var(--color-muted); cursor: pointer; font-size: 15px; line-height: 1; padding: 0 4px; }
  .cp-btn:hover { color: var(--accent); }

  .mvt-table { width: 100%; }
  .mvt-table th, .mvt-table td { text-align: left; padding: 5px 6px; border-bottom: 1px solid var(--color-border); font-size: 13px; }
  .mvt-table .gain { color: var(--color-success); }
  .mvt-table .dep { color: var(--color-danger); }

  .pay-action { display: flex; justify-content: flex-end; margin-top: 12px; }
  .paid-badge { color: var(--color-success, #4caf82); font-weight: 600; font-size: 13px; }

  .taux-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
  .taux-row { display: flex; align-items: center; gap: 10px; }
  .taux-grade { width: 200px; font-size: 13px; }
  .taux-head { color: var(--color-muted); font-size: 11px; }
  .taux-head .unit { width: 90px; text-align: center; }
  .taux-row input { width: 90px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 6px 9px; }
  .unit { font-size: 12px; color: var(--color-muted); }
</style>
