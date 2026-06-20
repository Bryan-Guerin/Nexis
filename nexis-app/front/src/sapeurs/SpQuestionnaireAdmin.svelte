<script>
    import {onMount} from 'svelte'
    import {api} from '../shared/api.js'
    import {toast} from '../shared/toasts.js'
    import {confirm} from '../shared/confirm.js'
    import Modal from '../shared/Modal.svelte'

    // Questionnaire guidé du dispatcher (config admin). Liste ordonnée à conditions :
    // chaque réponse peut préremplir un champ d'intervention et/ou suggérer une nature.
    const TYPES  = [['OUI_NON', 'Oui / Non'], ['NOMBRE', 'Nombre']]
    const CIBLES = [
        ['AUCUNE',            'Aiguillage (aucun préremplissage)'],
        ['INCENDIE',          'Coche « Incendie » (si oui)'],
        ['NB_VICTIMES',       'Nombre de victimes (réponse numérique)'],
        ['VEHICULE_IMPLIQUE', 'Coche « Véhicule impliqué » (si oui)'],
    ]
    function typeLabel(t)  { return TYPES.find(x => x[0] === t)?.[1] ?? t }
    function cibleLabel(c) { return CIBLES.find(x => x[0] === c)?.[1] ?? c }
    function natureLabel(id) { return natures.find(n => n.id === id)?.label ?? '?' }
    function questionLabel(id) { return questions.find(q => q.id === id)?.libelle ?? '?' }

    let questions = $state([])
    let natures   = $state([])
    let showForm  = $state(false)
    let editing   = $state(null)
    let form      = $state(emptyForm())
    let dragIndex = $state(null)

    function emptyForm() {
        return { libelle: '', type: 'OUI_NON', cible: 'AUCUNE', natureSuggereeId: '',
                 conditionQuestionId: '', conditionAttendue: true }
    }

    onMount(async () => {
        ;[questions, natures] = await Promise.all([
            api.get('/sp/questions').catch(() => []),
            api.get('/sp/natures').catch(() => []),
        ])
    })

    function openCreate() { editing = null; form = emptyForm(); showForm = true }
    function openEdit(q) {
        editing = q
        form = { libelle: q.libelle, type: q.type, cible: q.cible,
                 natureSuggereeId: q.natureSuggereeId ?? '', conditionQuestionId: q.conditionQuestionId ?? '',
                 conditionAttendue: q.conditionAttendue }
        showForm = true
    }

    async function submit() {
        if (!form.libelle.trim()) { toast.error('Libellé requis'); return }
        const payload = {
            libelle: form.libelle.trim(), type: form.type, cible: form.cible,
            natureSuggereeId: form.natureSuggereeId || null,
            conditionQuestionId: form.conditionQuestionId || null,
            conditionAttendue: form.conditionAttendue,
        }
        try {
            if (editing) {
                const u = await api.put(`/sp/questions/${editing.id}`, payload)
                questions = questions.map(q => q.id === u.id ? u : q)
                toast.success('Question modifiée.')
            } else {
                const c = await api.post('/sp/questions', payload)
                questions = [...questions, c]
                toast.success('Question créée.')
            }
            showForm = false
        } catch { /* toast par api.js */ }
    }

    async function supprimer(q) {
        if (!await confirm({ title: 'Supprimer la question', message: `Supprimer « ${q.libelle} » ?`, danger: true })) return
        try { await api.delete(`/sp/questions/${q.id}`); questions = questions.filter(x => x.id !== q.id) }
        catch { /* toast par api.js */ }
    }

    // Conditions possibles = toutes les questions OUI_NON sauf celle éditée.
    let conditionsPossibles = $derived(questions.filter(q => q.type === 'OUI_NON' && q.id !== editing?.id))

    function onDragStart(i) { dragIndex = i }
    function onDragOver(e, i) {
        e.preventDefault()
        if (dragIndex === null || dragIndex === i) return
        const arr = [...questions]
        const [m] = arr.splice(dragIndex, 1)
        arr.splice(i, 0, m)
        questions = arr
        dragIndex = i
    }
    async function persistOrder() {
        if (dragIndex === null) return
        dragIndex = null
        try { await api.put('/sp/questions/order', { ids: questions.map(q => q.id) }) }
        catch { /* toast par api.js */ }
    }
</script>

<div class="head">
    <h3>Questionnaire dispatch</h3>
    <button class="btn-primary" onclick={openCreate}>+ Nouvelle question</button>
</div>
<p class="muted small">Présenté au dispatcher en mode guidé à la création d'une intervention. Les
    questions s'affichent dans l'ordre ; une question peut dépendre de la réponse à une autre.</p>

{#if questions.length === 0}
    <p class="muted small">Aucune question. Crée-en pour activer le mode guidé.</p>
{:else}
    <ul class="q-list">
        {#each questions as q, i (q.id)}
            <li class="q-row" class:dragging={dragIndex === i}
                draggable="true"
                ondragstart={() => onDragStart(i)}
                ondragover={(e) => onDragOver(e, i)}
                ondragend={persistOrder}>
                <span class="handle" title="Glisser pour réordonner">⠿</span>
                <div class="q-main">
                    <div class="q-lib">{q.libelle}</div>
                    <div class="q-meta muted small">
                        {typeLabel(q.type)} · {cibleLabel(q.cible)}
                        {#if q.natureSuggereeId} · 🚒 propose {natureLabel(q.natureSuggereeId)}{/if}
                        {#if q.conditionQuestionId} · si « {questionLabel(q.conditionQuestionId)} » = {q.conditionAttendue ? 'oui' : 'non'}{/if}
                    </div>
                </div>
                <button class="btn-ghost-sm" onclick={() => openEdit(q)}>Éditer</button>
                <button class="rm-btn" onclick={() => supprimer(q)} title="Supprimer">×</button>
            </li>
        {/each}
    </ul>
{/if}

{#if showForm}
    <Modal title={editing ? 'Modifier la question' : 'Nouvelle question'} width="520px" onclose={() => showForm = false}>
        <div class="form">
            <label class="field-label">Libellé
                <input type="text" bind:value={form.libelle} placeholder="ex: Y a-t-il un feu ?" maxlength="200" />
            </label>
            <div class="row">
                <label class="field-label">Type de réponse
                    <select bind:value={form.type}>
                        {#each TYPES as [v, l]}<option value={v}>{l}</option>{/each}
                    </select>
                </label>
                <label class="field-label">Effet (préremplissage)
                    <select bind:value={form.cible}>
                        {#each CIBLES as [v, l]}<option value={v}>{l}</option>{/each}
                    </select>
                </label>
            </div>
            <label class="field-label">Nature suggérée (si réponse positive)
                <select bind:value={form.natureSuggereeId}>
                    <option value="">— aucune —</option>
                    {#each natures as n (n.id)}<option value={n.id}>{n.label}</option>{/each}
                </select>
            </label>
            <label class="field-label">N'afficher que si (condition)
                <select bind:value={form.conditionQuestionId}>
                    <option value="">— toujours afficher —</option>
                    {#each conditionsPossibles as q (q.id)}<option value={q.id}>{q.libelle}</option>{/each}
                </select>
            </label>
            {#if form.conditionQuestionId}
                <label class="field-label">Réponse attendue à cette condition
                    <select bind:value={form.conditionAttendue}>
                        <option value={true}>Oui</option>
                        <option value={false}>Non</option>
                    </select>
                </label>
            {/if}
        </div>
        {#snippet actions()}
            <button class="btn-ghost" onclick={() => showForm = false}>Annuler</button>
            <button class="btn-primary" onclick={submit}>{editing ? 'Enregistrer' : 'Créer'}</button>
        {/snippet}
    </Modal>
{/if}

<style>
    .head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 4px; }
    .head h3 { margin: 0; }
    .q-list { list-style: none; padding: 0; margin: 10px 0 0; display: flex; flex-direction: column; gap: 6px; }
    .q-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); }
    .q-row.dragging { opacity: .5; }
    .handle { color: var(--color-muted); cursor: grab; font-size: 16px; }
    .q-main { flex: 1; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
    .q-lib { font-weight: 600; }
    .form { display: flex; flex-direction: column; gap: 12px; }
    .row { display: flex; gap: 10px; }
    .row .field-label { flex: 1; }
    .rm-btn { background: none; border: none; color: var(--color-muted); font-size: 18px; line-height: 1; cursor: pointer; padding: 0 6px; }
    .rm-btn:hover { color: var(--color-danger); }
</style>
