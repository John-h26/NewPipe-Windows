# Corrections à reporter dans les documents 01, 02 et 03 du projet Cowork

Ces documents sont dans le projet Cowork « NewPipe Windows », pas dans ce dossier.

## 01 — Architecture

- Supprimer l'option C « Media3 KMP (JetBrains, Apache-2.0) » : cette bibliothèque n'existe pas.
- Remplacer « le module `:desktopApp` cible déjà Windows » par : `:desktopApp` (commit
  `acb7dfd6`) ne contient qu'un point d'entrée vers les écrans Réglages et À propos de `:shared` ;
  `:shared` dépend du plugin Android et exige le SDK Android pour compiler.
- Ajouter les choix retenus : VLC via vlcj 4.9.0 avec rendu par callback dans Compose, proxy
  local googlevideo, extracteur figé sur le commit `e8f89145`.
- JDK : 21 partout (pas 17).

## 02 — Parité fonctionnelle

- Takeout : le document ne donne que les chemins français. En anglais, le fichier est
  `subscriptions/subscriptions.csv`. Plutôt que de coder les chemins des deux langues (non
  vérifiés ici pour le français), détecter le fichier par son en-tête CSV (identifiant de
  chaîne, URL, titre) dans toute l'archive.
- Mettre à jour les statuts avec le tableau « Reste à faire » de `04-etat-beta.md`.

## 03 — Choix du lecteur

- « FFmpeg utilisé par les deux » est faux : JavaFX Media repose sur GStreamer ; VLC embarque
  ses propres décodeurs (dont libavcodec).
- Le poids de 5 % donné à l'interop Compose était sous-estimé : c'est ce qui a coûté le plus
  d'itérations (SwingPanel, écran noir). Le rendu par callback dans un `Canvas` Compose règle
  le problème.
- Ajouter la contrainte découverte : VLC ne sait pas faire les requêtes POST + `range` que
  YouTube exige ; un proxy local est nécessaire quel que soit le lecteur hors ExoPlayer.
