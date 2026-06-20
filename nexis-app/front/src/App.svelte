<script>
    // noinspection ES6UnusedImports
    import Router from 'svelte-spa-router'
    import {wrap} from 'svelte-spa-router/wrap'
    import {authToken} from './shared/stores.js'
    import {realtime} from './shared/realtime.js'
    import {titleForHash} from './shared/pageTitle.js'
    import Toasts from './shared/Toasts.svelte'
    import ConfirmHost from './shared/ConfirmHost.svelte'
    import Layout from './shell/Layout.svelte'
    import Login from './auth/Login.svelte'
    import Dashboard from './home/Dashboard.svelte'

    // Code-splitting : chaque page de section devient son propre chunk JS, chargé à la
    // navigation (la première fois). Login + Dashboard + Layout restent dans le bundle initial.
    const routes = {
    '/':                 Dashboard,
    '/gn':               wrap({ asyncComponent: () => import('./gendarmerie/GnDashboard.svelte') }),
    '/gn/vehicules':     wrap({ asyncComponent: () => import('./gendarmerie/GnVehiculesPage.svelte') }),
    '/gn/effectifs':     wrap({ asyncComponent: () => import('./gendarmerie/GnEffectifsPage.svelte') }),
    '/gn/planning':      wrap({ asyncComponent: () => import('./gendarmerie/GnPlanningPage.svelte') }),
    '/gn/dispatch':      wrap({ asyncComponent: () => import('./gendarmerie/GnDispatchPage.svelte') }),
    '/gn/config':        wrap({ asyncComponent: () => import('./gendarmerie/GnConfigPage.svelte') }),
    '/sp':               wrap({ asyncComponent: () => import('./sapeurs/SpDashboard.svelte') }),
    '/sp/vehicules':     wrap({ asyncComponent: () => import('./sapeurs/SpVehiculesPage.svelte') }),
    '/sp/effectifs':     wrap({ asyncComponent: () => import('./sapeurs/SpEffectifsPage.svelte') }),
    '/sp/planning':      wrap({ asyncComponent: () => import('./sapeurs/SpPlanningPage.svelte') }),
    '/sp/dispatch':      wrap({ asyncComponent: () => import('./sapeurs/SpDispatchPage.svelte') }),
    '/sp/feuille-garde': wrap({ asyncComponent: () => import('./sapeurs/SpFeuilleGarde.svelte') }),
    '/sp/interventions': wrap({ asyncComponent: () => import('./sapeurs/SpInterventionsPage.svelte') }),
    '/sp/main-courante': wrap({ asyncComponent: () => import('./sapeurs/SpMainCourantePage.svelte') }),
    '/sp/stats':         wrap({ asyncComponent: () => import('./sapeurs/SpStatsPage.svelte') }),
    '/sp/rh':            wrap({ asyncComponent: () => import('./sapeurs/SpRhPage.svelte') }),
    '/sp/documents':     wrap({ asyncComponent: () => import('./sapeurs/SpDocumentsPage.svelte') }),
    '/sp/icones':        wrap({ asyncComponent: () => import('./sapeurs/SpIconesPage.svelte') }),
    '/sp/config':        wrap({ asyncComponent: () => import('./sapeurs/SpConfigPage.svelte') }),
    '/admin':            wrap({ asyncComponent: () => import('./admin/AdminPage.svelte') }),
    '/admin/audit':      wrap({ asyncComponent: () => import('./admin/AuditPage.svelte') }),
    '*':                 Dashboard,
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
<ConfirmHost />
