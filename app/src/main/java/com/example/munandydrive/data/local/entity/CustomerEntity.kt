package com.example.munandydrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val openingTime: String?,
    val closingTime: String?,
    val contact: String?,
    val preferredProducts: String?,
    val notes: String?,
    val territoryId: Int?,
    val createdBy: Int?,
    val lastUpdated: Long = System.currentTimeMillis()
)
