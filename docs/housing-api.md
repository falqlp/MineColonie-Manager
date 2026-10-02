# Gestion des logements : MVP et API vérifiée

## Version inspectée

Minecraft 1.21.1, NeoForge 21.1.80, MineColonies
`1.1.1403-1.21.1-snapshot`, exactement la dépendance de `gradle.properties`.
Inspection directe de son JAR de sources, avant l'implémentation : aucune méthode
d'affectation ou de capacité n'a été supposée à partir d'une autre version.

## API publique utilisée

| Donnée | Méthode exacte |
|---|---|
| Colonies de la dimension du joueur | `IMinecoloniesAPI.getInstance().getColonyManager().getColonies(player.level())` |
| Citoyens actuels | `IColony.getCitizenManager().getCitizens()` |
| Citoyen par ID | `getCitizenManager().getCivilian(id)` |
| Identité / âge disponible | `ICitizenData.getId()`, `getName()`, `isChild()` ; pas d'âge numérique inventé |
| Logement | `ICitizenData.getHomeBuilding()` |
| Changer la référence du logement | `ICitizenData.setHomeBuilding(IBuilding)` ; **insuffisant seul** pour modifier la liste des occupants |
| Travail | `ICitizenData.getWorkBuilding()` ; peut être null |
| Métier | `getJob().getJobRegistryEntry().getTranslationKey()` ; peut être sans métier |
| Compétences actuelles | `getCitizenSkillHandler().getLevel(Skill)` |
| Bâtiments | `IColony.getServerBuildingManager().getBuildings()` / `getBuilding(BlockPos)` |
| Identifiant / position du bâtiment | `IBuilding.getID()` (BlockPos), `getPosition()` |
| Nom du bâtiment | `IBuilding.getBuildingDisplayName()` |
| Niveau | `getBuildingLevel()`, `getBuildingLevelEquivalent()`, `getMaxBuildingLevel()` |
| Affectation possible | `IBuilding.canAssignCitizens()` |
| Permissions | `IPermissions.isColonyMember(player)` et `hasPermission(player, Action)` |

L'addon prend les positions des blocs de hutte identifiant les bâtiments, **pas**
les lits, entrées ou positions momentanées des citoyens. Pour un courier, le lieu
de travail affiché est celui renvoyé par MineColonies, pas un Warehouse supposé.
`workerLevel` signifie la **plus haute compétence actuelle**, pas le niveau de
la hutte ni un hypothétique niveau unique de métier. Le niveau de la résidence,
son occupation et le plafond de compétences sont affichés séparément.

## API interne explicitement employée

Concentrée dans `ColonyHousingAdapter` et `HousingSessions` :

- `core.colony.buildings.modules.HomeBuildingModule` identifie les résidences
  ordinaires, sans supposer un ancien `BuildingHome` inexistant dans cette version.
- `LivingBuildingModule` porte les occupants et les mutations. Ses méthodes
  `getModuleMax()`, `getAssignedCitizen()`, `hasAssignedCitizen(...)`,
  `removeCitizen(...)`, `assignCitizen(...)` implémentent `IAssignsCitizen`,
  interface publique. Le type concret reste **une dépendance interne fragile**.
- `WorkAtHomeBuildingModule` indique les métiers avec logement obligatoire sur
  place. Ces citoyens et logements sont exclus des échanges ; ils restent visibles.
- `GuardBuildingModule` dérive de ce module mais partage un plafond global entre
  les métiers de garde : on ne somme pas les limites knight/ranger/druid.
  `TavernLivingBuildingModule` (capacité 4 si construit) reste un logement spécial
  visible, exclu des échanges de résidences ordinaires.
- Pour les résidences ordinaires, la capacité est `LivingBuildingModule.getModuleMax()`
  (le niveau dans cette version), pas un nombre de lits arbitrairement scanné.
  Places libres = max(0, capacité - occupants). Un logement non affectable
  n'est pas compté comme place disponible. Les logements spéciaux restent affichés
  comme tels ; leurs places ne sont pas des destinations candidates.
- Avant toute mutation, la liste du module doit correspondre aux citoyens qui
  référencent cette résidence. Une incohérence rend le logement non affectable.

L'implémentation native de référence a été inspectée dans
`core/network/messages/server/colony/building/home/AssignUnassignMessage.java`,
ainsi que `LivingBuildingModule.java`, `AbstractAssignedCitizenModule.java` et
`CitizenData.java`. On retire puis affecte **via le module**, ce qui met à jour
le logement du citoyen, son lit, les drapeaux de synchronisation et le calcul
de capacité native. On n'envoie pas le paquet interne MineColonies depuis le client.

## Niveau de résidence et progression

La mécanique existe dans `CitizenSkillHandler.addXpToSkill(...)` :
si le logement n'est pas à son niveau maximal, ou que son maximum est inférieur
au maximum standard de 5, l'ajout d'XP s'arrête lorsqu'une compétence atteint
`(getBuildingLevelEquivalent() + 1) * 10`. Une résidence standard niveau 2 a
donc un seuil de 30, **pas un worker de niveau 2**. Une résidence standard niveau
5 utilise le maximum global `MAX_CITIZEN_LEVEL` (99).

Le panneau calcule ce seuil selon cette règle native. Il signale une restriction
de progression quand la plus haute compétence a atteint le plafond de logement.
Une compétence déjà élevée n'est pas supprimée par une affectation. Les échanges
n'abaissent jamais le plafond d'aucun participant : c'est une règle conservatrice
de l'addon, pas une interdiction native universelle. Cela limite souvent les
échanges aux maisons de niveaux équivalents. Aucun score /100 n'est présenté
comme une valeur MineColonies ; les scores restent une fonctionnalité future.

## Permissions, confirmation et concurrence

- Consultation : être membre **et** avoir `ACCESS_HUTS`.
- Calcul des échanges et application : avoir aussi `MANAGE_HUTS`, comme
  `AbstractColonyServerMessage.permissionNeeded()` pour l'affectation native.
- `allowManualSwaps=false` conserve les propositions mais interdit leur application.
- Un visiteur, même muni de l'objet, ne reçoit pas les détails et ne réorganise rien.
- Le paquet client d'application contient seulement le UUID d'une proposition
  serveur et son indice, jamais une liste libre de destinations.
- Proposition liée au joueur, à sa dimension et à un instantané ; expire après
  1 200 ticks (une minute à 20 TPS). Le jeton est consommé une seule fois.
- Le serveur relit les deux citoyens, leur travail, les logements, occupants,
  capacités, niveaux, construction et permissions. Tout changement des données
  concernées exige une nouvelle confirmation après actualisation.
- Les deux retraits précèdent les deux affectations : échanger deux maisons pleines
  ne dépasse pas leur capacité. Toutes les opérations s'exécutent dans un même
  traitement sur le thread serveur. Deux joueurs ne peuvent donc pas appliquer
  simultanément une ancienne affectation : le second instantané devient périmé.
- Un échec d'affectation déclenche la restauration des logements d'origine.
  Un échec de restauration est signalé explicitement à l'écran et journalisé.

## Algorithme et performances

MVP : analyse des paires O(n²), pas solveur global / Hungarian.
Les enfants, citoyens sans logement/travail, métiers à logement obligatoire,
logements spéciaux, non construits, incohérents ou surchargés sont exclus.
Une paire n'est proposée que si chaque trajet est réduit ou égal, et si le gain
total atteint le minimum configurable. Les meilleures paires indépendantes sont
retenues : un citoyen n'apparaît jamais dans deux suggestions d'un même instantané.
Les occupations restent constantes, même si plusieurs paires utilisent une maison.

La moyenne utilise uniquement les citoyens avec logement **et** travail. Les
sans-logement et sans-travail restent visibles avec un statut séparé. Le gain
prévisionnel du résumé correspond aux échanges proposés, pas à un optimum global.
La distance euclidienne horizontale X/Z ignore le relief, routes et obstacles ;
aucune estimation de temps de trajet ni appel pathfinding n'est inventé.

Calcul uniquement à l'ouverture, sur « Actualiser » et après un échange. Les
positions, affectations et propositions restent dans un instantané réutilisé
par l'interface. Requêtes complètes limitées à une par seconde par joueur.
Au-delà de `maxAnalysisCitizens`, tous les citoyens restent consultables mais
l'analyse quadratique est désactivée avec un avertissement. Les jetons ne sont
pas persistés sur disque et ne survivent pas au redémarrage.

## Config serveur

`<monde>/serverconfig/colonyresourceledger-server.toml`, section `[housing]` :

```toml
enabled = true
allowManualSwaps = true
goodDistance = 50.0
acceptableDistance = 150.0
farDistance = 300.0
minimumSwapGain = 10.0
maxAnalysisCitizens = 500
maxSuggestions = 20
```

Bornes inclusives : bon <= good, acceptable <= acceptable, loin <= far,
très loin au-delà. Si les seuils sont mal ordonnés, acceptable est relevé à good
et far à acceptable. Ces valeurs ne sont pas codées en dur dans la classification.
Pas de mode automatique ni de cooldown d'optimisation automatique dans ce MVP.

## Interface et test manuel

Fabriquer **livre + boussole + papier**, sans ordre, ou utiliser :

```text
/give @s colonyresourceledger:colony_housing_manager
```

Clic droit près de la colonie. Objet ajouté à l'onglet créatif de l'addon,
avec une icône custom vanilla-like : carnet vert avec emblème de maison,
texture 32 × 32 transparente (voir `docs/item-textures.md`).
Trois onglets : citoyens, résidences, échanges. Recherche nom/métier/maison/travail,
tri nom/métier/distance, filtre de statut. Cliquer une résidence affiche uniquement
ses occupants ; le bouton de filtre permet d'effacer cette sélection.
Survoler une ligne affiche les détails et coordonnées complets. Pagination et
roulette rendent la dernière ligne accessible sans dépasser le panneau opaque.
Les textes n'ont pas d'ombre ; tous les éléments sont dessinés après le flou du monde.
Cliquer une paire affiche ses deux distances avant/après ; « Confirmer l'échange »
est nécessaire pour l'appliquer. « Annuler » retire la sélection.

Vérifications en jeu à réaliser :

1. Deux citoyens logés loin de leur travail dans deux maisons standards de même
   niveau ; vérifier les distances, gain puis affectations après confirmation.
2. Maisons pleines : occupation inchangée après l'échange, aucun citoyen sans logement.
3. Modifier une affectation / détruire une maison entre proposition et confirmation :
   proposition refusée, nouvel instantané affiché.
4. Gardes, enfants, sans-logement, sans-travail et logements niveau 0 : consultation
   possible mais aucune proposition impossible.
5. Joueur membre avec `ACCESS_HUTS` seul : lecture sans propositions. Visiteur :
   aucun détail. Retirer `MANAGE_HUTS` avant confirmation : refus.
6. Deux joueurs confirmant la même paire : un seul échange ; le second reçoit un refus.
7. Mode suggestions uniquement et seuils personnalisés dans la config serveur.
8. Petite fenêtre / échelle GUI élevée : fond net, pagination jusqu'au dernier colon.

## Hors MVP, architecture prête pour la suite

Optimisation globale, déplacements vers places libres, score estimé de l'addon,
recommandations d'amélioration, verrouillages persistants avec boutons, préférences
de quartiers, regroupement des familles, automatisation, carte et historique restent
en phases 2/3. Les champs `housingLocked`, `preferredResidence`,
`excludedFromOptimization` existent et l'analyse respecte les deux premiers,
mais aucun verrouillage manuel persistant n'est encore exposé.
Un échange manuel peut séparer des partenaires : la confirmation le signale.

## Vérification automatisée

`verifyHousingServices` couvre distances, seuils, exclusions, capacités, conservation
du plafond, paires indépendantes, limite de taille et restauration après échec.
`verifyHousingPayload` couvre les trois codecs, statuts, positions nulles et données
vides. Les six suites de l'addon sont reliées à `check`.
Ces tests de service/codecs ne remplacent pas les vérifications en jeu ci-dessus.
