# 05 — Cahier des charges v0.4

Base : v0.3 (commit `dbf775f`, 24/10/2026). Constats issus des premiers essais de la v0.3.

## Périmètre

| # | Évolution | Type | Priorité | Charge estimée |
|---|---|---|---|---|
| E1 | Barres de défilement dans les listes | Correction ergonomique | Haute | 0,5 j |
| E2 | Téléchargement des vidéos en MP4 (YouTube) | Fonction manquante | Haute | 0,5 à 1 j |
| E3 | Plein écran sans bande en haut | Correction | Haute | 0,5 j |
| E4 | Playlists locales | Nouvelle fonction | Moyenne | 2 à 3 j |
| | **Total** | | | **3,5 à 5 j** |

Hors périmètre v0.4 (voir `04-etat-beta.md` § 6) : pages chaîne et playlist en ligne,
abonnements et historique, import Takeout et sauvegarde Android, sous-titres.

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

---

## Livraison et validation

- Version `0.4.0` (`gradle.properties`), MSI construit par la CI.
- Autotest étendu : téléchargement MP4 assemblé (E2) ; création, rechargement et lecture d'une
  playlist (E4).
- Captures `--snapshot` mises à jour, dont l'écran Playlists et le plein écran.
- Pour E1 et E3 : validation manuelle sur le poste (souris, clavier, double écran), en plus des
  captures.
- Mise à jour de `04-etat-beta.md` (état, limites) et du README.
