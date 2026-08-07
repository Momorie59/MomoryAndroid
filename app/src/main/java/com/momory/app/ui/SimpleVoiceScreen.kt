package com.momory.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momory.app.ChatViewModel
import com.momory.app.ui.theme.LocalAccent
import com.momory.app.ui.theme.MomoryRed
import com.momory.app.ui.theme.backgroundBrush

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SimpleVoiceScreen(viewModel: ChatViewModel, onMicClick: () -> Unit) {
    val messages = viewModel.messages
    val isSending by viewModel.isSending
    val isListening by viewModel.isListening
    val isSpeaking by viewModel.isSpeaking
    val voiceEnabled by viewModel.voiceEnabled
    val settings by viewModel.settings
    val voiceHint by viewModel.voiceHint
    val micPermissionGranted by viewModel.micPermissionGranted
    val awaitingCommand by viewModel.awaitingCommand
    val accent = LocalAccent.current

    val listState = rememberLazyListState()
    val busy = isSending || isListening || isSpeaking

    val lastMessageText = messages.lastOrNull()?.text?.value
    LaunchedEffect(messages.size, lastMessageText) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    val statusText = when {
        !micPermissionGranted -> "Appuie pour autoriser le micro"
        isSpeaking -> "${settings.assistantName} parle…"
        isSending -> "${settings.assistantName} réfléchit…"
        isListening && awaitingCommand -> "Écoute ta question…"
        isListening -> "Écoute…"
        awaitingCommand -> "${settings.assistantName} t'écoute…"
        settings.continuousVoiceMode -> "Appuie, ou dis « ${settings.assistantName} »"
        else -> "Appuie pour parler"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    val glowSize by animateDpAsState(
        targetValue = if (isListening) 150.dp else 130.dp,
        label = "glow"
    )

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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (messages.isEmpty()) {
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

            Text(
                statusText,
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isSpeaking -> accent.secondary
                    isListening -> accent.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )
            if (voiceHint != null) {
                Text(
                    voiceHint ?: "",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))

            val circleBrush = when {
                busy && !isListening -> Brush.radialGradient(listOf(MomoryRed, MomoryRed.copy(alpha = 0.7f)))
                isListening -> Brush.radialGradient(listOf(accent.primary, accent.secondary))
                else -> accent.brush
            }
            val glowColor = when {
                busy && !isListening -> MomoryRed
                isListening -> accent.primary
                else -> accent.secondary
            }

            Box(
                modifier = Modifier.padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Halo lumineux : deux disques semi-transparents empilés pour un effet de lueur
                // sans dépendre de Modifier.blur() (indisponible avant l'API 31).
                Box(
                    modifier = Modifier
                        .size(glowSize)
                        .scale(pulse)
                        .background(glowColor.copy(alpha = 0.12f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(pulse)
                        .background(glowColor.copy(alpha = 0.18f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(8.dp, CircleShape, ambientColor = glowColor, spotColor = glowColor)
                        .background(circleBrush, CircleShape)
                        .clickable {
                            when {
                                // Le bouton écoute toujours directement, sans mot-clé : celui-ci ne
                                // sert qu'à activer le micro à distance en mode continu.
                                busy -> viewModel.stopEverything()
                                else -> onMicClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(targetState = busy, label = "mic-icon") { isBusy ->
                        Icon(
                            if (isBusy) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isBusy) "Stop" else "Parler",
                            tint = if (isBusy) Color.White else Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }
}
