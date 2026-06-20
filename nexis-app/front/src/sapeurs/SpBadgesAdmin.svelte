<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {toast} from '../shared/toasts.js'
    import {confirm} from '../shared/confirm.js'
    import Modal from '../shared/Modal.svelte'

    // Catalogue des badges (succès) géré par l'admin SP.
    // Conditions = ce qu'on compte pour attribuer le badge.
    const CONDITIONS = [
        ['INTER_COUNT',              'Nombre d\'interventions'],
        ['INTER_NATURE_COUNT',       'Interventions d\'une nature'],
        ['INTER_TYPE_FONCTION_COUNT','Interventions dans un rôle (chef, conducteur…)'],
        ['INTER_SEMAINE_COUNT',      'Interventions de la semaine (votées)'],
        ['GARDE_HEURES',        'Heures de garde cumulées'],
        ['SERVICE_JOURS',       'Jours d\'ancienneté'],
        ['GRADE_JOURS',         'Jours dans le grade'],
        ['QUALIF_COUNT',        'Nombre de qualifications'],
        ['QUALIF_TYPE_COUNT',   'Qualifications d\'un type'],
        ['FONCTION_ORGA',       'Appartenance à une fonction (service)'],
    ]
    // Conditions sans seuil (booléennes : présence)
    const SANS_SEUIL = new Set(['FONCTION_ORGA'])
    function condLabel(c) { return CONDITIONS.find(x => x[0] === c)?.[1] ?? c }

    // Types de fonction (pour QUALIF_TYPE_COUNT)
    const TYPES_FONCTION = [
        ['CHEF_AGRES',  'Chef d\'agrès'],
        ['CONDUCTEUR',  'Conducteur'],
        ['CHEF_EQUIPE', 'Chef d\'équipe'],
        ['EQUIPIER',    'Équipier'],
    ]
    function typeFonctionLabel(t) { return TYPES_FONCTION.find(x => x[0] === t)?.[1] ?? t }
    // Conditions qui ciblent un type de fonction (réutilisent le champ typeFonction)
    const TYPE_FONCTION_CONDS = new Set(['QUALIF_TYPE_COUNT', 'INTER_TYPE_FONCTION_COUNT'])
    function seuilUnite(c) {
        if (c === 'GARDE_HEURES') return 'heures'
        if (c === 'SERVICE_JOURS' || c === 'GRADE_JOURS') return 'jours'
        if (c === 'QUALIF_COUNT' || c === 'QUALIF_TYPE_COUNT') return 'qualifications'
        return 'interventions'
    }

    let badges  = $state([])
    let natures = $state([])
    let fonctionsOrga = $state([])

    let showForm = $state(false)
    let editing  = $state(null)   // badge en cours d'édition ; null = création
    let form     = $state(emptyForm())
    let dragIndex = $state(null)

    function emptyForm() {
        return { code: '', label: '', icone: '🏅', description: '',
                 typeCondition: 'INTER_COUNT', natureId: '', typeFonction: 'CONDUCTEUR', fonctionOrgaId: '', seuil: 1, xpReward: 50 }
    }

    onMount(async () => {
        ;[badges, natures, fonctionsOrga] = await Promise.all([
            api.get('/sp/badges').catch(() => []),
            api.get('/sp/natures').catch(() => []),
            api.get('/sp/fonctions-orga').catch(() => []),
        ])
    })

    function openCreate() { editing = null; form = emptyForm(); showForm = true }
    function openEdit(b) {
        editing = b
        form = {
            code: b.code, label: b.label, icone: b.icone || '🏅',
            description: b.description ?? '',
            typeCondition: b.typeCondition,
            natureId: b.natureId ?? '',
            typeFonction: b.typeFonction ?? 'CONDUCTEUR',
            fonctionOrgaId: b.fonctionOrgaId ?? '',
            seuil: b.seuil, xpReward: b.xpReward,
        }
        showForm = true
    }

    async function submit() {
        if (!form.label.trim()) { toast.error('Libellé requis'); return }
        if (form.typeCondition === 'INTER_NATURE_COUNT' && !form.natureId) {
            toast.error('Nature requise pour ce type de condition'); return
        }
        if (form.typeCondition === 'FONCTION_ORGA' && !form.fonctionOrgaId) {
            toast.error('Fonction d\'organigramme requise pour ce type de condition'); return
        }
        const payload = {
            label: form.label.trim(),
            icone: form.icone || null,
            description: form.description || null,
            typeCondition: form.typeCondition,
            natureId: form.typeCondition === 'INTER_NATURE_COUNT' ? form.natureId : null,
            typeFonction: TYPE_FONCTION_CONDS.has(form.typeCondition) ? form.typeFonction : null,
            fonctionOrgaId: form.typeCondition === 'FONCTION_ORGA' ? form.fonctionOrgaId : null,
            seuil: SANS_SEUIL.has(form.typeCondition) ? 1 : (Number(form.seuil) || 1),
            xpReward: Number(form.xpReward) || 0,
        }
        try {
            if (editing) {
                const u = await api.put(`/sp/badges/${editing.id}`, payload)
                badges = badges.map(b => b.id === u.id ? u : b)
                toast.success('Badge modifié.')
            } else {
                if (!form.code.trim()) { toast.error('Code requis'); return }
                const created = await api.post('/sp/badges', { code: form.code.trim(), ...payload })
                badges = [...badges, created]
                toast.success('Badge créé.')
            }
            showForm = false
        } catch { /* toast par api.js */ }
    }

    async function supprimer(b) {
        if (!await confirm({ title: 'Supprimer le badge', message: `Supprimer « ${b.label} » ?\nLes attributions sont également retirées.`, danger: true })) return
        try { await api.delete(`/sp/badges/${b.id}`); badges = badges.filter(x => x.id !== b.id) }
        catch { /* toast par api.js */ }
    }

    async function evaluer() {
        try {
            const res = await api.post('/sp/badges/eval')
            if (res?.attribues > 0) toast.success(`${res.attribues} attribution(s) effectuée(s).`)
            else toast.info('Aucun nouveau badge à attribuer.')
        } catch { /* toast par api.js */ }
    }

    function onDragStart(i) { dragIndex = i }
    function onDragOver(e, i) {
        e.preventDefault()
        if (dragIndex === null || dragIndex === i) return
        const arr = [...badges]
        const [m] = arr.splice(dragIndex, 1)
        arr.splice(i, 0, m)
        badges = arr
        dragIndex = i
    }
    async function persistOrder() {
        if (dragIndex === null) return
        dragIndex = null
        try { await api.put('/sp/badges/order', { ids: badges.map(b => b.id) }) }
        catch { /* toast par api.js */ }
    }
</script>

<div class="head">
    <h3>Badges (succès)</h3>
    <div class="actions">
        <button class="btn-ghost" onclick={evaluer} title="Re-évalue tous les membres actifs">↻ Évaluer maintenant</button>
        <button class="btn-primary" onclick={openCreate}>+ Nouveau badge</button>
    </div>
</div>

{#if badges.length === 0}
    <p class="muted small">Aucun badge défini. Crée-en un pour démarrer le système de succès.</p>
{:else}
    <ul class="badges-list">
        {#each badges as b, i (b.id)}
            <li class="badge-row" class:dragging={dragIndex === i}
                draggable="true"
                ondragstart={() => onDragStart(i)}
                ondragover={(e) => onDragOver(e, i)}
                ondragend={persistOrder}>
                <span class="handle" title="Glisser pour réordonner">⠿</span>
                <span class="b-ico">{b.icone || '🏅'}</span>
                <div class="b-main">
                    <div class="b-label">{b.label} <span class="chip-code">{b.code}</span></div>
                    <div class="b-cond muted small">
                        {condLabel(b.typeCondition)}
                        {#if b.natureLabel} · {b.natureLabel}{/if}
                        {#if b.typeFonction} · {typeFonctionLabel(b.typeFonction)}{/if}
                        {#if b.fonctionOrgaLabel} · {b.fonctionOrgaLabel}{/if}
                        {#if !SANS_SEUIL.has(b.typeCondition)} · seuil {b.seuil}{/if}
                        · +{b.xpReward} XP
                    </div>
                    {#if b.description}<div class="b-desc muted small">{b.description}</div>{/if}
                </div>
                <button class="btn-ghost-sm" onclick={() => openEdit(b)}>Éditer</button>
                <button class="rm-btn" onclick={() => supprimer(b)} title="Supprimer">×</button>
            </li>
        {/each}
    </ul>
{/if}

<!-- Modale création / édition -->
{#if showForm}
    <Modal title={editing ? 'Modifier le badge' : 'Nouveau badge'} width="520px" onclose={() => showForm = false}>
        <div class="form">
            {#if !editing}
                <label class="field-label">Code (technique, unique)
                    <input type="text" bind:value={form.code} placeholder="ex: INCENDIE_10" maxlength="50" />
                </label>
            {/if}
            <label class="field-label">Libellé
                <input type="text" bind:value={form.label} placeholder="ex: Pompier des feux" maxlength="120" />
            </label>
            <div class="row">
                <label class="field-label" style="flex:0 0 80px">Icône
                    <input type="text" bind:value={form.icone} placeholder="🔥" maxlength="4" />
                </label>
                <label class="field-label">Description (optionnelle)
                    <input type="text" bind:value={form.description} placeholder="Affichée au survol" maxlength="200" />
                </label>
            </div>
            <label class="field-label">Condition d'obtention
                <select bind:value={form.typeCondition}>
                    {#each CONDITIONS as [v, l]}<option value={v}>{l}</option>{/each}
                </select>
            </label>
            {#if form.typeCondition === 'INTER_NATURE_COUNT'}
                <label class="field-label">Nature ciblée
                    <select bind:value={form.natureId}>
                        <option value="">— choisir —</option>
                        {#each natures as n (n.id)}<option value={n.id}>{n.label}</option>{/each}
                    </select>
                </label>
            {/if}
            {#if TYPE_FONCTION_CONDS.has(form.typeCondition)}
                <label class="field-label">Type de fonction ciblé
                    <select bind:value={form.typeFonction}>
                        {#each TYPES_FONCTION as [v, l]}<option value={v}>{l}</option>{/each}
                    </select>
                </label>
            {/if}
            {#if form.typeCondition === 'FONCTION_ORGA'}
                <label class="field-label">Fonction d'organigramme (service)
                    <select bind:value={form.fonctionOrgaId}>
                        <option value="">— choisir —</option>
                        {#each fonctionsOrga as f (f.id)}<option value={f.id}>{f.label}</option>{/each}
                    </select>
                </label>
            {/if}
            <div class="row">
                {#if !SANS_SEUIL.has(form.typeCondition)}
                    <label class="field-label">Seuil ({seuilUnite(form.typeCondition)})
                        <input type="number" min="1" bind:value={form.seuil} />
                    </label>
                {/if}
                <label class="field-label">Prime XP au déblocage
                    <input type="number" min="0" bind:value={form.xpReward} />
                </label>
            </div>
        </div>
        {#snippet actions()}
            <button class="btn-ghost" onclick={() => showForm = false}>Annuler</button>
            <button class="btn-primary" onclick={submit}>{editing ? 'Enregistrer' : 'Créer'}</button>
        {/snippet}
    </Modal>
{/if}

<style>
    .head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
    .head h3 { margin: 0; }
    .actions { display: flex; gap: 8px; }
    .badges-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
    .badge-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); }
    .badge-row.dragging { opacity: .5; }
    .handle { color: var(--color-muted); cursor: grab; font-size: 16px; }
    .b-ico { font-size: 24px; }
    .b-main { flex: 1; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
    .b-label { font-weight: 600; }
    .form { display: flex; flex-direction: column; gap: 12px; }
    .row { display: flex; gap: 10px; }
    .row .field-label { flex: 1; }
    .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 18px; line-height: 1; cursor: pointer; padding: 0 6px; }
    .rm-btn:hover { color: var(--color-danger); }
</style>
