# Classification de l'activité des colons

## Problème et source vérifiée

Version inspectée directement dans le JAR de sources installé : MineColonies
`1.1.1403-1.21.1-snapshot`, Minecraft 1.21.1 / NeoForge 21.1.80.

`ICitizenData.getJobStatus()` n'est pas un journal d'activité fiable à lui seul.
`EntityAIWorkDeliveryman` n'actualise pas systématiquement ce statut, et ses
indicateurs `setWorking(true)` / `VisibleCitizenStatus.WORKING` sont aussi posés
pendant l'attente d'une livraison. Le builder utilise sa machine à états et son
ordre de chantier sans toujours changer le `JobStatus` initial. Recopier ce champ
faussait donc les pourcentages.

## Lecture des signaux

`JobActivityAdapter` concentre l'intégration MineColonies ;
`JobActivityClassifier` applique la décision sans dépendre de classes du jeu.
Le même adaptateur sert aux échantillons enregistrés et au statut actuel affiché.

API publiques utilisées :

- `ICitizenData.getJob()`, `getJobStatus()`, `getWorkBuilding()`, `getEntity()`,
  `isPaused()`, `isAsleep()`, `getStatus()` ;
- `IJob.getWorkerAI()` et `ITickingStateAI.getState()` ;
- `AIWorkerState` et `CitizenAIState` ;
- `IBuilding.hasOpenSyncRequest(citizen)` et `hasCitizenCompletedRequests(citizen)`.

Dépendances **internes** à cette version, isolées dans l'adaptateur :

- `EntityCitizen.getCitizenAI().getState()` : état global indiquant si l'IA de
  travail est réellement exécutée. Un état de travail conservé pendant la nuit
  n'est pas une preuve d'activité ;
- `JobBuilder` et `AbstractBuildingStructureBuilder.getWorkOrder()` : un chantier
  affecté, pas simplement un bâtiment de builder construit ;
- `JobDeliveryman.getTaskQueue()` : copie en lecture seule des tâches affectées.

**Ne jamais appeler `JobDeliveryman.getCurrentTask()` pour observer l'activité.**
Malgré son nom, cette méthode sélectionne et retire des demandes de la file de
l'entrepôt, affecte des tâches et marque le module modifié. Ouvrir le panneau ou
échantillonner l'activité ne doit déclencher aucune livraison, affectation ni tick
d'IA. Aucun setter natif ni `getCurrentTask()` n'est appelé ici.

## Règles

- Sans métier, citoyen en pause/endormi ou IA globale hors travail : `IDLE`.
  L'initialisation de l'IA et les états de pause/garde endormi sont aussi inactifs.
- Builder sans ordre de chantier : `IDLE`, y compris s'il boucle sur `START_WORKING`.
- `NEEDS_ITEM` avec une demande synchrone encore ouverte, sans demande terminée
  à récupérer : `STUCK` ; une demande terminée en cours de récupération : `WORKING`.
- Livreurs dans les étapes `PREPARE_DELIVERY`, `DELIVERY`, `PICKUP`, `DUMPING` :
  activité, y compris le déchargement après suppression de la tâche de la file.
- Livreur en attente/sélection sans tâche affectée : `IDLE`. Avoir un entrepôt,
  une destination de promenade ou un indicateur natif « travaille » ne suffit pas.
- Builder avec ordre de chantier et livreur avec tâche affectée : préparation,
  trajet et exécution comptent comme activité. Le passage transitoire par `IDLE`
  de leur machine à états ne doit pas masquer cette tâche connue.
- Hors interruption/inactivité confirmée, le statut natif `STUCK` est conservé.
- Les autres métiers dont l'IA exécute une étape de travail sont actifs ;
  `IDLE`, `WANDER`, `RECESS`, `LUMBERJACK_NO_TREES_FOUND` sont inactifs.
  Les états inconnus/personnalisés conservent le statut natif en repli.

Les limites sont volontaires : ce n'est pas une détection universelle de pathfinding
bloqué, ni une mesure de blocs posés ou d'objets livrés. Un métier peut rester dans
une étape de travail sans progresser physiquement. Une précision par motif
(sommeil, repas, trajet, manque d'outil...) demanderait des catégories supplémentaires.

## Historique et compatibilité

Échantillonnage inchangé : 20 ticks, jours Minecraft de 24 000 ticks, colons
chargés et colonies actives. Les trois états et leur ordre sont conservés ; aucun
format de paquet ou de sauvegarde ne change. Les données passées sont préservées
mais ne peuvent pas être reclassées : les signaux d'IA de l'époque n'ont pas été
enregistrés. Les pourcentages des longues périodes / Total garderont donc les
anciennes observations. Les nouvelles observations utilisent la correction.

`verifyJobActivity` teste la décision et la correspondance avec les véritables
constantes de l'API installée. Les tests historiques, codecs et calculs de ressources
restent obligatoires dans `test`, `check` et `build`.

Test en jeu à effectuer après redémarrage / mise à jour de l'addon côté serveur :
builder avec chantier puis sans chantier ; livreur en livraison puis sans tâche ;
manque de matériel puis récupération ; pause et sommeil. Le statut actuel change
immédiatement à la prochaine actualisation du panneau ; les pourcentages se mettent
à jour progressivement selon la période sélectionnée.
