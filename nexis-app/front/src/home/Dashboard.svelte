<script>
    import {currentUser} from '../shared/stores.js'

    const modules = [
    { path: '/gn', title: 'Gendarmerie',      desc: 'Véhicules, interventions, effectifs GN', roles: ['ROLE_GN'] },
    { path: '/sp', title: 'Sapeurs-Pompiers', desc: 'Véhicules, interventions, effectifs SP', roles: ['ROLE_SP'] },
    { path: '/admin', title: 'Administration', desc: 'Gestion des utilisateurs et rôles',      roles: ['ROLE_SYSTEM'] },
  ]

  function canSee(roles) {
    const userRoles = $currentUser?.roles ?? []
    return roles.some(r => userRoles.includes(r))
  }
</script>

<div class="page">
  <div class="welcome">
    <h2>Tableau de bord</h2>
    <p class="muted">Bienvenue, <strong>{$currentUser?.username ?? '—'}</strong></p>
  </div>

  <div class="card-grid">
    {#each modules.filter(m => canSee(m.roles)) as mod}
      <a href="#{mod.path}" class="module-card">
        <span class="module-title">{mod.title}</span>
        <span class="module-desc">{mod.desc}</span>
      </a>
    {/each}
  </div>
</div>

<style>
  .page { gap: 28px; }
  .welcome h2 { font-size: 22px; font-weight: 600; margin-bottom: 4px; }
  .muted strong { color: var(--color-text); font-weight: 500; }

  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 16px;
  }

  .module-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    color: var(--color-text);
    transition: border-color 0.2s, background 0.2s;
  }
  .module-card:hover {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 6%, transparent);
  }

  .module-title { font-size: 16px; font-weight: 600; }
  .module-desc  { font-size: 12px; color: var(--color-muted); line-height: 1.4; }
</style>
