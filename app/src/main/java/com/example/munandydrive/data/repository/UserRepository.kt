package com.example.munandydrive.data.repository

import com.example.munandydrive.data.local.dao.UserDao
import com.example.munandydrive.data.local.entity.UserEntity
import com.example.munandydrive.data.model.User
import com.example.munandydrive.data.remote.MunandyApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserRepository(
    private val userDao: UserDao,
    private val apiService: MunandyApiService
) {
    val allUsers: Flow<List<User>> = userDao.getAllUsers().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun refreshUsers() {
        val remoteUsers = apiService.getUsers()
        remoteUsers.forEach { user -> userDao.insertUser(user.toEntity()) }
        if (remoteUsers.isNotEmpty()) {
            userDao.deleteUsersNotIn(remoteUsers.map { it.id })
        }
    }

    suspend fun createUser(
        name: String, email: String, password: String,
        role: String, territoryId: Int? = null
    ) {
        val body = mutableMapOf(
            "name" to name, "email" to email,
            "password" to password, "role" to role
        )
        territoryId?.let { body["territory_id"] = it.toString() }
        apiService.createUser(body)
        refreshUsers()
    }

    suspend fun updateUserStatus(userId: Int, isActive: Boolean) {
        apiService.updateUserStatus(userId, mapOf("is_active" to isActive))
        refreshUsers()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        apiService.changePassword(mapOf("current_password" to currentPassword, "new_password" to newPassword))
    }

    suspend fun resetUserPassword(userId: Int, newPassword: String) {
        apiService.resetUserPassword(userId, mapOf("new_password" to newPassword))
        refreshUsers()
    }

    private fun UserEntity.toDomain() = User(
        id = id, name = name, email = email,
        role = role, territoryId = territoryId, isActive = isActive
    )

    private fun User.toEntity() = UserEntity(
        id = id, name = name, email = email,
        role = role, territoryId = territoryId, isActive = isActive
    )
}
