package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.model.Customer
import com.example.munandydrive.data.repository.CustomerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class CustomerUiState {
    object Idle : CustomerUiState()
    object Loading : CustomerUiState()
    object Success : CustomerUiState()
    data class Error(val message: String) : CustomerUiState()
}

class CustomerViewModel(private val repository: CustomerRepository) : ViewModel() {

    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<CustomerUiState>(CustomerUiState.Idle)
    val uiState: StateFlow<CustomerUiState> = _uiState.asStateFlow()

    fun refreshCustomers() {
        viewModelScope.launch {
            _uiState.value = CustomerUiState.Loading
            try {
                repository.refreshCustomers()
                _uiState.value = CustomerUiState.Success
            } catch (e: Exception) {
                _uiState.value = CustomerUiState.Error(e.message ?: "Failed to load customers")
            }
        }
    }

    fun createCustomer(
        name: String, address: String?,
        openingTime: String?, closingTime: String?,
        contact: String?, preferredProducts: String?
    ) {
        viewModelScope.launch {
            _uiState.value = CustomerUiState.Loading
            try {
                repository.createCustomer(
                    name = name, address = address,
                    lat = null, lng = null,
                    openingTime = openingTime, closingTime = closingTime,
                    contact = contact, preferredProducts = preferredProducts
                )
                _uiState.value = CustomerUiState.Success
            } catch (e: Exception) {
                _uiState.value = CustomerUiState.Error(e.message ?: "Failed to create customer")
            }
        }
    }

    fun editCustomer(
        id: Int, name: String, address: String?,
        openingTime: String?, closingTime: String?,
        contact: String?, preferredProducts: String?
    ) {
        viewModelScope.launch {
            _uiState.value = CustomerUiState.Loading
            try {
                repository.editCustomer(id, name, address, openingTime, closingTime, contact, preferredProducts)
                _uiState.value = CustomerUiState.Success
            } catch (e: Exception) {
                _uiState.value = CustomerUiState.Error(e.message ?: "Failed to update customer")
            }
        }
    }

    fun deleteCustomer(id: Int) {
        viewModelScope.launch {
            _uiState.value = CustomerUiState.Loading
            try {
                repository.deleteCustomer(id)
                _uiState.value = CustomerUiState.Success
            } catch (e: Exception) {
                _uiState.value = CustomerUiState.Error(e.message ?: "Failed to delete customer")
            }
        }
    }

    /**
     * Creates a customer at a map-tapped location and returns the new ID.
     * Called from a composable coroutineScope during route saving.
     */
    suspend fun createForRoute(
        name: String, lat: Double, lng: Double, address: String? = null
    ): Int = repository.createCustomerAndGetId(name, lat, lng, address)

    class Factory(private val repository: CustomerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CustomerViewModel(repository) as T
    }
}
