package com.hush.app.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.Rule
import com.hush.app.domain.model.RuleAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rulesList by viewModel.rulesList.collectAsState()
    var selectedRule by remember { mutableStateOf<Rule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Rules Management") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Navigate or open create dialog */ }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rule")
            }
        },
        modifier = modifier.testTag("rules_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (rulesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("rules_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No active rules", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(
                        items = rulesList,
                        key = { it.id }
                    ) { rule ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteRule(rule)
                                    true
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
                                        .clip(CardDefaults.shape)
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
                                        .testTag("rule_card_${rule.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            val displayName = rule.name
                                            Text(displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(rule.appDisplayName ?: rule.appPackage ?: "All Apps", style = MaterialTheme.typography.bodySmall)
                                        }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = actionState == RuleAction.BLOCK,
                            onClick = { actionState = RuleAction.BLOCK },
                            modifier = Modifier.testTag("rule_edit_action_block")
                        )
                        Text("BLOCK")
                        RadioButton(
                            selected = actionState == RuleAction.MUTE,
                            onClick = { actionState = RuleAction.MUTE },
                            modifier = Modifier.testTag("rule_edit_action_mute")
                        )
                        Text("MUTE")
                        RadioButton(
                            selected = actionState == RuleAction.ALLOW,
                            onClick = { actionState = RuleAction.ALLOW },
                            modifier = Modifier.testTag("rule_edit_action_allow")
                        )
                        Text("ALLOW")
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
}
