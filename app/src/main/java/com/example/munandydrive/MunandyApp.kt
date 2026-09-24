package com.example.munandydrive

import android.app.Application
import com.example.munandydrive.data.local.AppDatabase
import com.example.munandydrive.data.remote.LocationHelper
import com.example.munandydrive.data.remote.RetrofitClient
import com.example.munandydrive.data.remote.TokenManager
import com.example.munandydrive.data.repository.AuthRepository
import com.example.munandydrive.data.repository.CustomerRepository
import com.example.munandydrive.data.repository.RouteRepository
import com.example.munandydrive.data.repository.UserRepository
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.MapsInitializer.Renderer

class MunandyApp : Application() {

    @OptIn(MapsComposeExperimentalApi::class)
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(this, Renderer.LATEST, null)
    }

    val tokenManager: TokenManager by lazy { TokenManager(this) }
    val apiService by lazy { RetrofitClient.create(tokenManager) }
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val locationHelper: LocationHelper by lazy { LocationHelper(this) }
    val pendingStopUpdateDao by lazy { database.pendingStopUpdateDao() }

    val authRepository: AuthRepository     by lazy { AuthRepository(apiService, tokenManager) }
    val userRepository: UserRepository     by lazy { UserRepository(database.userDao(), apiService) }
    val customerRepository: CustomerRepository by lazy { CustomerRepository(database.customerDao(), apiService) }
    val routeRepository: RouteRepository   by lazy { RouteRepository(database.routeDao(), apiService) }
}
