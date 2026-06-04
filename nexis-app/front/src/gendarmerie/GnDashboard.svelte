<script>
  import { onMount } from 'svelte'
  import { api } from '../shared/api.js'

  let disponibles  = $state('—')
  let enService    = $state('—')
  let effectifs    = $state('—')

  onMount(async () => {
    const [veh, serv, mem] = await Promise.allSettled([
      api.get('/gn/vehicules?etat=DISPONIBLE'),
      api.get('/gn/affectations'),
      api.get('/gn/membres?actif=true'),
    ])
    if (veh.status  === 'fulfilled') disponibles = veh.value.length
    if (serv.status === 'fulfilled') enService   = serv.value.length
    if (mem.status  === 'fulfilled') effectifs   = mem.value.length
  })
</script>

<div class="page">
  <h2>Gendarmerie — Tableau de bord</h2>

  <div class="stat-grid">
    <div class="stat-card">
      <span class="stat-label">Véhicules disponibles</span>
      <span class="stat-value">{disponibles}</span>
    </div>
    <div class="stat-card">
      <span class="stat-label">Affectations actives</span>
      <span class="stat-value">{enService}</span>
    </div>
    <div class="stat-card">
      <span class="stat-label">Effectifs actifs</span>
      <span class="stat-value">{effectifs}</span>
    </div>
  </div>

  <div class="quick-links">
    <a href="#/gn/vehicules" class="quick-link">Gérer les véhicules →</a>
    <a href="#/gn/effectifs" class="quick-link">Gérer les effectifs →</a>
  </div>
</div>
