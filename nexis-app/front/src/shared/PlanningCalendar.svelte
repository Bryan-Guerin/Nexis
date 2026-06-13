<script>
    /**
     * Composant planning partagé GN / SP.
     * Affichage paysage : colonnes = heures (6h→6h), lignes = effectifs.
     *
     * Props attendues :
     *   planningPath  — ex. '/gn/planning'
     *   membresPath   — ex. '/gn/membres'
     *   selfPath      — ex. '/gn/planning/me'
     *   title         — titre de la page
     *
     * La couleur d'accent provient du thème de faction (--accent), posé par Layout.
     */
    import {onMount} from 'svelte'
    import {api} from './api.js'

    let { planningPath, membresPath, selfPath, title, canManageGarde = false, gardeBase = null } = $props()

  // ── Constantes de mise en page ──────────────────────────────────────────────
  const PX_M     = 1.2        // pixels par minute (axe horizontal)
  const H_START  = 6          // heure de début (06:00)
  const SCROLL_H = 18         // au chargement, on cadre sur 18:00 (plage de jeu 18h→6h)
  const TOTAL_M  = 24 * 60    // fenêtre : 24 h
  const COL_W    = 196        // largeur de la colonne membre (px)
  const ROW_H    = 36         // hauteur d'une ligne membre (px)
  const HDR_H    = 28         // hauteur de la ligne d'en-tête horaire (px)
  const TRACK_W  = TOTAL_M * PX_M   // largeur totale de la piste (px)

  // ── État ────────────────────────────────────────────────────────────────────
  let membres    = $state([])
  let planning   = $state([])
  let loading    = $state(true)
  let error      = $state('')
  let currentDay = $state(today())

  // Conteneur scrollable du calendrier : on cadre l'affichage sur 18h au 1er rendu
  let calOuter   = $state(null)
  let centered   = false
  $effect(() => {
    if (!loading && calOuter && !centered) {
      centered = true
      calOuter.scrollLeft = (SCROLL_H - H_START) * 60 * PX_M
    }
  })

  // Statuts de planning configurés (référentiel de la faction)
  let statuts = $state([])

  // Formulaire auto-déclaration
  let showForm  = $state(false)
  let fStatutId = $state('')
  let fDebut    = $state('')
  let fFin      = $state('')
  let fNotes    = $state('')
  let fError    = $state('')
  let fBusy     = $state(false)

  // ── Repères de l'en-tête (une marque par heure) ─────────────────────────────
  const hourTicks = Array.from({ length: 25 }, (_, i) => ({
    label: `${String((H_START + i) % 24).padStart(2, '0')}:00`,
    left:  i * 60 * PX_M,
  }))

  // Traits de grille (toutes les 15 min)
  const gridLines = Array.from({ length: TOTAL_M / 15 }, (_, i) => ({
    left:  i * 15 * PX_M,
    major: i % 4 === 0,   // trait plein à chaque heure
  }))

  // ── Helpers date ────────────────────────────────────────────────────────────
  // Jour opérationnel : la journée court de 6h à 6h. Avant 6h, on est encore sur la veille.
  function today() {
    const d = new Date()
    if (d.getHours() < H_START) d.setDate(d.getDate() - 1)
    d.setHours(0, 0, 0, 0)
    return d
  }

  // Formate une Date en valeur d'<input datetime-local> en heure LOCALE (pas UTC).
  function toLocalInput(d) {
    const p = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
  }

  function dayStart(d) {
    const r = new Date(d); r.setHours(H_START, 0, 0, 0); return r
  }
  function dayEnd(d) {
    const r = new Date(d); r.setDate(r.getDate() + 1); r.setHours(H_START, 0, 0, 0); return r
  }

  // ── Gestion des gardes par le dispatch (boutons par effectif) ───────────────
  let enService = $state(new Set())
  async function loadEnService() {
    if (!canManageGarde || !gardeBase) return
    const ids = await api.get(`${gardeBase}/membres/en-service`).catch(() => [])
    enService = new Set(ids)
  }
  let gardeDuree = $state({})   // membreId -> heures choisies (défaut 4)
  async function demarrerGarde(m) {
    error = ''
    const h = gardeDuree[m.id] ?? 4
    try { await api.post(`${gardeBase}/planning/membres/${m.id}/prendre-garde?heures=${h}`); await load(); await loadEnService() }
    catch (e) { error = e.message }
  }
  async function terminerGarde(m) {
    if (!window.confirm(`Terminer la garde de ${m.nomComplet || m.username} ?`)) return
    error = ''
    try { await api.put(`${gardeBase}/planning/membres/${m.id}/terminer-garde`); await load(); await loadEnService() }
    catch (e) { error = e.message }
  }

  // ── Chargement ──────────────────────────────────────────────────────────────
  onMount(() => { load(); loadEnService() })

  async function load() {
    loading = true; error = ''
    try {
      ;[membres, planning, statuts] = await Promise.all([
        api.get(`${membresPath}?actif=true`),
        api.get(planningPath),
        api.get(`${planningPath}/statuts`),
      ])
      if (canManageGarde) gardeDuree = Object.fromEntries(membres.map(m => [m.id, gardeDuree[m.id] ?? 4]))
    } catch (e) { error = e.message }
    finally    { loading = false }
  }

  // ── Navigation ──────────────────────────────────────────────────────────────
  function prevDay() { const d = new Date(currentDay); d.setDate(d.getDate()-1); currentDay = d }
  function nextDay() { const d = new Date(currentDay); d.setDate(d.getDate()+1); currentDay = d }

  function fmtDay(d) {
    return d.toLocaleDateString('fr-FR', {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
    })
  }

  // ── Calcul des blocs par membre pour le jour affiché ────────────────────────
  let planningByMembre = $derived.by(() => {
    const start = dayStart(currentDay).getTime()
    const end   = dayEnd(currentDay).getTime()
    /** @type {Record<string, Array>} */
    const map = {}

    for (const p of planning) {
      const ps = new Date(p.debut).getTime()
      const pe = new Date(p.fin).getTime()
      if (ps >= end || pe <= start) continue          // hors de la fenêtre

      const s    = Math.max(ps, start)
      const e2   = Math.min(pe, end)
      const left = (s - start) / 60000 * PX_M
      const w    = Math.max((e2 - s) / 60000 * PX_M, 3)

      ;(map[p.membreId] ??= []).push({ ...p, leftPx: left, widthPx: w })
    }
    return map
  })

  // ── Formulaire auto-déclaration ─────────────────────────────────────────────
  function roundUp15(d) {
    d.setMinutes(Math.ceil(d.getMinutes() / 15) * 15, 0, 0); return d
  }

  function openForm() {
    const now = roundUp15(new Date())
    const fin = new Date(now); fin.setHours(fin.getHours() + 3)   // plage par défaut : 3h
    fDebut = toLocalInput(now)   // heure locale (française), pas UTC
    fFin   = toLocalInput(fin)
    const defaut = statuts.find(s => s.categorie === 'GARDE') ?? statuts[0]
    fStatutId = defaut ? defaut.id : ''
    fNotes = ''; fError = ''; showForm = true
  }

  async function submitForm() {
    fError = ''; fBusy = true
    try {
      await api.post(selfPath, {
        debut:   new Date(fDebut).toISOString(),
        fin:     new Date(fFin).toISOString(),
        statutId: fStatutId,
        notes:   fNotes || null,
      })
      showForm = false
      await load()
    } catch (e) { fError = e.message }
    finally    { fBusy = false }
  }
</script>

<!-- ── Page ────────────────────────────────────────────────────────────────── -->
<div class="page">
  <div class="page-header">
    <h2>{title}</h2>
    <div class="nav-day">
      <button class="btn-ghost" onclick={prevDay}>‹</button>
      <span class="day-label">{fmtDay(currentDay)}</span>
      <button class="btn-ghost" onclick={nextDay}>›</button>
      <button class="btn-primary" onclick={openForm}>Se déclarer</button>
      <button class="btn-ghost" onclick={load}>↺</button>
    </div>
  </div>

  {#if loading}
    <p class="muted">Chargement…</p>
  {:else if error}
    <p class="inline-error">{error}</p>
  {:else}

    <!-- ── Calendrier ─────────────────────────────────────────────────────── -->
    <div class="cal-outer" bind:this={calOuter}>
      <div class="cal-inner" style="min-width:{COL_W + TRACK_W}px">

        <!-- En-tête horaire (sticky top) -->
        <div class="hdr-row" style="height:{HDR_H}px">
          <!-- Coin supérieur gauche (sticky left + top) -->
          <div class="corner" style="width:{COL_W}px; height:{HDR_H}px"></div>
          <!-- Piste horaire -->
          <div class="hdr-track" style="width:{TRACK_W}px; height:{HDR_H}px">
            {#each hourTicks as t}
              <span class="hour-tick" style="left:{t.left}px">{t.label}</span>
            {/each}
          </div>
        </div>

        <!-- Lignes membres -->
        {#each membres as m (m.id)}
          <div class="cal-row" style="height:{ROW_H}px">

            <!-- Cellule membre (sticky left) -->
            <div class="member-cell" style="width:{COL_W}px; height:{ROW_H}px">
              <span class="m-grade">{m.grade}</span>
              <span class="m-name">{m.username}</span>
              <span class="m-mat">{m.matricule}</span>
              {#if canManageGarde}
                {#if enService.has(m.id)}
                  <button class="g-btn stop" title="Terminer la garde" onclick={() => terminerGarde(m)}>⏹</button>
                {:else}
                  <select class="g-duree" title="Durée de la garde" bind:value={gardeDuree[m.id]}>
                    <option value={1}>1h</option>
                    <option value={2}>2h</option>
                    <option value={3}>3h</option>
                    <option value={4}>4h</option>
                  </select>
                  <button class="g-btn start" title="Démarrer la garde" onclick={() => demarrerGarde(m)}>▶</button>
                {/if}
              {/if}
            </div>

            <!-- Piste planning -->
            <div class="track" style="width:{TRACK_W}px; height:{ROW_H}px">
              <!-- Grille -->
              {#each gridLines as g}
                <div
                  class="gl"
                  class:gl-major={g.major}
                  style="left:{g.left}px"
                ></div>
              {/each}

              <!-- Blocs planning -->
              {#each planningByMembre[m.id] ?? [] as ev (ev.id)}
                <div
                  class="block"
                  style="
                    left:{ev.leftPx}px;
                    width:{ev.widthPx}px;
                    background:{ev.statut.couleur}26;
                    border-top:3px solid {ev.statut.couleur};
                  "
                  title="{ev.statut.label}{ev.notes ? ' — ' + ev.notes : ''}"
                >
                  {#if ev.widthPx >= 44}
                    <span
                      class="block-label"
                      style="color:{ev.statut.couleur}"
                    >{ev.statut.label}</span>
                  {/if}
                </div>
              {/each}
            </div>

          </div>
        {/each}

        {#if membres.length === 0}
          <p class="empty">Aucun effectif actif</p>
        {/if}

      </div>
    </div>
  {/if}
</div>

<!-- ── Modal auto-déclaration ─────────────────────────────────────────────── -->
{#if showForm}
  <div class="backdrop" onclick={() => showForm = false}>
    <div class="modal" onclick={e => e.stopPropagation()}>
      <h3>Se déclarer</h3>

      <label>Statut
        <select bind:value={fStatutId}>
          {#each statuts as s (s.id)}
            <option value={s.id}>{s.label}</option>
          {/each}
        </select>
      </label>
      <label>Début
        <input type="datetime-local" bind:value={fDebut} />
      </label>
      <label>Fin
        <input type="datetime-local" bind:value={fFin} />
      </label>
      <label>Notes
        <input type="text" bind:value={fNotes} placeholder="Optionnel" />
      </label>

      {#if fError}<p class="inline-error">{fError}</p>{/if}

      <div class="modal-actions">
        <button class="btn-ghost" onclick={() => showForm = false}>Annuler</button>
        <button class="btn-primary" onclick={submitForm} disabled={fBusy}>
          {fBusy ? 'Enregistrement…' : 'Confirmer'}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  /* ── Barre de navigation jour ─────────────────────────────────────────────── */
  .page-header { flex-wrap: wrap; gap: 12px; }
  .nav-day  { display: flex; align-items: center; gap: 8px; }
  .day-label { font-size: 14px; font-weight: 500; min-width: 240px; text-align: center; }

  /* ── Calendrier ─────────────────────────────────────────────────────────── */
  .cal-outer {
    overflow: auto;
    max-height: calc(100vh - 160px);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
  }
  .cal-inner { display: flex; flex-direction: column; }

  /* En-tête horaire */
  .hdr-row {
    display: flex;
    position: sticky; top: 0; z-index: 10;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
  }
  .corner {
    flex-shrink: 0;
    position: sticky; left: 0; z-index: 11;
    background: var(--color-surface);
    border-right: 1px solid var(--color-border);
  }
  .hdr-track {
    flex-shrink: 0;
    position: relative;
  }
  .hour-tick {
    position: absolute;
    top: 50%; transform: translateY(-50%);
    font-size: 11px; color: var(--color-muted);
    white-space: nowrap;
    padding-left: 4px;
  }

  /* Ligne membre */
  .cal-row {
    display: flex;
    border-bottom: 1px solid var(--color-border);
  }
  .cal-row:last-child { border-bottom: none; }

  /* Cellule membre (sticky left) */
  .member-cell {
    flex-shrink: 0;
    position: sticky; left: 0; z-index: 5;
    background: var(--color-surface);
    border-right: 1px solid var(--color-border);
    display: flex; align-items: center; gap: 8px;
    padding: 0 12px;
    overflow: hidden;
  }
  .m-grade {
    font-size: 10px; font-weight: 700;
    color: var(--color-muted);
    text-transform: uppercase; letter-spacing: 0.4px;
    white-space: nowrap;
  }
  .m-name {
    font-size: 12px; font-weight: 500;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    flex: 1; min-width: 0;
  }
  .m-mat {
    font-family: monospace; font-size: 10px;
    color: var(--color-muted); white-space: nowrap;
  }
  .g-duree { flex-shrink: 0; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 4px; color: var(--color-text); font-size: 10px; padding: 2px 3px; outline: none; }
  .g-btn { flex-shrink: 0; border: 1px solid var(--color-border); background: var(--color-bg); border-radius: 4px; font-size: 10px; line-height: 1; padding: 3px 5px; cursor: pointer; }
  .g-btn.start { color: var(--color-success); border-color: color-mix(in srgb, var(--color-success) 45%, var(--color-border)); }
  .g-btn.stop  { color: var(--color-danger); border-color: color-mix(in srgb, var(--color-danger) 45%, var(--color-border)); }

  /* Piste planning */
  .track {
    flex-shrink: 0;
    position: relative;
    background: var(--color-bg);
  }

  /* Traits de grille */
  .gl {
    position: absolute; top: 0; bottom: 0;
    width: 1px;
    background: var(--color-border); opacity: 0.3;
  }
  .gl-major { opacity: 0.7; }

  /* Blocs planning */
  .block {
    position: absolute; top: 2px; bottom: 2px;
    border-radius: 3px;
    overflow: hidden;
    display: flex; align-items: center;
    padding: 0 5px;
    box-sizing: border-box;
    cursor: default;
    transition: filter .1s;
  }
  .block:hover { filter: brightness(1.2); }
  .block-label {
    font-size: 10px; font-weight: 600;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }

  /* .empty, .btn-ghost, .btn-primary, .backdrop, .modal-actions proviennent du socle ui.css */

  /* ── Modale (compacte, propre au calendrier) ─────────────────────────────── */
  .modal { width: 360px; gap: 14px; }
  .modal label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-muted); }
  .modal select, .modal input[type="datetime-local"], .modal input[type="text"] {
    background: var(--color-bg); border: 1px solid var(--color-border);
    border-radius: var(--radius); color: var(--color-text);
    font-size: 13px; padding: 7px 10px; outline: none;
  }
  .modal select:focus, .modal input:focus { border-color: var(--accent); }
</style>
