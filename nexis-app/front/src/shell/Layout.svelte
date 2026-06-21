<script>
    import {onMount} from 'svelte'
    import Nav from './Nav.svelte'
    import MonAffectation from './MonAffectation.svelte'
    import Modal from '../shared/Modal.svelte'
    import {authToken, currentUser, theme as themeMode, criAValiderCount} from '../shared/stores.js'
    import {api} from '../shared/api.js'
    import {realtime, startBipLoop, stopBipLoop} from '../shared/realtime.js'
    import {
        clearNotifications,
        markAllRead,
        notifications,
        startNotifications,
        unread
    } from '../shared/notifications.js'

    let { children } = $props()

  // ── Menu latéral rétractable ────────────────────────────────────────────────
  // Desktop : ancré (pousse le contenu). Mobile (≤768px) : overlay au-dessus.
  // État mémorisé en localStorage ; défaut = ouvert sur grand écran, fermé sur mobile.
  function initialNavOpen() {
    try {
      const saved = localStorage.getItem('nav_open')
      if (saved !== null) return saved === '1'
    } catch { /* ignore */ }
    return window.innerWidth > 768
  }
  let navOpen = $state(initialNavOpen())
  function toggleNav() {
    navOpen = !navOpen
    try { localStorage.setItem('nav_open', navOpen ? '1' : '0') } catch { /* ignore */ }
  }

  // ── Profil / avatar ─────────────────────────────────────────────────────────
  const AVATARS = ['🧑‍🚒', '👨‍🚒', '👩‍🚒', '🚒', '🚑', '🦺', '⛑️', '🔥', '🧯', '🚓', '👮', '🐶']
  let avatar      = $state('')
  let showAvatar  = $state(false)

  // Bip reçu (équipage) → pager visuel + son en boucle 30 s ou jusqu'à acquittement
  let pager          = $state(null)   // l'événement BIP reçu
  let pagerCountdown = $state(0)
  let pagerTimer     = null

  // CRI à valider (badge nav) : ping initial + refresh sur événement de soumission/validation, + poll lent (fallback).
  async function refreshCriCount() {
    try {
      if (!$currentUser?.roles?.includes('ROLE_SP')) { criAValiderCount.set(0); return }
      const r = await api.get('/sp/cri/a-valider/count')
      criAValiderCount.set(r.peutValider ? r.count : 0)
    } catch { /* silencieux */ }
  }

  onMount(() => {
    loadProfile()
    startNotifications()
    refreshCriCount()
    const off = realtime.on(ev => {
      if (ev.type === 'BIP') declenchePager(ev)
      if (ev.type === 'COMPTE_DESACTIVE') { logout(); window.location.assign('/') }
      if (ev.type === 'INTERVENTION_CLOTUREE') refreshCriCount()
    })
    const pollCri = setInterval(refreshCriCount, 30000)   // poll régulier (pas d'event dédié)
    // Sur mobile, refermer le menu overlay après une navigation
    const closeOnNav = () => { if (window.innerWidth <= 768) navOpen = false }
    window.addEventListener('hashchange', closeOnNav)
    return () => { off(); stopBipLoop(); clearInterval(pagerTimer); clearInterval(pollCri); window.removeEventListener('hashchange', closeOnNav) }
  })

  // Centre de notifications (cloche)
  let notifOpen = $state(false)
  function toggleNotif() { notifOpen = !notifOpen; if (notifOpen) markAllRead() }
  function heureNotif(t) { return new Date(t).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) }

  function declenchePager(ev) {
    pager = ev
    pagerCountdown = 30
    startBipLoop(30000)
    clearInterval(pagerTimer)
    pagerTimer = setInterval(() => {
      pagerCountdown -= 1
      if (pagerCountdown <= 0) acquitter()
    }, 1000)
  }

  function acquitter() {
    stopBipLoop()
    clearInterval(pagerTimer)
    pager = null
  }

  async function loadProfile() {
    try { const me = await api.get('/account'); avatar = me?.avatar ?? '' } catch { /* ignore */ }
  }

  async function chooseAvatar(a) {
    try { await api.patch('/account/avatar', { avatar: a }); avatar = a } catch { /* ignore */ }
    showAvatar = false
  }

  // Thème par faction : bleu (GN) / vert (SP), déduit de la route courante
  let hash = $state(window.location.hash)
  window.addEventListener('hashchange', () => { hash = window.location.hash })
  let theme = $derived(
    hash.startsWith('#/sp') ? 'theme-sp' :
    hash.startsWith('#/gn') ? 'theme-gn' : ''
  )

  // Menu utilisateur (haut droite)
  let menuOpen = $state(false)

  // Modale changement de mot de passe
  let showPwd    = $state(false)
  let pwdForm    = $state({ current: '', next: '', confirm: '' })
  let pwdError   = $state('')
  let pwdSuccess = $state('')
  let pwdBusy    = $state(false)

  function logout() {
    authToken.logout()
    currentUser.set(null)
  }

  function openPwd() {
    pwdForm = { current: '', next: '', confirm: '' }
    pwdError = ''; pwdSuccess = ''
    showPwd = true
    menuOpen = false
  }

  async function submitPwd(e) {
    e.preventDefault(); pwdError = ''; pwdSuccess = ''
    if (pwdForm.next !== pwdForm.confirm) { pwdError = 'La confirmation ne correspond pas'; return }
    if (pwdForm.next.length < 6) { pwdError = 'Le nouveau mot de passe doit faire au moins 6 caractères'; return }
    pwdBusy = true
    try {
      await api.post('/account/password', { currentPassword: pwdForm.current, newPassword: pwdForm.next })
      pwdSuccess = 'Mot de passe modifié avec succès.'
      pwdForm = { current: '', next: '', confirm: '' }
    } catch (e) { pwdError = e.message }
    finally { pwdBusy = false }
  }
</script>

{#if pager}
  <div class="pager-overlay" role="alertdialog" aria-modal="true" aria-label="Bip d'alerte">
    <div class="pager">
      <div class="pager-top">
        <span class="led" aria-hidden="true"></span>
        <span class="pager-brand">NEXIS · BIP</span>
        <span class="pager-count" aria-label={`${pagerCountdown} secondes restantes`}>{pagerCountdown}s</span>
      </div>
      <div class="pager-screen">
        {#if pager.payload?.code}
          <div class="scr big">🚨 DÉPART {pager.payload.nature ?? ''}</div>
          <div class="scr">{pager.payload.motif ?? ''}</div>
          {#if pager.payload.observation}<div class="scr">📝 {pager.payload.observation}</div>{/if}
          {#if pager.payload.commune || pager.payload.coord}
            <div class="scr">{[pager.payload.commune, pager.payload.coord].filter(Boolean).join('  ·  ')}</div>
          {/if}
          <div class="scr">{#if pager.payload.enginImageId}<img class="bip-veh" src="/api/sp/icones/{pager.payload.enginImageId}/contenu" alt="" />{:else}🚒{/if} {pager.payload.engin ?? ''}  ·  {pager.payload.poste ?? pager.payload.code}</div>
        {:else}
          <div class="scr big">🔔 BIP</div>
          <div class="scr">{pager.message}</div>
        {/if}
      </div>
      <button class="pager-ack" onclick={acquitter}>ACQUITTER</button>
    </div>
  </div>
{/if}

<a href="#main-content" class="skip-link">Aller au contenu principal</a>

<div class="layout">
  <header>
    <div class="header-left">
      <button class="nav-toggle" onclick={toggleNav} aria-label="Afficher/masquer le menu" aria-expanded={navOpen}>☰</button>
      <button class="brand" onclick={toggleNav} title="Afficher/masquer le menu">NEXIS</button>
    </div>
    <div class="user-area">
      {#if $currentUser}
        <MonAffectation />
        <div class="notif-wrap">
          <button class="notif-btn" onclick={toggleNotif} title="Notifications">
            🔔{#if $unread > 0}<span class="notif-badge">{$unread > 9 ? '9+' : $unread}</span>{/if}
          </button>
          {#if notifOpen}
            <button class="menu-overlay" aria-label="Fermer" onclick={() => notifOpen = false}></button>
            <div class="notif-panel">
              <div class="notif-head">
                <span>Notifications</span>
                {#if $notifications.length > 0}<button class="notif-clear" onclick={clearNotifications}>Tout effacer</button>{/if}
              </div>
              <div class="notif-list">
                {#each $notifications as n (n.id)}
                  <div class="notif-item" class:unread={!n.read}>
                    <span class="notif-ico">{n.icon}</span>
                    <span class="notif-msg">{n.message}</span>
                    <span class="notif-time">{heureNotif(n.time)}</span>
                  </div>
                {/each}
                {#if $notifications.length === 0}<p class="notif-empty">Aucune notification</p>{/if}
              </div>
            </div>
          {/if}
        </div>
        <button class="user-btn" onclick={() => menuOpen = !menuOpen}>
          <span class="avatar">{avatar || '🧑‍🚒'}</span>
          <span class="username">{$currentUser.username}</span>
          <span class="caret">▾</span>
        </button>
        {#if menuOpen}
          <button class="menu-overlay" aria-label="Fermer le menu" onclick={() => menuOpen = false}></button>
          <div class="user-menu">
            <button class="menu-item" onclick={() => { showAvatar = true; menuOpen = false }}>Changer d'avatar</button>
            <button class="menu-item" onclick={openPwd}>Modifier mon mot de passe</button>
            <button class="menu-item" onclick={() => themeMode.toggle()}>
              {$themeMode === 'light' ? '🌙 Thème sombre' : '☀️ Thème clair'}
            </button>
            <button class="menu-item danger" onclick={logout}>Déconnexion</button>
          </div>
        {/if}
      {:else}
        <button class="logout-btn" onclick={logout}>Déconnexion</button>
      {/if}
    </div>
  </header>

  <div class="body" class:nav-hidden={!navOpen}>
    <aside>
      <Nav />
    </aside>
    {#if navOpen}
      <button class="nav-backdrop" aria-label="Fermer le menu" onclick={toggleNav}></button>
    {/if}
    <main id="main-content" class={theme} tabindex="-1">
      {@render children()}
    </main>
  </div>
</div>

<!-- ── Modale : choix d'avatar ──────────────────────────────────────────────── -->
{#if showAvatar}
  <Modal title="Choisir un avatar" onclose={() => showAvatar = false}>
    <div class="avatar-grid">
      {#each AVATARS as a}
        <button class="avatar-choice" class:selected={a === avatar} onclick={() => chooseAvatar(a)}>{a}</button>
      {/each}
    </div>
    {#snippet actions()}
      <button class="btn-ghost-sm" onclick={() => showAvatar = false}>Fermer</button>
    {/snippet}
  </Modal>
{/if}

<!-- ── Modale : changer son mot de passe ────────────────────────────────────── -->
{#if showPwd}
  <Modal title="Modifier mon mot de passe" onclose={() => showPwd = false}>
    {#if pwdError}<p class="inline-error">{pwdError}</p>{/if}
    {#if pwdSuccess}<p class="success-msg">{pwdSuccess}</p>{/if}

    <form onsubmit={submitPwd} style="display:flex;flex-direction:column;gap:12px">
      <label class="field-label">Mot de passe actuel
        <input type="password" bind:value={pwdForm.current} autocomplete="current-password" required />
      </label>
      <label class="field-label">Nouveau mot de passe
        <input type="password" bind:value={pwdForm.next} autocomplete="new-password" required />
      </label>
      <label class="field-label">Confirmer
        <input type="password" bind:value={pwdForm.confirm} autocomplete="new-password" required />
      </label>
      <div class="modal-actions">
        <button type="button" class="btn-ghost-sm" onclick={() => showPwd = false}>Fermer</button>
        <button type="submit" class="btn-primary" disabled={pwdBusy}>{pwdBusy ? '…' : 'Valider'}</button>
      </div>
    </form>
  </Modal>
{/if}

<style>
  .layout {
    display: grid;
    grid-template-rows: var(--header-h) 1fr;
    height: 100%;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
  }

  .header-left { display: flex; align-items: center; gap: 10px; }

  .nav-toggle {
    background: transparent; border: 1px solid var(--color-border);
    border-radius: var(--radius); color: var(--color-text);
    font-size: 16px; line-height: 1; padding: 4px 9px; cursor: pointer;
    transition: border-color 0.2s, color 0.2s;
  }
  .nav-toggle:hover { border-color: var(--color-primary); color: var(--color-primary); }

  .brand {
    background: none; border: none; cursor: pointer;
    font-weight: 700;
    font-size: 15px;
    letter-spacing: 3px;
    color: var(--color-primary);
    padding: 0;
  }

  .user-area { position: relative; display: flex; align-items: center; gap: 12px; }

  .user-btn {
    display: flex; align-items: center; gap: 6px;
    background: transparent; border: 1px solid var(--color-border);
    border-radius: var(--radius); padding: 5px 10px;
    color: var(--color-text); cursor: pointer;
    transition: border-color 0.2s;
  }
  .user-btn:hover { border-color: var(--color-primary); }
  .username { color: var(--color-muted); font-size: 13px; }
  .caret { color: var(--color-muted); font-size: 10px; }
  .avatar { font-size: 18px; line-height: 1; }

  .avatar-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; }
  .avatar-choice {
    font-size: 24px; line-height: 1; padding: 8px;
    background: var(--color-bg); border: 1px solid var(--color-border);
    border-radius: var(--radius); cursor: pointer; transition: border-color .15s;
  }
  .avatar-choice:hover { border-color: var(--accent); }
  .avatar-choice.selected { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }

  .menu-overlay { position: fixed; inset: 0; background: transparent; border: none; cursor: default; z-index: 90; }
  .user-menu {
    position: absolute; top: calc(100% + 6px); right: 0; z-index: 91;
    background: var(--color-surface); border: 1px solid var(--color-border);
    border-radius: var(--radius); min-width: 220px;
    display: flex; flex-direction: column; padding: 4px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  }
  .menu-item {
    text-align: left; background: none; border: none;
    color: var(--color-text); font-size: 13px;
    padding: 8px 10px; border-radius: var(--radius); cursor: pointer;
  }
  .menu-item:hover { background: var(--color-border); }
  .menu-item.danger { color: var(--color-danger); }

  .logout-btn {
    background: transparent; border: 1px solid var(--color-border);
    border-radius: var(--radius); color: var(--color-muted);
    font-size: 12px; padding: 5px 10px;
    transition: border-color 0.2s, color 0.2s;
  }
  .logout-btn:hover { border-color: var(--color-danger); color: var(--color-danger); }

  /* Centre de notifications */
  .notif-wrap { position: relative; }
  .notif-btn { position: relative; background: none; border: none; font-size: 18px; line-height: 1; padding: 4px 6px; cursor: pointer; }
  .notif-badge { position: absolute; top: -2px; right: -2px; background: var(--color-danger); color: #fff; font-size: 9px; font-weight: 700; min-width: 15px; height: 15px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; padding: 0 3px; }
  .notif-panel { position: absolute; right: 0; top: calc(100% + 6px); width: 320px; max-height: 60vh; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); box-shadow: 0 8px 24px rgba(0,0,0,.3); z-index: 100; display: flex; flex-direction: column; overflow: hidden; }
  .notif-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid var(--color-border); font-size: 11px; font-weight: 700; color: var(--color-muted); text-transform: uppercase; letter-spacing: .5px; }
  .notif-clear { background: none; border: none; color: var(--color-muted); font-size: 11px; cursor: pointer; text-transform: none; letter-spacing: 0; }
  .notif-clear:hover { color: var(--color-danger); }
  .notif-list { overflow-y: auto; }
  .notif-item { display: flex; align-items: center; gap: 8px; padding: 9px 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; }
  .notif-item:last-child { border-bottom: none; }
  .notif-item.unread { background: color-mix(in srgb, var(--accent) 8%, transparent); }
  .notif-msg { flex: 1; }
  .notif-time { font-size: 11px; color: var(--color-muted); white-space: nowrap; }
  .notif-empty { padding: 16px; text-align: center; color: var(--color-muted); font-size: 13px; }

  .success-msg {
    color: var(--color-success); background: rgba(76, 175, 130, 0.1);
    border: 1px solid var(--color-success); border-radius: var(--radius);
    padding: 8px 12px; font-size: 13px;
  }

  /* ── Pager (style boîtier type Birdie) ─────────────────────────────────── */
  .pager-overlay {
    /* Alerte critique : au-dessus de tout (carte Leaflet ≤1000, modales ≤1500, toasts 2000). */
    position: fixed; inset: 0; z-index: 3000;
    background: rgba(0, 0, 0, 0.6);
    display: flex; align-items: center; justify-content: center;
  }
  .pager {
    width: 340px;
    background: linear-gradient(#2b2f3a, #161922);
    border: 1px solid #000; border-radius: 18px;
    padding: 14px;
    box-shadow: 0 0 0 2px #3a3f4d, 0 18px 50px rgba(0, 0, 0, 0.6);
    animation: pager-shake 0.5s ease-in-out infinite;
  }
  .pager-top {
    display: flex; align-items: center; gap: 8px;
    color: #c7ccd9; font-size: 11px; font-weight: 700; letter-spacing: 1px;
    padding: 0 4px 8px;
  }
  .pager-brand { flex: 1; }
  .pager-count { font-family: monospace; color: #ff7a7a; }
  .led {
    width: 10px; height: 10px; border-radius: 50%;
    background: #ff3b3b; box-shadow: 0 0 8px #ff3b3b;
    animation: led-blink 0.5s steps(1) infinite;
  }
  .pager-screen {
    background: #0d1f0d; border: 2px solid #050; border-radius: 8px;
    padding: 12px; min-height: 120px;
    display: flex; flex-direction: column; gap: 6px;
    box-shadow: inset 0 0 18px rgba(0, 0, 0, 0.7);
  }
  .scr {
    font-family: 'Courier New', monospace; color: #8dff7a;
    font-size: 13px; line-height: 1.35; text-shadow: 0 0 4px rgba(141, 255, 122, 0.5);
    word-break: break-word;
  }
  .scr.big { font-size: 16px; font-weight: 700; }
  .bip-veh { height: 16px; width: 16px; object-fit: contain; vertical-align: -3px; }
  .pager-ack {
    width: 100%; margin-top: 12px;
    background: #e8a23a; color: #1a1a1a; border: none;
    border-radius: 8px; padding: 12px; font-size: 15px; font-weight: 800;
    letter-spacing: 1px; cursor: pointer; box-shadow: 0 4px 0 #b07820;
  }
  .pager-ack:active { transform: translateY(2px); box-shadow: 0 2px 0 #b07820; }
  @keyframes led-blink { 0% { opacity: 1; } 50% { opacity: 0.2; } 100% { opacity: 1; } }
  @keyframes pager-shake {
    0%, 100% { transform: translate(0, 0) rotate(0); }
    25% { transform: translate(-2px, 1px) rotate(-0.6deg); }
    75% { transform: translate(2px, -1px) rotate(0.6deg); }
  }

  .body {
    display: grid;
    grid-template-columns: var(--nav-width) 1fr;
    overflow: hidden;
    transition: grid-template-columns 0.18s ease;
  }
  /* Desktop : menu rétracté → la colonne se replie, le contenu prend toute la largeur */
  .body.nav-hidden { grid-template-columns: 0 1fr; }

  aside {
    background: var(--color-surface);
    border-right: 1px solid var(--color-border);
    overflow-y: auto;
    overflow-x: hidden;
  }
  .body.nav-hidden aside { border-right: none; }

  main {
    overflow-y: auto;
    padding: 24px;
  }

  /* Backdrop : affiché uniquement en mode overlay (mobile) */
  .nav-backdrop { display: none; }

  /* ── Mobile : menu en overlay au-dessus du contenu (pas de recalcul de layout) ── */
  @media (max-width: 768px) {
    .body,
    .body.nav-hidden { grid-template-columns: 1fr; }   /* le contenu occupe toujours toute la largeur */

    aside {
      position: fixed;
      top: var(--header-h); left: 0; bottom: 0;
      width: min(82vw, 300px);
      z-index: 1100;   /* au-dessus des panes/contrôles Leaflet (jusqu'à 1000) */
      transform: translateX(-100%);
      transition: transform 0.2s ease;
      box-shadow: 0 0 28px rgba(0, 0, 0, 0.45);
      border-right: 1px solid var(--color-border);
    }
    .body:not(.nav-hidden) aside { transform: translateX(0); }

    .nav-backdrop {
      display: block;
      position: fixed;
      top: var(--header-h); left: 0; right: 0; bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      border: none; z-index: 1099;
    }

    main { padding: 16px; }

    /* Compact header sur petit écran */
    header { padding: 0 12px; }
    .user-area { gap: 8px; }
    .username, .caret { display: none; }   /* avatar seul suffit */
  }

</style>
