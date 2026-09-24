package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(navController: NavHostController, adminViewModel: AdminViewModel) {
    val stats   by adminViewModel.stats.collectAsState()
    val loading by adminViewModel.loading.collectAsState()

    LaunchedEffect(Unit) { adminViewModel.loadStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = { adminViewModel.loadStats() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.AccountCircle, "Profile")
                    }
                }
            )
        },
        bottomBar = { AdminBottomBar(navController) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()
        ) {
            Text("Today's Summary", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryCard("Active Routes",  stats.activeRoutes.toString(),  Icons.Default.Route,       Modifier.weight(1f))
                SummaryCard("Active Drivers", stats.activeDrivers.toString(), Icons.Default.Person,      Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryCard("Stops Done",     stats.stopsCompleted.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
                SummaryCard("Pending",        stats.stopsPending.toString(),   Icons.Default.Schedule,    Modifier.weight(1f))
            }

            Spacer(Modifier.height(32.dp))
            Text("Quick Links", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp))

            val quickLinks = listOf(
                QuickLink("Users",    Icons.Default.People,   Screen.UserManagement.route),
                QuickLink("Routes",   Icons.Default.Map,      Screen.RouteManagement.route),
                QuickLink("Tracking", Icons.Default.MyLocation, Screen.ActiveRouteList.route),
                QuickLink("Reports",  Icons.Default.BarChart, Screen.Reports.route)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement   = Arrangement.spacedBy(16.dp)
            ) {
                items(quickLinks) { link ->
                    Card(onClick = { navController.navigate(link.route) },
                        modifier = Modifier.height(100.dp)) {
                        Column(Modifier.fillMaxSize(),
                            Arrangement.Center, Alignment.CenterHorizontally) {
                            Icon(link.icon, null)
                            Spacer(Modifier.height(8.dp))
                            Text(link.title)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, content = content)
    } else {
        Card(modifier = modifier, colors = colors, content = content)
    }
}

@Composable
fun AdminBottomBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Dashboard") },
            selected = true, onClick = {})
        NavigationBarItem(
            icon = { Icon(Icons.Default.People, null) }, label = { Text("Users") },
            selected = false, onClick = { navController.navigate(Screen.UserManagement.route) })
        NavigationBarItem(
            icon = { Icon(Icons.Default.Map, null) }, label = { Text("Routes") },
            selected = false, onClick = { navController.navigate(Screen.RouteManagement.route) })
        NavigationBarItem(
            icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Reports") },
            selected = false, onClick = { navController.navigate(Screen.Reports.route) })
    }
}

data class QuickLink(val title: String, val icon: ImageVector, val route: String)
