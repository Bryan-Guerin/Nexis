<script>
  import { currentUser } from '../shared/stores.js'

  const nav = [
    { path: '/',                   label: 'Accueil',         roles: null },

    { section: 'Gendarmerie',                                roles: ['ROLE_GN'] },
    { path: '/gn',                 label: 'Tableau de bord', roles: ['ROLE_GN'] },
    { path: '/gn/vehicules',       label: 'Véhicules',       roles: ['ROLE_GN'] },
    { path: '/gn/effectifs',       label: 'Effectifs',       roles: ['ROLE_GN'] },
    { path: '/gn/planning',        label: 'Planning',        roles: ['ROLE_GN'] },
    { path: '/gn/dispatch',        label: 'Dispatch',        roles: ['ROLE_GN'] },
    { path: '/gn/interventions',   label: 'Interventions',   roles: ['ROLE_GN'] },
    { path: '/gn/config',          label: 'Configuration',   roles: ['ROLE_ADMIN_GN'] },

    { section: 'Sapeurs-Pompiers',                           roles: ['ROLE_SP'] },
    { path: '/sp',                 label: 'Tableau de bord', roles: ['ROLE_SP'] },
    { path: '/sp/vehicules',       label: 'Véhicules',       roles: ['ROLE_SP'] },
    { path: '/sp/effectifs',       label: 'Effectifs',       roles: ['ROLE_SP'] },
    { path: '/sp/planning',        label: 'Planning',        roles: ['ROLE_SP'] },
    { path: '/sp/dispatch',        label: 'Dispatch',        roles: ['ROLE_SP'] },
    { path: '/sp/interventions',   label: 'Interventions',   roles: ['ROLE_SP'] },
    { path: '/sp/stats',           label: 'Statistiques',    roles: ['ROLE_SP'] },
    { path: '/sp/main-courante',   label: 'Main courante',   roles: ['ROLE_SP'] },
    { path: '/sp/documents',       label: 'Documents',       roles: ['ROLE_SP'] },
    { path: '/sp/rh',              label: 'RH / Paie',       roles: ['ROLE_SP_RH'] },
    { path: '/sp/config',          label: 'Configuration',   roles: ['ROLE_ADMIN_SP'] },

    { section: 'Administration',                             roles: ['ROLE_SYSTEM'] },
    { path: '/admin',              label: 'Utilisateurs',    roles: ['ROLE_SYSTEM'] },
    { path: '/admin/audit',        label: 'Audit',           roles: ['ROLE_SYSTEM'] },
  ]

  // Regroupe les liens sous leur catégorie. "Accueil" (sans section) reste isolé en tête.
  const standalone = []
  const groups = []
  {
    let current = null
    for (const item of nav) {
      if (item.section) { current = { section: item.section, roles: item.roles, items: [] }; groups.push(current) }
      else if (current) { current.items.push(item) }
      else { standalone.push(item) }
    }
  }

  function canSee(roles) {
    if (!roles) return true
    const userRoles = $currentUser?.roles ?? []
    return roles.some(r => userRoles.includes(r))
  }

  let currentHash = $state(window.location.hash)
  window.addEventListener('hashchange', () => { currentHash = window.location.hash })

  // État replié/déplié des catégories, persisté
  let collapsed = $state(JSON.parse(localStorage.getItem('nav_collapsed') ?? '{}'))
  function toggle(section) {
    collapsed = { ...collapsed, [section]: !collapsed[section] }
    localStorage.setItem('nav_collapsed', JSON.stringify(collapsed))
  }
</script>

<nav>
  {#each standalone as item}
    {#if canSee(item.roles)}
      <a href="#{item.path}" class:active={currentHash === `#${item.path}`}>{item.label}</a>
    {/if}
  {/each}

  {#each groups as g}
    {#if g.items.some(it => canSee(it.roles))}
      <button class="nav-section" onclick={() => toggle(g.section)} aria-expanded={!collapsed[g.section]}>
        <span class="chevron" class:collapsed={collapsed[g.section]}>▾</span>
        {g.section}
      </button>
      {#if !collapsed[g.section]}
        {#each g.items as item}
          {#if canSee(item.roles)}
            <a href="#{item.path}" class:active={currentHash === `#${item.path}`}>{item.label}</a>
          {/if}
        {/each}
      {/if}
    {/if}
  {/each}
</nav>

<style>
  nav {
    display: flex;
    flex-direction: column;
    padding: 16px 0;
    gap: 2px;
    overflow-y: auto;
  }

  .nav-section {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    background: none;
    border: none;
    text-align: left;
    color: var(--color-muted);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.8px;
    text-transform: uppercase;
    padding: 16px 16px 6px;
    cursor: pointer;
    transition: color 0.15s;
  }
  .nav-section:hover { color: var(--color-text); }

  .chevron { font-size: 9px; transition: transform 0.15s; }
  .chevron.collapsed { transform: rotate(-90deg); }

  a {
    color: var(--color-text);
    font-size: 13px;
    padding: 8px 16px;
    border-radius: var(--radius);
    margin: 0 8px;
    transition: background 0.15s, color 0.15s;
  }

  a:hover { background: var(--color-border); }

  a.active {
    background: rgba(79, 110, 247, 0.15);
    color: var(--color-primary);
    font-weight: 500;
  }
</style>
