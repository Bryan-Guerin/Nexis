<script>
    import PlanningCalendar from '../shared/PlanningCalendar.svelte'
    import {currentUser} from '../shared/stores.js'

    // Le dispatch (et l'admin SP) peut démarrer / terminer les gardes des autres effectifs.
    let canManageGarde = $derived(
      ($currentUser?.roles ?? []).some(r => r === 'ROLE_SP_DISPATCH' || r === 'ROLE_ADMIN_SP')
    )
</script>

<PlanningCalendar
  title="Planning — Sapeurs-Pompiers"
  planningPath="/sp/planning"
  membresPath="/sp/membres"
  selfPath="/sp/planning/me"
  {canManageGarde}
  gardeBase="/sp"
/>
