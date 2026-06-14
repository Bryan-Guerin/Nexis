<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'

    let paie    = $state(null)
  let grades  = $state([])
  let error   = $state('')
  let savingId = $state(null)
  let reglant = $state(false)

  onMount(() => { load(); loadGrades() })

  async function load(lundi) {
    error = ''
    try { paie = await api.get('/sp/rh/paie' + (lundi ? `?lundi=${lundi}` : '')) }
    catch (e) { error = e.message }
  }
  async function loadGrades() {
    try { grades = await api.get('/sp/rh/grades') } catch (e) { error = e.message }
  }

  function shiftWeek(days) {
    if (!paie) return
    const d = new Date(paie.debut + 'T12:00:00')
    d.setDate(d.getDate() + days)
    load(d.toISOString().slice(0, 10))
  }

  async function saveTaux(g) {
    savingId = g.id; error = ''
    try {
      await api.put(`/sp/rh/grades/${g.id}/taux`, { tauxHoraire: Number(g.tauxHoraire), tauxAstreinte: Number(g.tauxAstreinte) })
      await load(paie?.debut)   // recalcule la paie avec le nouveau taux
    } catch (e) { error = e.message }
    finally { savingId = null }
  }

  async function regler() {
    if (!paie || paie.payee) return
    if (!confirm(`Marquer la paie de la semaine du ${jour(paie.debut)} comme réglée ?\nAction irréversible — chaque membre sera notifié de son versement.`)) return
    reglant = true; error = ''
    try { paie = await api.post(`/sp/rh/paie/regler?lundi=${paie.debut}`) }
    catch (e) { error = e.message }
    finally { reglant = false }
  }

  function jour(iso) { return new Date(iso + 'T12:00:00').toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }) }
  function dateHeure(iso) { return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) }
  function eur(n) { return (n ?? 0).toFixed(2) + ' €' }
</script>

<div class="page">
  <div class="page-header">
    <h2>RH / Paie — Sapeurs-Pompiers</h2>
  </div>

  {#if error}<p class="inline-error">{error}</p>{/if}

  <!-- Paie hebdomadaire -->
  <section class="panel">
    <div class="week-nav">
      <button class="btn-ghost-sm" onclick={() => shiftWeek(-7)}>← Semaine préc.</button>
      {#if paie}<span class="week-label">Semaine du {jour(paie.debut)} au {jour(paie.fin)}</span>{/if}
      <button class="btn-ghost-sm" onclick={() => shiftWeek(7)}>Semaine suiv. →</button>
      <button class="btn-ghost-sm" onclick={() => load()}>Cette semaine</button>
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
