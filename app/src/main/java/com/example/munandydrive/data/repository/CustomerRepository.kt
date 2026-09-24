package com.example.munandydrive.data.repository

import com.example.munandydrive.data.local.dao.CustomerDao
import com.example.munandydrive.data.local.entity.CustomerEntity
import com.example.munandydrive.data.model.CreateCustomerRequest
import com.example.munandydrive.data.model.Customer
import com.example.munandydrive.data.remote.MunandyApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CustomerRepository(
    private val customerDao: CustomerDao,
    private val apiService: MunandyApiService
) {
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun refreshCustomers() {
        val remoteCustomers = apiService.getCustomers()
        if (remoteCustomers.isEmpty()) {
            customerDao.deleteAllCustomers()
        } else {
            remoteCustomers.forEach { customer -> customerDao.insertCustomer(customer.toEntity()) }
            customerDao.deleteCustomersNotIn(remoteCustomers.map { it.id })
        }
    }

    suspend fun createCustomer(
        name: String, address: String?,
        lat: Double?, lng: Double?,
        openingTime: String?, closingTime: String?,
        contact: String?, preferredProducts: String?
    ) {
        apiService.createCustomer(
            CreateCustomerRequest(
                name = name, address = address,
                lat = lat, lng = lng,
                openingTime = openingTime, closingTime = closingTime,
                contact = contact, preferredProducts = preferredProducts
            )
        )
        refreshCustomers()
    }

    /**
     * Creates a customer from a map-tapped location and returns the new customer's ID.
     * Does NOT refresh the local cache (caller should refresh once all route stops are saved).
     */
    suspend fun createCustomerAndGetId(
        name: String, lat: Double, lng: Double, address: String? = null
    ): Int {
        val response = apiService.createCustomer(
            CreateCustomerRequest(name = name, lat = lat, lng = lng, address = address)
        )
        return response.id ?: throw Exception("Server did not return a customer ID for \"$name\"")
    }

    suspend fun editCustomer(
        id: Int, name: String, address: String?,
        openingTime: String?, closingTime: String?,
        contact: String?, preferredProducts: String?
    ) {
        apiService.updateCustomer(id, CreateCustomerRequest(
            name = name, address = address,
            openingTime = openingTime, closingTime = closingTime,
            contact = contact, preferredProducts = preferredProducts
        ))
        refreshCustomers()
    }

    suspend fun deleteCustomer(id: Int) {
        try { apiService.deleteCustomer(id) } catch (_: Exception) {}
        customerDao.deleteCustomer(id)
    }

    private fun CustomerEntity.toDomain() = Customer(
        id = id, name = name, address = address, lat = lat, lng = lng,
        openingTime = openingTime, closingTime = closingTime, contact = contact,
        preferredProducts = preferredProducts, notes = notes,
        territoryId = territoryId, createdBy = createdBy
    )

    private fun Customer.toEntity() = CustomerEntity(
        id = id, name = name, address = address, lat = lat, lng = lng,
        openingTime = openingTime, closingTime = closingTime, contact = contact,
        preferredProducts = preferredProducts, notes = notes,
        territoryId = territoryId, createdBy = createdBy
    )
}
