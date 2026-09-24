package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.model.StatsResponse
import com.example.munandydrive.data.remote.MunandyApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(private val apiService: MunandyApiService) : ViewModel() {

    private val _stats = MutableStateFlow(StatsResponse())
    val stats: StateFlow<StatsResponse> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadStats() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _stats.value = apiService.getStats()
            } catch (_: Exception) { /* keep last known */ }
            _loading.value = false
        }
    }

    class Factory(private val apiService: MunandyApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(apiService) as T
    }
}
