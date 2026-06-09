// Titre de l'onglet navigateur selon la route courante (hash sans le #).
// Format : "Nexis — <libellé>". Route inconnue → "Nexis".

const TITLES = {
  '/':                  'Accueil',

  '/gn':                'Gendarmerie · Tableau de bord',
  '/gn/vehicules':      'Gendarmerie · Véhicules',
  '/gn/effectifs':      'Gendarmerie · Effectifs',
  '/gn/planning':       'Gendarmerie · Planning',
  '/gn/dispatch':       'Gendarmerie · Dispatch',
  '/gn/config':         'Gendarmerie · Configuration',

  '/sp':                'Sapeurs-Pompiers · Tableau de bord',
  '/sp/vehicules':      'Sapeurs-Pompiers · Véhicules',
  '/sp/effectifs':      'Sapeurs-Pompiers · Effectifs',
  '/sp/planning':       'Sapeurs-Pompiers · Planning',
  '/sp/dispatch':       'Sapeurs-Pompiers · Dispatch',
  '/sp/interventions':  'Sapeurs-Pompiers · Interventions',
  '/sp/main-courante':  'Sapeurs-Pompiers · Main courante',
  '/sp/stats':          'Sapeurs-Pompiers · Statistiques',
  '/sp/rh':             'Sapeurs-Pompiers · RH / Paie',
  '/sp/documents':      'Sapeurs-Pompiers · Documents',
  '/sp/config':         'Sapeurs-Pompiers · Configuration',

  '/admin':             'Administration · Utilisateurs',
  '/admin/audit':       'Administration · Audit',
}

export function titleForHash(hash) {
  const path = (hash || '').replace(/^#/, '') || '/'
  const label = TITLES[path]
  return label ? `Nexis — ${label}` : 'Nexis'
}
