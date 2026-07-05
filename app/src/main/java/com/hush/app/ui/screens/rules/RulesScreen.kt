package com.hush.app.ui.screens.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.Rule
import com.hush.app.domain.model.RuleAction
import com.hush.app.ui.components.AttributeChip
import com.hush.app.ui.components.EmptyState
import com.hush.app.ui.components.HushHeader
import com.hush.app.ui.theme.*
import java.time.format.DateTimeFormatter

private fun actionColor(action: RuleAction): Color = when (action) {
    RuleAction.BLOCK -> AccentRed
    RuleAction.MUTE -> AccentAmber
    RuleAction.ALLOW -> AccentGreen
}

private fun actionIcon(action: RuleAction): ImageVector = when (action) {
    RuleAction.BLOCK -> Icons.Outlined.Block
    RuleAction.MUTE -> Icons.Outlined.VolumeOff
    RuleAction.ALLOW -> Icons.Outlined.DoneAll
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rulesList by viewModel.rulesList.collectAsState()
    var selectedRule by remember { mutableStateOf<Rule?>(null) }
    var rulePendingDeletion by remember { mutableStateOf<Rule?>(null) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("rules_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HushHeader(
                title = "Rules",
                subtitle = if (rulesList.isEmpty()) "Your filters live here"
                           else "${rulesList.count { it.enabled }} of ${rulesList.size} active",
                leadingIcon = Icons.Outlined.FilterAlt
            )

            // ── Content ──
            if (rulesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("rules_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.FilterAlt,
                        title = "No active rules",
                        message = "Head to Chat and tell Hush what to filter — try \"Mute Instagram\"."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = rulesList,
                        key = { _, rule -> rule.id }
                    ) { _, rule ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                // Ask for confirmation before deleting; don't
                                // let the card actually dismiss on swipe.
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    rulePendingDeletion = rule
                                }
                                false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.animateItem(),
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(AccentRed)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Rule",
                                        tint = Color.White
                                    )
                                }
                            },
                            content = {
                                RuleCard(
                                    rule = rule,
                                    timeFormatter = timeFormatter,
                                    onClick = { selectedRule = rule },
                                    onToggle = { viewModel.toggleRuleEnabled(rule) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Rule Detail / Edit Dialog ──
    if (selectedRule != null) {
        val rule = selectedRule!!
        var actionState by remember(rule) { mutableStateOf(rule.action) }
        AlertDialog(
            onDismissRequest = { selectedRule = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Column {
                    Text(
                        "Rule details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(rule.name, style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Original prompt", "\"${rule.originalPrompt}\"")
                    DetailRow("App", rule.appDisplayName ?: rule.appPackage ?: "All apps")
                    DetailRow("Matches", buildString {
                        append(rule.matchField.name.lowercase().replaceFirstChar { it.uppercase() })
                        append(" · ")
                        append(rule.matchType.name.lowercase())
                        rule.matchPattern?.let { append(" \"$it\"") }
                        if (rule.isInverted) append(" (exception)")
                    })
                    if (rule.timeStart != null || rule.timeEnd != null) {
                        DetailRow(
                            "Active window",
                            listOfNotNull(
                                rule.timeStart?.format(timeFormatter),
                                rule.timeEnd?.format(timeFormatter)
                            ).joinToString(" – ")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Action",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleAction.entries.filter { it != RuleAction.ALLOW }.forEach { action ->
                            val selected = actionState == action
                            val color = actionColor(action)
                            FilterChip(
                                selected = selected,
                                onClick = { actionState = action },
                                label = {
                                    Text(
                                        action.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = actionIcon(action),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color.copy(alpha = 0.15f),
                                    selectedLabelColor = color,
                                    selectedLeadingIconColor = color
                                ),
                                modifier = Modifier.testTag("rule_edit_action_${action.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { rulePendingDeletion = rule },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rule_delete_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentRed
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Rule")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateRule(rule.copy(action = actionState))
                        selectedRule = null
                    },
                    modifier = Modifier.testTag("rule_edit_save_button")
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRule = null }) {
                    Text("Close")
                }
            },
            modifier = Modifier.testTag("rule_detail_dialog")
        )
    }

    // ── Delete Confirmation Dialog ──
    if (rulePendingDeletion != null) {
        val rule = rulePendingDeletion!!
        AlertDialog(
            onDismissRequest = { rulePendingDeletion = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete rule?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "This can't be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule)
                        if (selectedRule?.id == rule.id) selectedRule = null
                        rulePendingDeletion = null
                    },
                    modifier = Modifier.testTag("rule_delete_confirm_button")
                ) {
                    Text("Delete", color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rulePendingDeletion = null }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("rule_delete_dialog")
        )
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val color = actionColor(rule.action)
    val contentAlpha = if (rule.enabled) 1f else 0.55f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("rule_card_${rule.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Action-colored icon tile
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = if (rule.enabled) 0.15f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = actionIcon(rule.action),
                    contentDescription = null,
                    tint = color.copy(alpha = contentAlpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Rule info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttributeChip(
                        icon = Icons.Outlined.Apps,
                        label = rule.appDisplayName ?: rule.appPackage ?: "All apps",
                        color = AccentBlue.copy(alpha = contentAlpha)
                    )
                    if (rule.timeStart != null || rule.timeEnd != null) {
                        AttributeChip(
                            icon = Icons.Outlined.Schedule,
                            label = listOfNotNull(
                                rule.timeStart?.format(timeFormatter),
                                rule.timeEnd?.format(timeFormatter)
                            ).joinToString("–"),
                            color = AccentTeal.copy(alpha = contentAlpha)
                        )
                    }
                    if (rule.isInverted) {
                        AttributeChip(
                            icon = Icons.Outlined.SwapHoriz,
                            label = "Exception",
                            color = AccentAmber.copy(alpha = contentAlpha)
                        )
                    } else if (rule.matchPattern != null) {
                        val patternIcon = if (rule.matchField == MatchField.SENDER) Icons.Outlined.Person else Icons.Outlined.Search
                        AttributeChip(
                            icon = patternIcon,
                            label = "\"${rule.matchPattern}\"",
                            color = AccentPurple.copy(alpha = contentAlpha)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Toggle switch
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = color,
                    checkedThumbColor = Color.White
                ),
                modifier = Modifier.testTag("rule_toggle_${rule.id}")
            )
        }
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
