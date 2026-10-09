# Jours écoulés

Widget Android qui affiche le nombre de jours écoulés depuis une date (ou restants avant une date future).

- Chaque widget a sa propre date et un nom facultatif ; on peut en poser plusieurs.
- Toucher le widget rouvre l'écran de réglage pour changer la date.
- Mise à jour automatique juste après minuit.
- Sur Android 12+, les couleurs suivent le fond d'écran (Material You).
- Android 8.0 minimum.

## Obtenir l'APK

### Option 1 — GitHub (sans rien installer)
1. Crée un dépôt GitHub et envoie-y le contenu de ce dossier.
2. Onglet **Actions** → le workflow « Compiler l'APK » se lance tout seul (~3 min).
3. Ouvre l'exécution terminée, télécharge l'artefact **jours-ecoules-apk** (un zip contenant `app-debug.apk`).

### Option 2 — Android Studio
Ouvre le dossier dans Android Studio, puis **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
L'APK se trouve dans `app/build/outputs/apk/debug/`.

## Installer
Copie `app-debug.apk` sur le téléphone, ouvre-le et autorise l'installation depuis cette source.
Ensuite : appui long sur l'écran d'accueil → Widgets → « Jours écoulés ».
