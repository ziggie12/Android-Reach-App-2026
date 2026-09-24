package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.model.ReportsSummary
import com.example.munandydrive.data.remote.MunandyApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportsViewModel(private val apiService: MunandyApiService) : ViewModel() {

    private val _summary = MutableStateFlow<ReportsSummary?>(null)
    val summary: StateFlow<ReportsSummary?> = _summary.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _summary.value = apiService.getReportsSummary()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load reports"
            } finally {
                _loading.value = false
            }
        }
    }

    class Factory(private val apiService: MunandyApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReportsViewModel(apiService) as T
    }
}
