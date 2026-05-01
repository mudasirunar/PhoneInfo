package com.example.phoneinfo

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.phoneinfo.ui.screens.AppsScreen
import com.example.phoneinfo.ui.screens.DetailScreen
import com.example.phoneinfo.ui.screens.HomeScreen
import com.example.phoneinfo.ui.screens.SpeedTestScreen
import com.example.phoneinfo.ui.theme.AccentTeal
import com.example.phoneinfo.ui.theme.AppBackground
import com.example.phoneinfo.ui.theme.BgGradientStart
import com.example.phoneinfo.ui.theme.PhoneInfoTheme
import com.example.phoneinfo.ui.theme.TextPrimary
import com.example.phoneinfo.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

        enableEdgeToEdge()
        setContent {
            PhoneInfoTheme {
                var showUsageAccessDialog by remember { mutableStateOf(false) }
                var runtimePermissionsHandled by remember { mutableStateOf(false) }

                val requestMultiplePermissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    permissions.entries.forEach {
                        Log.d("PERMISSIONS", "${it.key} = ${it.value}")
                    }
                    runtimePermissionsHandled = true
                }

                LaunchedEffect(Unit) {
                    val permissionsToRequest = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_PHONE_NUMBERS,
                        Manifest.permission.CAMERA
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
                }

                LaunchedEffect(runtimePermissionsHandled) {
                    if (runtimePermissionsHandled) {
                        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                        val hasCompletedPrompt = prefs.getBoolean("usage_prompt_done", false)
                        if (!hasUsageStatsPermission() && !hasCompletedPrompt) {
                            showUsageAccessDialog = true
                        }
                    }
                }

                if (showUsageAccessDialog) {
                    AlertDialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false
                        ),
                        containerColor = BgGradientStart,
                        titleContentColor = AccentTeal,
                        textContentColor = TextPrimary,
                        shape = RoundedCornerShape(16.dp),
                        title = { Text("Usage Access Required") },
                        text = { Text("This permission is required to accurately calculate and display app storage usage statistics.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showUsageAccessDialog = false
                                getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                                    .putBoolean("usage_prompt_done", true).apply()

                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.parse("package:$packageName")
                                })
                            }) {
                                Text("Grant Permission", color = AccentTeal)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showUsageAccessDialog = false
                                getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                                    .putBoolean("usage_prompt_done", true).apply()
                            }) {
                                Text("Skip", color = TextSecondary)
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground),
                    contentAlignment = Alignment.Center
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
