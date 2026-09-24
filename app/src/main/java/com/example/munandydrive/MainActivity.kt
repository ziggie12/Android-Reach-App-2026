package com.example.munandydrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.munandydrive.ui.navigation.MunandyNavGraph
import com.example.munandydrive.ui.theme.MunandyDriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MunandyDriveTheme {
                val navController = rememberNavController()
                MunandyNavGraph(navController = navController)
            }
        }
    }
}
