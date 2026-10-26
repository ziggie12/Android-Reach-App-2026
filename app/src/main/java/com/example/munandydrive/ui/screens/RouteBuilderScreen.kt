package com.example.munandydrive.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.munandydrive.MunandyApp
import com.example.munandydrive.data.model.User
import com.example.munandydrive.data.model.UserRole
import com.example.munandydrive.ui.viewmodel.CustomerViewModel
import com.example.munandydrive.ui.viewmodel.RouteViewModel
import com.example.munandydrive.ui.viewmodel.TrackingViewModel
import com.example.munandydrive.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

// ── Local data class (not serialized) ────────────────────────────────────────
private data class PendingStop(
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val customerId: Int? = null  // non-null = existing customer, skip re-creation on save
)

// Harare, Zimbabwe — sensible default centre for the app
private val DEFAULT_CENTER = LatLng(-17.8292, 31.0522)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteBuilderScreen(
    navController: NavHostController,
    routeViewModel: RouteViewModel,
    userViewModel: UserViewModel,
    customerViewModel: CustomerViewModel,
    trackingViewModel: TrackingViewModel,
    editRouteId: Int? = null
) {
    val users       by userViewModel.users.collectAsState()
    val routes      by routeViewModel.routes.collectAsState()
    val syncedRoute by trackingViewModel.syncedRoute.collectAsState()
    val salesmen    = users.filter { it.role == UserRole.SALESMAN }

    val editRouteName = remember(editRouteId, routes) {
        editRouteId?.let { id -> routes.firstOrNull { it.id == id }?.name } ?: ""
    }

    var step         by remember { mutableStateOf(if (editRouteId != null) 2 else 1) }
    var routeName    by remember { mutableStateOf("") }
    var routeDate    by remember { mutableStateOf("") }
    var assignedTo   by remember { mutableStateOf<User?>(null) }
    val pendingStops = remember { mutableStateListOf<PendingStop>() }
    var saveError    by remember { mutableStateOf<String?>(null) }
    var saving       by remember { mutableStateOf(false) }
    var stopsLoaded  by remember { mutableStateOf(false) }
    // Set once the new route exists on the server, so retrying a failed save reuses it
    // instead of creating a duplicate route.
    var createdRouteId by remember { mutableStateOf<Int?>(null) }
    val scope        = rememberCoroutineScope()

    LaunchedEffect(Unit) { userViewModel.refreshUsers() }

    // In edit mode: load the route's existing stops so they appear on the map
    LaunchedEffect(editRouteId) {
        if (editRouteId != null) trackingViewModel.loadRoute(editRouteId)
    }
    LaunchedEffect(syncedRoute) {
        if (editRouteId != null && !stopsLoaded) {
            val route = syncedRoute ?: return@LaunchedEffect
            if (route.routeId == editRouteId) {
                pendingStops.clear()
                route.stops.forEach { s ->
                    if (s.lat != 0.0 || s.lng != 0.0) {
                        pendingStops.add(PendingStop(
                            name       = s.name,
                            address    = s.address.ifBlank { null },
                            lat        = s.lat,
                            lng        = s.lng,
                            customerId = s.customerId
                        ))
                    }
                }
                stopsLoaded = true
            }
        }
    }

    // Show a loading indicator while existing stops are being fetched
    val loadingExisting = editRouteId != null && !stopsLoaded

    if (step == 1) {
        RouteDetailsStep(
            routeName    = routeName,
            routeDate    = routeDate,
            salesmen     = salesmen,
            assignedTo   = assignedTo,
            onNameChange = { routeName = it },
            onDateChange = { routeDate = it },
            onAssign     = { assignedTo = it },
            onBack       = { navController.popBackStack() },
            onContinue   = { step = 2 }
        )
    } else {
        MapStopPickerStep(
            routeName      = if (editRouteId != null) editRouteName else routeName,
            pendingStops   = pendingStops,
            saving         = saving,
            saveError      = saveError,
            loadingExisting = loadingExisting,
            onBack         = { if (editRouteId != null) navController.popBackStack() else step = 1 },
            onRemoveStop   = { idx -> pendingStops.removeAt(idx) },
            onAddStop      = { name, address, lat, lng ->
                pendingStops.add(PendingStop(name, address, lat, lng))
            },
            onSave = {
                if (pendingStops.isEmpty()) {
                    saveError = "Tap the map to add at least one stop"
                    return@MapStopPickerStep
                }
                saveError = null
                saving = true
                scope.launch {
                    try {
                        // Every step is retry-safe, so tapping Save again after a failure
                        // never creates duplicate customers or a duplicate route.
                        // 1. Create customers for newly tapped stops, remembering their IDs.
                        pendingStops.forEachIndexed { i, stop ->
                            if (stop.customerId == null) {
                                val id = customerViewModel.createForRoute(stop.name, stop.lat, stop.lng, stop.address)
                                pendingStops[i] = stop.copy(customerId = id)
                            }
                        }
                        val customerIds = pendingStops.map { it.customerId!! }
                        // 2. Create the route once (edit mode already has one).
                        val routeId = editRouteId ?: createdRouteId
                            ?: routeViewModel.createRouteOnly(routeName, routeDate, assignedTo?.id)
                                .also { createdRouteId = it }
                        // 3. The server replaces the route's whole stop list, so repeating this is safe.
                        routeViewModel.addStops(routeId, customerIds)
                        saving = false
                        navController.popBackStack()
                    } catch (e: Exception) {
                        saveError = e.message ?: "Failed to save route"
                        saving = false
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1 — Route details form
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDetailsStep(
    routeName: String, routeDate: String,
    salesmen: List<User>, assignedTo: User?,
    onNameChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onAssign: (User?) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var salesExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route Builder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Route Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Give your route a name, pick a date, then tap Continue to place stops on the map.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = routeName, onValueChange = onNameChange,
                label = { Text("Route Name *") },
                leadingIcon = { Icon(Icons.Default.Route, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = routeDate, onValueChange = onDateChange,
                label = { Text("Date *") },
                placeholder = { Text("YYYY-MM-DD") },
                leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (salesmen.isNotEmpty()) {
                ExposedDropdownMenuBox(salesExpanded, { salesExpanded = it }) {
                    OutlinedTextField(
                        value = assignedTo?.name ?: "Unassigned",
                        onValueChange = {},
                        label = { Text("Assign to Salesman") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(salesExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(salesExpanded, { salesExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Unassigned") },
                            onClick = { onAssign(null); salesExpanded = false }
                        )
                        salesmen.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u.name) },
                                onClick = { onAssign(u); salesExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = routeName.isNotBlank() && routeDate.isNotBlank()
            ) {
                Icon(Icons.Default.Map, null)
                Spacer(Modifier.width(8.dp))
                Text("Continue to Map →", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2 — Full-screen map stop picker
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStopPickerStep(
    routeName: String,
    pendingStops: List<PendingStop>,
    saving: Boolean,
    saveError: String?,
    loadingExisting: Boolean = false,
    onBack: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onAddStop: (name: String, address: String?, lat: Double, lng: Double) -> Unit,
    onSave: () -> Unit
) {
    // Dialog state
    var tappedLatLng     by remember { mutableStateOf<LatLng?>(null) }
    var stopDialogName   by remember { mutableStateOf("") }
    var stopDialogAddr   by remember { mutableStateOf("") }

    // Location permission
    val context = LocalContext.current
    val locationHelper = (context.applicationContext as MunandyApp).locationHelper
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        locationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                          perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Map camera
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, 12f)
    }

    // Request permission on first open, then pan camera to real location
    LaunchedEffect(Unit) {
        if (!locationGranted) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            val loc = locationHelper.getLastLocation()
            if (loc != null) {
                cameraState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f)
                )
            }
        }
    }

    // Auto-pan to first stop when it's added
    LaunchedEffect(pendingStops.size) {
        if (pendingStops.size == 1) {
            val s = pendingStops.first()
            cameraState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lng), 14f)
            )
        }
    }

    Box(Modifier.fillMaxSize()) {

        // ── Google Map ────────────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = locationGranted),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false),

            onMapClick = { latLng ->
                tappedLatLng = latLng
                stopDialogName = ""
                stopDialogAddr = ""
            }
        ) {
            // Polyline connecting stops in order
            if (pendingStops.size >= 2) {
                Polyline(
                    points = pendingStops.map { LatLng(it.lat, it.lng) },
                    color  = Color(0xFF1976D2),
                    width  = 5f
                )
            }
            // Numbered markers
            pendingStops.forEachIndexed { i, stop ->
                Marker(
                    state   = rememberMarkerState(position = LatLng(stop.lat, stop.lng)),
                    title   = "${i + 1}. ${stop.name}",
                    snippet = stop.address ?: "%.5f, %.5f".format(stop.lat, stop.lng)
                )
            }
        }

        // ── Back / header chip (top-left) ────────────────────────────────────
        Card(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(routeName, style = MaterialTheme.typography.labelLarge)
            }
        }

        // ── Loading overlay while existing stops are fetched ─────────────────
        if (loadingExisting) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading existing stops…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── "Tap map to add stop" hint (centre, only when no stops) ──────────
        AnimatedVisibility(
            visible = pendingStops.isEmpty() && !loadingExisting,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TouchApp, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Tap anywhere on the\nmap to add a stop",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ── Bottom card: stop list + save ─────────────────────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(16.dp)) {

                // Header row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (pendingStops.isEmpty()) "No stops yet"
                        else "${pendingStops.size} stop${if (pendingStops.size == 1) "" else "s"} added",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onSave,
                        enabled = pendingStops.isNotEmpty() && !saving
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Route")
                        }
                    }
                }

                // Error message
                saveError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                // Stop list (max 4 visible, scrollable)
                if (pendingStops.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        itemsIndexed(pendingStops) { i, stop ->
                            PendingStopRow(
                                number  = i + 1,
                                stop    = stop,
                                onRemove = { onRemoveStop(i) }
                            )
                            if (i < pendingStops.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 44.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── "Add Stop" dialog (appears when user taps map) ───────────────────────
    tappedLatLng?.let { latLng ->
        AlertDialog(
            onDismissRequest = { tappedLatLng = null },
            title = { Text("Add Stop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Location: %.5f, %.5f".format(latLng.latitude, latLng.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = stopDialogName,
                        onValueChange = { stopDialogName = it },
                        label = { Text("Stop Name *") },
                        placeholder = { Text("e.g. City Bakery") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = stopDialogAddr,
                        onValueChange = { stopDialogAddr = it },
                        label = { Text("Address (optional)") },
                        placeholder = { Text("e.g. 12 Main Street") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddStop(
                            stopDialogName.trim(),
                            stopDialogAddr.trim().ifBlank { null },
                            latLng.latitude, latLng.longitude
                        )
                        tappedLatLng = null
                    },
                    enabled = stopDialogName.isNotBlank()
                ) { Text("Add Stop") }
            },
            dismissButton = {
                TextButton(onClick = { tappedLatLng = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PendingStopRow(number: Int, stop: PendingStop, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            Alignment.Center
        ) {
            Text(
                "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Text(
                stop.address ?: "%.5f, %.5f".format(stop.lat, stop.lng),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Remove",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp))
        }
    }
}
