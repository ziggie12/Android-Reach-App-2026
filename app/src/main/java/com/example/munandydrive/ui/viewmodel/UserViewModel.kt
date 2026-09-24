package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.model.User
import com.example.munandydrive.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UserUiState {
    object Idle : UserUiState()
    object Loading : UserUiState()
    object Success : UserUiState()
    data class Error(val message: String) : UserUiState()
}

class UserViewModel(private val repository: UserRepository) : ViewModel() {

    val users: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Idle)
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    fun refreshUsers() {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            try {
                repository.refreshUsers()
                _uiState.value = UserUiState.Success
            } catch (e: Exception) {
                _uiState.value = UserUiState.Error(e.message ?: "Failed to load users")
            }
        }
    }

    fun createUser(name: String, email: String, password: String, role: String) {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            try {
                repository.createUser(name, email, password, role)
                _uiState.value = UserUiState.Success
            } catch (e: Exception) {
                _uiState.value = UserUiState.Error(e.message ?: "Failed to create user")
            }
        }
    }

    fun updateUserStatus(userId: Int, isActive: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateUserStatus(userId, isActive)
            } catch (e: Exception) {
                _uiState.value = UserUiState.Error(e.message ?: "Failed to update user")
            }
        }
    }

    fun resetUserPassword(userId: Int, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            try {
                repository.resetUserPassword(userId, newPassword)
                _uiState.value = UserUiState.Success
            } catch (e: Exception) {
                _uiState.value = UserUiState.Error(e.message ?: "Failed to reset password")
            }
        }
    }

    class Factory(private val repository: UserRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UserViewModel(repository) as T
    }
}
