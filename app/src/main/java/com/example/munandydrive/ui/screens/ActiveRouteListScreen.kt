package com.example.munandydrive.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.RouteUiState
import com.example.munandydrive.ui.viewmodel.RouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRouteListScreen(navController: NavHostController, routeViewModel: RouteViewModel) {
    val routes  by routeViewModel.routes.collectAsState()
    val uiState by routeViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { routeViewModel.refreshRoutes() }

    val activeRoutes = routes.filter { it.status == RouteStatus.ACTIVE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Routes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { routeViewModel.refreshRoutes() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState is RouteUiState.Loading && routes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            activeRoutes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.LocationOff, null,
                            Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No active routes", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Routes set to Active will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { routeViewModel.refreshRoutes() }) {
                            Text("Refresh")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item {
                        Text(
                            "${activeRoutes.size} route${if (activeRoutes.size != 1) "s" else ""} on the road",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(activeRoutes) { route ->
                        ListItem(
                            headlineContent = {
                                Text(route.name, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Column {
                                    route.assignedToName?.let {
                                        Text("Driver: $it",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Date: ${route.date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            },
                            leadingContent = {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Icon(
                                            Icons.Default.DirectionsCar, null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Default.ChevronRight, "Track",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate(Screen.LiveTracking.forRoute(route.id))
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
