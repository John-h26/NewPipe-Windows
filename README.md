# NewPipe Windows — v0.3

Client libre pour YouTube, PeerTube, SoundCloud, media.ccc.de et Bandcamp sur Windows.
Basé sur [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) et sur le module
`:desktopApp` (Compose Multiplatform) de [NewPipe](https://github.com/TeamNewPipe/NewPipe).
Lecture par VLC (vlcj), rendue directement dans l'interface Compose.

Licence : **GPL-3.0-or-later** (imposée par NewPipe ; tout binaire distribué doit être
accompagné de son code source).

## Fonctions de la v0.3

- Recherche sur les 5 services, avec pagination ; langue et pays envoyés aux services
- Pages chaîne intégrées (onglets vidéos, shorts, directs, playlists… avec pagination), ouvertes
  depuis un résultat de recherche ou le nom de la chaîne d'une vidéo
- Lecture jusqu'en 4K (vidéo + audio séparés pour YouTube), saut, volume mémorisé, plein écran
- File de lecture (ajout, ordre, retrait), enchaînement automatique, colonne « À suivre »
- Téléchargement des vidéos avec son et des pistes audio, progression en direct
- Réglages : langue/pays, résolution max, décodage matériel, instance PeerTube,
  dossier de téléchargement, dossier VLC, thème

Raccourcis (écran Lecture, inactifs pendant une saisie) : `Espace`/`K` lecture-pause,
`←`/`J` et `→`/`L` ±10 s, `M` muet, `F` plein écran, `Échap` quitter le plein écran,
`N` suivante, `P` précédente. Double-clic sur la vidéo : plein écran.

## Installer

Dans PowerShell (sans droits administrateur) :

```powershell
irm https://raw.githubusercontent.com/John-h26/NewPipe-Windows/main/install.ps1 | iex
```

Le script télécharge la dernière version publiée (`NewPipe-Windows-win64.zip`, Java et VLC
inclus), vérifie son empreinte SHA-256, l'installe dans `%LOCALAPPDATA%\Programs\NewPipe Windows`
et crée un raccourci dans le menu Démarrer. Le relancer met à jour l'application.

## Construire et lancer

Prérequis : **JDK 21** (Temurin conseillé). Le projet ne doit pas être dans OneDrive.

```bat
run.bat
```

`run.bat` utilise le JDK portable de `_tools\jdk-21*` s'il existe, sinon `JAVA_HOME`.
Équivalent :

```bat
gradlew.bat :desktopApp:run
```

VLC 3 (64 bits) est cherché dans cet ordre : dossier des réglages, VLC embarqué dans le MSI,
`_tools\vlc-*` (développement), VLC installé.

### Installateur MSI

```bat
gradlew.bat :desktopApp:packageMsi -PvlcDir=_tools/vlc-3.0.24
```

Résultat : `desktopApp/build/compose/binaries/main/msi/NewPipe Windows-0.3.0.msi` (environ
140 Mo, VLC inclus, installation par utilisateur). WiX 3.11 est téléchargé par le plugin Compose.
Sans `-PvlcDir` ni `_tools/vlc-*`, le MSI ne contient pas VLC et il faut un VLC installé.

La CI GitHub (`.github/workflows/build.yml`) construit le MSI à chaque push.

### Vérifications sans interface

```bat
rem Recherche, détails, page chaîne (tous les onglets), 25 s de lecture avec saut au milieu
rem (service 0 = YouTube, 1 = SoundCloud, 3 = PeerTube)
gradlew.bat :desktopApp:run --args="--selftest it-connect 25 0"

rem Captures PNG de chaque écran pendant un scénario réel (dans desktopApp\build\snapshots)
gradlew.bat :desktopApp:run --args="--snapshot it-connect build\snapshots"
```

Journaux : `%TEMP%\npw-app.log` (application) et `%TEMP%\npw-vlc.log` (VLC, détaillé).

## Organisation

```
desktopApp/src/main/kotlin/net/newpipe/app/
  Main.kt, AppGraph.kt      point d'entrée, raccourcis, assemblage et actions
  net/                      Downloader de l'extracteur, lecture googlevideo, proxy local
  extractor/                accès à l'extracteur, choix des flux, mise en forme
  player/                   VLC : localisation, lecteur, rendu des images dans Compose
  queue/, download/         file de lecture, téléchargements
  ui/                       écrans Compose
  theme/                    thème repris de l'upstream
docs/                       état de la bêta, corrections des documents du projet
```

Versions figées (`gradle/libs.versions.toml`) : Kotlin 2.4.21, Compose Multiplatform 1.12.2,
NewPipeExtractor `e8f89145` (même commit que l'upstream), vlcj 4.9.0, Gradle 9.8.2
(wrapper vérifié par `distributionSha256Sum`).

Détails, choix techniques et reste à faire : [docs/04-etat-beta.md](docs/04-etat-beta.md).

NewPipe Windows n'est ni affilié à YouTube ni approuvé par Google.
