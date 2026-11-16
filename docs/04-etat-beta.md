# 04 — État de la bêta (v0.3, 24/10/2026)

## 1. Ce qui a changé par rapport à la v7.11

La v7.11 n'existe plus ; la v0.3 a été reconstruite à partir de l'upstream
`TeamNewPipe/NewPipe`, branche `dev`, commit `acb7dfd6` (23/10/2026).

**Pourquoi pas un simple fork du module `:desktopApp` upstream.** Ce module ne contient
qu'un `Main.kt` qui affiche les écrans *Réglages* et *À propos* du module `:shared` : ni
extracteur, ni recherche, ni lecteur. Et `:shared` utilise le plugin Android
(`com.android.kotlin.multiplatform.library`), donc compiler `:desktopApp` exige le SDK Android.
La v0.3 est un projet Gradle autonome qui reprend de l'upstream : les versions (Kotlin,
Compose, commit de l'extracteur), le wrapper Gradle vérifié, le thème, le `DownloaderImpl`
(porté) et la logique de lecture YouTube de `YoutubeHttpDataSource`.

## 2. Décisions

| Sujet | Choix | Raison |
|---|---|---|
| Lecteur | VLC 3.0.24 via vlcj 4.9.0, rendu par callback dans un `Canvas` Compose | ExoPlayer est Android seulement ; pas d'interop Swing |
| Accès googlevideo | Proxy HTTP local (127.0.0.1) entre VLC et YouTube | VLC ne fait que des GET ; YouTube attend des POST (voir § 3) |
| Rendu des images | 2 `Bitmap` Skia réutilisés, alpha ignoré, 30 images/s max vers Compose | Aucune allocation Java par image ; plus d'image noire |
| Injection | Manuelle (`AppGraph`) | Koin n'apporte rien à cette taille ; à reprendre si le code rejoint `:shared` |
| Vignettes | Coil 3 (comme l'upstream) | Caches mémoire et disque bornés |
| material3 | 1.12.0-alpha03 au lieu de 1.11.0-alpha07 (upstream) | Voir § 4, bug n° 1 |
| Runtime embarqué | Modules de `suggestRuntimeModules` + `java.scripting` + `jdk.localedata` | Sans `jdk.localedata`, dates et nombres en anglais |

## 3. Corrections de la revue appliquées

Chaque point a été vérifié par exécution (`--selftest`, `--snapshot`, exe packagé), sauf mention.

| # | Problème | Correction | Vérification |
|---|---|---|---|
| 1.1 | Gel à 3 s | Cause réelle trouvée dans le code Android : `YoutubeHttpDataSource` lit googlevideo en **POST**, avec `&range=` dans l'URL, un compteur `&rn=` et le User-Agent visionOS (`getVisionOsUserAgent`). Le proxy local le reproduit, par blocs de 2 Mo. L'audio est attaché (`slaves().add`) avant `play()`. | 1080p60 lu en continu ; saut à mi-vidéo OK (YouTube, PeerTube, SoundCloud) |
| 1.2 | Écran noir (alpha) | `ColorAlphaType.OPAQUE` sur un `Bitmap` Skia BGRA (équivalent de `TYPE_INT_RGB`) | Image visible sur la capture `2-lecture.png` |
| 1.3 | Raccourcis pendant la saisie | `AppState.typing` alimenté par tous les champs (`AppTextField`), remis à faux quand le champ disparaît | Revue de code ; pas de test clavier automatisé |
| 1.4 | Titres en anglais | `forceLocalization` / `forceContentCountry` sur chaque extracteur + `NewPipe.setupLocalization` ; langue et pays en réglage | Titres et « il y a 2 semaines » en français |
| 2.1 | Lecture relancée par l'index | `LaunchedEffect(queue.currentId)` ; retrait d'un élément précédent sans effet | Revue de code |
| 2.2 | Progression à 0 % | `mutableFloatStateOf` dans `DownloadJob` | Capture `4-telechargements.png` |
| 2.3 | Résultats perdus | `query`, `results`, `nextPage` dans `AppState` | Revue de code |
| 2.4 | HTML brut | `Description.Type.HTML` → Jsoup avec sauts de ligne, URL cliquables | Description propre sur la capture |
| 2.5 | Date ISO brute | Date longue localisée ; dates ISO PeerTube dans les listes aussi | « 5 octobre 2026 », « 6 mars 2018 » |
| 2.6 | `StreamInfo` appelé 2 fois | Un appel, partagé détails + lecture | Revue de code |
| 2.7 | Réglages sans effet | Décodage matériel (`--avcodec-hw`), PeerTube (`setInstance`, vérifiée), volume mémorisé | PeerTube testé ; décodage matériel non testé |
| 2.8 | 429 non converti | `ReCaptchaException` dans `DesktopDownloader` + message dédié | Revue de code |
| 2.9 | Cache illimité, écrasement | Coil (caches bornés) ; « titre (2).m4a » | 2ᵉ téléchargement nommé « (2) » |
| 2.10 | Erreur affichée 3 fois | Un seul bloc d'état sous la vidéo ; `streamLabel` ne contient plus d'erreur | Revue de code |
| 3 | Build | Extracteur figé sur commit ; `distributionSha256Sum` ; JDK 21 partout ; flags SwingPanel retirés ; VLC embarqué dans le MSI ; WiX téléchargé par le plugin ; projet hors OneDrive | MSI construit, exe packagé testé |

## 4. Bugs trouvés pendant la reconstruction

1. **Plantage de tous les champs texte** (`AbstractMethodError` sur `CustomStyle.applyStyle`) :
   material3 1.11.0-alpha07, version de l'upstream, est compilé contre foundation 1.11, mais
   Compose 1.12.2 charge foundation 1.12.2. Corrigé avec material3 1.12.0-alpha03.
   **À signaler à l'upstream** : leur `:desktopApp` a la même combinaison.
2. **`SearchInfo.getInfo(extractor)` ne charge pas la page** (contrairement à `StreamInfo`) :
   il faut appeler `fetchPage()` avant, sinon zéro résultat.
3. **PeerTube** : les MP4 « fragmentés » ne permettent pas de se déplacer dans VLC → le choix
   des flux préfère la playlist HLS à résolution égale. VLC n'y trouve pas la durée : celle du
   service est utilisée.
4. **SoundCloud** ne fournit plus que de l'audio HLS → accepté quand il n'y a pas de vidéo.
5. **Hauteur 1088** : VLC aligne le tampon (1920x1088 pour du 1080p) ; seules les lignes utiles
   sont dessinées.

## 5. Limites connues

- **Compteurs abrégés de YouTube faux hors anglais** : l'extracteur renvoie 135 pour
  « 135 k abonnés » avec la localisation fr-FR (`Utils.mixedNumberWordToLong` ne reconnaît que
  « 135K »). Même défaut pour les vues des onglets de chaîne. Bug de NewPipeExtractor, à
  signaler. En attendant, ces compteurs sont masqués pour YouTube quand la langue n'est pas
  l'anglais (`Formatting.abbreviatedCount`).
- **Gel intermittent au démarrage de la lecture YouTube** (16/11/2026) : 2 autotests sur une
  vingtaine sont restés en BUFFERING pendant 20 s (VLC : « Stream buffering done (3966 ms in
  19458 ms) »). Non reproduit ensuite, cause non identifiée.
- Certains titres SoundCloud n'ont aucun flux (`StreamExtractException`) : limite du service.
- Messages `h264 … get_buffer() failed` à l'arrêt de la lecture : sans conséquence.
- MSI non signé : SmartScreen avertira à l'installation.
- Le MSI n'a pas été installé sur ce poste ; seul l'exécutable de la distribution a été testé.
  Son premier lancement en autotest a renvoyé le code 1 sans laisser de journal (le journal
  fichier n'existait pas encore) ; les deux lancements suivants ont réussi. Cause non identifiée.
- L'interface a été vérifiée par captures hors écran, pas en manipulation réelle à la souris et
  au clavier.

## 6. Reste à faire pour la parité avec Android

| Fonction | État v0.3 | Charge estimée |
|---|---|---|
| Pages chaîne | Faites le 16/11/2026 (`ChannelScreen`) ; les playlists d'une chaîne s'ouvrent encore dans le navigateur | — |
| Pages playlist | Ouverture dans le navigateur | 1–2 j |
| Téléchargement vidéo HD (DASH vidéo + audio) | Absent : demande un muxeur (VLC `sout` ou portage de `Mp4FromDashWriter`) | 2–3 j |
| Abonnements, flux, historique (base locale) | Absent | 5–8 j |
| Import Takeout et sauvegarde NewPipe Android | Absent (chemins FR **et** EN, voir doc 02) | 2–3 j |
| Sous-titres, choix de qualité et de piste audio pendant la lecture | Absent | 2 j |
| Lecture en arrière-plan / mini-lecteur | Absent | 2–3 j |
| Commentaires, chapitres, SponsorBlock-like | Absent | 3–5 j |
| Tendances / page d'accueil | Absent | 1–2 j |
| Traductions (strings Compose de l'upstream) | Interface en français codé en dur | 2 j |
| Retour vers l'upstream (`:shared` jvmMain) | Non commencé | à cadrer avec l'équipe NewPipe |

## 7. Enseignements

- Lire le code Android avant de diagnostiquer : la cause du gel était documentée dans
  `YoutubeHttpDataSource`.
- Vérifier chaque API dans les sources ou les jars (`javap`), pas de mémoire. Fait ici pour vlcj
  4.9.0, Skiko 0.150.2 et l'extracteur : le code a compilé du premier coup.
- Tester sans interface (`--selftest`, `--snapshot`) et l'exécutable packagé : c'est ce qui a
  révélé le plantage material3 et l'absence de `jdk.localedata`.
