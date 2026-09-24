package com.example.munandydrive.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    val id: Int,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("opening_time") val openingTime: String? = null,
    @SerialName("closing_time") val closingTime: String? = null,
    val contact: String? = null,
    @SerialName("preferred_products") val preferredProducts: String? = null,
    val notes: String? = null,
    @SerialName("territory_id") val territoryId: Int? = null,
    @SerialName("created_by") val createdBy: Int? = null
)

/** Used when creating a new customer from the map-based route builder. */
@Serializable
data class CreateCustomerRequest(
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("opening_time") val openingTime: String? = null,
    @SerialName("closing_time") val closingTime: String? = null,
    val contact: String? = null,
    @SerialName("preferred_products") val preferredProducts: String? = null,
    val notes: String? = null,
    @SerialName("territory_id") val territoryId: Int? = null
)
