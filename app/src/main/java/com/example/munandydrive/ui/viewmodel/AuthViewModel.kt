package com.example.munandydrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.munandydrive.data.model.LoggedInUser
import com.example.munandydrive.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: LoggedInUser) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            repository.login(email, password).fold(
                onSuccess = { user -> _loginState.value = LoginUiState.Success(user) },
                onFailure = { e ->
                    val message = when {
                        e.message?.contains("Unable to resolve host") == true -> "Cannot reach server. Check your connection."
                        e.message?.contains("401") == true -> "Invalid email or password."
                        e.message?.contains("inactive") == true -> "Your account is inactive."
                        else -> e.message ?: "Login failed. Please try again."
                    }
                    _loginState.value = LoginUiState.Error(message)
                }
            )
        }
    }

    fun resetState() {
        _loginState.value = LoginUiState.Idle
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(repository) as T
    }
}
