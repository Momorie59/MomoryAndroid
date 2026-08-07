package com.momory.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

@Serializable
data class ChatRequestOptions(
    // Pénalise légèrement la répétition de tokens déjà générés — juste assez pour éviter
    // les boucles sur les mêmes phrases, sans trop lisser le style du modèle (une valeur
    // trop haute + une température trop basse rendent les réponses plates/génériques).
    @SerialName("repeat_penalty") val repeatPenalty: Double = 1.15,
    @SerialName("repeat_last_n") val repeatLastN: Int = 256,
    val temperature: Double = 0.85
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val options: ChatRequestOptions = ChatRequestOptions()
)

@Serializable
data class ChatStreamChunk(
    val message: ChatMessageChunk? = null,
    val done: Boolean = false
)

@Serializable
data class ChatMessageChunk(
    val content: String? = null
)

@Serializable
data class OllamaModel(
    val name: String
)

@Serializable
data class TagsResponse(
    val models: List<OllamaModel> = emptyList()
)

/** Réponse du dashboard Momory (endpoint /api/stats, champ "momory") —
 * permet de récupérer la config serveur automatiquement, comme momory-cli. */
@Serializable
data class MomoryServerInfo(
    val host: String,
    val port: Int = 11434,
    @SerialName("chat_model") val chatModel: String? = null,
    @SerialName("coder_model") val coderModel: String? = null,
    @SerialName("qdrant_port") val qdrantPort: Int? = null
)

@Serializable
data class DashboardStatsResponse(
    val momory: MomoryServerInfo? = null
)
