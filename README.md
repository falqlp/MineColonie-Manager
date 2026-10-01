# Colony Resource Ledger

Addon MineColonies pour Minecraft 1.21.1 / NeoForge. Un clic droit avec le
`Colony Resource Ledger` ouvre une vue serveur des ressources réclamées par
tous les builders de la colonie accessible la plus proche.

## Fabriquer le registre

Combinez **1 livre, 1 papier et 1 poche d'encre** dans une grille de fabrication,
dans n'importe quel ordre, pour obtenir un registre. La recette se débloque dans
le livre de recettes lorsque vous récupérez un livre.

## État du MVP

- dépendance obligatoire MineColonies ;
- détection de la colonie et contrôle `IPermissions.isColonyMember` côté serveur ;
- lecture des requêtes `Stack` ouvertes des citoyens affectés aux Builder Huts ;
- agrégation exacte par item et Data Components via `ItemStorage` ;
- comptage des racks des Builder Huts et Warehouses avec `InventoryUtils` ;
- cache serveur de cinq secondes et rafraîchissement client de cinq secondes ;
- paquet réseau minimal : aucun objet colonie ou inventaire brut n'est envoyé ;
- écran avec recherche et vues « Toutes », « À fournir » et « Par builder » ;
- traductions française et anglaise.

La quantité affichée suit actuellement :

```text
à fournir = max(0, besoin - racks builders - racks warehouses)
```

## Choix de fiabilité

`IRequest#getDeliveries()` ne signifie pas nécessairement « dans l'inventaire
d'un courier ». MineColonies remplit cette liste pendant la résolution du
warehouse, avant que le retrait physique ait forcément eu lieu. La déduire en
plus du stock warehouse créerait précisément le double comptage que ce mod doit
éviter. Le MVP affiche donc le transit à zéro jusqu'à ce que la requête enfant
`Delivery`, son état et l'inventaire du courier soient corrélés.

De même, la production automatique, les réservations et le chantier cible ne
sont pas devinés. L'onglet « Par builder » groupe pour l'instant par Builder Hut.

## API MineColonies vérifiée

L'intégration est isolée dans
`integration/minecolonies/MineColoniesGateway.java`. Elle utilise les surfaces
publiques suivantes de la branche `version/1.21` :

- `IMinecoloniesAPI#getColonyManager()` ;
- `IColonyManager#getColonies(Level)` ;
- `IColony#getServerBuildingManager()` et `IRegisteredStructureManager#getBuildings()` ;
- `IBuilding#getAllAssignedCitizen()` et `getOpenRequests(int)` ;
- `IRequest#getRequest()` ;
- `Stack#getStack()` / `getCount()` ;
- `InventoryUtils#getCountFromBuilding(...)`.

`InventoryUtils` est une classe publique d'API, mais le comptage par scan de
racks chargés reste une intégration plus fragile que le Request System. Ce point
est volontairement concentré dans la gateway.

## Compiler

Java 21 est requis.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.1.12-hotspot'
.\gradlew.bat build
```

L'artefact est généré dans `build/libs/`.

## Étapes suivantes

1. Relier chaque builder au `WorkOrder` actif pour nommer le vrai chantier.
2. Corréler parent `Stack` → enfant `Delivery` → courier pour un transit fiable.
3. Lire les chaînes de crafting publiques et vérifier récursivement ingrédients
   et capacité avant d'afficher « Production possible ».
4. Ajouter sélection multi-colonies et détail cliquable d'une ressource.
