<script>
  import {onMount} from 'svelte'
  import {api} from '../shared/api.js'

  // Modal de création d'intervention, réutilisable (écran interventions + bouton rapide dispatch).
  let { onclose, oncreated } = $props()

  let natures   = $state([])
  let vehicules = $state([])
  let form      = $state({ motif: '', natureId: '', requerant: '', telephone: '', observation: '', commune: '', coordonnees: '', nbVictimes: '', incendie: false, vehiculeImplique: false })
  let createSel = $state([])
  let createError = $state('')

  onMount(async () => {
    ;[vehicules, natures] = await Promise.all([
      api.get('/sp/vehicules/engageables').catch(() => []),
      api.get('/sp/natures').catch(() => []),
    ])
  })

  function coordDisplay(raw) { return raw.length > 3 ? raw.slice(0, 3) + ' ' + raw.slice(3) : raw }
  function onCoordInput(e) { form.coordonnees = e.target.value.replace(/\D/g, '').slice(0, 6) }
  function toggle(list, id) { return list.includes(id) ? list.filter(x => x !== id) : [...list, id] }

  function estPropose(v) { return v.arme && (v.natureIds ?? []).includes(form.natureId) }
  let engageablesTries = $derived([...vehicules].sort((a, b) =>
    (estPropose(b) ? 1 : 0) - (estPropose(a) ? 1 : 0) ||
    (b.arme ? 1 : 0) - (a.arme ? 1 : 0) ||
    a.libelle.localeCompare(b.libelle)
  ))
  function avertirNonArmes(ids) {
    const nonArmes = vehicules.filter(v => ids.includes(v.vehiculeId) && !v.arme)
    if (nonArmes.length === 0) return true
    return window.confirm(`⚠ ${nonArmes.map(v => v.libelle).join(', ')} non armé(s) (poste obligatoire non couvert).\nEngager quand même ?`)
  }
  async function avertirDesaffectation(ids) {
    if (!ids || ids.length === 0) return true
    let preview = []
    try { preview = await api.post('/sp/interventions/preview-desaffectation', { vehiculeIds: ids }) }
    catch { preview = [] }
    if (!preview || preview.length === 0) return true
    const lignes = preview.map(p => `• ${p.gradeCode} ${p.nom} — ${p.fonction} (${p.vehicule})`).join('\n')
    return window.confirm(`Au déclenchement, ces effectifs sur un poste NON obligatoire seront désaffectés :\n\n${lignes}\n\nDéclencher quand même ?`)
  }

  async function submitCreate(e) {
    e.preventDefault(); createError = ''
    if (!form.motif.trim()) { createError = 'Motif requis'; return }
    if (!form.natureId) { createError = 'La nature est obligatoire'; return }
    if (!avertirNonArmes(createSel)) return
    if (!(await avertirDesaffectation(createSel))) return
    try {
      await api.post('/sp/interventions', {
        motif: form.motif,
        natureId: form.natureId,
        requerant: form.requerant || null,
        telephone: form.telephone || null,
        observation: form.observation || null,
        commune: form.commune || null,
        coordonnees: form.coordonnees || null,
        nbVictimes: form.nbVictimes !== '' ? Number(form.nbVictimes) : null,
        incendie: form.incendie,
        vehiculeImplique: form.vehiculeImplique,
        vehiculeIds: createSel,
      })
      oncreated?.()
      onclose()
    } catch (err) { createError = err.message }
  }
</script>

<div class="backdrop">
  <div class="modal create-modal">
    <button class="modal-x" title="Fermer" onclick={onclose}>✕</button>
    <h3>Nouvelle intervention</h3>
    {#if createError}<p class="inline-error">{createError}</p>{/if}
    <form class="create-modal-form" onsubmit={submitCreate}>
      <div class="form-row">
        <label>Nature *
          <select bind:value={form.natureId} required>
            <option value="" disabled>— choisir —</option>
            {#each natures as n (n.id)}<option value={n.id}>{n.code} · {n.label}</option>{/each}
          </select>
        </label>
        <label>Motif<input type="text" bind:value={form.motif} placeholder="ex: Feu d'habitation" required /></label>
      </div>
      <div class="form-row">
        <label>Requérant<input type="text" bind:value={form.requerant} maxlength="40" /></label>
        <label>Téléphone<input type="tel" bind:value={form.telephone} maxlength="10" placeholder="10 chiffres" /></label>
        <label>Commune<input type="text" bind:value={form.commune} maxlength="40" /></label>
        <label>Coordonnées<input type="text" inputmode="numeric" maxlength="7" value={coordDisplay(form.coordonnees)} oninput={onCoordInput} placeholder="ex: 060 150" /></label>
      </div>
      <label class="full">Observation<input type="text" bind:value={form.observation} placeholder="Précisions / description" /></label>

      <div class="qualif">
        <span class="pick-label">Qualification</span>
        <div class="qualif-row">
          <label class="q-vic">Nb victimes<input type="number" min="0" bind:value={form.nbVictimes} placeholder="0" /></label>
          <label class="q-chk"><input type="checkbox" bind:checked={form.incendie} /> Incendie</label>
          <label class="q-chk"><input type="checkbox" bind:checked={form.vehiculeImplique} /> Véhicule impliqué</label>
        </div>
      </div>

      <div class="veh-pick">
        <span class="pick-label">Engins engagés</span>
        <div class="veh-grid">
          {#each engageablesTries as v (v.vehiculeId)}
            <label class="veh-check" class:propose={estPropose(v)}>
              <input type="checkbox" checked={createSel.includes(v.vehiculeId)} onchange={() => createSel = toggle(createSel, v.vehiculeId)} />
              {v.libelle} <span class="chip-code">{v.typeCode}</span>
              {#if estPropose(v)}<span class="propose-tag">proposé</span>{/if}
              {#if !v.arme}<span class="non-arme">non armé</span>{/if}
            </label>
          {/each}
        </div>
        {#if vehicules.length === 0}<span class="muted small">Aucun véhicule</span>{/if}
      </div>
      <div class="modal-actions">
        <button type="submit" class="btn-primary">Déclencher l'intervention</button>
      </div>
    </form>
  </div>
</div>

<style>
  .create-modal { width: 640px; max-height: 88vh; overflow-y: auto; position: relative; }
  .modal-x { position: absolute; top: 12px; right: 14px; background: none; border: none; color: var(--color-muted); font-size: 18px; cursor: pointer; line-height: 1; }
  .modal-x:hover { color: var(--color-danger); }
  .create-modal-form { display: flex; flex-direction: column; gap: 14px; margin-top: 4px; }
  .create-modal label { display: flex; flex-direction: column; gap: 5px; font-size: 11px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .4px; }
  .create-modal label.full { width: 100%; }
  .create-modal input, .create-modal select { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 7px 10px; outline: none; text-transform: none; letter-spacing: 0; }
  .create-modal .veh-check { text-transform: none; letter-spacing: 0; flex-direction: row; align-items: center; gap: 8px; }
  .form-row { display: flex; gap: 10px; flex-wrap: wrap; }
  .form-row label { flex: 1; min-width: 140px; }
  .veh-pick { display: flex; flex-direction: column; gap: 6px; }
  .pick-label { font-size: 11px; color: var(--color-muted); text-transform: uppercase; letter-spacing: .5px; }
  .veh-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 6px; }
  .veh-check { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }

  .qualif { display: flex; flex-direction: column; gap: 6px; }
  .qualif-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
  .q-vic { display: flex; flex-direction: column; gap: 2px; font-size: 12px; color: var(--color-muted); }
  .q-vic input { width: 80px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); color: var(--color-text); font-size: 13px; padding: 5px 8px; }
  .q-chk { display: flex; align-items: center; gap: 6px; font-size: 13px; }

  .veh-check.propose { border-left: 2px solid var(--color-success); padding-left: 5px; border-radius: 2px; }
  .propose-tag { font-size: 9px; font-weight: 700; color: var(--color-success); background: color-mix(in srgb, var(--color-success) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
  .non-arme { font-size: 9px; font-weight: 700; color: var(--color-danger); background: color-mix(in srgb, var(--color-danger) 16%, transparent); border-radius: 6px; padding: 1px 6px; }
</style>
