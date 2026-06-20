package com.hush.app.ui.screens.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.Rule

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
    val textState = viewModel.textState.value

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleListening()
        } else {
            viewModel.errorMessage.value = "Microphone permission is required for voice commands"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Conversational Assistant") })
        },
        modifier = modifier.testTag("chat_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Unsupported AI Banner
            AnimatedVisibility(
                visible = !aiEngine.isAvailable(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("ai_unsupported_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Error")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Gemini Nano is not supported on this device.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Message Log
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(mockMessages.size) { index ->
                    val isUser = index % 2 != 0
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isUser) 16.dp else 0.dp,
                                        bottomEnd = if (isUser) 0.dp else 16.dp
                                    )
                                )
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = mockMessages[index],
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
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
                                    .clip(RoundedCornerShape(16.dp))
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
                                .padding(8.dp)
                                .testTag("voice_waveform_ui")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Listening...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                val primaryColor = MaterialTheme.colorScheme.primary
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
                                .padding(8.dp)
                                .testTag("ai_rule_card")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Proposed Rule", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Summary: ${rule.summary}")
                                Text("Action: ${rule.action}")
                                Text("Match Field: ${rule.matchField}")
                                Text("Match Type: ${rule.matchType}")
                                rule.matchPattern?.let { Text("Pattern: $it") }

                                if (!isInstalled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Warning: App package is not installed on this device.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("ai_rule_warning_uninstalled")
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { viewModel.cancelProposedRule() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.testTag("ai_rule_cancel")
                                    ) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.confirmProposedRule() },
                                        modifier = Modifier.testTag("ai_rule_confirm")
                                    ) {
                                        Text("Confirm")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
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
                    enabled = aiEngine.isAvailable()
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            viewModel.handleSend(textState)
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button"),
                    enabled = aiEngine.isAvailable()
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
                    enabled = aiEngine.isAvailable()
                ) {
                    Text("🎙️")
                }
            }
        }
    }
}

