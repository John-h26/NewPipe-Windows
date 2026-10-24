# NewPipe Windows — état et corrections à faire pour la v0.3

Reprise de la discussion Cowork « NewPipe Windows » (bêta v7.11).

## Base technique retenue
- Kotlin Multiplatform + Compose Multiplatform Desktop, NewPipeExtractor (Java, réutilisé tel quel), Koin, Ktor, packaging jpackage/WiX (MSI).
- Upstream `TeamNewPipe/NewPipe` (branche `dev`) contient déjà `:desktopApp` / `:shared` ciblant Windows (TargetFormat.Msi).
- Lecteur : VLCJ (rendu par callback vers `ImageBitmap` Compose).
- Licence : GPL-3 obligatoire.

## 1. Corrections critiques (lecture vidéo)
1. **Gel à 3 s** — diagnostic GC faux (le gel existait dès la v7.6). Hypothèses : User-Agent VLC ≠ client YouTube de l'extracteur ; bridage des téléchargements d'un seul bloc (Android télécharge par plages Range).
   - Test : ouvrir une URL `video=` du log dans VLC normal (Média → Ouvrir un flux réseau). Blocage ~3 s ou 403 ⇒ cause confirmée.
   - Correctif : log VLC détaillé ; `prepare(videoUrl, ":http-user-agent=$ua", ":http-referrer=https://www.youtube.com/")`, ajout de l'audio en slave **avant** `play()` (supprime la course des 500 ms). `$ua` à reprendre de l'extracteur.
2. **Écran noir** — `BufferedImage.TYPE_INT_ARGB_PRE` → `TYPE_INT_RGB` (alpha à 0 rempli par VLC).
3. **Raccourcis clavier actifs pendant la saisie** — `state.typing` alimenté par `onFocusChanged` du champ de recherche ; `onPreviewKeyEvent` retourne `false` si `typing`.
4. **Titres en anglais** — explication « langue de la chaîne » fausse : la recherche ne transmet pas la localisation. Passer `Localization("fr","FR")` explicitement à la recherche ; langue en réglage.

## 2. Bugs fonctionnels
| # | Problème | Correction |
|---|---|---|
| 2.1 | Lecture déclenchée sur l'index de la file | Clé sur `currentItem?.id` |
| 2.2 | Progression téléchargement bloquée à 0 % | `mutableFloatStateOf` / `mutableStateOf` dans `DownloadJob` |
| 2.3 | Résultats perdus en changeant d'onglet | `query`/`results` dans `AppState` |
| 2.4 | Description en HTML brut | `Description.HTML` → Jsoup |
| 2.5 | Date brute ISO | `uploadDate?.offsetDateTime()` formaté FR |
| 2.6 | `StreamInfo.getInfo()` appelé deux fois | Un seul appel partagé |
| 2.7 | Réglages décodage matériel / PeerTube non appliqués, volume non sauvegardé | Brancher ou retirer |
| 2.8 | HTTP 429 non converti | `ReCaptchaException` dans `DesktopDownloader` |
| 2.9 | Cache vignettes illimité, écrasement de fichiers | Cache LRU, nom unique |
| 2.10 | Erreurs affichées 3 fois, `streamLabel` contient l'erreur | Nettoyer |

## 3. Build et livraison
- Figer NewPipeExtractor sur un commit (pas `dev-SNAPSHOT` via JitPack).
- Synchroniser README / workflow CI (JDK 17, version extracteur) ; vérifier WiX 3 sur `windows-latest`.
- MSI autonome : VLC non embarqué (documenter ou empaqueter) ; modules Java via `suggestRuntimeModules` → `nativeDistributions { modules(...) }`.
- Ajouter `distributionSha256Sum` au wrapper Gradle.
- Retirer `sun.java2d.noddraw` et `compose.interop.blending` (restes SwingPanel).
- Sortir le projet de OneDrive.

## 4. Documents du projet
- 01 et 03 : supprimer l'option « Media3 KMP » (inexistante).
- 03 : JavaFX Media repose sur GStreamer, pas FFmpeg ; réévaluer le poids de l'interop Compose (5 % sous-estimé).
- 02 : chemins Takeout FR **et** EN (`subscriptions/subscriptions.csv`).
- Ajouter `04-etat-beta.md` : choix VLCJ, état bêta, enseignements, reste à faire.

## Ordre proposé
1. Test VLC du § 1.1.
2. v0.3 : corrections 1.1–1.4 et 2.1–2.6, extracteur figé.
3. Correction des documents 01/03 + création du 04.
