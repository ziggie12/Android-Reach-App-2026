package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.local.dao.PendingStopUpdateDao
import com.example.munandydrive.data.local.entity.PendingStopUpdateEntity
import com.example.munandydrive.data.model.DriverLocation
import com.example.munandydrive.data.model.PostLocationRequest
import com.example.munandydrive.data.model.SyncedRoute
import com.example.munandydrive.data.model.UpdateStopStatusRequest
import com.example.munandydrive.data.remote.LocationHelper
import com.example.munandydrive.data.remote.MunandyApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

sealed class TrackingUiState {
    object Idle : TrackingUiState()
    object Loading : TrackingUiState()
    object Success : TrackingUiState()
    data class Error(val message: String) : TrackingUiState()
}

class TrackingViewModel(
    private val apiService: MunandyApiService,
    private val locationHelper: LocationHelper,
    private val pendingDao: PendingStopUpdateDao
) : ViewModel() {

    // ── Live Tracking (Admin / Supervisor) ──────────────────────────────────
    private val _driverLocations = MutableStateFlow<List<DriverLocation>>(emptyList())
    val driverLocations: StateFlow<List<DriverLocation>> = _driverLocations.asStateFlow()

    private val _selectedDriver = MutableStateFlow<DriverLocation?>(null)
    val selectedDriver: StateFlow<DriverLocation?> = _selectedDriver.asStateFlow()

    private val _lastRefreshed = MutableStateFlow<String?>(null)
    val lastRefreshed: StateFlow<String?> = _lastRefreshed.asStateFlow()

    private var pollingJob: Job? = null

    fun startLiveTracking() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchDriverLocations()
                delay(10_000)
            }
        }
    }

    fun stopLiveTracking() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun fetchDriverLocations() {
        viewModelScope.launch {
            try {
                val locations = apiService.getActiveDriverLocations()
                _driverLocations.value = locations
                val now = java.time.LocalTime.now()
                _lastRefreshed.value = String.format("%02d:%02d:%02d", now.hour, now.minute, now.second)
                // keep selected driver in sync
                _selectedDriver.value = locations.find { it.userId == _selectedDriver.value?.userId }
            } catch (_: Exception) { /* keep last known data visible */ }
        }
    }

    fun selectDriver(driver: DriverLocation?) { _selectedDriver.value = driver }

    // ── Active Route (Salesman) ─────────────────────────────────────────────
    private val _syncedRoute = MutableStateFlow<SyncedRoute?>(null)
    val syncedRoute: StateFlow<SyncedRoute?> = _syncedRoute.asStateFlow()

    private val _routeUiState = MutableStateFlow<TrackingUiState>(TrackingUiState.Idle)
    val routeUiState: StateFlow<TrackingUiState> = _routeUiState.asStateFlow()

    private var locationJob: Job? = null

    fun loadRoute(routeId: Int) {
        viewModelScope.launch {
            _routeUiState.value = TrackingUiState.Loading
            try {
                // Flush any queued offline updates before loading fresh state
                flushPendingUpdates()
                _syncedRoute.value = apiService.syncRoute(routeId)
                _routeUiState.value = TrackingUiState.Success
            } catch (e: Exception) {
                _routeUiState.value = TrackingUiState.Error(e.message ?: "Failed to load route")
            }
        }
    }

    fun startLocationPosting(routeId: Int) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                postCurrentLocation(routeId)
            }
        }
    }

    fun stopLocationPosting() {
        locationJob?.cancel()
        locationJob = null
    }

    private suspend fun postCurrentLocation(routeId: Int) {
        try {
            val loc = locationHelper.getLastLocation() ?: return
            apiService.postLocation(PostLocationRequest(
                lat = loc.latitude,
                lng = loc.longitude,
                timestamp = Instant.now().toString(),
                routeId = routeId
            ))
        } catch (_: Exception) { /* non-fatal */ }
    }

    fun markArrived(routeId: Int, stopId: Int) {
        val arrivedAt = Instant.now().toString()
        viewModelScope.launch {
            postCurrentLocation(routeId)
            val request = UpdateStopStatusRequest(status = "Arrived", arrivedAt = arrivedAt)
            try {
                apiService.updateStopStatus(routeId, stopId, request)
                pendingDao.deleteForStop(routeId, stopId)
                loadRoute(routeId)
            } catch (_: Exception) {
                pendingDao.insert(PendingStopUpdateEntity(
                    routeId = routeId, stopId = stopId,
                    status = "Arrived", arrivedAt = arrivedAt
                ))
                _routeUiState.value = TrackingUiState.Error("Saved offline — will sync when connected")
            }
        }
    }

    fun markCompleted(routeId: Int, stopId: Int, notes: String, photoBase64: String? = null) {
        val completedAt = Instant.now().toString()
        viewModelScope.launch {
            val request = UpdateStopStatusRequest(
                status = "Completed",
                completedAt = completedAt,
                notes = notes.ifBlank { null }
            )
            try {
                apiService.updateStopStatus(routeId, stopId, request)
                pendingDao.deleteForStop(routeId, stopId)
                if (!photoBase64.isNullOrBlank()) {
                    try {
                        val bytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT)
                        val body  = bytes.toRequestBody("image/jpeg".toMediaType())
                        val part  = MultipartBody.Part.createFormData("photo", "pod.jpg", body)
                        apiService.uploadPod(routeId, stopId, part)
                    } catch (_: Exception) { /* POD upload failure is non-fatal */ }
                }
                loadRoute(routeId)
            } catch (_: Exception) {
                pendingDao.insert(PendingStopUpdateEntity(
                    routeId = routeId, stopId = stopId,
                    status = "Completed", completedAt = completedAt,
                    notes = notes.ifBlank { null }
                ))
                _routeUiState.value = TrackingUiState.Error("Saved offline — will sync when connected")
            }
        }
    }

    fun markSkipped(routeId: Int, stopId: Int, reason: String) {
        viewModelScope.launch {
            val request = UpdateStopStatusRequest(status = "Skipped", skippedReason = reason)
            try {
                apiService.updateStopStatus(routeId, stopId, request)
                pendingDao.deleteForStop(routeId, stopId)
                loadRoute(routeId)
            } catch (_: Exception) {
                pendingDao.insert(PendingStopUpdateEntity(
                    routeId = routeId, stopId = stopId,
                    status = "Skipped", skippedReason = reason
                ))
                _routeUiState.value = TrackingUiState.Error("Saved offline — will sync when connected")
            }
        }
    }

    fun flushPendingUpdates(onDone: (synced: Int) -> Unit = {}) {
        viewModelScope.launch {
            val pending = pendingDao.getAll()
            var synced = 0
            pending.forEach { update ->
                try {
                    apiService.updateStopStatus(update.routeId, update.stopId, UpdateStopStatusRequest(
                        status        = update.status,
                        notes         = update.notes,
                        skippedReason = update.skippedReason,
                        arrivedAt     = update.arrivedAt,
                        completedAt   = update.completedAt
                    ))
                    pendingDao.delete(update)
                    synced++
                } catch (_: Exception) {}
            }
            onDone(synced)
        }
    }

    fun removeStop(routeId: Int, stopId: Int) {
        viewModelScope.launch {
            try {
                apiService.deleteStop(routeId, stopId)
                loadRoute(routeId)
            } catch (e: Exception) {
                _routeUiState.value = TrackingUiState.Error(e.message ?: "Failed to remove stop")
            }
        }
    }

    fun completeRoute(routeId: Int) {
        viewModelScope.launch {
            try {
                apiService.updateRouteStatus(routeId, mapOf("status" to "Complete"))
            } catch (_: Exception) { /* non-fatal */ }
        }
    }

    class Factory(
        private val apiService: MunandyApiService,
        private val locationHelper: LocationHelper,
        private val pendingDao: PendingStopUpdateDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrackingViewModel(apiService, locationHelper, pendingDao) as T
    }
}
