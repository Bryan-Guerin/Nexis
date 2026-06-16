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
    └── geojson/             # couches vectorielles (tableau de Features, coords [x,y] mètres monde)
        ├── forest.geojson
        ├── track.geojson
        ├── road.geojson
        ├── main_road.geojson
        ├── church.geojson
        ├── fuelstation.geojson
        ├── hospital.geojson
        ├── namecity.geojson
        ├── namevillage.geojson
        └── namemarine.geojson
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
