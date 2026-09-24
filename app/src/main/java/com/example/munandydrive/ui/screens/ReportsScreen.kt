package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.data.model.ReportsSummary
import com.example.munandydrive.data.model.SalesmanReport
import com.example.munandydrive.ui.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavHostController, reportsViewModel: ReportsViewModel) {
    val summary by reportsViewModel.summary.collectAsState()
    val loading by reportsViewModel.loading.collectAsState()
    val error   by reportsViewModel.error.collectAsState()

    LaunchedEffect(Unit) { reportsViewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reportsViewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { reportsViewModel.load() }) { Text("Retry") }
                    }
                }
            }

            summary != null -> ReportContent(
                summary = summary!!,
                modifier = Modifier.padding(paddingValues)
            )

            else -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReportContent(summary: ReportsSummary, modifier: Modifier = Modifier) {
    val totalRoutes = summary.draftRoutes + summary.activeRoutes + summary.completeRoutes + summary.inactiveRoutes
    val totalStops  = summary.pendingStops + summary.arrivedStops + summary.completedStops + summary.skippedStops
    val completionRate = if (totalStops > 0)
        (summary.completedStops * 100f / totalStops).toInt() else 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Overview cards ──────────────────────────────────────────────────
        item {
            Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    modifier    = Modifier.weight(1f),
                    value       = "$completionRate%",
                    label       = "Completion Rate",
                    icon        = Icons.Default.CheckCircle,
                    tint        = Color(0xFF2E7D32)
                )
                StatCard(
                    modifier    = Modifier.weight(1f),
                    value       = "${summary.completedStops}",
                    label       = "Stops Done",
                    icon        = Icons.Default.Place,
                    tint        = Color(0xFF1565C0)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    modifier    = Modifier.weight(1f),
                    value       = summary.avgStopDurationMinutes?.let { "${it}m" } ?: "—",
                    label       = "Avg Stop Time",
                    icon        = Icons.Default.Timer,
                    tint        = Color(0xFF6A1B9A)
                )
                StatCard(
                    modifier    = Modifier.weight(1f),
                    value       = "${summary.skippedStops}",
                    label       = "Stops Skipped",
                    icon        = Icons.Default.Cancel,
                    tint        = Color(0xFFC62828)
                )
            }
        }

        // ── Route breakdown ─────────────────────────────────────────────────
        item {
            Text("Routes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteStatRow("Active",   summary.activeRoutes,   Color(0xFF1565C0))
                    HorizontalDivider()
                    RouteStatRow("Complete", summary.completeRoutes, Color(0xFF2E7D32))
                    HorizontalDivider()
                    RouteStatRow("Draft",    summary.draftRoutes,    Color(0xFF757575))
                    HorizontalDivider()
                    RouteStatRow("Inactive", summary.inactiveRoutes, Color(0xFF9E9E9E))
                    HorizontalDivider()
                    RouteStatRow("Total",    totalRoutes,            MaterialTheme.colorScheme.primary,
                        bold = true)
                }
            }
        }

        // ── Per-salesman table ───────────────────────────────────────────────
        if (summary.salesmen.isNotEmpty()) {
            item {
                Text("Salesman Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            items(summary.salesmen) { salesman ->
                SalesmanCard(salesman)
            }
        } else {
            item {
                Text("Salesman Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No salesmen data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RouteStatRow(label: String, count: Int, color: Color, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SalesmanCard(s: SalesmanReport) {
    val completionPct = if (s.stopsTotal > 0) (s.stopsCompleted * 100f / s.stopsTotal).toInt() else 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$completionPct%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        completionPct >= 80 -> Color(0xFF2E7D32)
                        completionPct >= 50 -> Color(0xFFE65100)
                        else               -> Color(0xFFC62828)
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            if (s.stopsTotal > 0) {
                LinearProgressIndicator(
                    progress = { completionPct / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        completionPct >= 80 -> Color(0xFF2E7D32)
                        completionPct >= 50 -> Color(0xFFE65100)
                        else               -> Color(0xFFC62828)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Routes", "${s.routesAssigned}")
                MiniStat("Done", "${s.stopsCompleted}")
                MiniStat("Skipped", "${s.stopsSkipped}")
                MiniStat("Total", "${s.stopsTotal}")
            }
        }
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
