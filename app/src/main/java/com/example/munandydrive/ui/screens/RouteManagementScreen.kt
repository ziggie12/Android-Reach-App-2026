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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import com.example.munandydrive.data.model.Route
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.data.model.User
import com.example.munandydrive.data.model.UserRole
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.RouteUiState
import com.example.munandydrive.ui.viewmodel.RouteViewModel
import com.example.munandydrive.ui.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteManagementScreen(
    navController: NavHostController,
    routeViewModel: RouteViewModel,
    userViewModel: UserViewModel
) {
    val routes      by routeViewModel.routes.collectAsState()
    val uiState     by routeViewModel.uiState.collectAsState()
    val deleteError by routeViewModel.deleteError.collectAsState()
    val users       by userViewModel.users.collectAsState()
    var showCreate   by remember { mutableStateOf(false) }
    var statusTarget by remember { mutableStateOf<Route?>(null) }
    var deleteTarget by remember { mutableStateOf<Route?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        routeViewModel.refreshRoutes()
        userViewModel.refreshUsers()
    }

    DisposableEffect(Unit) {
        routeViewModel.startPolling()
        onDispose { routeViewModel.stopPolling() }
    }

    LaunchedEffect(deleteError) {
        deleteError?.let { msg ->
            scope.launch { snackbarHostState.showSnackbar("Delete failed: $msg") }
            routeViewModel.clearDeleteError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route Management") },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, "Create Route")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState is RouteUiState.Loading && routes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState is RouteUiState.Error && routes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((uiState as RouteUiState.Error).message,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { routeViewModel.refreshRoutes() }) { Text("Retry") }
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(routes) { route ->
                        RouteItem(
                            route         = route,
                            onStatusClick = { statusTarget = route },
                            onBuildClick  = { navController.navigate(Screen.RouteBuilder.editRoute(route.id)) },
                            onMapClick    = { navController.navigate(Screen.RouteDetail.createRoute(route.id)) },
                            onDeleteClick = { deleteTarget = route }
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                    if (routes.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                Text("No routes yet. Tap + to create one.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { route ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Route") },
            text  = { Text("Delete \"${route.name}\"? This removes all stops and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        routeViewModel.deleteRoute(route.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // Status change dialog
    statusTarget?.let { route ->
        StatusChangeDialog(
            currentStatus = route.status,
            onDismiss     = { statusTarget = null },
            onConfirm     = { newStatus ->
                routeViewModel.updateRouteStatus(route.id, newStatus)
                statusTarget = null
            }
        )
    }

    // Create route dialog
    if (showCreate) {
        CreateRouteDialog(
            salesmen  = users.filter { it.role == UserRole.SALESMAN },
            onDismiss = { showCreate = false },
            onConfirm = { name, date, assignedTo ->
                routeViewModel.createRoute(name, date, assignedTo)
                showCreate = false
            }
        )
    }
}

@Composable
private fun RouteItem(
    route: Route,
    onStatusClick: () -> Unit,
    onBuildClick: () -> Unit,
    onMapClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(route.name, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Column {
                Text("Assigned to: ${route.assignedToName ?: "Unassigned"}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Date: ${route.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                StatusBadge(route.status, onClick = onStatusClick)
                Row(horizontalArrangement = Arrangement.End) {
                    if (route.status == RouteStatus.DRAFT) {
                        TextButton(
                            onClick = onBuildClick,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Build", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = onMapClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Map, "View on Map",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete, "Delete Route",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun StatusBadge(status: RouteStatus, onClick: (() -> Unit)? = null) {
    val color = when (status) {
        RouteStatus.DRAFT    -> Color.Gray
        RouteStatus.ACTIVE   -> MaterialTheme.colorScheme.primary
        RouteStatus.COMPLETE -> Color(0xFF4CAF50)
        RouteStatus.INACTIVE -> MaterialTheme.colorScheme.error
    }
    Surface(
        color  = color,
        shape  = RoundedCornerShape(4.dp),
        onClick = onClick ?: {}
    ) {
        Text(status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun StatusChangeDialog(
    currentStatus: RouteStatus,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val statuses = listOf("Draft", "Active", "Complete", "Inactive")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                statuses.forEach { s ->
                    val isCurrent = s.uppercase() == currentStatus.name
                    OutlinedButton(
                        onClick = { onConfirm(s) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isCurrent)
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ButtonDefaults.outlinedButtonColors()
                    ) { Text(s) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Suppress("OPT_IN_USAGE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRouteDialog(
    salesmen: List<User>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: String, assignedTo: Int?) -> Unit
) {
    var name           by remember { mutableStateOf("") }
    var date           by remember { mutableStateOf("") }
    var assignedTo     by remember { mutableStateOf<User?>(null) }
    var salesExpanded  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Route") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Route Name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("2026-06-07") })
                if (salesmen.isNotEmpty()) {
                    ExposedDropdownMenuBox(salesExpanded, { salesExpanded = it }) {
                        OutlinedTextField(
                            value = assignedTo?.name ?: "Unassigned",
                            onValueChange = {},
                            label = { Text("Assign to Salesman") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(salesExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(salesExpanded, { salesExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Unassigned") },
                                onClick = { assignedTo = null; salesExpanded = false })
                            salesmen.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u.name) },
                                    onClick = { assignedTo = u; salesExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, date, assignedTo?.id) },
                enabled = name.isNotBlank() && date.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
