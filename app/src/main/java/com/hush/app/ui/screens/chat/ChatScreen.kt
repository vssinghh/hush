package com.hush.app.ui.screens.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.Rule
import com.hush.app.domain.repository.AIStatus
import com.hush.app.ui.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val aiEngine = viewModel.aiEngine
    val ruleRepository = viewModel.ruleRepository
    val permissionManager = viewModel.permissionManager
    val context = LocalContext.current

    val mockMessages = viewModel.mockMessages
    val proposedRule = viewModel.proposedRule.value
    val errorMessage = viewModel.errorMessage.value
    val isListening = viewModel.isListening.value
    val isProcessing = viewModel.isProcessing.value
    val textState = viewModel.textState.value

    val aiStatus by aiEngine.status.collectAsState()
    val downloadProgress by aiEngine.downloadProgress.collectAsState()
    val aiErrorMessage by aiEngine.errorMessage.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleListening()
        } else {
            viewModel.errorMessage.value = "Microphone permission is required for voice commands"
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val currentTime = remember { LocalTime.now().format(timeFormatter) }

    Scaffold(
        modifier = modifier.testTag("chat_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top spacing
            Spacer(modifier = Modifier.height(8.dp))

            // ── Chat Messages ──
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                state = rememberLazyListState()
            ) {

                // AI Status as inline chat message
                item {
                    when (aiStatus) {
                        AIStatus.CHECKING -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_checking_banner")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Checking AI model availability…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        AIStatus.NOT_DOWNLOADED -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_download_banner"),
                                containerColor = AccentAmberLight
                            ) {
                                Column {
                                    Text(
                                        "Setting up on-device AI",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = CardOnLight
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Hush needs to download the Gemini Nano AI model (~350 MB). Please make sure you're connected to WiFi and your device is up to date, then tap the button below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CardOnLightMuted
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.startModelDownload() },
                                            modifier = Modifier.testTag("ai_download_button"),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentAmber,
                                                contentColor = CardOnLight
                                            )
                                        ) {
                                            Text("Set Up AI", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.openAICoreUpdateInStore(context) },
                                            modifier = Modifier.testTag("ai_update_button"),
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, CardOnLightMuted),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = CardOnLight
                                            )
                                        ) {
                                            Text("Check for Updates", fontSize = 13.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        currentTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CardOnLightMuted,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }

                        AIStatus.DOWNLOADING -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_downloading_banner"),
                                containerColor = AccentBlueLight
                            ) {
                                Column {
                                    Text(
                                        "Downloading Gemini Nano…",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = WarmOnSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "This may take a few minutes. Please stay on WiFi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarmOnSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = AccentBlue,
                                        trackColor = AccentBlue.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }

                        AIStatus.ERROR -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_error_banner"),
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "AI engine encountered an error",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            aiErrorMessage ?: "This may be temporary. Tap Retry to try again.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.retryAICheck() },
                                        modifier = Modifier.testTag("ai_retry_button"),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                        }

                        AIStatus.NOT_SUPPORTED -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_unsupported_banner"),
                                containerColor = StatusBlockedBg
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Not supported",
                                            tint = AccentRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "On-device AI unavailable",
                                            color = CardOnLight,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        aiErrorMessage ?: "Gemini Nano requires a compatible device (Pixel 8+, Samsung S24+). Voice and text commands are unavailable on this device.",
                                        color = CardOnLightMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.retryAICheck() },
                                            modifier = Modifier.testTag("ai_retry_button"),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentRed,
                                                contentColor = androidx.compose.ui.graphics.Color.White
                                            )
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry", fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.openAICoreUpdateInStore(context) },
                                            modifier = Modifier.testTag("ai_update_button"),
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, CardOnLightMuted),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = CardOnLight
                                            )
                                        ) {
                                            Text("Check for Updates")
                                        }
                                    }
                                }
                            }
                        }

                        AIStatus.READY -> {
                            // No banner needed — AI is ready
                        }
                    }
                }

                // Chat message bubbles
                items(mockMessages.size) { index ->
                    val isUser = index % 2 != 0
                    // Smart timestamps: show only on first and last message
                    val showTimestamp = index == 0 || index == mockMessages.size - 1
                    ChatBubble(
                        message = mockMessages[index],
                        isUser = isUser,
                        timestamp = currentTime,
                        showTimestamp = showTimestamp
                    )
                }

                // Show thinking indicator while AI is processing
                if (isProcessing) {
                    item {
                        ThinkingBubble()
                    }
                }

                // Show error message bubble if any
                errorMessage?.let { err ->
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp)
                                    .testTag("chat_error_message")
                            ) {
                                Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // Show voice waveform UI
                if (isListening) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_waveform_ui"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AccentPurpleLight
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Listening...",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = AccentPurple,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                val primaryColor = AccentPurple
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                ) {
                                    val barWidth = 6.dp.toPx()
                                    val spaceBetween = 4.dp.toPx()
                                    val totalWidth = size.width
                                    val count = viewModel.amplitudes.size
                                    val startX = (totalWidth - (count * (barWidth + spaceBetween))) / 2

                                    for (i in 0 until count) {
                                        val heightFactor = viewModel.amplitudes.getOrNull(i) ?: 0.1f
                                        val barHeight = size.height * heightFactor
                                        val x = startX + i * (barWidth + spaceBetween)
                                        val y = (size.height - barHeight) / 2

                                        drawRoundRect(
                                            color = primaryColor,
                                            topLeft = Offset(x, y),
                                            size = Size(barWidth, barHeight),
                                            cornerRadius = CornerRadius(4.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Show Proposed Rule Card
                proposedRule?.let { rule ->
                    item {
                        val isInstalled = rule.app?.let { viewModel.packageResolver.isInstalled(it) } ?: true
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_rule_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AccentGreenLight
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Proposed Rule",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = AccentGreen
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Summary: ${rule.summary}", style = MaterialTheme.typography.bodySmall, color = CardOnLight)
                                Text("Action: ${rule.action}", style = MaterialTheme.typography.bodySmall, color = CardOnLightMuted)
                                Text("Match Field: ${rule.matchField}", style = MaterialTheme.typography.bodySmall, color = CardOnLightMuted)
                                Text("Match Type: ${rule.matchType}", style = MaterialTheme.typography.bodySmall, color = CardOnLightMuted)
                                rule.matchPattern?.let { Text("Pattern: $it", style = MaterialTheme.typography.bodySmall, color = CardOnLightMuted) }

                                if (!isInstalled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Warning: App package is not installed on this device.",
                                        color = AccentRed,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("ai_rule_warning_uninstalled")
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelProposedRule() },
                                        modifier = Modifier.testTag("ai_rule_cancel"),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, CardOnLightMuted),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = CardOnLight
                                        )
                                    ) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.confirmProposedRule() },
                                        modifier = Modifier.testTag("ai_rule_confirm"),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGreen,
                                            contentColor = androidx.compose.ui.graphics.Color.White
                                        )
                                    ) {
                                        Text("Confirm", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Suggestion Chips (shown only for welcome state) ──
            if (mockMessages.size <= 2) {
                val suggestions = listOf(
                    "Mute Instagram",
                    "Block Slack after 6pm",
                    "Silence promos",
                    "Mute email apps"
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("suggestion_chips_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions.size) { idx ->
                        SuggestionChip(
                            onClick = {
                                viewModel.textState.value = suggestions[idx]
                                viewModel.handleSend(suggestions[idx])
                            },
                            label = {
                                Text(
                                    suggestions[idx],
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("suggestion_chip_$idx"),
                            shape = RoundedCornerShape(20.dp),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = AccentPurple.copy(alpha = 0.5f)
                            ),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = AccentPurpleLight,
                                labelColor = AccentPurple
                            )
                        )
                    }
                }
            }

            // ── Input Bar (keeping existing design) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { viewModel.textState.value = it },
                    placeholder = { Text("Type command...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    enabled = aiStatus == AIStatus.READY
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            viewModel.handleSend(textState)
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button"),
                    enabled = aiStatus == AIStatus.READY
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (permissionManager.hasMicrophonePermission()) {
                            viewModel.toggleListening()
                        } else {
                            permissionManager.requestMicrophonePermission(permissionLauncher)
                        }
                    },
                    modifier = Modifier.testTag("chat_mic_button"),
                    enabled = aiStatus == AIStatus.READY
                ) {
                    Text("🎙️")
                }
            }
        }
    }
}

// ── Reusable Components ──

@Composable
private fun AiStatusBubble(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = containerColor,
            tonalElevation = 1.dp
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: String,
    isUser: Boolean,
    timestamp: String,
    showTimestamp: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .then(
                        // Add subtle shadow to system bubbles in light mode
                        if (!isUser && !isDark) Modifier.shadow(
                            elevation = 2.dp,
                            shape = bubbleShape
                        ) else Modifier
                    ),
                shape = bubbleShape,
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                       else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Smart timestamps: only show when requested
            if (showTimestamp) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.testTag("ai_thinking_bubble"),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val transition = rememberInfiniteTransition(label = "thinking")
                repeat(3) { index ->
                    val offsetY by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0f at 0
                                -8f at 200 + (index * 150)
                                0f at 400 + (index * 150)
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(AccentPurple.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}
