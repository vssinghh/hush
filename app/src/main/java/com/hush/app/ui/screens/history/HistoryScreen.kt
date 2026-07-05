package com.hush.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import com.hush.app.ui.components.EmptyState
import com.hush.app.ui.components.HushHeader
import com.hush.app.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val historyLogs by viewModel.historyLogs.collectAsState()

    var selectedLog by remember { mutableStateOf<NotificationEvent?>(null) }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault())
    }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen")
    ) {
        HushHeader(
            title = "History",
            subtitle = "Everything Hush has filtered",
            leadingIcon = Icons.Outlined.Inbox
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // ── Search Input ──
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search notifications…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
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

            Spacer(modifier = Modifier.height(10.dp))

            // ── Filter Tabs ──
            val tabs = listOf(
                Triple("All", "All", MaterialTheme.colorScheme.primary),
                Triple("BLOCK", "Blocked", StatusBlocked),
                Triple("MUTE", "Muted", StatusMuted),
                Triple("ALLOW", "Delivered", StatusDelivered)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { (key, label, color) ->
                    val selected = selectedTab == key
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setSelectedTab(key) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.15f),
                            selectedLabelColor = color
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = color.copy(alpha = 0.4f),
                            selectedBorderWidth = 1.dp
                        ),
                        modifier = Modifier.testTag("history_tab_${label.lowercase().replace("delivered", "allowed")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Logs List ──
            // The list stays composed even when empty (tests and semantics rely
            // on a stable node); the empty state is drawn over it.
            Box(modifier = Modifier.fillMaxSize()) {
                if (historyLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.Inbox,
                            title = if (searchQuery.isBlank() && selectedTab == "All") "Nothing filtered yet"
                                    else "No matches",
                            message = if (searchQuery.isBlank() && selectedTab == "All")
                                "Once your rules start catching notifications, they'll show up here."
                            else
                                "Try a different search or filter.",
                            modifier = Modifier.padding(bottom = 48.dp)
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("history_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = historyLogs,
                        key = { _, log -> log.id }
                    ) { index, log ->
                        // Day header is rendered inside the item so list
                        // children == log count (grouping without extra rows)
                        val logDate = remember(log.timestamp) {
                            log.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        val prevDate = if (index == 0) null else remember(historyLogs[index - 1].timestamp) {
                            historyLogs[index - 1].timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        Column(
                            modifier = Modifier
                                .animateItem()
                                // One structural node per log entry (the day
                                // header rides along inside the same item)
                                .semantics { isTraversalGroup = true }
                        ) {
                            if (logDate != prevDate) {
                                val label = when (logDate) {
                                    LocalDate.now() -> "Today"
                                    LocalDate.now().minusDays(1) -> "Yesterday"
                                    else -> logDate.format(dayFormatter)
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = if (index == 0) 0.dp else 10.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }
                            HistoryEntryCard(
                                log = log,
                                timeFormatter = timeFormatter,
                                onClick = { selectedLog = log }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Detail Dialog ──
    if (selectedLog != null) {
        val log = selectedLog!!
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Column {
                    Text(
                        "Notification",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(log.appName, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.width(10.dp))
                        StatusBadge(action = log.actionTaken)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Package", log.packageName)
                    DetailRow("Title", log.title ?: "No Title")
                    DetailRow("Content", log.text ?: "No Content")
                    if (log.sender != null) {
                        DetailRow("Sender", log.sender)
                    }
                    val ruleText = if (log.matchedRuleId == null && log.matchedRuleName != null) {
                        "${log.matchedRuleName} (deleted)"
                    } else {
                        log.matchedRuleName ?: "None"
                    }
                    DetailRow("Triggered by rule", ruleText)
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
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
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
        shape = RoundedCornerShape(14.dp),
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
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
