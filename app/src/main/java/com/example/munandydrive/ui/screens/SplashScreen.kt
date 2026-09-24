package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.MunandyApp
import com.example.munandydrive.ui.navigation.Screen

@Composable
fun SplashScreen(navController: NavHostController) {
    val app = LocalContext.current.applicationContext as MunandyApp

    LaunchedEffect(Unit) {
        val destination = try {
            val role = app.tokenManager.getStoredRole()
            if (role == null) {
                Screen.Login.route
            } else {
                // Verify the token is still valid against the server.
                // If the server is unreachable or returns 401/403, go to login.
                app.apiService.verifyToken()
                when (role) {
                    "Admin"      -> Screen.AdminDashboard.route
                    "Supervisor" -> Screen.SupervisorDashboard.route
                    "Salesman"   -> Screen.SalesmanDashboard.route
                    else         -> Screen.Login.route
                }
            }
        } catch (e: Exception) {
            app.tokenManager.clearSession()
            Screen.Login.route
        }

        navController.navigate(destination) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Munandy Drive",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
        }
    }
}
