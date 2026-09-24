package com.example.munandydrive.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.munandydrive.MunandyApp
import com.example.munandydrive.data.model.SyncedStop
import com.example.munandydrive.ui.viewmodel.TrackingUiState
import com.example.munandydrive.ui.viewmodel.TrackingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun launchNavigation(context: android.content.Context, lat: Double, lng: Double) {
    val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
    }
}

// Stop status → map marker colour
private fun stopHue(status: String) = when (status) {
    "Completed" -> BitmapDescriptorFactory.HUE_GREEN
    "Arrived"   -> BitmapDescriptorFactory.HUE_AZURE
    "Skipped"   -> BitmapDescriptorFactory.HUE_RED
    else        -> BitmapDescriptorFactory.HUE_YELLOW
}

// Stop status → surface tint
private fun stopColor(status: String) = when (status) {
    "Completed" -> Color(0xFFE8F5E9)
    "Arrived"   -> Color(0xFFE3F2FD)
    "Skipped"   -> Color(0xFFFFEBEE)
    else        -> Color.Unspecified
}

private enum class TimeWindowStatus { OPEN, CLOSING_SOON, NOT_OPEN_YET, CLOSED, UNKNOWN }

private fun timeWindowStatus(openingTime: String?, closingTime: String?): TimeWindowStatus {
    if (openingTime == null || closingTime == null) return TimeWindowStatus.UNKNOWN
    return try {
        val fmt  = DateTimeFormatter.ofPattern("HH:mm")
        val now  = LocalTime.now()
        val open = LocalTime.parse(openingTime, fmt)
        val close = LocalTime.parse(closingTime, fmt)
        when {
            now.isBefore(open)  -> TimeWindowStatus.NOT_OPEN_YET
            now.isAfter(close)  -> TimeWindowStatus.CLOSED
            java.time.Duration.between(now, close).toMinutes() <= 30 -> TimeWindowStatus.CLOSING_SOON
            else                -> TimeWindowStatus.OPEN
        }
    } catch (_: Exception) { TimeWindowStatus.UNKNOWN }
}

private fun Bitmap.toBase64Jpeg(): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRouteScreen(
    routeId: Int,
    trackingViewModel: TrackingViewModel,
    onBack: () -> Unit,
    onViewSummary: () -> Unit = {}
) {
    val syncedRoute  by trackingViewModel.syncedRoute.collectAsState()
    val routeUiState by trackingViewModel.routeUiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(routeId) {
        trackingViewModel.loadRoute(routeId)
        trackingViewModel.startLocationPosting(routeId)
    }
    DisposableEffect(Unit) {
        onDispose { trackingViewModel.stopLocationPosting() }
    }

    val stops = syncedRoute?.stops.orEmpty()
    val nextStop = stops.firstOrNull { it.status == "Pending" }

    val locationHelper = (context.applicationContext as MunandyApp).locationHelper

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-17.8292, 31.0522), 14f)
    }

    // Pan to phone GPS on open; LaunchedEffect(nextStop) will override to the stop once loaded
    LaunchedEffect(Unit) {
        val loc = locationHelper.getLastLocation()
        if (loc != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14f)
            )
        }
    }

    LaunchedEffect(nextStop) {
        nextStop?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 14f), 800
            )
        }
    }

    var showAllStops      by remember { mutableStateOf(false) }
    var skipTarget        by remember { mutableStateOf<SyncedStop?>(null) }
    var completeTarget    by remember { mutableStateOf<SyncedStop?>(null) }
    var selectedStop      by remember { mutableStateOf<SyncedStop?>(null) }
    var notesText         by remember { mutableStateOf("") }
    var skipReasonText    by remember { mutableStateOf("") }
    var podBitmap         by remember { mutableStateOf<Bitmap?>(null) }

    // Camera launcher for proof-of-delivery (returns thumbnail Bitmap)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> podBitmap = bitmap }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraLauncher.launch(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Route", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAllStops = true }) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, "All Stops")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            // ── MAP ──────────────────────────────────────────────────────────
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                val coords = stops.map { LatLng(it.lat, it.lng) }
                if (coords.size >= 2) {
                    Polyline(points = coords, color = Color(0xFF1976D2), width = 6f)
                }
                stops.forEachIndexed { idx, stop ->
                    Marker(
                        state   = MarkerState(LatLng(stop.lat, stop.lng)),
                        title   = "${idx + 1}. ${stop.name}",
                        snippet = stop.status,
                        icon    = BitmapDescriptorFactory.defaultMarker(stopHue(stop.status)),
                        onClick = { selectedStop = stop; true }
                    )
                }
            }

            // ── LOADING / ERROR ──────────────────────────────────────────────
            when (routeUiState) {
                is TrackingUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is TrackingUiState.Error -> Card(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        (routeUiState as TrackingUiState.Error).message,
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                else -> {}
            }

            // ── NEXT STOP CARD ───────────────────────────────────────────────
            if (nextStop != null) {
                Card(
                    modifier  = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("NEXT STOP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(nextStop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(nextStop.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                        // Time window with colour-coded status
                        if (nextStop.openingTime != null) {
                            val twStatus = timeWindowStatus(nextStop.openingTime, nextStop.closingTime)
                            val (twColor, twLabel) = when (twStatus) {
                                TimeWindowStatus.OPEN         -> Color(0xFF2E7D32) to "Open"
                                TimeWindowStatus.CLOSING_SOON -> Color(0xFFE65100) to "Closing soon"
                                TimeWindowStatus.NOT_OPEN_YET -> Color(0xFF1565C0) to "Not open yet"
                                TimeWindowStatus.CLOSED       -> Color(0xFFC62828) to "CLOSED"
                                TimeWindowStatus.UNKNOWN      -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) to ""
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Open ${nextStop.openingTime} – ${nextStop.closingTime ?: "?"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = twColor
                                )
                                if (twLabel.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = twColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            twLabel,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = twColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { launchNavigation(context, nextStop.lat, nextStop.lng) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Navigate")
                            }
                            Button(
                                onClick = { trackingViewModel.markArrived(routeId, nextStop.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Icon(Icons.Default.Place, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Arrived")
                            }
                        }
                    }
                }
            }

            // ── ARRIVED STOP CARD ────────────────────────────────────────────
            val arrivedStop = stops.firstOrNull { it.status == "Arrived" }
            if (arrivedStop != null && nextStop == null) {
                Card(
                    modifier  = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("AT STOP", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                        Text(arrivedStop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { skipTarget = arrivedStop },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Skip")
                            }
                            Button(
                                onClick = { completeTarget = arrivedStop; podBitmap = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Complete")
                            }
                        }
                    }
                }
            }

            // All-done message
            if (stops.isNotEmpty() && stops.all { it.status == "Completed" || it.status == "Skipped" }) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                            Spacer(Modifier.width(12.dp))
                            Text("All stops done! Great work.", fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32))
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onViewSummary,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Summarize, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View Summary & Submit")
                        }
                    }
                }
            }
        }
    }

    // ── ALL STOPS SHEET ──────────────────────────────────────────────────────
    if (showAllStops) {
        ModalBottomSheet(onDismissRequest = { showAllStops = false }) {
            Text("All Stops", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(stops) { idx, stop ->
                    StopListItem(idx + 1, stop,
                        onArrived   = { trackingViewModel.markArrived(routeId, stop.id); showAllStops = false },
                        onComplete  = { completeTarget = stop; podBitmap = null; showAllStops = false },
                        onSkip      = { skipTarget = stop; showAllStops = false },
                        onNavigate  = { launchNavigation(context, stop.lat, stop.lng); showAllStops = false }
                    )
                }
            }
        }
    }

    // ── STOP ACTION SHEET (marker tap or list row tap) ───────────────────────
    selectedStop?.let { stop ->
        ModalBottomSheet(onDismissRequest = { selectedStop = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stop.name, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text(stop.address, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stopColor(stop.status).let { if (it == Color.Unspecified) Color(0xFFF5F5F5) else it }
                    ) {
                        Text(stop.status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusTextColor(stop.status), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(20.dp))
                when (stop.status) {
                    "Pending" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { launchNavigation(context, stop.lat, stop.lng); selectedStop = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Navigate")
                        }
                        Button(
                            onClick = { trackingViewModel.markArrived(routeId, stop.id); selectedStop = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.Place, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Arrived")
                        }
                    }
                    "Arrived" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { launchNavigation(context, stop.lat, stop.lng); selectedStop = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Navigate")
                            }
                            Button(
                                onClick = { completeTarget = stop; podBitmap = null; selectedStop = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Complete")
                            }
                        }
                        OutlinedButton(
                            onClick = { skipTarget = stop; selectedStop = null },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Skip Stop")
                        }
                    }
                    else -> OutlinedButton(
                        onClick = { launchNavigation(context, stop.lat, stop.lng); selectedStop = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Navigate Here")
                    }
                }
            }
        }
    }

    // ── COMPLETE DIALOG (with POD photo) ────────────────────────────────────
    completeTarget?.let { stop ->
        AlertDialog(
            onDismissRequest = { completeTarget = null; notesText = ""; podBitmap = null },
            title = { Text("Complete: ${stop.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (podBitmap != null) {
                        Image(
                            bitmap = podBitmap!!.asImageBitmap(),
                            contentDescription = "POD photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        TextButton(
                            onClick = { podBitmap = null },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Remove photo") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Take Proof-of-Delivery Photo")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val photo = podBitmap?.toBase64Jpeg()
                    trackingViewModel.markCompleted(routeId, stop.id, notesText, photo)
                    completeTarget = null; notesText = ""; podBitmap = null
                }) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { completeTarget = null; notesText = ""; podBitmap = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── SKIP DIALOG ──────────────────────────────────────────────────────────
    skipTarget?.let { stop ->
        AlertDialog(
            onDismissRequest = { skipTarget = null; skipReasonText = "" },
            title = { Text("Skip: ${stop.name}") },
            text = {
                OutlinedTextField(
                    value = skipReasonText,
                    onValueChange = { skipReasonText = it },
                    label = { Text("Reason for skipping") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        trackingViewModel.markSkipped(routeId, stop.id, skipReasonText)
                        skipTarget = null; skipReasonText = ""
                    },
                    enabled = skipReasonText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { skipTarget = null; skipReasonText = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StopListItem(
    number: Int,
    stop: SyncedStop,
    onArrived: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onNavigate: () -> Unit
) {
    val bg = stopColor(stop.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(statusBadgeColor(stop.status)),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, fontWeight = FontWeight.SemiBold)
            Text(stop.address, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(stop.status, style = MaterialTheme.typography.labelSmall,
                color = statusTextColor(stop.status), fontWeight = FontWeight.Bold)
        }
        if (stop.status == "Pending") {
            IconButton(onClick = onNavigate) {
                Icon(Icons.Default.Navigation, "Navigate", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onArrived) {
                Icon(Icons.Default.Place, "Arrived", tint = Color(0xFF1976D2))
            }
            IconButton(onClick = onSkip) {
                Icon(Icons.Default.Close, "Skip", tint = MaterialTheme.colorScheme.error)
            }
        } else if (stop.status == "Arrived") {
            IconButton(onClick = onNavigate) {
                Icon(Icons.Default.Navigation, "Navigate", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onComplete) {
                Icon(Icons.Default.Check, "Complete", tint = Color(0xFF2E7D32))
            }
            IconButton(onClick = onSkip) {
                Icon(Icons.Default.Close, "Skip", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

private fun statusBadgeColor(status: String) = when (status) {
    "Completed" -> Color(0xFF2E7D32)
    "Arrived"   -> Color(0xFF1565C0)
    "Skipped"   -> Color(0xFFC62828)
    else        -> Color(0xFF757575)
}

private fun statusTextColor(status: String) = when (status) {
    "Completed" -> Color(0xFF2E7D32)
    "Arrived"   -> Color(0xFF1565C0)
    "Skipped"   -> Color(0xFFC62828)
    else        -> Color(0xFF757575)
}
