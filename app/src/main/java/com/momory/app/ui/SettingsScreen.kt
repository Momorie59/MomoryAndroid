package com.momory.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momory.app.data.InterfaceMode
import com.momory.app.data.MomorySettings
import com.momory.app.ui.theme.AppTheme
import com.momory.app.ui.theme.LocalAccent
import com.momory.app.ui.theme.accentFor

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = LocalAccent.current.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    current: MomorySettings,
    availableModels: List<String>,
    availableVoices: List<String>,
    onDismiss: () -> Unit,
    onSave: (MomorySettings) -> Unit,
    onAutoDetect: (host: String, callback: (Result<MomorySettings>) -> Unit) -> Unit,
    onFetchModels: (host: String, port: Int, callback: (Result<List<String>>) -> Unit) -> Unit,
    onRefreshVoices: () -> Unit,
    onPreviewVoice: (String) -> Unit
) {
    var host by remember { mutableStateOf(current.host) }
    var port by remember { mutableStateOf(current.port.toString()) }
    var model by remember { mutableStateOf(current.model) }
    var autoDetecting by remember { mutableStateOf(false) }
    var autoError by remember { mutableStateOf<String?>(null) }
    var advancedInterface by remember { mutableStateOf(current.interfaceMode == InterfaceMode.ADVANCED) }
    var continuousVoiceMode by remember { mutableStateOf(current.continuousVoiceMode) }
    var assistantName by remember { mutableStateOf(current.assistantName) }
    var voiceName by remember { mutableStateOf(current.voiceName) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var fetchingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var voicesExpanded by remember { mutableStateOf(false) }
    var appTheme by remember { mutableStateOf(current.appTheme) }

    LaunchedEffect(Unit) { onRefreshVoices() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Configuration automatique depuis le dashboard (recommandé) :",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; autoError = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("Adresse du serveur") },
                        placeholder = { Text("192.168.1.16") },
                        singleLine = true
                    )
                    Button(
                        enabled = host.isNotBlank() && !autoDetecting,
                        onClick = {
                            autoDetecting = true
                            autoError = null
                            onAutoDetect(host) { result ->
                                autoDetecting = false
                                result.onSuccess { s ->
                                    host = s.host
                                    port = s.port.toString()
                                    model = s.model
                                }.onFailure { e ->
                                    autoError = e.message ?: "Échec de la détection automatique."
                                }
                            }
                        }
                    ) {
                        Text(if (autoDetecting) "…" else "Auto")
                    }
                }
                autoError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                SectionHeader(Icons.Filled.Dns, "Ou saisie manuelle")

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port Ollama") },
                    placeholder = { Text("11434") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = modelsExpanded && availableModels.isNotEmpty(),
                        onExpandedChange = { modelsExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Modèle") },
                            placeholder = { Text("llama3.1:8b") },
                            singleLine = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = modelsExpanded && availableModels.isNotEmpty(),
                            onDismissRequest = { modelsExpanded = false }
                        ) {
                            availableModels.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        model = name
                                        modelsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        enabled = host.isNotBlank() && port.isNotBlank() && !fetchingModels,
                        onClick = {
                            fetchingModels = true
                            modelsError = null
                            onFetchModels(host, port.toIntOrNull() ?: 11434) { result ->
                                fetchingModels = false
                                result.onSuccess { list ->
                                    if (list.isEmpty()) {
                                        modelsError = "Aucun modèle installé sur ce serveur."
                                    } else {
                                        modelsExpanded = true
                                    }
                                }.onFailure { e ->
                                    modelsError = e.message ?: "Impossible de récupérer les modèles."
                                }
                            }
                        }
                    ) {
                        Text(if (fetchingModels) "…" else "Lister")
                    }
                }
                modelsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                SectionHeader(Icons.Filled.Palette, "Apparence")

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppTheme.entries.forEach { themeOption ->
                        val swatch = accentFor(themeOption)
                        val selected = appTheme == themeOption
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(swatch.brush, CircleShape)
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { appTheme = themeOption },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(themeOption.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider()
                SectionHeader(Icons.Filled.Face, "Identité et interface")

                OutlinedTextField(
                    value = assistantName,
                    onValueChange = { assistantName = it },
                    label = { Text("Nom de l'assistant") },
                    placeholder = { Text("Momory") },
                    singleLine = true,
                    supportingText = { Text("Sert de mot-clé vocal et de prénom utilisé par l'assistant pour se présenter.") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Interface avancée", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Chat texte complet plutôt que l'écran vocal simplifié. Un bouton rapide existe aussi sur l'écran principal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = advancedInterface, onCheckedChange = { advancedInterface = it })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mode vocal continu", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Écoute en continu et répond dès qu'elle entend son nom, sans réappuyer sur le micro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = continuousVoiceMode, onCheckedChange = { continuousVoiceMode = it })
                }

                HorizontalDivider()
                SectionHeader(Icons.Filled.RecordVoiceOver, "Voix")

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = voicesExpanded && availableVoices.isNotEmpty(),
                        onExpandedChange = { voicesExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = voiceName.ifBlank { "Par défaut" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Voix") },
                            singleLine = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = voicesExpanded && availableVoices.isNotEmpty(),
                            onDismissRequest = { voicesExpanded = false }
                        ) {
                            availableVoices.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        voiceName = name
                                        voicesExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        enabled = voiceName.isNotBlank(),
                        onClick = { onPreviewVoice(voiceName) }
                    ) { Text("Écouter") }
                }
                if (availableVoices.isEmpty()) {
                    Text(
                        "Aucune voix française détectée pour l'instant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank() && model.isNotBlank(),
                onClick = {
                    onSave(
                        MomorySettings(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 11434,
                            model = model.trim(),
                            interfaceMode = if (advancedInterface) InterfaceMode.ADVANCED else InterfaceMode.SIMPLE,
                            continuousVoiceMode = continuousVoiceMode,
                            assistantName = assistantName.trim().ifBlank { "Momory" },
                            voiceName = voiceName,
                            appTheme = appTheme
                        )
                    )
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
