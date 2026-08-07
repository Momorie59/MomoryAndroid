<div align="center">

<img src="assets/logo.svg" alt="Logo Momory" width="120" height="120">

# Momory Android

**Parle à ton IA locale, depuis ton téléphone.**

App Android native (chat + vocal complet) connectée à ton serveur
[Momory · IA Local](https://github.com/Momorie59/ia-local-automatique) — aucune donnée
n'envoie vers un cloud tiers, tout reste sur ton réseau.

[![Kotlin](https://img.shields.io/badge/kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](.)
[![Android](https://img.shields.io/badge/android-8.0%2B-3DDC84?logo=android&logoColor=white)](.)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](.)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE.md)

</div>

---

## ✨ Ce que tu obtiens

| | |
|---|---|
| 💬 **Chat texte** | Discussion classique, réponses en streaming (affichage au fur et à mesure) |
| 🎙️ **Vocal complet** | Tu parles, l'app transcrit, envoie à ton IA, et te lit la réponse à voix haute |
| ⚙️ **Config automatique** | Entre juste l'IP de ton serveur — adresse, port et modèle se récupèrent tout seuls depuis le dashboard |
| 🎨 **Identité visuelle Momory** | Même thème sombre / accent néon que le dashboard web |
| 🔒 **100% local** | Aucun compte, aucune clé API, aucune donnée envoyée ailleurs qu'à ton propre serveur |

---

## 📲 Installation

### Option rapide — APK prêt à l'emploi

Télécharge **[`Momory.apk`]((https://github.com/Momorie59/ia-local-automatique/blob/main/Momory.apk))** directement depuis ce dépôt (clique dessus →
**Download**), transfère-le sur ton téléphone et installe-le.

Android va probablement te demander d'autoriser **"Sources inconnues"** (ou "Installer des
applications inconnues") pour l'app que tu utilises pour l'ouvrir (Fichiers, Chrome...) —
c'est normal pour un APK qui ne vient pas du Play Store, accepte-le pour cette installation.

### Option développeur — compiler soi-même

Prérequis : [Android Studio](https://developer.android.com/studio)

```bash
git clone https://github.com/Momorie59/MomoryAndroid.git
```

Ouvre le dossier dans Android Studio, laisse Gradle synchroniser, puis **Run ▶** (sur un
téléphone en mode développeur/débogage USB, ou un émulateur) — ou **Build → Build APK(s)**
pour générer ton propre `.apk`.

---

## 🚀 Premier lancement

1. L'app demande la permission micro — accepte-la (nécessaire pour le vocal)
2. Une fenêtre de connexion s'ouvre automatiquement : tape l'IP de ton serveur
   (ex: `192.168.1.16`) et appuie sur **Auto** — port et modèle se remplissent tout seuls
3. Enregistre, et c'est prêt : tape ou appuie sur 🎙 pour parler

---

## 📂 Structure

```
app/src/main/java/com/momory/app/
  MainActivity.kt            Point d'entrée, permissions micro
  ChatViewModel.kt             État de la conversation, appels réseau, TTS
  data/
    OllamaModels.kt              Modèles de données (sérialisation JSON)
    OllamaClient.kt               Client HTTP (chat streaming, config auto)
    SettingsStore.kt              Persistance des réglages serveur
  voice/
    SpeechToText.kt                Reconnaissance vocale (API Android native)
    TextToSpeechManager.kt         Synthèse vocale
  ui/
    ChatScreen.kt                   Écran principal (bulles, micro, envoi)
    SettingsScreen.kt                Dialogue de configuration
    theme/                           Couleurs/thème (identité visuelle Momory)
```

## 🔌 Ce qui n'est pas inclus (par choix)

- Pas d'accès aux fichiers du téléphone (contrairement à `momory-cli` sur PC) —
  uniquement de la discussion
- Pas de mémoire longue durée (Qdrant) depuis l'app pour l'instant

## 🌐 Réseau local

L'app autorise le trafic HTTP non chiffré (`usesCleartextTraffic`) car ton serveur Ollama
tourne en local sans certificat TLS — normal pour un usage domestique. N'utilise pas
l'app en dehors de ton réseau de confiance sans VPN.

## 🔗 Projet lié

Le serveur (installeur + dashboard web + CLI) : **[Momorie59/ia-local-automatique](https://github.com/Momorie59/ia-local-automatique)**

## 📜 Licence

Voir [LICENSE.md](LICENSE.md).

---

<div align="center">
<sub>Développé pour tourner entièrement en local — aucune donnée n'envoie vers un cloud tiers.</sub>
</div>
