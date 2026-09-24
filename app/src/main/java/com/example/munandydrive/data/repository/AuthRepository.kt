package com.example.munandydrive.data.repository

import com.example.munandydrive.data.model.LoggedInUser
import com.example.munandydrive.data.model.LoginRequest
import com.example.munandydrive.data.remote.MunandyApiService
import com.example.munandydrive.data.remote.TokenManager

class AuthRepository(
    private val apiService: MunandyApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Result<LoggedInUser> = try {
        val response = apiService.login(LoginRequest(email, password))
        tokenManager.saveSession(response.token, response.user)
        Result.success(response.user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun logout() {
        tokenManager.clearSession()
    }

    suspend fun getStoredRole(): String? = tokenManager.getStoredRole()
}
