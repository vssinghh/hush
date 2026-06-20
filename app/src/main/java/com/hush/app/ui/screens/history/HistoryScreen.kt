package com.hush.app.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hush.app.domain.model.NotificationEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Interception History") })
        },
        modifier = modifier.testTag("history_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search logs") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                singleLine = true
            )

            // Tabs for Filtering
            val tabs = listOf(
                Pair("All", "all"),
                Pair("BLOCK", "blocked"),
                Pair("MUTE", "muted"),
                Pair("ALLOW", "allowed")
            )
            val currentTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)

            TabRow(
                selectedTabIndex = currentTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { (tabValue, tabLabel) ->
                    Tab(
                        selected = selectedTab == tabValue,
                        onClick = { viewModel.setSelectedTab(tabValue) },
                        text = {
                            val displayText = when (tabValue) {
                                "All" -> "All"
                                "BLOCK" -> "Blocked"
                                "MUTE" -> "Muted"
                                "ALLOW" -> "Allowed"
                                else -> tabValue
                            }
                            Text(displayText)
                        },
                        modifier = Modifier.testTag("history_tab_$tabLabel")
                    )
                }
            }

            // Logs List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(
                    items = historyLogs,
                    key = { it.id }
                ) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLog = log },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.appName, style = MaterialTheme.typography.titleMedium)
                                val textSnippet = log.text ?: "No Content"
                                Text(textSnippet, style = MaterialTheme.typography.bodyMedium)
                            }
                            val timeStr = runCatching { timeFormatter.format(log.timestamp) }.getOrDefault("")
                            Text(timeStr, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
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
}
