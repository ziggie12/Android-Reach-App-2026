package com.example.munandydrive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.munandydrive.MunandyApp
import com.example.munandydrive.data.model.DriverLocation
import com.example.munandydrive.ui.viewmodel.TrackingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

// Distinct hue per driver
private val DRIVER_HUES = listOf(
    BitmapDescriptorFactory.HUE_AZURE,
    BitmapDescriptorFactory.HUE_RED,
    BitmapDescriptorFactory.HUE_GREEN,
    BitmapDescriptorFactory.HUE_ORANGE,
    BitmapDescriptorFactory.HUE_VIOLET,
    BitmapDescriptorFactory.HUE_ROSE,
    BitmapDescriptorFactory.HUE_CYAN,
    BitmapDescriptorFactory.HUE_YELLOW
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrackingScreen(
    trackingViewModel: TrackingViewModel,
    preSelectedRouteId: Int? = null,
    onBack: () -> Unit
) {
    val driverLocations by trackingViewModel.driverLocations.collectAsState()
    val selectedDriver  by trackingViewModel.selectedDriver.collectAsState()
    val lastRefreshed   by trackingViewModel.lastRefreshed.collectAsState()

    val locationHelper = (LocalContext.current.applicationContext as MunandyApp).locationHelper

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-17.8292, 31.0522), 12f)
    }

    // Start / stop 10-second polling; also pan camera to phone GPS on open
    LaunchedEffect(Unit) {
        trackingViewModel.startLiveTracking()
        val loc = locationHelper.getLastLocation()
        if (loc != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 12f)
            )
        }
    }
    DisposableEffect(Unit) { onDispose { trackingViewModel.stopLiveTracking() } }

    // Auto-select the driver for the pre-chosen route once locations arrive
    LaunchedEffect(driverLocations, preSelectedRouteId) {
        if (preSelectedRouteId != null && selectedDriver == null) {
            val match = driverLocations.firstOrNull { it.routeId == preSelectedRouteId }
            if (match != null) trackingViewModel.selectDriver(match)
        }
    }

    // Animate camera when driver is selected
    LaunchedEffect(selectedDriver) {
        selectedDriver?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 14f), 800
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Tracking", fontWeight = FontWeight.Bold)
                        lastRefreshed?.let {
                            Text("Updated $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Box(
                        Modifier
                            .padding(end = 16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${driverLocations.size} active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = true)
            ) {
                driverLocations.forEachIndexed { index, driver ->
                    val hue  = DRIVER_HUES[index % DRIVER_HUES.size]
                    val isSelected = selectedDriver?.userId == driver.userId
                    Marker(
                        state   = MarkerState(position = LatLng(driver.lat, driver.lng)),
                        title   = driver.userName,
                        snippet = driver.routeName,
                        icon    = BitmapDescriptorFactory.defaultMarker(hue),
                        zIndex  = if (isSelected) 1f else 0f,
                        onClick = {
                            trackingViewModel.selectDriver(if (isSelected) null else driver)
                            true
                        }
                    )
                }
            }

            // Waiting state — route selected but driver hasn't posted location yet
            val waitingForDriver = preSelectedRouteId != null
                && driverLocations.none { it.routeId == preSelectedRouteId }
            if (waitingForDriver || (preSelectedRouteId == null && driverLocations.isEmpty())) {
                Card(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.LocationOff, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (waitingForDriver) "Waiting for driver location"
                            else "No active drivers",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (waitingForDriver) "The driver's location will appear once they open their route."
                            else "Drivers appear here once they start active routes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Driver colour legend (bottom-left)
            if (driverLocations.isNotEmpty()) {
                Card(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        driverLocations.forEachIndexed { index, driver ->
                            val hue        = DRIVER_HUES[index % DRIVER_HUES.size]
                            val isSelected = selectedDriver?.userId == driver.userId
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        trackingViewModel.selectDriver(if (isSelected) null else driver)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(hueToColor(hue)))
                                Spacer(Modifier.width(8.dp))
                                Text(driver.userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // Selected-driver detail card
            AnimatedVisibility(
                visible  = selectedDriver != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter    = slideInVertically { it },
                exit     = slideOutVertically { it }
            ) {
                selectedDriver?.let { DriverDetailCard(it) { trackingViewModel.selectDriver(null) } }
            }
        }
    }
}

@Composable
private fun DriverDetailCard(driver: DriverLocation, onDismiss: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(16.dp),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column {
                    Text(driver.userName,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(driver.routeName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatChip("Lat", "%.5f".format(driver.lat), Modifier.weight(1f))
                StatChip("Lng", "%.5f".format(driver.lng), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            val time = driver.timestamp
                .substringAfterLast("T").substringBefore(".").substringBefore("Z")
            Text("Last seen $time UTC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun hueToColor(hue: Float): Color = when {
    hue < 30f  -> Color(0xFFE53935)
    hue < 60f  -> Color(0xFFFB8C00)
    hue < 90f  -> Color(0xFFFDD835)
    hue < 150f -> Color(0xFF43A047)
    hue < 200f -> Color(0xFF00ACC1)
    hue < 240f -> Color(0xFF1E88E5)
    hue < 290f -> Color(0xFF8E24AA)
    hue < 330f -> Color(0xFFD81B60)
    else       -> Color(0xFFE53935)
}
