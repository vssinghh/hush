package com.hush.app.ui.screens.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.Rule
import com.hush.app.domain.model.RuleAction
import com.hush.app.ui.theme.*

// Accent color pairs cycled by index % 6
private data class AccentPair(val main: Color, val light: Color)

private val accentPairs = listOf(
    AccentPair(AccentPurple, AccentPurpleLight),
    AccentPair(AccentBlue, AccentBlueLight),
    AccentPair(AccentGreen, AccentGreenLight),
    AccentPair(AccentRed, AccentRedLight),
    AccentPair(AccentAmber, AccentAmberLight),
    AccentPair(AccentTeal, AccentTealLight),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rulesList by viewModel.rulesList.collectAsState()
    var selectedRule by remember { mutableStateOf<Rule?>(null) }
    var rulePendingDeletion by remember { mutableStateOf<Rule?>(null) }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("rules_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Content ──
            if (rulesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("rules_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No rules yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Head to Chat and tell the AI what to filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = rulesList,
                        key = { _, rule -> rule.id }
                    ) { index, rule ->
                        val accent = accentPairs[index % accentPairs.size]

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    rulePendingDeletion=rule
                                    false
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Red.copy(alpha = 0.8f))
                                        .padding(horizontal = 16.dp),
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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedRule = rule }
                                        .testTag("rule_card_${rule.id}"),
                                    shape = RoundedCornerShape(16.dp),
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
                                        // Colored icon circle
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(accent.light),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Notifications,
                                                contentDescription = null,
                                                tint = accent.main,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Rule info
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rule.name,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = rule.appDisplayName
                                                    ?: rule.appPackage
                                                    ?: "All Apps",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (rule.action == RuleAction.BLOCK || rule.action == RuleAction.MUTE) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (rule.action == RuleAction.BLOCK) AccentRed.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = rule.action.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (rule.action == RuleAction.BLOCK) AccentRed else AccentAmber,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Toggle switch
                                        Switch(
                                            checked = rule.enabled,
                                            onCheckedChange = {
                                                viewModel.toggleRuleEnabled(rule)
                                            },
                                            modifier = Modifier.testTag("rule_toggle_${rule.id}")
                                        )
                                    }
                                }
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
            title = { Text(rule.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original Prompt: ${rule.originalPrompt}")
                    if (rule.appPackage != null) {
                        Text("Package: ${rule.appPackage}")
                    }
                    if (rule.appDisplayName != null) {
                        Text("App Name: ${rule.appDisplayName}")
                    }
                    Text("Match Field: ${rule.matchField}")
                    Text("Match Type: ${rule.matchType}")
                    if (rule.matchPattern != null) {
                        Text("Pattern: ${rule.matchPattern}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Action:")
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        RuleAction.entries.filter { it != RuleAction.ALLOW }.forEach { action ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { actionState = action }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = actionState == action,
                                    onClick = { actionState = action },
                                    modifier = Modifier.testTag("rule_edit_action_${action.name.lowercase()}")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(action.name)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Delete button
                    OutlinedButton(
                        onClick = {
                            rulePendingDeletion=rule
                        },
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
                    Text("Save")
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
    if (rulePendingDeletion != null) {
        val rule = rulePendingDeletion!!

        AlertDialog(
            onDismissRequest = {
                rulePendingDeletion = null
            },
            title = {
                Text("Delete rule?")
            },
            text = {
                Column {
                    Text(rule.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This action cannot be undone.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule)

                        if (selectedRule?.id == rule.id) {
                            selectedRule = null
                        }

                        rulePendingDeletion = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rulePendingDeletion = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
