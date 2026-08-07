package com.momory.app.data

import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

class OllamaClient(
    private val host: String,
    private val port: Int
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl get() = "http://$host:$port"

    @Volatile private var activeCall: Call? = null

    /** Interrompt immédiatement le streaming en cours (bouton "Stop"). */
    fun cancelActive() {
        activeCall?.cancel()
    }

    /** Vérifie que le serveur Ollama répond. */
    fun ping(): Boolean {
        return try {
            val req = Request.Builder().url("$baseUrl/api/tags").get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /** Liste les modèles installés sur le serveur. */
    fun listModels(): List<String> {
        val req = Request.Builder().url("$baseUrl/api/tags").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: return emptyList()
            return json.decodeFromString<TagsResponse>(body).models.map { it.name }
        }
    }

    /**
     * Envoie la conversation au modèle et diffuse la réponse token par token
     * via onToken (appelé sur le thread appelant — à invoquer depuis un
     * contexte IO, puis repasser sur Main pour mettre à jour l'UI).
     * Retourne le texte complet une fois la réponse terminée.
     */
    fun chatStream(model: String, messages: List<ChatMessage>, onToken: (String) -> Unit): String {
        val reqBody = ChatRequest(model = model, messages = messages, stream = true)
        val bodyJson = json.encodeToString(ChatRequest.serializer(), reqBody)
        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()

        val full = StringBuilder()
        val call = client.newCall(request)
        activeCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val detail = resp.body?.string().orEmpty()
                    throw Exception("Le serveur a répondu HTTP ${resp.code}. $detail")
                }
                val source = resp.body?.byteStream() ?: throw Exception("Réponse vide du serveur.")
                val reader: BufferedReader = source.bufferedReader()
                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    try {
                        val chunk = json.decodeFromString<ChatStreamChunk>(line)
                        val token = chunk.message?.content
                        if (!token.isNullOrEmpty()) {
                            full.append(token)
                            onToken(token)
                        }
                    } catch (_: Exception) {
                        // Ligne NDJSON malformée/partielle — on l'ignore plutôt que de planter.
                    }
                }
            }
        } finally {
            activeCall = null
        }
        return full.toString()
    }

    companion object {
        /**
         * Récupère la config serveur directement depuis le dashboard Momory
         * (même principe que `momory config --auto` côté CLI) — aucune saisie
         * manuelle nécessaire si le dashboard tourne sur le port 7842.
         */
        fun fetchFromDashboard(dashboardHost: String, dashboardPort: Int = 7842): MomoryServerInfo {
            val client = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url("http://$dashboardHost:$dashboardPort/api/stats").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Dashboard injoignable : HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw Exception("Réponse vide du dashboard.")
                val json = Json { ignoreUnknownKeys = true }
                val stats = json.decodeFromString<DashboardStatsResponse>(body)
                return stats.momory ?: throw Exception(
                    "Le dashboard ne renvoie pas d'infos Momory (version du serveur trop ancienne ?)."
                )
            }
        }
    }
}
