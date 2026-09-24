package com.example.munandydrive.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.munandydrive.ui.viewmodel.TrackingUiState
import com.example.munandydrive.ui.viewmodel.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    navController: NavHostController,
    routeId: Int,
    stopId: Int,
    trackingViewModel: TrackingViewModel
) {
    val syncedRoute  by trackingViewModel.syncedRoute.collectAsState()
    val routeUiState by trackingViewModel.routeUiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(routeId) {
        if (syncedRoute == null) trackingViewModel.loadRoute(routeId)
    }

    val stop = syncedRoute?.stops?.firstOrNull { it.id == stopId }

    var notesText      by remember { mutableStateOf("") }
    var skipReasonText by remember { mutableStateOf("") }
    var showSkipDialog by remember { mutableStateOf(false) }

    // Pre-fill notes if stop already has them
    LaunchedEffect(stop) {
        stop?.notes?.let { notesText = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stop?.name ?: "Stop Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            routeUiState is TrackingUiState.Loading && stop == null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            stop == null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    Text("Stop not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Status badge
                    val (statusColor, statusBg) = when (stop.status) {
                        "Completed" -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
                        "Arrived"   -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
                        "Skipped"   -> Color(0xFFC62828) to Color(0xFFFFEBEE)
                        else        -> Color(0xFF757575) to Color(0xFFF5F5F5)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            stop.status.uppercase(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(stop.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (stop.address.isNotBlank()) {
                        Text(stop.address, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Contact — tap to call
                    if (!stop.contact.isNullOrBlank()) {
                        Card(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${stop.contact}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Contact", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stop.contact, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Opening hours
                    if (!stop.openingTime.isNullOrBlank()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Hours", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${stop.openingTime} – ${stop.closingTime ?: "?"}",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Preferred products
                    if (!stop.preferredProducts.isNullOrBlank()) {
                        Text("Preferred Products", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(stop.preferredProducts, modifier = Modifier.padding(14.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Existing notes / skip reason
                    if (!stop.notes.isNullOrBlank()) {
                        Text("Visit Notes", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(stop.notes, modifier = Modifier.padding(14.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!stop.skippedReason.isNullOrBlank()) {
                        Text("Skip Reason", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(stop.skippedReason, modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Action area — only for Arrived stops
                    if (stop.status == "Arrived") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text("Visit Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSkipDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Skip Stop")
                            }
                            Button(
                                onClick = {
                                    trackingViewModel.markCompleted(routeId, stop.id, notesText)
                                    navController.popBackStack()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Complete")
                            }
                        }
                    }

                    // Navigation + delivery action
                    Spacer(Modifier.height(8.dp))
                    val navigateOnClick = {
                        val uri = Uri.parse("google.navigation:q=${stop.lat},${stop.lng}&mode=d")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${stop.lat},${stop.lng}")))
                        }
                        Unit
                    }
                    if (stop.status == "Pending") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = navigateOnClick, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Navigate")
                            }
                            Button(
                                onClick = {
                                    trackingViewModel.markArrived(routeId, stop.id)
                                    navController.popBackStack()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Icon(Icons.Default.Place, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Arrived")
                            }
                        }
                    } else {
                        OutlinedButton(onClick = navigateOnClick, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Navigate Here")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Skip dialog
    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false; skipReasonText = "" },
            title = { Text("Skip Stop") },
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
                        trackingViewModel.markSkipped(routeId, stop!!.id, skipReasonText)
                        showSkipDialog = false
                        navController.popBackStack()
                    },
                    enabled = skipReasonText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false; skipReasonText = "" }) { Text("Cancel") }
            }
        )
    }
}
