package com.hush.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import com.hush.app.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val historyLogs by viewModel.historyLogs.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    var selectedLog by remember { mutableStateOf<NotificationEvent?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen")
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Search Input + Clear All ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search notifications...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            IconButton(
                onClick = { showClearDialog = true },
                enabled = historyLogs.isNotEmpty(),
                modifier = Modifier.testTag("history_clear_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear all history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            FilterChip(
                selected = selectedFilter == RuleAction.BLOCK,
                onClick = {
                    viewModel.toggleFilter(RuleAction.BLOCK)
                },
                label = {
                    Text("Blocked")
                }
            )

            FilterChip(
                selected = selectedFilter == RuleAction.MUTE,
                onClick = {
                    viewModel.toggleFilter(RuleAction.MUTE)
                },
                label = {
                    Text("Muted")
                }
            )

            FilterChip(
                selected = selectedFilter == RuleAction.ALLOW,
                onClick = {
                    viewModel.toggleFilter(RuleAction.ALLOW)
                },
                label = {
                    Text("Delivered")
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // ── Logs List ──
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("history_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = historyLogs,
                key = { it.id }
            ) { log ->
                HistoryEntryCard(
                    log = log,
                    timeFormatter = timeFormatter,
                    onClick = { selectedLog = log }
                )
            }
        }
    }

    // ── Detail Dialog ──
    if (selectedLog != null) {
        val log = selectedLog!!
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            title = { Text(log.appName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Package: ${log.packageName}")
                    Text("Title: ${log.title ?: "No Title"}")
                    Text("Content: ${log.text ?: "No Content"}")
                    if (log.sender != null) {
                        Text("Sender: ${log.sender}")
                    }
                    Text("Action: ${log.actionTaken}")
                    val ruleText = if (log.matchedRuleId == null && log.matchedRuleName != null) {
                        "Rule deleted"
                    } else {
                        log.matchedRuleName ?: "None"
                    }
                    Text("Triggered by Rule: $ruleText")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLog = null }) {
                    Text("Close")
                }
            },
            modifier = Modifier.testTag("history_detail_dialog")
        )
    }

    // ── Clear All Confirmation Dialog ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently delete all notification logs.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("history_clear_dialog")
        )
    }
}

// ── Single History Entry Card ──
@Composable
private fun HistoryEntryCard(
    log: NotificationEvent,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val iconColors = remember {
        listOf(
            AccentPurple, AccentBlue, AccentGreen,
            AccentRed, AccentAmber, AccentTeal
        )
    }
    val iconColor = remember(log.appName) {
        iconColors[log.appName.hashCode().absoluteValue % iconColors.size]
    }
    val initial = remember(log.appName) {
        log.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── App Icon Circle ──
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
            }

            // ── Content Column ──
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: App name + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val timeStr = runCatching { timeFormatter.format(log.timestamp) }.getOrDefault("")
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Status badge + preview text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(action = log.actionTaken)

                    Text(
                        text = log.text ?: "No Content",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Status Badge Pill ──
@Composable
private fun StatusBadge(action: RuleAction) {
    val (label, textColor, bgColor) = when (action) {
        RuleAction.ALLOW -> Triple("Delivered", StatusDelivered, StatusDeliveredBg)
        RuleAction.MUTE -> Triple("Muted", StatusMuted, StatusMutedBg)
        RuleAction.BLOCK -> Triple("Blocked", StatusBlocked, StatusBlockedBg)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
