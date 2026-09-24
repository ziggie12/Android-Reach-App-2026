package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.model.Route
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.data.repository.RouteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class RouteUiState {
    object Idle : RouteUiState()
    object Loading : RouteUiState()
    object Success : RouteUiState()
    data class Error(val message: String) : RouteUiState()
}

class RouteViewModel(private val repository: RouteRepository) : ViewModel() {

    val routes: StateFlow<List<Route>> = repository.allRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRoute: StateFlow<Route?> = repository.allRoutes
        .map { list -> list.firstOrNull { it.status == RouteStatus.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun clearDeleteError() { _deleteError.value = null }

    // Screens that need live route data call startPolling() and stopPolling()
    // in a DisposableEffect so the background loop only runs when needed.
    private var pollingJob: Job? = null
    private var pollingRefCount = 0

    fun startPolling() {
        pollingRefCount++
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                try { repository.refreshRoutes() } catch (_: Exception) {}
            }
        }
    }

    fun stopPolling() {
        pollingRefCount = maxOf(0, pollingRefCount - 1)
        if (pollingRefCount == 0) {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    fun refreshRoutes() {
        viewModelScope.launch {
            _uiState.value = RouteUiState.Loading
            try {
                repository.refreshRoutes()
                _uiState.value = RouteUiState.Success
            } catch (e: Exception) {
                _uiState.value = RouteUiState.Error(e.message ?: "Failed to load routes")
            }
        }
    }

    /** Creates a route then optionally adds stops. Returns the new route ID. */
    fun createRoute(
        name: String, date: String, assignedTo: Int?,
        customerIds: List<Int> = emptyList(),
        onResult: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = RouteUiState.Loading
            try {
                val routeId = repository.createRoute(name, date, assignedTo)
                if (customerIds.isNotEmpty()) {
                    repository.addStops(routeId, customerIds)
                }
                _uiState.value = RouteUiState.Success
                onResult(routeId)
            } catch (e: Exception) {
                _uiState.value = RouteUiState.Error(e.message ?: "Failed to create route")
            }
        }
    }

    fun updateRouteStatus(routeId: Int, status: String) {
        viewModelScope.launch {
            try {
                repository.updateRouteStatus(routeId, status)
            } catch (e: Exception) {
                _uiState.value = RouteUiState.Error(e.message ?: "Failed to update route status")
            }
        }
    }

    /**
     * Suspend version used directly from a composable coroutineScope.
     * Creates the route, adds all stops, then refreshes the list.
     */
    suspend fun saveRouteWithStops(
        name: String, date: String, assignedTo: Int?,
        customerIds: List<Int>
    ): Int {
        val routeId = repository.createRoute(name, date, assignedTo)
        if (customerIds.isNotEmpty()) {
            repository.addStops(routeId, customerIds)
        }
        return routeId
    }

    suspend fun addStops(routeId: Int, customerIds: List<Int>) {
        repository.addStops(routeId, customerIds)
        repository.refreshRoutes()
    }

    fun assignRoute(routeId: Int, userId: Int?) {
        viewModelScope.launch {
            try {
                repository.assignRoute(routeId, userId)
            } catch (e: Exception) {
                _uiState.value = RouteUiState.Error(e.message ?: "Failed to assign route")
            }
        }
    }

    fun deleteRoute(routeId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteRoute(routeId)
                onSuccess()
            } catch (e: Exception) {
                _deleteError.value = e.message ?: "Failed to delete route"
            }
        }
    }

    class Factory(private val repository: RouteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RouteViewModel(repository) as T
    }
}
