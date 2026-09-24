package com.example.munandydrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    @SerialName("Admin") ADMIN,
    @SerialName("Supervisor") SUPERVISOR,
    @SerialName("Salesman") SALESMAN
}

@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: UserRole,
    @SerialName("territory_id") val territoryId: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true
)
