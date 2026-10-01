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
- lecture des matériaux du chantier via `BuildingResourcesModule` des Builder Huts ;
- agrégation exacte par item et Data Components via `ItemStorage` ;
- comptage des racks des Builder Huts et Warehouses avec `InventoryUtils` ;
- cache serveur de cinq secondes et rafraîchissement client de cinq secondes ;
- paquet réseau minimal : aucun objet colonie ou inventaire brut n'est envoyé ;
- écran avec recherche et deux onglets « Besoins builders » et « À fournir » ;
- prévision récursive des ingrédients des recettes apprises et activées des ateliers ;
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

## Les deux onglets

« Besoins builders » conserve les blocs demandés par les chantiers, avec leur
quantité totale et les stocks. Survolez une ligne pour voir les chantiers concernés.

« À fournir » est un **plan d'approvisionnement estimé**, pas une deuxième liste
à additionner à la première. Les blocs manquants qu'un atelier sait fabriquer
sont remplacés par les ingrédients de sa recette. Si ces ingrédients sont eux-mêmes
fabricables, le calcul remonte la chaîne. Sans recette connue et activée, le bloc
d'origine reste à fournir. Exemple : 8 escaliers, avec les recettes 6 planches →
4 escaliers et 1 bûche → 4 planches, demandent 3 bûches si aucun stock n'est disponible.

Le plan utilise d'abord les blocs déjà disponibles, arrondit aux lots de la recette
et réutilise les surplus prévus d'un lot. Il réserve virtuellement les stocks de
blocs finis pour tous les builders avant de les utiliser comme ingrédients.
Les stocks des entrepôts, des racks d'ateliers et de leurs travailleurs sont
affectés une seule fois. « Stock affecté » désigne cette affectation **dans le calcul**,
pas une réservation réelle dans MineColonies. Les intermédiaires déjà en stock
peuvent apparaître en vert ; la quantité rouge indique ce qui reste à apporter.
Survolez une ligne pour voir l'atelier et le produit concernés.

Limites : choix stable du premier atelier et de sa première recette correspondante,
sans garantir que le Request System choisira le même chemin. Recettes multi-sorties
(dont les variantes Domum Ornamentum) prises en compte lorsqu'elles sont apprises.
Pas de promesse de production immédiate : affectation d'un travailleur, outils,
combustible, réservations des autres demandes, délais, capacités et rendements
aléatoires ne sont pas simulés. Les recettes spéciales absentes de la liste des
recettes apprises ne sont pas inventées. Aucune demande ni fabrication n'est lancée.
Les cycles et chaînes trop longues sont interrompus, avec un avertissement
« Prévision partielle » et le besoin restant affiché comme bloc à fournir.

## API MineColonies vérifiée

L'intégration est isolée dans
`integration/minecolonies/MineColoniesGateway.java`. Elle utilise les surfaces
publiques suivantes de la branche `version/1.21` :

- `IMinecoloniesAPI#getColonyManager()` ;
- `IColonyManager#getColonies(Level)` ;
- `IColony#getServerBuildingManager()` et `IRegisteredStructureManager#getBuildings()` ;
- `IBuilding#getAllAssignedCitizen()` et `getModules(ICraftingBuildingModule.class)` ;
- `BuildingResourcesModule#getNeededResources()` et `BuildingBuilderResource#getAmount()` ;
- `ICraftingBuildingModule#getRecipes()` / `isDisabled(...)` ;
- `IRecipeStorage#getCleanedInput()` / `getPrimaryOutput()` / `getClassicForMultiOutput(...)` ;
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

`build` exécute aussi les tests du plan d'approvisionnement et du paquet réseau.
Pour les lancer seuls :

```powershell
.\gradlew.bat verifySupplyPlanner verifyLedgerPayload
```

Le protocole réseau est en version 2 : mettre à jour l'addon côté client et serveur.

## Étapes suivantes

1. Relier chaque builder au `WorkOrder` actif pour nommer le vrai chantier.
2. Corréler parent `Stack` → enfant `Delivery` → courier pour un transit fiable.
3. Corréler la prévision d'ingrédients avec les réservations et la capacité réelle
   des ateliers avant d'afficher « Production possible maintenant ».
4. Ajouter sélection multi-colonies et détail cliquable d'une ressource.
