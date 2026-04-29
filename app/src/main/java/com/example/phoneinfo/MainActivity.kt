package com.example.phoneinfo

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.phoneinfo.ui.theme.PhoneInfoTheme

class MainActivity : ComponentActivity() {

    // Updated to handle multiple permissions
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("PERMISSIONS", "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all necessary permissions on startup
        requestMultiplePermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS
            )
        )

        enableEdgeToEdge()
        setContent {
            PhoneInfoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(onNavigateToDetails = {
                                navController.navigate("details")
                            })
                        }
                        composable("details") {
                            DetailScreen(onNavigateBack = {
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }
}
