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

## Suivi des colons

Le nouvel objet **Suivi des colons** se fabrique avec **1 livre + 1 montre**, sans
ordre imposé. Un clic droit ouvre le panneau de la colonie accessible la plus proche.
Pour tester en créatif : `/give @s colonyresourceledger:colony_job_monitor`.

Chaque ligne affiche le métier actuel, le JobStatus actuel, et le pourcentage de
temps observé dans `IDLE` (inactif), `WORKING` (travaille) et `STUCK` (bloqué).
Une barre colorée représente les mêmes pourcentages. Recherche par nom ou métier,
défilement et boutons de pagination permettent de consulter toute la colonie.
Le résumé en haut est pondéré par les durées observées des colons actuels.

Périodes glissantes : **1, 3, 7 et 30 jours Minecraft**, ou **Total depuis le début
du suivi**. Un jour vaut 24 000 ticks. Le compteur utilise `gameTime`, pas `dayTime` :
dormir pour sauter la nuit ou utiliser `/time` n'ajoute pas de durée artificielle.
Les pauses du serveur et le temps où le monde est fermé ne sont pas comptés.
Les périodes longues ne reconstituent pas des données antérieures à l'installation.

Un échantillon est pris toutes les 20 ticks, côté serveur, lorsque la colonie est
active et que l'entité du colon est chargée. Seuls deux échantillons consécutifs
permettent de compter l'intervalle entre eux. Les trous de chargement et les
redémarrages ne sont donc pas extrapolés. Les pourcentages sont normalisés sur le
temps **réellement observé**, pas sur toute la durée sélectionnée. Une info-bulle
indique ce temps, en jours Minecraft ; sans observation les pourcentages sont « — ».
Les pourcentages arrondis à une décimale totalisent 100,0 %.

L'historique suit le colon même s'il change de métier. JobStatus est l'état natif
MineColonies, pas une mesure de rendement : un colon qui dort peut conserver son
dernier JobStatus, et `STUCK` ne révèle pas la cause du blocage. Des transitions
plus rapides que 20 ticks peuvent être manquées.

Le suivi fonctionne même si le panneau est fermé. Il est sauvegardé par dimension
dans `data/colonyresourceledger_job_history.dat`, avec des identités séparées par
colonie et colon. L'historique glissant est borné à 30 jours (36 000 octets par colon,
plus les compteurs cumulés conservés pour « Total »). Le panneau affiche les colons
actuellement présents, pas les colons décédés ou supprimés. Le client reçoit
uniquement le résumé de la période demandée, jamais l'historique brut.

## Gestion des logements (MVP)

Objet **Gestion des logements** : craft sans ordre **1 livre + 1 boussole + 1 papier**.
En créatif : `/give @s colonyresourceledger:colony_housing_manager`, puis clic droit.

Trois vues : citoyens (métier, domicile, travail, distance, niveau et plafond de
compétences), résidences (occupation et occupants), échanges proposés (avant/après
et gain). Recherche, tri et filtre de statut ; cliquer une résidence filtre ses
occupants. Survoler les lignes pour les détails complets. Un échange nécessite une
sélection puis **Confirmer l'échange** ; aucune réaffectation automatique.

Distances horizontales X/Z en blocs, pas des trajets réels. Lecture réservée aux
membres avec `ACCESS_HUTS`, échanges à `MANAGE_HUTS`. Le serveur revalide chaque
confirmation. Les gardes à logement obligatoire, enfants et logements spéciaux
ne sont pas déplacés ; les capacités et plafonds de progression sont conservés.
Le MVP ne regroupe pas les familles et ne fait pas d'optimisation globale.

Seuils et mode suggestion uniquement configurables dans
`<monde>/serverconfig/colonyresourceledger-server.toml` (`[housing]`).
L'API exacte inspectée, les dépendances internes, règles, limites et scénarios
de test sont documentés dans [docs/housing-api.md](docs/housing-api.md).

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

`build` exécute aussi les tests du plan d'approvisionnement, des historiques et des paquets réseau.
Le groupe de sources `test` reçoit explicitement les dépendances Minecraft,
NeoForge et leurs bibliothèques via `neoForge.addModdingDependenciesTo(sourceSets.test)`.
Elles sont nécessaires à la compilation **et** à l'exécution des tests de codecs
et NBT ; les tests ne sont pas inclus dans le JAR du mod.
Ces suites sont des programmes Java `main`, pas des tests JUnit. La tâche `test`
exécute les six tâches `verify…` et autorise l'absence de tests JUnit découverts
(`failOnNoDiscoveredTests = false`, nécessaire avec Gradle 9). Les erreurs des
vérifications font toujours échouer `test`, `check` et `build`.
Pour les lancer seuls :

```powershell
.\gradlew.bat verifySupplyPlanner verifyLedgerPayload verifyJobHistory verifyJobPayload verifyHousingServices verifyHousingPayload
```

Le protocole réseau est en version 4 : mettre à jour l'addon côté client et serveur.

## Étapes suivantes

1. Relier chaque builder au `WorkOrder` actif pour nommer le vrai chantier.
2. Corréler parent `Stack` → enfant `Delivery` → courier pour un transit fiable.
3. Corréler la prévision d'ingrédients avec les réservations et la capacité réelle
   des ateliers avant d'afficher « Production possible maintenant ».
4. Ajouter sélection multi-colonies et détail cliquable d'une ressource.
