<script>
    // noinspection ES6UnusedImports
    import Router from 'svelte-spa-router'
    import {authToken} from './shared/stores.js'
    import {realtime} from './shared/realtime.js'
    import {titleForHash} from './shared/pageTitle.js'
    import Toasts from './shared/Toasts.svelte'
    import Layout from './shell/Layout.svelte'
    import Login from './auth/Login.svelte'
    import Dashboard from './home/Dashboard.svelte'
    import GnDashboard from './gendarmerie/GnDashboard.svelte'
    import GnVehiculesPage from './gendarmerie/GnVehiculesPage.svelte'
    import GnEffectifsPage from './gendarmerie/GnEffectifsPage.svelte'
    import GnPlanningPage from './gendarmerie/GnPlanningPage.svelte'
    import GnConfigPage from './gendarmerie/GnConfigPage.svelte'
    import GnDispatchPage from './gendarmerie/GnDispatchPage.svelte'
    import SpDashboard from './sapeurs/SpDashboard.svelte'
    import SpVehiculesPage from './sapeurs/SpVehiculesPage.svelte'
    import SpEffectifsPage from './sapeurs/SpEffectifsPage.svelte'
    import SpPlanningPage from './sapeurs/SpPlanningPage.svelte'
    import SpConfigPage from './sapeurs/SpConfigPage.svelte'
    import SpDispatchPage from './sapeurs/SpDispatchPage.svelte'
    import SpFeuilleGarde from './sapeurs/SpFeuilleGarde.svelte'
    import SpInterventionsPage from './sapeurs/SpInterventionsPage.svelte'
    import SpMainCourantePage from './sapeurs/SpMainCourantePage.svelte'
    import SpStatsPage from './sapeurs/SpStatsPage.svelte'
    import SpRhPage from './sapeurs/SpRhPage.svelte'
    import SpDocumentsPage from './sapeurs/SpDocumentsPage.svelte'
    import AdminPage from './admin/AdminPage.svelte'
    import AuditPage from './admin/AuditPage.svelte'

    const routes = {
    '/':                Dashboard,
    '/gn':              GnDashboard,
    '/gn/vehicules':    GnVehiculesPage,
    '/gn/effectifs':    GnEffectifsPage,
    '/gn/planning':     GnPlanningPage,
    '/gn/dispatch':     GnDispatchPage,
    '/gn/config':       GnConfigPage,
    '/sp':              SpDashboard,
    '/sp/vehicules':    SpVehiculesPage,
    '/sp/effectifs':    SpEffectifsPage,
    '/sp/planning':     SpPlanningPage,
    '/sp/dispatch':     SpDispatchPage,
    '/sp/feuille-garde': SpFeuilleGarde,
    '/sp/interventions': SpInterventionsPage,
    '/sp/main-courante': SpMainCourantePage,
    '/sp/stats':        SpStatsPage,
    '/sp/rh':           SpRhPage,
    '/sp/documents':    SpDocumentsPage,
    '/sp/config':       SpConfigPage,
    '/admin':           AdminPage,
    '/admin/audit':     AuditPage,
    '*':                Dashboard,
  }

  // Ouvre/ferme le canal temps réel selon l'état d'authentification
  $effect(() => {
    if ($authToken) realtime.connect()
    else realtime.disconnect()
  })

  // Titre de l'onglet = "Nexis — <page courante>"
  let routeHash = $state(window.location.hash)
  window.addEventListener('hashchange', () => { routeHash = window.location.hash })
  $effect(() => { document.title = titleForHash(routeHash) })
</script>

{#if $authToken}
  <Layout>
    <Router {routes} />
  </Layout>
{:else}
  <Login />
{/if}

<Toasts />
