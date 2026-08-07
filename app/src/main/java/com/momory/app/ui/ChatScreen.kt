package com.momory.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.momory.app.ChatViewModel
import com.momory.app.UiMessage
import com.momory.app.data.InterfaceMode
import com.momory.app.ui.theme.LocalAccent
import com.momory.app.ui.theme.MomoryBg
import com.momory.app.ui.theme.MomorySurface2
import com.momory.app.ui.theme.backgroundBrush
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChatScreen(viewModel: ChatViewModel, onMicClick: () -> Unit) {
    val settings by viewModel.settings
    val showSettings by viewModel.showSettings
    val showHistory by viewModel.showHistory
    val error by viewModel.errorMessage

    when (settings.interfaceMode) {
        InterfaceMode.SIMPLE -> SimpleVoiceScreen(viewModel = viewModel, onMicClick = onMicClick)
        InterfaceMode.ADVANCED -> AdvancedChatScreen(viewModel = viewModel, onMicClick = onMicClick)
    }

    if (showSettings) {
        SettingsDialog(
            current = settings,
            availableModels = viewModel.availableModels.value,
            availableVoices = viewModel.availableVoices.value,
            onDismiss = { viewModel.closeSettings() },
            onSave = { viewModel.saveSettings(it) },
            onAutoDetect = { host, callback -> viewModel.autoDetect(host, callback) },
            onFetchModels = { host, port, callback -> viewModel.fetchModels(host, port, callback) },
            onRefreshVoices = { viewModel.refreshVoiceOptions() },
            onPreviewVoice = { name -> viewModel.previewVoice(name) }
        )
    }

    if (showHistory) {
        HistoryDialog(
            conversations = viewModel.conversationList,
            onDismiss = { viewModel.closeHistory() },
            onOpen = { id -> viewModel.openConversation(id) },
            onDelete = { id -> viewModel.deleteConversation(id) },
            onNew = { viewModel.startNewConversation() }
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Oups") },
            text = { Text(error ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AdvancedChatScreen(viewModel: ChatViewModel, onMicClick: () -> Unit) {
    val messages = viewModel.messages
    val isSending by viewModel.isSending
    val isListening by viewModel.isListening
    val isSpeaking by viewModel.isSpeaking
    val voiceEnabled by viewModel.voiceEnabled
    val settings by viewModel.settings
    val voiceHint by viewModel.voiceHint
    val accent = LocalAccent.current

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val busy = isSending || isListening || isSpeaking

    // Se recale aussi sur le texte du dernier message (pas juste sur messages.size) pour
    // continuer à suivre la réponse pendant qu'elle s'affiche token par token, sinon la fin
    // du texte reste cachée sous la barre du bas une fois la bulle plus haute que l'écran.
    val lastMessageText = messages.lastOrNull()?.text?.value
    LaunchedEffect(messages.size, lastMessageText) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { AssistantAvatar(size = 34.dp) },
                actions = {
                    ModeSwitchChip(mode = settings.interfaceMode, onClick = { viewModel.toggleInterfaceMode() })
                    IconButton(onClick = { viewModel.openHistory() }) {
                        Icon(Icons.Filled.History, contentDescription = "Conversations précédentes")
                    }
                    IconButton(onClick = { viewModel.toggleContinuousMode() }) {
                        Icon(
                            Icons.Filled.Loop,
                            contentDescription = "Mode vocal continu",
                            tint = if (settings.continuousVoiceMode) accent.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { viewModel.voiceEnabled.value = !voiceEnabled }) {
                        Icon(
                            if (voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Activer/couper la voix",
                            tint = if (voiceEnabled) accent.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush(accent))
                .padding(padding)
        ) {
            if (messages.isEmpty() && !isSending) {
                EmptyConversationHint(settings.assistantName, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(msg, modifier = Modifier.animateItemPlacement())
                    }
                    if (isSending) {
                        item { TypingIndicator(settings.assistantName) }
                    }
                }
            }

            if (isListening) {
                Text(
                    "🎙 Écoute en cours…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = accent.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (isSpeaking) {
                Text(
                    "🔊 ${settings.assistantName} parle…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = accent.secondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (voiceHint != null) {
                Text(
                    voiceHint ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy) {
                    IconButton(onClick = { viewModel.stopEverything() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = onMicClick) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Parler",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Écris ou parle à ${settings.assistantName}…") },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendMessage(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !isSending
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = accent.secondary)
                }
            }
        }
    }
}

/** Bouton bien visible pour basculer entre l'interface texte et l'interface vocale. */
@Composable
fun ModeSwitchChip(mode: InterfaceMode, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val isSimple = mode == InterfaceMode.SIMPLE
    val label = if (isSimple) "Vocal" else "Texte"
    val icon = if (isSimple) Icons.Filled.Mic else Icons.Filled.Forum
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MomorySurface2,
            labelColor = accent.primary,
            leadingIconContentColor = accent.primary
        ),
        modifier = Modifier.padding(end = 4.dp)
    )
}

/**
 * Avatar de l'assistant — même dessin que l'icône de l'appli (nœud central + 3 billes en
 * orbite), redessiné à la main pour pouvoir animer la rotation des billes, comme sur le
 * dashboard web. L'icône de lancement elle-même (drawable/ic_launcher_foreground) reste
 * statique, Android ne permet pas d'animer une icône d'accueil.
 */
@Composable
fun AssistantAvatar(size: Dp = 32.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo-orbit")
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "logo-orbit-angle"
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(MomoryBg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.9f)) {
            val scale = this.size.minDimension / 108f
            val center = Offset(54f, 54f) * scale

            drawCircle(
                color = Color(0xFF00FFB3),
                radius = 14f * scale,
                center = center,
                style = Stroke(width = 4f * scale)
            )
            drawCircle(color = Color(0xFF00FFB3), radius = 5.5f * scale, center = center)

            // Même rayon d'orbite que ic_launcher_foreground.xml, pour rester dans la zone
            // sûre de l'icône adaptative et garder les deux dessins visuellement identiques.
            val orbitRadius = 24f * scale
            val orbitDots = listOf(
                -90f to Color(0xFF4D9FFF),
                30f to Color(0xFFA855F7),
                150f to Color(0xFFEAB308)
            )
            orbitDots.forEach { (baseAngle, color) ->
                val angleRad = Math.toRadians((baseAngle + rotationDeg).toDouble())
                val dotCenter = Offset(
                    x = center.x + orbitRadius * cos(angleRad).toFloat(),
                    y = center.y + orbitRadius * sin(angleRad).toFloat()
                )
                drawCircle(color = color, radius = 4f * scale, center = dotCenter)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: UiMessage, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val isUser = msg.role == "user"
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            AssistantAvatar(size = 26.dp)
            Spacer(Modifier.width(6.dp))
        }
        val bubbleBrush = if (isUser) accent.brush else SolidColor(MomorySurface2)
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(2.dp, shape)
                .background(bubbleBrush, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                msg.text.value,
                color = if (isUser) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TypingIndicator(assistantName: String = "Momory") {
    Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.Bottom) {
        AssistantAvatar(size = 26.dp)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
                .background(MomorySurface2, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("$assistantName réfléchit…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun EmptyConversationHint(assistantName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AssistantAvatar(size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Bonjour, je suis $assistantName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pose-moi une question, à l'écrit ou à voix haute.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
