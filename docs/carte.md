# Carte (fonds UnRealLife)

Les fonds de carte (tuiles satellite, geojson vectoriels, méta) sont **volumineux**
(~50 Mo, plus si on ajoute `house`/eau). Ils sont donc **hors git et hors jar**,
servis depuis un **volume monté** sur le VPS, comme le branding.

- URL servie par l'app : `/map/**`
- Mapping backend : `micronaut.router.static-resources.map` →
  `file:${NEXIS_MAP_DIR:/map}` (voir `application.properties`)
- Volume Docker : `/opt/volumes/nexis/map:/map:ro` (voir `docker-compose.yaml`)

## Où déposer les assets (sur le VPS)

Copier l'arborescence du terrain dans `/opt/volumes/nexis/map/` :

```
/opt/volumes/nexis/map/
└── unreallife/
    ├── meta.json
    ├── preview.png
    ├── sat/                 # mosaïque satellite 4×4 (1 px = 1 m)
    │   ├── 0/0.png … 0/3.png
    │   ├── 1/0.png … 1/3.png
    │   ├── 2/0.png … 2/3.png
    │   └── 3/0.png … 3/3.png
    ├── geojson/             # couches vectorielles légères (tableau de Features, coords [x,y] m)
    │   ├── forest / track / road(+road-bridge) / main_road(+bridge) / railway
    │   ├── powerline / mounts / rock / rocks / rockarea
    │   ├── church / fuelstation / hospital
    │   ├── namecity / namevillage / namemarine
    │   └── watertower / lighthouse / view-tower / transmitter / chapel / cross /
    │       busstop / fountain / ruin / shipwreck / tourism / airport / hill /
    │       powersolar / powerwind / bunker / citycenter        (exclus : bush)
    └── tiles/               # couches lourdes tuilées (chargées à la demande au zoom)
        ├── house/{cx}/{cy}.geojson  + house/index.json
        └── tree/{cx}/{cy}.geojson   + tree/index.json
```

## Couches lourdes tuilées (house, tree)

`house` (66 Mo) et `tree` (18 Mo) sont trop gros à charger d'un bloc. Le script
`scripts/split-map-layers.mjs` les découpe en **tuiles spatiales** (grille 32 = cellule
320 m). La carte (mode vecteur) ne fetch que les cellules **visibles** au-delà d'un zoom.

Régénérer les tuiles (offline, à refaire si la map change) :

```
node scripts/split-map-layers.mjs \
  ops/grad_meh/unreallife_map/geojson \
  nexis-app/front/public/map/unreallife/tiles 32 house,tree
```

`sat/{x}/{y}.png` : `x` = colonne (0 = ouest), `y` = ligne (0 = nord). Chaque tuile
2560², total 10240² = worldSize.

Permissions : le conteneur tourne en `PUID:PGID` (cf. `.env`) ; le volume est monté
en lecture seule (`:ro`), donc un simple `chmod -R a+rX` suffit.

## Dev local

En dev, **Vite sert `nexis-app/front/public/` directement** : garder les assets dans
`nexis-app/front/public/map/unreallife/` (dossier gitignoré). Pas besoin de
`NEXIS_MAP_DIR` en dev. Pour tester le service backend en local, pointer
`NEXIS_MAP_DIR` vers ce dossier.

## Couches absentes

- **Eau** : grad_meh n'a produit **aucune** couche eau (lac/mer) pour ce terrain.
  Visible uniquement en mode **satellite**. Pour l'avoir en vecteur : re-run grad_meh
  avec la couche eau, ou ajouter un `water.geojson` tracé à la main.
- **house** (~66 Mo) : exclu par défaut (trop lourd). Si déposé sur le volume,
  prévoir un rendu canvas + toggle dédié côté front.
