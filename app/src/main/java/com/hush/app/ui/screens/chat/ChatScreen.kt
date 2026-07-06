package com.hush.app.ui.screens.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.ChatMessage
import com.hush.app.domain.model.ChatRole
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIStatus
import com.hush.app.ui.components.AttributeChip
import com.hush.app.ui.components.HushHeader
import com.hush.app.ui.components.StatusPill
import com.hush.app.ui.theme.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val aiEngine = viewModel.aiEngine
    val permissionManager = viewModel.permissionManager
    val context = LocalContext.current

    val messages = viewModel.messages
    val proposedRule = viewModel.proposedRule.value
    val errorMessage = viewModel.errorMessage.value
    val isListening = viewModel.isListening.value
    val isProcessing = viewModel.isProcessing.value
    val textState = viewModel.textState.value

    val aiStatus by aiEngine.status.collectAsState()
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
    val listState = rememberLazyListState()

    // Keep the conversation pinned to the latest content
    LaunchedEffect(messages.size, isProcessing, proposedRule, errorMessage, isListening) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // Input is usable whenever we're not still probing the device: with
    // Gemini Nano we parse with AI, otherwise the built-in parser takes over.
    val inputEnabled = aiStatus != AIStatus.CHECKING

    Scaffold(
        modifier = modifier.testTag("chat_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HushHeader(
                title = "Hush",
                subtitle = "Tell your phone what deserves attention",
                trailing = {
                    when (aiStatus) {
                        AIStatus.READY -> StatusPill("Gemini Nano", SageGreen)
                        AIStatus.CHECKING -> StatusPill("Checking…", DuskGold)
                        AIStatus.DOWNLOADING -> StatusPill("Downloading", SlateBlue)
                        else -> StatusPill("Basic mode", PlumMist)
                    }
                }
            )

            // ── Chat Messages ──
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                state = listState
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
                                accent = DuskGold
                            ) {
                                Column {
                                    Text(
                                        "Set up on-device AI",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Hush can download Gemini Nano (~350 MB) for smarter command understanding. Until then, a built-in parser handles simple commands.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.startModelDownload() },
                                            modifier = Modifier.testTag("ai_download_button"),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DuskGold,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Set Up AI", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.openAICoreUpdateInStore(context) },
                                            modifier = Modifier.testTag("ai_update_button"),
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Text("Check for Updates", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        AIStatus.DOWNLOADING -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_downloading_banner"),
                                accent = SlateBlue
                            ) {
                                Column {
                                    Text(
                                        "Downloading Gemini Nano…",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "This may take a few minutes. Please stay on WiFi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = SlateBlue,
                                        trackColor = SlateBlue.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }

                        AIStatus.ERROR -> {
                            AiStatusBubble(
                                modifier = Modifier.testTag("ai_error_banner"),
                                accent = MaterialTheme.colorScheme.error
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            aiErrorMessage ?: "This may be temporary. Tap Retry to try again.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.retryAICheck() },
                                        modifier = Modifier.testTag("ai_retry_button"),
                                        shape = CircleShape
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
                                accent = PlumMist
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = "Basic mode",
                                            tint = PlumMist,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Running in basic mode",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        aiErrorMessage
                                            ?: "Gemini Nano isn't available on this device, so Hush uses its built-in parser. Simple commands like \"Mute Instagram\" or \"Block Slack after 6pm\" work great.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.retryAICheck() },
                                            modifier = Modifier.testTag("ai_retry_button"),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry AI", fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.openAICoreUpdateInStore(context) },
                                            modifier = Modifier.testTag("ai_update_button"),
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurface
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
                items(messages.size) { index ->
                    val message = messages[index]
                    val previous = messages.getOrNull(index - 1)
                    // Show timestamp when the sender changes or on the last message
                    val showTimestamp = previous?.role != message.role || index == messages.size - 1
                    ChatBubble(
                        message = message,
                        timestamp = message.time.format(timeFormatter),
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
                                    .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp)
                                    .testTag("chat_error_message")
                            ) {
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Show voice waveform UI
                if (isListening) {
                    item {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_waveform_ui"),
                            shape = MaterialTheme.shapes.large,
                            color = primaryColor.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Listening…",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = primaryColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
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
                        ProposedRuleCard(
                            rule = rule,
                            isInstalled = rule.app?.let { viewModel.packageResolver.isInstalled(it) } ?: true,
                            appLabel = rule.app?.let { ChatViewModel.resolveAppDisplayName(it) },
                            onConfirm = { viewModel.confirmProposedRule() },
                            onCancel = { viewModel.cancelProposedRule() }
                        )
                    }
                }
            }

            // ── Suggestion Chips (shown only for welcome state) ──
            if (messages.size <= 1) {
                val suggestions = listOf(
                    "Mute Instagram",
                    "Block Slack after 6pm",
                    "Silence promos",
                    "Mute WhatsApp except from Mom"
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
                            shape = CircleShape,
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outline
                            ),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // ── Input Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bordered pill input with the mic living inside it
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = textState,
                            onValueChange = { viewModel.textState.value = it },
                            placeholder = {
                                Text(
                                    "Try \"Mute Instagram\"…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            enabled = inputEnabled,
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        IconButton(
                            onClick = {
                                if (permissionManager.hasMicrophonePermission()) {
                                    viewModel.toggleListening()
                                } else {
                                    permissionManager.requestMicrophonePermission(permissionLauncher)
                                }
                            },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(40.dp)
                                .testTag("chat_mic_button"),
                            enabled = inputEnabled,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isListening) EmberRed else Color.Transparent,
                                contentColor = if (isListening) Color.White
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isListening) "Stop listening" else "Voice command",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                // Solid ink send button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                ) {
                    IconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                viewModel.handleSend(textState)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_send_button"),
                        enabled = inputEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Reusable Components ──

@Composable
private fun AiStatusBubble(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            color = accent?.copy(alpha = 0.08f) ?: MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                1.dp,
                accent?.copy(alpha = 0.30f) ?: MaterialTheme.colorScheme.outline
            )
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    timestamp: String,
    showTimestamp: Boolean = true
) {
    val isUser = message.role == ChatRole.USER
    val bubbleShape = RoundedCornerShape(
        topStart = if (isUser) 20.dp else 6.dp,
        topEnd = if (isUser) 6.dp else 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (!isUser) {
                // Serif "h" monogram — the quiet voice of the app
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "h",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = HushSerif,
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                if (isUser) {
                    // Solid ink bubble — the user's voice carries the brand color
                    Box(
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .clip(bubbleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.widthIn(max = 290.dp),
                        shape = bubbleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (showTimestamp) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposedRuleCard(
    rule: ParsedCommand,
    isInstalled: Boolean,
    appLabel: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val actionColor = when (rule.action) {
        RuleAction.BLOCK -> EmberRed
        RuleAction.MUTE -> DuskGold
        RuleAction.ALLOW -> SageGreen
    }
    val actionIcon = when (rule.action) {
        RuleAction.BLOCK -> Icons.Outlined.Block
        RuleAction.MUTE -> Icons.Outlined.VolumeOff
        RuleAction.ALLOW -> Icons.Outlined.DoneAll
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_rule_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, actionColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(actionColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "New rule ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = actionColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        rule.summary,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = HushSerif),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attribute chips describing the parsed rule
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AttributeChip(icon = actionIcon, label = rule.action.name.lowercase().replaceFirstChar { it.uppercase() }, color = actionColor)
                AttributeChip(icon = Icons.Outlined.Apps, label = appLabel ?: "All apps", color = SlateBlue)
                if (rule.timeStart != null || rule.timeEnd != null) {
                    val window = listOfNotNull(
                        rule.timeStart?.format(timeFormatter),
                        rule.timeEnd?.format(timeFormatter)
                    ).joinToString("–")
                    AttributeChip(icon = Icons.Outlined.Schedule, label = window, color = HarborTeal)
                }
            }
            if (rule.matchPattern != null || rule.isInverted) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rule.matchPattern?.let { pattern ->
                        val patternIcon = if (rule.matchField == MatchField.SENDER) Icons.Outlined.Person else Icons.Outlined.Search
                        AttributeChip(icon = patternIcon, label = "\"$pattern\"", color = PlumMist)
                    }
                    if (rule.isInverted) {
                        AttributeChip(icon = Icons.Outlined.SwapHoriz, label = "Exception rule", color = DuskGold)
                    }
                }
            }

            if (!isInstalled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("ai_rule_warning_uninstalled")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EmberRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "This app isn't installed on this device.",
                        color = EmberRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("ai_rule_cancel"),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("ai_rule_confirm"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = actionColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("Add Rule", fontWeight = FontWeight.SemiBold)
                }
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
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}
