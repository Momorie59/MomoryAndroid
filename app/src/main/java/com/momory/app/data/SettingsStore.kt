package com.momory.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.momory.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "momory_settings")

enum class InterfaceMode { SIMPLE, ADVANCED }

data class MomorySettings(
    val host: String = "",
    val port: Int = 11434,
    val model: String = "",
    val interfaceMode: InterfaceMode = InterfaceMode.ADVANCED,
    val continuousVoiceMode: Boolean = false,
    // Sert à la fois de mot-clé d'activation vocale et de prénom que l'assistant
    // utilise pour se présenter (system prompt, titre de l'appli, messages de statut).
    val assistantName: String = "Momory",
    val voiceName: String = "",
    val appTheme: AppTheme = AppTheme.NEON
) {
    val isConfigured get() = host.isNotBlank() && model.isNotBlank()
}

class SettingsStore(private val context: Context) {
    private val keyHost = stringPreferencesKey("host")
    private val keyPort = stringPreferencesKey("port")
    private val keyModel = stringPreferencesKey("model")
    private val keyInterfaceMode = stringPreferencesKey("interface_mode")
    private val keyContinuousVoice = booleanPreferencesKey("continuous_voice_mode")
    private val keyAssistantName = stringPreferencesKey("assistant_name")
    private val keyVoiceName = stringPreferencesKey("voice_name")
    private val keyAppTheme = stringPreferencesKey("app_theme")

    val settingsFlow: Flow<MomorySettings> = context.dataStore.data.map { prefs ->
        MomorySettings(
            host = prefs[keyHost] ?: "",
            port = prefs[keyPort]?.toIntOrNull() ?: 11434,
            model = prefs[keyModel] ?: "",
            interfaceMode = runCatching { InterfaceMode.valueOf(prefs[keyInterfaceMode] ?: "") }
                .getOrDefault(InterfaceMode.ADVANCED),
            continuousVoiceMode = prefs[keyContinuousVoice] ?: false,
            assistantName = prefs[keyAssistantName]?.takeIf { it.isNotBlank() } ?: "Momory",
            voiceName = prefs[keyVoiceName] ?: "",
            appTheme = runCatching { AppTheme.valueOf(prefs[keyAppTheme] ?: "") }
                .getOrDefault(AppTheme.NEON)
        )
    }

    suspend fun save(settings: MomorySettings) {
        context.dataStore.edit { prefs ->
            prefs[keyHost] = settings.host
            prefs[keyPort] = settings.port.toString()
            prefs[keyModel] = settings.model
            prefs[keyInterfaceMode] = settings.interfaceMode.name
            prefs[keyContinuousVoice] = settings.continuousVoiceMode
            prefs[keyAssistantName] = settings.assistantName
            prefs[keyVoiceName] = settings.voiceName
            prefs[keyAppTheme] = settings.appTheme.name
        }
    }
}
