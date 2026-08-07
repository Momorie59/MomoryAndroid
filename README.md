# Momory — Application Android

Application Android native (Kotlin + Jetpack Compose) pour discuter avec ton
assistant IA personnel local, connectée à ton serveur Ollama sur ton réseau —
aucune donnée n'est envoyée à un service tiers.

**Statut : V1, compilée et testée sur appareils réels.**

## Fonctionnalités

- **Chat texte et vocal** — deux interfaces au choix (bouton de bascule rapide) :
  - **Texte** — chat complet avec historique, par défaut.
  - **Vocal** — gros bouton d'écoute, pensé pour une utilisation mains libres.
- **Mode vocal continu** (désactivé par défaut, activable dans les réglages) —
  écoute en arrière-plan et se déclenche en disant le nom de l'assistant, avec
  reconnaissance tolérante aux petites erreurs de transcription.
- **Nom de l'assistant personnalisable** — sert à la fois de mot-clé vocal et de
  prénom utilisé par l'IA pour se présenter.
- **Choix de la voix TTS** et **thèmes de couleurs** (Néon, Violet, Coucher de
  soleil, Océan) dans les réglages.
- **Historique des conversations** — sauvegarde locale, consultable et supprimable.
- **Sélecteur de modèle** — liste les modèles installés sur le serveur Ollama.
- **Recherche de lieux à proximité** ("trouve-moi un restaurant près de moi") —
  utilise la position du téléphone et OpenStreetMap (gratuit, sans clé API) pour
  donner des résultats réels au modèle plutôt que des réponses inventées.
- **Configuration automatique** — entre l'IP du serveur et appuie sur "Auto" pour
  récupérer host/port/modèle depuis le dashboard (comme `momory config --auto`
  côté CLI).
- **Streaming** — la réponse s'affiche au fur et à mesure.

## Prérequis

- [Android Studio](https://developer.android.com/studio) (gratuit)
- Un téléphone Android ≥ 8.0 (API 26) ou un émulateur
- Un serveur Ollama accessible sur le réseau local

## Ouvrir et compiler

1. Ouvre Android Studio → **Open** → sélectionne ce dossier
2. Laisse Gradle synchroniser (première fois : peut prendre plusieurs minutes)
3. Branche ton téléphone (mode développeur + débogage USB activés), ou lance un
   émulateur
4. Clique sur **Run ▶**

Pour un `.apk` installable directement (debug) :
**Build → Build Bundle(s) / APK(s) → Build APK(s)**, fichier dans
`app/build/outputs/apk/debug/`.

Pour un build **release** signé, un `app/keystore.properties` (non versionné,
voir `.gitignore`) doit exister avec les clés `storeFile`, `storePassword`,
`keyAlias`, `keyPassword` pointant vers un keystore généré via `keytool`.

## Permissions demandées

- **Micro** — pour la reconnaissance vocale (fonctionnalité native Android, aucun
  service cloud tiers).
- **Position** — uniquement pour la recherche de lieux à proximité ; optionnelle,
  l'appli fonctionne sans.

## Structure

```
app/src/main/java/com/momory/app/
  MainActivity.kt              Point d'entrée, permissions micro/position
  ChatViewModel.kt              État de la conversation, appels réseau, TTS, logique vocale
  data/
    OllamaModels.kt              Modèles de données (sérialisation JSON)
    OllamaClient.kt               Client HTTP (chat streaming, config auto, liste des modèles)
    SettingsStore.kt              Persistance des réglages (DataStore)
    ConversationStore.kt          Persistance de l'historique des conversations (JSON local)
  location/
    LocationProvider.kt           Position du téléphone (API Android native)
    PlacesClient.kt                Recherche de lieux via OpenStreetMap (Overpass API)
  voice/
    SpeechToText.kt                Reconnaissance vocale (API Android native)
    TextToSpeechManager.kt         Synthèse vocale, choix de la voix
  ui/
    ChatScreen.kt                  Écran texte (bulles, historique, réglages)
    SimpleVoiceScreen.kt            Écran vocal (gros bouton, halo animé)
    SettingsScreen.kt               Dialogue de configuration
    HistoryDialog.kt                Liste des conversations précédentes
    theme/                          Couleurs, thèmes, dégradés
```

## Réseau local (HTTP non chiffré)

L'appli autorise le trafic HTTP en clair (`usesCleartextTraffic`) car le serveur
Ollama tourne en local sur le réseau, sans certificat TLS — normal pour un usage
domestique, mais ça veut dire : ne pas utiliser cette appli en dehors du réseau
de confiance sans VPN.
