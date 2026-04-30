package com.example.phoneinfo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.phoneinfo.ui.screens.AppsScreen
import com.example.phoneinfo.ui.screens.DetailScreen
import com.example.phoneinfo.ui.screens.HomeScreen
import com.example.phoneinfo.ui.screens.SpeedTestScreen
import com.example.phoneinfo.ui.theme.AppBackground
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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val startTime = System.currentTimeMillis()
        val minimumDuration = 1800L

        splashScreen.setKeepOnScreenCondition {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             val elapsedTime = System.currentTimeMillis() - startTime
             elapsedTime < minimumDuration
            } else {
                false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { provider ->
                provider.remove()
            }
        }

        // Request all necessary permissions on startup
        requestMultiplePermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CAMERA
            )
        )

        enableEdgeToEdge()
        setContent {
            PhoneInfoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground)
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(400)
                            ) + fadeIn(animationSpec = tween(400))
                        },
                        exitTransition = {
                            if (targetState.destination.route == "speedTest" || targetState.destination.route == "apps") {
                                // Keep DetailScreen still and slightly fade it when SpeedTest or Apps slides up
                                fadeOut(animationSpec = tween(400, delayMillis = 100))
                            } else {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth / 3 },
                                    animationSpec = tween(400)
                                ) + fadeOut(animationSpec = tween(400))
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route == "speedTest" || initialState.destination.route == "apps") {
                                // Fade DetailScreen back in when SpeedTest or Apps slides down
                                fadeIn(animationSpec = tween(400))
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -fullWidth / 3 },
                                    animationSpec = tween(400)
                                ) + fadeIn(animationSpec = tween(400))
                            }
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(400)
                            ) + fadeOut(animationSpec = tween(400))
                        }
                    ) {
                        composable("home") {
                            HomeScreen(onNavigateToDetails = {
                                navController.navigate("details")
                            })
                        }
                        composable("details") {
                            DetailScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToSpeedTest = {
                                    navController.navigate("speedTest")
                                },
                                onNavigateToApps = {
                                    navController.navigate("apps")
                                }
                            )
                        }
                        composable(
                            "apps",
                            enterTransition = {
                                slideInVertically(
                                    initialOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeIn(animationSpec = tween(400))
                            },
                            exitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeOut(animationSpec = tween(400))
                            },
                            popEnterTransition = {
                                slideInVertically(
                                    initialOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeIn(animationSpec = tween(400))
                            },
                            popExitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeOut(animationSpec = tween(400))
                            }
                        ) {
                            AppsScreen(onNavigateBack = {
                                navController.popBackStack()
                            })
                        }
                        composable(
                            "speedTest",
                            enterTransition = {
                                slideInVertically(
                                    initialOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeIn(animationSpec = tween(400))
                            },
                            exitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeOut(animationSpec = tween(400))
                            },
                            popEnterTransition = {
                                slideInVertically(
                                    initialOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeIn(animationSpec = tween(400))
                            },
                            popExitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(400)
                                ) + fadeOut(animationSpec = tween(400))
                            }
                        ) {
                            SpeedTestScreen(onNavigateBack = {
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }
}
