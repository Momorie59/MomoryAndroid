package com.momory.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momory.app.data.ChatMessage
import com.momory.app.data.Conversation
import com.momory.app.data.ConversationStore
import com.momory.app.data.InterfaceMode
import com.momory.app.data.MomorySettings
import com.momory.app.data.OllamaClient
import com.momory.app.data.SettingsStore
import com.momory.app.data.StoredMessage
import com.momory.app.location.LocationProvider
import com.momory.app.location.LocationResult
import com.momory.app.location.PlacesClient
import com.momory.app.voice.SpeechToText
import com.momory.app.voice.TextToSpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.UUID

class UiMessage(val role: String, initial: String = "") {
    val id: String = UUID.randomUUID().toString()
    var text = androidx.compose.runtime.mutableStateOf(initial)
        private set

    fun append(token: String) {
        text.value += token
    }

    fun currentText(): String = text.value
}

private const val TAG = "MomoryVoice"

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val conversationStore = ConversationStore(application)
    private val tts = TextToSpeechManager(application) { onSpeechFinished() }
    private var speechToText: SpeechToText? = null
    private val locationProvider = LocationProvider(application)
    private val placesClient = PlacesClient()

    var settings = mutableStateOf(MomorySettings())
        private set

    val messages = mutableStateListOf<UiMessage>()
    var isSending = mutableStateOf(false)
        private set
    var isListening = mutableStateOf(false)
        private set
    var isSpeaking = mutableStateOf(false)
        private set
    var voiceEnabled = mutableStateOf(true)
    var micPermissionGranted = mutableStateOf(false)
        private set
    var locationPermissionGranted = mutableStateOf(false)
        private set
    var continuousModeRunning = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var showSettings = mutableStateOf(false)
        private set
    var showHistory = mutableStateOf(false)
        private set
    /** Dernier retour du micro en mode continu quand le mot-clé n'a pas été détecté — sert de diagnostic visible. */
    var voiceHint = mutableStateOf<String?>(null)
        private set
    /** true juste après avoir entendu le mot-clé seul : la prochaine phrase est prise comme commande, sans redemander le mot-clé. */
    var awaitingCommand = mutableStateOf(false)
        private set

    val conversationList = mutableStateListOf<Conversation>()
    var availableModels = mutableStateOf<List<String>>(emptyList())
        private set
    var availableVoices = mutableStateOf<List<String>>(emptyList())
        private set

    private var currentConversationId = UUID.randomUUID().toString()
    private var sendJob: Job? = null
    private var activeClient: OllamaClient? = null
    private var userCancelled = false
    private var appInForeground = true
    /** Passe à true quand l'utilisateur appuie sur Stop — empêche le mode continu de redémarrer tout seul. */
    private var continuousModeUserPaused = false
    private var consecutiveRetryErrors = 0
    /** true pendant une écoute lancée par un appui manuel sur le bouton — bypass le mot-clé. */
    private var manualListenActive = false

    private fun systemPrompt(name: String) = ChatMessage(
        role = "system",
        content = "Tu es $name, l'assistant IA personnel local de l'utilisateur. " +
            "Réponds en français, de façon directe, utile, et assez concise " +
            "(les réponses sont aussi lues à voix haute). Va droit à la question posée, " +
            "sans préambule. Ne te présente pas ('je suis ton assistant personnel', " +
            "'je m'appelle $name', etc.) sauf si on te demande explicitement qui tu es. " +
            "Ne répète jamais une phrase ou une idée que tu as déjà formulée dans la conversation."
    )

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { s ->
                settings.value = s
                if (!s.isConfigured) showSettings.value = true
                if (s.voiceName.isNotBlank()) tts.applyVoice(s.voiceName)
                if (s.continuousVoiceMode) {
                    maybeStartContinuousListening()
                } else {
                    continuousModeRunning.value = false
                    if (!isSending.value && !isSpeaking.value) speechToText?.stop()
                }
            }
        }
        loadConversationList()
    }

    // ---- Permissions & cycle de vie ----------------------------------

    private fun ensureSpeechRecognizer() {
        if (speechToText != null) return
        speechToText = SpeechToText(
            context = getApplication(),
            onResult = { text -> handleVoiceResult(text) },
            onError = { code, msg -> handleVoiceError(code, msg) },
            onListeningChange = { listening ->
                isListening.value = listening
                if (listening) consecutiveRetryErrors = 0
            }
        )
    }

    fun setInitialMicPermission(granted: Boolean) {
        micPermissionGranted.value = granted
        if (granted) {
            ensureSpeechRecognizer()
            maybeStartContinuousListening()
        }
    }

    fun onMicPermissionRequestResult(granted: Boolean) {
        micPermissionGranted.value = granted
        if (granted) {
            ensureSpeechRecognizer()
            maybeStartContinuousListening()
        } else {
            errorMessage.value = "Permission micro refusée — active-la dans les réglages de l'appli pour utiliser le vocal."
        }
    }

    fun setInitialLocationPermission(granted: Boolean) {
        locationPermissionGranted.value = granted
    }

    fun onLocationPermissionResult(granted: Boolean) {
        locationPermissionGranted.value = granted
    }

    fun onAppForeground(foreground: Boolean) {
        appInForeground = foreground
        if (!foreground) {
            continuousModeRunning.value = false
            speechToText?.stop()
        } else {
            maybeStartContinuousListening()
        }
    }

    // ---- Écoute vocale --------------------------------------------------

    /**
     * Relance l'écoute continue si les conditions sont réunies. [debounceMs] > 0 quand on
     * relance après une erreur ou une phrase sans mot-clé, pour éviter de marteler le
     * SpeechRecognizer en boucle serrée (ce qui donne l'impression que le micro reste
     * bloqué "actif" en permanence).
     */
    private fun maybeStartContinuousListening(debounceMs: Long = 0) {
        if (!appInForeground || !micPermissionGranted.value ||
            !settings.value.continuousVoiceMode || continuousModeUserPaused
        ) return
        ensureSpeechRecognizer()
        continuousModeRunning.value = true
        if (isSending.value || isSpeaking.value || isListening.value) return
        if (debounceMs <= 0) {
            speechToText?.start()
        } else {
            viewModelScope.launch {
                delay(debounceMs)
                if (!appInForeground || !settings.value.continuousVoiceMode || continuousModeUserPaused ||
                    isSending.value || isSpeaking.value || isListening.value
                ) return@launch
                speechToText?.start()
            }
        }
    }

    fun resumeContinuousModeManually() {
        continuousModeUserPaused = false
        maybeStartContinuousListening()
    }

    /**
     * Bouton micro manuel (push-to-talk) : appuyer dessus écoute et transcrit directement,
     * sans exiger le mot-clé — celui-ci ne sert qu'à activer le micro à distance, sans y
     * toucher, en mode continu.
     */
    fun onMicButtonPressed() {
        if (isListening.value) {
            speechToText?.stop()
            isListening.value = false
            manualListenActive = false
        } else {
            manualListenActive = true
            ensureSpeechRecognizer()
            speechToText?.start()
        }
    }

    fun toggleContinuousMode() {
        val enabling = !settings.value.continuousVoiceMode
        if (enabling) continuousModeUserPaused = false
        saveSettings(settings.value.copy(continuousVoiceMode = enabling))
    }

    /** Bascule rapide Simple <-> Avancé accessible directement depuis l'écran principal. */
    fun toggleInterfaceMode() {
        val next = if (settings.value.interfaceMode == InterfaceMode.SIMPLE) InterfaceMode.ADVANCED else InterfaceMode.SIMPLE
        saveSettings(settings.value.copy(interfaceMode = next))
    }

    /** Mots-clés de type de lieu -> tag OpenStreetMap (clé, valeur) correspondant. */
    private val placeTagKeywords: List<Pair<List<String>, Pair<String, String>>> = listOf(
        listOf("restaurant", "resto", "manger", "pizza", "pizzeria") to ("amenity" to "restaurant"),
        listOf("pharmacie") to ("amenity" to "pharmacy"),
        listOf("boulangerie", "boulanger", "pain") to ("shop" to "bakery"),
        listOf("supermarche", "supermarché", "courses", "epicerie", "épicerie") to ("shop" to "supermarket"),
        listOf("cafe", "café") to ("amenity" to "cafe"),
        listOf("bar") to ("amenity" to "bar"),
        listOf("essence", "station-service", "station service", "carburant") to ("amenity" to "fuel"),
        listOf("hotel", "hôtel") to ("tourism" to "hotel"),
        listOf("banque", "distributeur", "atm") to ("amenity" to "atm")
    )

    private val nearMeKeywords = listOf(
        "pres de moi", "à proximité", "a proximite", "autour de moi", "pres d'ici",
        "proche de moi", "ma position", "pas loin", "aux alentours", "a cote"
    )

    /** Détecte une demande de type "trouve-moi un restaurant près de moi" et renvoie le tag OSM à chercher. */
    private fun detectPlaceIntent(text: String): Pair<String, String>? {
        val normalized = normalize(text)
        val matchedTag = placeTagKeywords.firstOrNull { (keywords, _) ->
            keywords.any { normalize(it) in normalized }
        }?.second
        if (matchedTag != null) return matchedTag
        val hasNearMe = nearMeKeywords.any { normalize(it) in normalized }
        return if (hasNearMe) "amenity" to "restaurant" else null
    }

    /** Distance de Levenshtein — nombre minimal de lettres à changer pour passer de a à b. */
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Cherche le mot-clé dans la phrase entendue, en tolérant les petites erreurs de
     * transcription (ex: "Momory" entendu "momori" ou "montmory" par le moteur vocal, qui ne
     * connaît pas ce mot inventé). Compare chaque mot — et chaque paire de mots consécutifs,
     * au cas où le moteur l'aurait coupé en deux — au mot-clé via une distance d'édition.
     */
    private fun stripWakeWord(raw: String, wakeWord: String): String? {
        if (wakeWord.isBlank()) return raw
        val normalizedWake = normalize(wakeWord)
        val originalWords = raw.trim().split(Regex("\\s+"))
        val normalizedWords = originalWords.map { normalize(it) }
        val threshold = maxOf(1, normalizedWake.length / 3)

        var bestEndIndex = -1
        var bestDist = Int.MAX_VALUE
        for (i in normalizedWords.indices) {
            val distSingle = levenshtein(normalizedWords[i], normalizedWake)
            if (distSingle < bestDist) {
                bestDist = distSingle
                bestEndIndex = i
            }
            if (i + 1 < normalizedWords.size) {
                val merged = normalizedWords[i] + normalizedWords[i + 1]
                val distPair = levenshtein(merged, normalizedWake)
                if (distPair < bestDist) {
                    bestDist = distPair
                    bestEndIndex = i + 1
                }
            }
        }

        Log.d(TAG, "stripWakeWord raw='$raw' wakeWord='$wakeWord' bestEndIndex=$bestEndIndex bestDist=$bestDist threshold=$threshold")
        if (bestEndIndex < 0 || bestDist > threshold) return null
        val result = originalWords.drop(bestEndIndex + 1).joinToString(" ").trim()
        Log.d(TAG, "stripWakeWord result='$result'")
        return result
    }

    private fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    private fun handleVoiceResult(text: String) {
        Log.d(TAG, "handleVoiceResult text='$text' manualListenActive=$manualListenActive continuousModeRunning=${continuousModeRunning.value} awaitingCommand=${awaitingCommand.value} assistantName='${settings.value.assistantName}'")
        consecutiveRetryErrors = 0
        if (manualListenActive) {
            // Écoute déclenchée par un appui bouton : on répond directement, le mot-clé
            // n'a de sens que pour l'écoute en arrière-plan sans y toucher.
            manualListenActive = false
            awaitingCommand.value = false
            voiceHint.value = null
            sendMessage(text)
            return
        }
        if (continuousModeRunning.value) {
            if (awaitingCommand.value) {
                // On a déjà entendu le mot-clé lors du tour précédent (avec une pause juste
                // après, comme le ferait n'importe qui) — tout ce qu'on entend maintenant EST
                // la commande, pas besoin de redire le mot-clé.
                awaitingCommand.value = false
                if (text.isBlank()) {
                    maybeStartContinuousListening(debounceMs = 300)
                    return
                }
                voiceHint.value = null
                sendMessage(text)
                return
            }

            val command = stripWakeWord(text, settings.value.assistantName)
            if (command == null) {
                // Le mot-clé n'a pas du tout été entendu — on l'affiche pour que ce soit
                // visible plutôt que de disparaître en silence.
                voiceHint.value = "Entendu : « $text » — commence par « ${settings.value.assistantName} »"
                maybeStartContinuousListening(debounceMs = 500)
                return
            }
            if (command.isBlank()) {
                // Le mot-clé a été dit seul (l'utilisateur a marqué une pause avant sa
                // question) — on continue d'écouter sans redemander le mot-clé.
                awaitingCommand.value = true
                voiceHint.value = "${settings.value.assistantName} t'écoute…"
                maybeStartContinuousListening(debounceMs = 150)
                return
            }
            voiceHint.value = null
            sendMessage(command)
        } else {
            voiceHint.value = null
            sendMessage(text)
        }
    }

    private fun handleVoiceError(code: Int, message: String) {
        Log.d(TAG, "handleVoiceError code=$code message='$message' manualListenActive=$manualListenActive continuousModeRunning=${continuousModeRunning.value} consecutiveRetryErrors=$consecutiveRetryErrors")
        if (manualListenActive) {
            // Échec d'une écoute déclenchée par appui bouton : on n'entre pas dans la boucle
            // de retry du mode continu, on redonne juste la main à l'utilisateur.
            manualListenActive = false
            errorMessage.value = message
            return
        }
        if (continuousModeRunning.value && SpeechToText.isRetryableError(code)) {
            consecutiveRetryErrors++
            // Après quelques silences, on arrête d'attendre la commande pour ne pas rester
            // "amorcé" indéfiniment et devoir redire le mot-clé la fois suivante.
            if (awaitingCommand.value && consecutiveRetryErrors >= 2) {
                awaitingCommand.value = false
                voiceHint.value = null
            }
            if (consecutiveRetryErrors >= 6) {
                consecutiveRetryErrors = 0
                continuousModeUserPaused = true
                continuousModeRunning.value = false
                errorMessage.value = "La reconnaissance vocale continue n'arrive pas à capter de voix sur cet appareil — mode continu mis en pause. Utilise le micro manuel, ou réactive-le depuis les réglages."
                return
            }
            maybeStartContinuousListening(debounceMs = 700)
            return
        }
        consecutiveRetryErrors = 0
        errorMessage.value = message
    }

    private fun onSpeechFinished() {
        isSpeaking.value = false
        maybeStartContinuousListening(debounceMs = 350)
    }

    /** Coupe tout immédiatement : la voix de Momory, la requête en cours, et l'écoute micro. */
    fun stopEverything() {
        userCancelled = true
        continuousModeUserPaused = true
        sendJob?.cancel()
        activeClient?.cancelActive()
        tts.stop()
        isSpeaking.value = false
        speechToText?.stop()
        isListening.value = false
        isSending.value = false
        continuousModeRunning.value = false
        voiceHint.value = null
        awaitingCommand.value = false
        manualListenActive = false
    }

    // ---- Réglages ---------------------------------------------------------

    fun openSettings() {
        refreshVoiceOptions()
        showSettings.value = true
    }
    fun closeSettings() { showSettings.value = false }
    fun dismissError() { errorMessage.value = null }

    fun saveSettings(newSettings: MomorySettings) {
        viewModelScope.launch {
            settingsStore.save(newSettings)
            showSettings.value = false
        }
    }

    fun autoDetect(dashboardHost: String, onResult: (Result<MomorySettings>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val info = OllamaClient.fetchFromDashboard(dashboardHost)
                    settings.value.copy(
                        host = info.host,
                        port = info.port,
                        model = info.chatModel ?: info.coderModel ?: ""
                    )
                }
            }
            onResult(result)
        }
    }

    /** Récupère la liste des modèles installés sur le serveur Ollama. */
    fun fetchModels(host: String, port: Int, onResult: (Result<List<String>>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { OllamaClient(host, port).listModels() }
            }
            result.onSuccess { availableModels.value = it }
            onResult(result)
        }
    }

    /** Rafraîchit la liste des voix TTS disponibles (le moteur doit avoir fini de s'initialiser). */
    fun refreshVoiceOptions() {
        availableVoices.value = tts.availableVoiceNames()
    }

    /** Applique une voix et prononce un exemple pour que l'utilisateur puisse l'écouter. */
    fun previewVoice(voiceName: String) {
        tts.applyVoice(voiceName)
        tts.speak("Bonjour, je m'appelle ${settings.value.assistantName}.")
    }

    // ---- Conversations ------------------------------------------------

    fun openHistory() {
        loadConversationList()
        showHistory.value = true
    }
    fun closeHistory() { showHistory.value = false }

    private fun loadConversationList() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { conversationStore.loadAll() }
            conversationList.clear()
            conversationList.addAll(list)
        }
    }

    /** Sauvegarde la conversation en cours puis repart de zéro. */
    fun startNewConversation() {
        persistCurrentConversation()
        currentConversationId = UUID.randomUUID().toString()
        messages.clear()
    }

    fun openConversation(id: String) {
        viewModelScope.launch {
            val conversation = withContext(Dispatchers.IO) { conversationStore.get(id) } ?: return@launch
            persistCurrentConversation()
            currentConversationId = conversation.id
            messages.clear()
            conversation.messages.forEach { m -> messages.add(UiMessage(m.role, m.content)) }
            showHistory.value = false
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { conversationStore.delete(id) }
            if (id == currentConversationId) messages.clear()
            loadConversationList()
        }
    }

    private fun conversationTitle(): String =
        messages.firstOrNull { it.role == "user" }?.currentText()?.take(48)
            ?: "Conversation"

    private fun persistCurrentConversation() {
        if (messages.isEmpty()) return
        val conversation = Conversation(
            id = currentConversationId,
            title = conversationTitle(),
            updatedAt = System.currentTimeMillis(),
            messages = messages.map { StoredMessage(it.role, it.currentText()) }
        )
        viewModelScope.launch(Dispatchers.IO) {
            conversationStore.save(conversation)
        }
    }

    /**
     * Si le message ressemble à "trouve-moi un restaurant près de moi", récupère la position
     * du téléphone et interroge OpenStreetMap pour de vrais résultats, injectés comme contexte
     * système temporaire — le modèle local n'a lui-même aucun accès internet ni GPS.
     */
    private suspend fun buildPlaceContext(userText: String): ChatMessage? {
        val placeIntent = detectPlaceIntent(userText) ?: return null
        if (!locationPermissionGranted.value) {
            return ChatMessage(
                "system",
                "L'utilisateur a demandé un lieu à proximité mais l'application n'a pas la " +
                    "permission de localisation. Explique-le lui poliment et dis-lui d'activer " +
                    "la position dans Réglages du téléphone > Applications > ${settings.value.assistantName} > Position."
            )
        }
        val locationResult = locationProvider.getCurrentLocation()
        val location = when (locationResult) {
            is LocationResult.Success -> locationResult.location
            LocationResult.NoPermission -> return ChatMessage(
                "system",
                "L'utilisateur a demandé un lieu à proximité mais l'application n'a pas la " +
                    "permission de localisation. Explique-le lui poliment."
            )
            LocationResult.LocationServicesDisabled -> return ChatMessage(
                "system",
                "Le service de localisation du téléphone (GPS) est désactivé dans les réglages " +
                    "système, même si l'appli a la permission. Dis à l'utilisateur d'activer la " +
                    "localisation dans les réglages rapides ou Réglages > Position du téléphone."
            )
            LocationResult.Timeout -> return ChatMessage(
                "system",
                "La position n'a pas pu être obtenue à temps (signal GPS faible, peut-être à " +
                    "l'intérieur). Dis-le à l'utilisateur et propose de réessayer dehors ou avec " +
                    "le Wi-Fi/les données mobiles activés."
            )
        }
        val (tagKey, tagValue) = placeIntent
        val places = runCatching {
            placesClient.searchNearby(location.latitude, location.longitude, tagKey, tagValue)
        }.getOrDefault(emptyList())
        return ChatMessage(
            "system",
            if (places.isEmpty()) {
                "Aucun résultat trouvé à proximité pour cette recherche (la base de données " +
                    "utilisée, OpenStreetMap, n'est pas toujours complète partout). Dis-le à " +
                    "l'utilisateur au lieu d'inventer un lieu, et précise que la couverture peut " +
                    "être incomplète selon la zone."
            } else {
                "Résultats à proximité (du plus proche au plus loin, source OpenStreetMap — " +
                    "peut être incomplète ou légèrement obsolète), à utiliser pour répondre sans " +
                    "en inventer d'autres :\n" +
                    places.joinToString("\n") { p ->
                        "- ${p.name} (${p.distanceMeters} m)" + (p.address?.let { ", $it" } ?: "")
                    }
            }
        )
    }

    // ---- Envoi de message ------------------------------------------------

    fun sendMessage(text: String) {
        Log.d(TAG, "sendMessage called with text='$text' isSending=${isSending.value}")
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isSending.value) return
        val s = settings.value
        if (!s.isConfigured) {
            showSettings.value = true
            return
        }

        messages.add(UiMessage("user", trimmed))
        val assistantMsg = UiMessage("assistant")
        messages.add(assistantMsg)
        isSending.value = true
        userCancelled = false

        sendJob = viewModelScope.launch {
            val client = OllamaClient(s.host, s.port)
            activeClient = client
            var willSpeak = false
            try {
                val placeContext = withContext(Dispatchers.IO) { buildPlaceContext(trimmed) }
                val history = listOfNotNull(systemPrompt(s.assistantName), placeContext) + messages
                    .filter { it !== assistantMsg }
                    .map { ChatMessage(it.role, it.currentText()) }

                val full = withContext(Dispatchers.IO) {
                    client.chatStream(s.model, history) { token -> assistantMsg.append(token) }
                }
                if (voiceEnabled.value && full.isNotBlank()) {
                    willSpeak = true
                    isSpeaking.value = true
                    tts.speak(full)
                }
            } catch (e: Exception) {
                if (!userCancelled) {
                    messages.remove(assistantMsg)
                    errorMessage.value = e.message ?: "Erreur inconnue."
                }
            } finally {
                isSending.value = false
                activeClient = null
                persistCurrentConversation()
                if (!willSpeak) maybeStartContinuousListening()
            }
        }
    }

    override fun onCleared() {
        speechToText?.stop()
        tts.shutdown()
        super.onCleared()
    }
}
