package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.MunandyApp
import com.example.munandydrive.data.model.LoggedInUser
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.data.model.User
import com.example.munandydrive.data.model.UserRole
import com.example.munandydrive.ui.navigation.Screen
import com.example.munandydrive.ui.viewmodel.RouteViewModel
import com.example.munandydrive.ui.viewmodel.TrackingViewModel
import com.example.munandydrive.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

private val DETAIL_FALLBACK_CENTER = LatLng(-17.8292, 31.0522) // Harare — only used if GPS unavailable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: Int,
    navController: NavHostController,
    trackingViewModel: TrackingViewModel,
    routeViewModel: RouteViewModel,
    userViewModel: UserViewModel
) {
    val syncedRoute by trackingViewModel.syncedRoute.collectAsState()
    val routes      by routeViewModel.routes.collectAsState()
    val users       by userViewModel.users.collectAsState()

    val route    = routes.firstOrNull { it.id == routeId }
    val salesmen = users.filter { it.role == UserRole.SALESMAN }
    val stops    = syncedRoute?.stops ?: emptyList()
    val located  = stops.filter { it.lat != 0.0 && it.lng != 0.0 }

    var showAssignDialog  by remember { mutableStateOf(false) }
    var showStatusDialog  by remember { mutableStateOf(false) }
    var showStopsSheet    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var stopToDelete      by remember { mutableStateOf<Int?>(null) }

    val app = LocalContext.current.applicationContext as MunandyApp
    var currentUser by remember { mutableStateOf<LoggedInUser?>(null) }
    val canAssign = currentUser?.role in listOf("Admin", "Supervisor")
    val canDelete = currentUser?.role == "Admin" ||
                    (currentUser?.role == "Supervisor" && route?.createdBy == currentUser?.id)

    LaunchedEffect(routeId) {
        currentUser = app.tokenManager.getStoredUser()
        trackingViewModel.loadRoute(routeId)
        routeViewModel.refreshRoutes()
        userViewModel.refreshUsers()
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DETAIL_FALLBACK_CENTER, 12f)
    }

    // Pan to phone GPS first; animate to first route stop once stops load
    LaunchedEffect(Unit) {
        val loc = app.locationHelper.getLastLocation()
        if (loc != null) {
            cameraState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14f)
            )
        }
    }
    LaunchedEffect(located) {
        if (located.isNotEmpty()) {
            cameraState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(located.first().lat, located.first().lng), 13f), 600
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route?.name ?: "Route Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showStopsSheet = true }) {
                        Icon(Icons.Default.List, "Stops list")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── Map ───────────────────────────────────────────────────────────
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                uiSettings = MapUiSettings(myLocationButtonEnabled = false)
            ) {
                // Polyline through all located stops
                if (located.size >= 2) {
                    Polyline(
                        points = located.map { LatLng(it.lat, it.lng) },
                        color  = Color(0xFF1976D2),
                        width  = 5f
                    )
                }
                // Markers — colour-coded by stop status
                located.forEachIndexed { i, stop ->
                    val hue = when (stop.status) {
                        "Completed" -> BitmapDescriptorFactory.HUE_GREEN
                        "Arrived"   -> BitmapDescriptorFactory.HUE_AZURE
                        "Skipped"   -> BitmapDescriptorFactory.HUE_RED
                        else        -> BitmapDescriptorFactory.HUE_YELLOW
                    }
                    Marker(
                        state   = rememberMarkerState(position = LatLng(stop.lat, stop.lng)),
                        title   = "${i + 1}. ${stop.name}",
                        snippet = stop.address,
                        icon    = BitmapDescriptorFactory.defaultMarker(hue)
                    )
                }
            }

            // ── Info card (bottom) ────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {

                    if (route != null) {
                        // Route name + status badge
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    route.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Date: ${route.date}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Assigned to: ${route.assignedToName ?: "Unassigned"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${stops.size} stop${if (stops.size == 1) "" else "s"}${
                                        if (stops.size != located.size)
                                            " (${stops.size - located.size} without GPS)"
                                        else ""
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(route.status, onClick = { showStatusDialog = true })
                        }

                        Spacer(Modifier.height(12.dp))

                        // Action buttons — two rows to avoid crowding
                        if (canAssign) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { showAssignDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (route.assignedToName == null) "Assign" else "Reassign",
                                        maxLines = 1
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        showStopsSheet = false
                                        navController.navigate(Screen.RouteBuilder.editRoute(routeId))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Stops", maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showStatusDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Status", maxLines = 1)
                            }
                            if (canDelete) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", maxLines = 1)
                                }
                            }
                        }
                    } else {
                        // Route info not loaded yet
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Loading route…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // No-GPS hint
                    if (syncedRoute != null && located.isEmpty() && stops.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠ None of the stops have GPS coordinates. " +
                            "Stops created via the map-based builder will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Loading indicator while syncing stops
            if (syncedRoute == null) {
                Box(Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // ── Assign salesman dialog ────────────────────────────────────────────────
    if (showAssignDialog) {
        AssignSalesmanDialog(
            salesmen   = salesmen,
            currentId  = route?.assignedTo,
            onDismiss  = { showAssignDialog = false },
            onConfirm  = { userId ->
                routeViewModel.assignRoute(routeId, userId)
                showAssignDialog = false
            }
        )
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteConfirm && route != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Route") },
            text  = { Text("Delete \"${route.name}\"? This will remove all stops and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        routeViewModel.deleteRoute(route.id) { navController.popBackStack() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    // ── Status change dialog ──────────────────────────────────────────────────
    if (showStatusDialog && route != null) {
        StatusChangeDialog(
            currentStatus = route.status,
            onDismiss     = { showStatusDialog = false },
            onConfirm     = { newStatus ->
                routeViewModel.updateRouteStatus(routeId, newStatus)
                showStatusDialog = false
            }
        )
    }

    // ── All stops bottom sheet ────────────────────────────────────────────────
    if (showStopsSheet) {
        ModalBottomSheet(onDismissRequest = { showStopsSheet = false }) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "All Stops (${stops.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                if (stops.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        Alignment.Center
                    ) {
                        Text("No stops found for this route.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        itemsIndexed(stops) { i, stop ->
                            val statusColor = when (stop.status) {
                                "Completed" -> Color(0xFF4CAF50)
                                "Arrived"   -> MaterialTheme.colorScheme.primary
                                "Skipped"   -> MaterialTheme.colorScheme.error
                                else        -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            ListItem(
                                leadingContent = {
                                    Text(
                                        "${i + 1}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                headlineContent = {
                                    Text(stop.name, fontWeight = FontWeight.SemiBold)
                                },
                                supportingContent = {
                                    Text(stop.address,
                                        style = MaterialTheme.typography.bodySmall)
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            color = statusColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                stop.status,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (canAssign) {
                                            IconButton(
                                                onClick = { stopToDelete = stop.id },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete, "Remove stop",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // ── Stop delete confirmation dialog ──────────────────────────────────────
    stopToDelete?.let { stopId ->
        val stopName = stops.firstOrNull { it.id == stopId }?.name ?: "this stop"
        AlertDialog(
            onDismissRequest = { stopToDelete = null },
            title = { Text("Remove Stop") },
            text  = { Text("Remove \"$stopName\" from the route? The customer record is kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        trackingViewModel.removeStop(routeId, stopId)
                        stopToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton({ stopToDelete = null }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Assign Salesman Dialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignSalesmanDialog(
    salesmen: List<User>,
    currentId: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var selected by remember { mutableStateOf<User?>(salesmen.firstOrNull { it.id == currentId }) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Assign Salesman") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (salesmen.isEmpty()) {
                    Text("No salesmen available. Create a Salesman user first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(
                            value = selected?.name ?: "Unassigned",
                            onValueChange = {},
                            label = { Text("Salesman") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Unassigned") },
                                onClick = { selected = null; expanded = false }
                            )
                            salesmen.forEach { u ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (u.id == currentId) {
                                                Icon(Icons.Default.Check, null,
                                                    Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(u.name)
                                        }
                                    },
                                    onClick = { selected = u; expanded = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected?.id) },
                enabled = salesmen.isNotEmpty()
            ) { Text("Assign") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
