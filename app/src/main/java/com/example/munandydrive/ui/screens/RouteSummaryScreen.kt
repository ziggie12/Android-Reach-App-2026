package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.data.model.SyncedStop
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.TrackingViewModel
import java.time.Instant
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSummaryScreen(
    navController: NavHostController,
    routeId: Int,
    trackingViewModel: TrackingViewModel
) {
    val syncedRoute by trackingViewModel.syncedRoute.collectAsState()

    LaunchedEffect(routeId) {
        if (syncedRoute == null) trackingViewModel.loadRoute(routeId)
    }

    val stops = syncedRoute?.stops.orEmpty()
    val completed = stops.filter { it.status == "Completed" }
    val skipped   = stops.filter { it.status == "Skipped" }
    val pending   = stops.filter { it.status == "Pending" || it.status == "Arrived" }

    var showPendingWarning by remember { mutableStateOf(false) }

    // Calculate duration from first arrived_at to last completed_at
    val durationText = run {
        val arrivals   = stops.mapNotNull { it.arrivedAt?.let { t -> runCatching { Instant.parse(t) }.getOrNull() } }
        val completions = stops.mapNotNull { it.completedAt?.let { t -> runCatching { Instant.parse(t) }.getOrNull() } }
        if (arrivals.isNotEmpty() && completions.isNotEmpty()) {
            val mins = Duration.between(arrivals.min(), completions.max()).toMinutes()
            if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        } else "—"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route Summary") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Summary", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        SummaryRow("Total Stops",     "${stops.size}")
                        SummaryRow("Completed",       "${completed.size}")
                        SummaryRow("Skipped",         "${skipped.size}")
                        SummaryRow("Completion Rate",
                            if (stops.isNotEmpty()) "${(completed.size * 100 / stops.size)}%" else "—")
                        SummaryRow("Time Taken",      durationText)
                    }
                }
            }

            // Stop list header
            item {
                Text("Stop Details", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }

            items(stops) { stop ->
                StopSummaryItem(stop)
            }

            // Submit button
            item {
                Spacer(Modifier.height(8.dp))
                if (pending.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${pending.size} stop${if (pending.size != 1) "s" else ""} not yet completed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        if (pending.isNotEmpty()) {
                            showPendingWarning = true
                        } else {
                            trackingViewModel.completeRoute(routeId)
                            navController.navigate(Screen.SalesmanDashboard.route) {
                                popUpTo(Screen.SalesmanDashboard.route) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit & Sync")
                }
            }
        }
    }

    if (showPendingWarning) {
        AlertDialog(
            onDismissRequest = { showPendingWarning = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Incomplete Stops") },
            text = {
                Text(
                    "${pending.size} stop${if (pending.size != 1) "s are" else " is"} still pending. " +
                    "Submitting now will close the route. Continue anyway?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPendingWarning = false
                        trackingViewModel.completeRoute(routeId)
                        navController.navigate(Screen.SalesmanDashboard.route) {
                            popUpTo(Screen.SalesmanDashboard.route) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Submit Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showPendingWarning = false }) { Text("Go Back") }
            }
        )
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StopSummaryItem(stop: SyncedStop) {
    val (icon, tint, bg) = when (stop.status) {
        "Completed" -> Triple(Icons.Default.CheckCircle,  Color(0xFF2E7D32), Color(0xFFE8F5E9))
        "Skipped"   -> Triple(Icons.Default.HighlightOff, Color(0xFFC62828), Color(0xFFFFEBEE))
        else        -> Triple(Icons.Default.RadioButtonUnchecked, Color(0xFF757575), Color.Transparent)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        ListItem(
            headlineContent = {
                Text(stop.name, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Column {
                    if (stop.address.isNotBlank()) {
                        Text(stop.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    stop.skippedReason?.let {
                        Text("Skipped: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    stop.notes?.let {
                        Text("Note: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            trailingContent = {
                Icon(icon, contentDescription = stop.status, tint = tint)
            }
        )
    }
}
