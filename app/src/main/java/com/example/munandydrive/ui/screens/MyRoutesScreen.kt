package com.example.munandydrive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.data.model.Route
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.RouteUiState
import com.example.munandydrive.ui.viewmodel.RouteViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun Route.parsedDate(): LocalDate? = try {
    LocalDate.parse(date.trim(), fmt)
} catch (_: DateTimeParseException) { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRoutesScreen(navController: NavHostController, viewModel: RouteViewModel) {
    val routes  by viewModel.routes.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshRoutes() }

    // Recent = today ± 30 days; older routes hidden unless user taps "Show all"
    val today    = remember { LocalDate.now() }
    val cutoff   = remember { today.minusDays(30) }
    val visible  = remember(routes, showAll) {
        if (showAll) routes
        else routes.filter { r -> r.parsedDate()?.isAfter(cutoff) ?: true }
    }
    val hiddenCount = routes.size - visible.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Routes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshRoutes() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState is RouteUiState.Loading && routes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState is RouteUiState.Error && routes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((uiState as RouteUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refreshRoutes() }) { Text("Retry") }
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.padding(paddingValues).fillMaxSize()) {
                    items(visible) { route ->
                        ListItem(
                            headlineContent = { Text(route.name) },
                            supportingContent = { Text("Date: ${route.date}") },
                            trailingContent = { StatusBadge(route.status) },
                            modifier = Modifier.clickable {
                                if (route.status == RouteStatus.ACTIVE) {
                                    navController.navigate(Screen.ActiveRoute.createRoute(route.id))
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                    if (hiddenCount > 0 && !showAll) {
                        item {
                            TextButton(
                                onClick = { showAll = true },
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Text("Show $hiddenCount older route${if (hiddenCount > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (visible.isEmpty() && routes.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                Text("No routes assigned yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
