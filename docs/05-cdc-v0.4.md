# 05 — Cahier des charges v0.4

Base : v0.3 (commit `dbf775f`, 24/10/2026). Constats issus des premiers essais de la v0.3.

## Périmètre

| # | Évolution | Type | Priorité | Charge estimée |
|---|---|---|---|---|
| E1 | Barres de défilement dans les listes | Correction ergonomique | Haute | 0,5 j |
| E2 | Téléchargement des vidéos en MP4 (YouTube) | Fonction manquante | Haute | 0,5 à 1 j |
| E3 | Plein écran sans bande en haut | Correction | Haute | 0,5 j |
| E4 | Playlists locales | Nouvelle fonction | Moyenne | 2 à 3 j |
| E5 | Import de playlists YouTube existantes (dépend de E4) | Nouvelle fonction | Moyenne | 2 à 2,5 j |
| | **Total** | | | **5,5 à 7,5 j** |

Hors périmètre v0.4 (voir `04-etat-beta.md` § 6) : pages chaîne et playlist en ligne,
abonnements et historique, import Takeout des abonnements et sauvegarde Android, sous-titres.

---

## E1 — Barres de défilement

**Constat.** Aucune barre de défilement n'est visible : on ne peut descendre dans les
résultats qu'à la molette. Compose Desktop n'en affiche pas par défaut et aucun écran de la
v0.3 n'en déclare (`LazyColumn` et `verticalScroll` seuls).

**Exigences.**
- Barre verticale (`VerticalScrollbar`) sur chaque zone qui défile :
  - résultats de recherche ;
  - colonne « À suivre » et zone des détails de l'écran Lecture ;
  - file de lecture ;
  - téléchargements ;
  - réglages et À propos ;
  - listes de playlists (E4).
- Barre déplaçable à la souris, clic dans la gouttière pour sauter d'une page.
- Couleurs du thème (clair, sombre, noir), largeur suffisante pour être saisie facilement.
- Un composant commun réutilisé par tous les écrans.

**Critères d'acceptation.**
- Sur une recherche de 60 résultats ou plus, la barre apparaît, se déplace à la souris et suit
  le chargement des pages suivantes.
- Aucune barre quand le contenu tient dans l'écran.

## E2 — Téléchargement des vidéos en MP4

**Constat.** Pour YouTube, le menu Télécharger ne propose que l'audio. YouTube ne fournit plus de
flux vidéo avec son intégré (« 0 muxés » sur toutes les vidéos testées). L'image et le son sont
séparés et doivent être assemblés.

**Exigences.**
- Le menu propose les vidéos MP4 (H.264) par résolution disponible, avec une taille estimée
  (vidéo + audio), par exemple « Vidéo 1080p MP4 · 245 Mo ».
- Téléchargement de la vidéo seule et de l'audio M4A dans des fichiers temporaires, avec le
  lecteur googlevideo existant (POST, `range`, blocs de 2 Mo).
- Assemblage en un seul MP4 **sans réencodage**, en reprenant `Mp4FromDashWriter` et
  `Mp4DashReader` de l'app Android (Java pur, GPL-3.0, seule dépendance : l'interface
  `SharpStream`, à adapter aux fichiers).
- Progression en deux phases affichée : « Téléchargement (vidéo / audio) » puis « Assemblage ».
- Annulation possible à tout moment ; fichiers temporaires supprimés en cas d'échec ou
  d'annulation.
- Les flux muxés (PeerTube) et les pistes audio restent proposés comme en v0.3.
- Hors périmètre : WebM/VP9 et AV1 (souvent au-delà de 1080p), qui demandent un autre
  assembleur.

**Critères d'acceptation.**
- Une vidéo YouTube de 10 min en 1080p donne un MP4 qui se lit avec image et son synchronisés
  dans VLC et dans le lecteur Windows, sans réencodage.
- Un fichier existant n'est jamais écrasé (règle « titre (2).mp4 » de la v0.3).
- L'autotest couvre le téléchargement et l'assemblage d'une vidéo courte.

## E3 — Plein écran sans bande en haut

**Constat.** En plein écran, une bande reste visible en haut de l'écran.

**Hypothèse à confirmer avant correction.** La fenêtre est décorée (barre de titre Windows) et
le plein écran passe par `WindowPlacement.Fullscreen`. Sur Windows, la barre de titre ou la
bordure de la fenêtre peut rester visible. Une capture de la bande permettra de trancher.

**Exigences.**
- En plein écran, la vidéo occupe tout l'écran : ni barre de titre, ni bordure, ni barre des
  tâches, ni rail de navigation.
- Les commandes du lecteur s'affichent en surimpression au bas de la vidéo. Elles se masquent
  après 3 s sans mouvement de souris, tout comme le curseur, et réapparaissent au moindre
  mouvement.
- Entrée par `F`, double-clic ou bouton ; sortie par `Échap`, `F`, double-clic ou bouton.
- En sortie, la fenêtre retrouve sa taille et sa position précédentes.
- Fonctionne aussi sur un second écran : la vidéo passe en plein écran sur l'écran où se trouve
  la fenêtre.

**Critères d'acceptation.**
- Capture d'écran en plein écran : aucune ligne de pixels hors vidéo et bandes noires.
- Trois allers-retours plein écran sans décalage de la fenêtre.

## E4 — Playlists locales

**Besoin.** Créer et conserver des listes de vidéos sur le poste, indépendamment d'un compte en
ligne, comme les playlists locales de NewPipe Android.

**Exigences fonctionnelles.**
- Nouvel écran « Playlists » dans le rail de navigation :
  - créer une playlist ;
  - la renommer ;
  - la supprimer, après confirmation.
- Ajout d'une vidéo à une playlist existante ou nouvelle depuis :
  - les résultats de recherche ;
  - l'écran Lecture (vidéo en cours) ;
  - la colonne « À suivre » ;
  - la file de lecture.
- Dans une playlist :
  - réordonner les vidéos ;
  - retirer une vidéo ;
  - afficher le nombre de vidéos et la durée totale.
- Lecture :
  - « Tout lire » remplace la file de lecture par la playlist ;
  - « Ajouter à la file » l'ajoute à la fin ;
  - un clic sur une vidéo lance la lecture à partir de celle-ci.
- « Enregistrer la file comme playlist » depuis l'écran File.
- Une même vidéo peut figurer dans plusieurs playlists. Les doublons dans une même playlist
  sont signalés.

**Exigences techniques.**
- Stockage par vidéo : URL, service, titre, chaîne, durée, URL de vignette, date d'ajout. Aucun
  appel réseau n'est nécessaire pour afficher une playlist.
- Fichier JSON dans `%APPDATA%\NewPipe Windows\playlists.json`, écrit de façon atomique
  (fichier temporaire puis renommage) pour ne rien perdre en cas d'arrêt brutal.
- Format versionné (champ `version`) pour permettre l'évolution vers une base SQLite quand
  arriveront les abonnements et l'historique.
- Export et import d'une playlist en JSON. Compatibilité avec les sauvegardes NewPipe Android :
  hors périmètre v0.4, mais le modèle de données doit la permettre.
- Les vidéos devenues indisponibles restent dans la playlist et sont signalées à la lecture,
  sans bloquer la suite.

**Critères d'acceptation.**
- Les playlists survivent au redémarrage de l'application et à une mise à jour par le MSI.
- 500 vidéos dans une playlist : affichage fluide, avec barre de défilement (E1).
- Fichier JSON corrompu : l'application démarre, signale l'erreur et conserve une copie du
  fichier.

## E5 — Import de playlists YouTube existantes

**Besoin.** Récupérer dans les playlists locales (E4) des playlists déjà créées sur YouTube.

**Contrainte.** NewPipe fonctionne sans compte Google : l'application ne peut lire que les
playlists accessibles sans connexion. Il faut donc deux voies d'import.

### Voie A — par lien (playlists publiques ou non répertoriées)

- Bouton « Importer depuis YouTube » dans l'écran Playlists. On colle l'URL de la playlist
  (`youtube.com/playlist?list=…`) ou l'URL d'une vidéo qui contient `list=`.
- Lecture par l'extracteur (`PlaylistInfo`), avec toutes les pages de la playlist (pagination
  `getMoreItems`), quelle que soit sa longueur.
- Aperçu avant import : titre, chaîne propriétaire, nombre de vidéos, vignette. Choix entre
  créer une nouvelle playlist locale (nom proposé : celui de YouTube) et ajouter à une
  playlist existante.
- Import de plusieurs liens à la suite (un par ligne).
- Option « Mettre à jour depuis YouTube » sur une playlist importée par lien : ajoute les
  nouvelles vidéos sans toucher à l'ordre ni aux ajouts manuels. L'URL d'origine est conservée.

### Voie B — par Google Takeout (playlists privées, « À regarder plus tard », vidéos aimées)

- Ces listes ne sont pas lisibles sans compte. L'utilisateur exporte ses données YouTube avec
  Google Takeout, puis sélectionne l'archive `.zip` ou le dossier extrait.
- Détection des fichiers CSV de playlists dans l'archive, **par leur en-tête et non par leur
  chemin** : les noms de dossiers dépendent de la langue du compte (même règle que pour les
  abonnements, voir doc 02). Le format exact est à relever sur un export réel avant de coder.
- Liste des playlists trouvées, avec cases à cocher pour choisir celles à importer.
- Takeout ne fournit que les identifiants des vidéos : titres, chaînes, durées et vignettes
  sont récupérés par l'extracteur, en tâche de fond. Les requêtes sont limitées en parallèle
  pour éviter les blocages 429 / captcha, et une progression est affichée. La playlist est
  utilisable avant la fin de la récupération.

### Règles communes

- Vidéos privées, supprimées ou indisponibles : importées avec la mention « indisponible »
  et comptées dans le rapport d'import. Elles ne bloquent pas l'import.
- Doublons dans une même playlist : ignorés et comptés.
- Rapport de fin d'import : vidéos importées, indisponibles, doublons ignorés.
- Import annulable. En cas d'annulation, ce qui est déjà importé est conservé.
- Les playlists importées sont des playlists locales ordinaires (E4) : aucune modification
  n'est jamais envoyée à YouTube.

**Critères d'acceptation.**
- Une playlist publique de 300 vidéos ou plus est importée en entier, dans le même ordre que
  sur YouTube.
- Une playlist non répertoriée est importée par son lien.
- Un export Takeout réel (compte en français) : toutes les playlists du compte sont détectées,
  « À regarder plus tard » comprise si Takeout la fournit.
- Une erreur réseau ou un captcha en cours d'import n'efface rien : l'import peut être repris.

---

## Livraison et validation

- Version `0.4.0` (`gradle.properties`), MSI construit par la CI.
- Autotest étendu : téléchargement MP4 assemblé (E2) ; création, rechargement et lecture d'une
  playlist (E4) ; import d'une playlist publique par lien (E5).
- Captures `--snapshot` mises à jour, dont l'écran Playlists et le plein écran.
- Pour E1 et E3 : validation manuelle sur le poste (souris, clavier, double écran), en plus des
  captures.
- Mise à jour de `04-etat-beta.md` (état, limites) et du README.
