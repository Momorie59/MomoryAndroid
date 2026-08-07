package com.momory.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredMessage(val role: String, val content: String)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<StoredMessage>
)

/** Persistance simple des conversations dans un fichier JSON local (pas de serveur distant). */
class ConversationStore(context: Context) {
    private val file = File(context.filesDir, "conversations.json")
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadAllUnsorted(): List<Conversation> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<Conversation>>(file.readText()) }
            .getOrDefault(emptyList())
    }

    fun loadAll(): List<Conversation> = loadAllUnsorted().sortedByDescending { it.updatedAt }

    fun get(id: String): Conversation? = loadAllUnsorted().firstOrNull { it.id == id }

    /** Écrase ou ajoute une conversation ; ne garde que les 50 plus récentes. */
    fun save(conversation: Conversation) {
        if (conversation.messages.isEmpty()) return
        val all = loadAllUnsorted().toMutableList()
        val idx = all.indexOfFirst { it.id == conversation.id }
        if (idx >= 0) all[idx] = conversation else all.add(conversation)
        val trimmed = all.sortedByDescending { it.updatedAt }.take(50)
        file.writeText(json.encodeToString(trimmed))
    }

    fun delete(id: String) {
        val all = loadAllUnsorted().filterNot { it.id == id }
        file.writeText(json.encodeToString(all))
    }
}
