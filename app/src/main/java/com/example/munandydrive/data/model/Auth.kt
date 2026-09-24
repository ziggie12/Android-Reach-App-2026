package com.example.munandydrive.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: LoggedInUser)

@Serializable
data class LoggedInUser(
    val id: Int,
    val name: String,
    val role: String,
    val email: String
)
