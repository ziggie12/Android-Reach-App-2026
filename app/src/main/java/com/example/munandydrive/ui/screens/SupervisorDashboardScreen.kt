package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.CustomerViewModel
import com.example.munandydrive.ui.viewmodel.RouteViewModel
import com.example.munandydrive.data.model.RouteStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorDashboardScreen(
    navController: NavHostController,
    routeViewModel: RouteViewModel,
    customerViewModel: CustomerViewModel
) {
    val routes    by routeViewModel.routes.collectAsState()
    val customers by customerViewModel.customers.collectAsState()
    LaunchedEffect(Unit) {
        routeViewModel.refreshRoutes()
        customerViewModel.refreshCustomers()
    }

    DisposableEffect(Unit) {
        routeViewModel.startPolling()
        onDispose { routeViewModel.stopPolling() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supervisor Dashboard") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        },
        bottomBar = {
            SupervisorBottomBar(navController)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Team Status",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val activeRoutes = routes.count { it.status == RouteStatus.ACTIVE }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryCard(
                    title = "Active Routes",
                    value = activeRoutes.toString(),
                    icon  = Icons.AutoMirrored.Filled.DirectionsRun,
                    modifier = Modifier.weight(1f),
                    onClick  = { navController.navigate(Screen.RouteManagement.route) }
                )
                SummaryCard(
                    title = "Customers",
                    value = customers.size.toString(),
                    icon  = Icons.Default.Store,
                    modifier = Modifier.weight(1f),
                    onClick  = { navController.navigate(Screen.CustomerManagement.route) }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Management",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val supervisorActions = listOf(
                QuickLink("My Routes", Icons.Default.Route, Screen.RouteManagement.route),
                QuickLink("Customers", Icons.Default.Groups, Screen.CustomerManagement.route),
                QuickLink("Route Builder", Icons.Default.AddLocationAlt, Screen.RouteBuilder.route),
                QuickLink("Live Tracking", Icons.Default.MyLocation, Screen.LiveTracking.route)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(supervisorActions) { link ->
                    Card(
                        onClick = { navController.navigate(link.route) },
                        modifier = Modifier.height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(link.icon, contentDescription = null)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(link.title)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupervisorBottomBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Dashboard") },
            selected = true,
            onClick = { /* Already here */ }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Groups, contentDescription = null) },
            label = { Text("Customers") },
            selected = false,
            onClick = { navController.navigate(Screen.CustomerManagement.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
            label = { Text("Builder") },
            selected = false,
            onClick = { navController.navigate(Screen.RouteBuilder.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
            label = { Text("Track") },
            selected = false,
            onClick = { navController.navigate(Screen.LiveTracking.route) }
        )
    }
}
