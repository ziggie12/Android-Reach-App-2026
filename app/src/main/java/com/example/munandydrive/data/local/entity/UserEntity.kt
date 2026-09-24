package com.example.munandydrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.munandydrive.data.model.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val email: String,
    val role: UserRole,
    val territoryId: Int?,
    val isActive: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)
