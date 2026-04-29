package com.example.phoneinfo

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.phoneinfo.PhoneInfoViewModel
import com.example.phoneinfo.SimInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit,
    phoneInfoViewModel: PhoneInfoViewModel = viewModel(
        factory = PhoneInfoViewModel.PhoneInfoViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val context = LocalContext.current
    val deviceInfo by phoneInfoViewModel.deviceInfo.collectAsState()

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(backgroundBrush)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // --- Basic Info Section ---
            item {
                DetailCategoryCard(title = "Overview", icon = Icons.Default.Memory) {
                    DetailInfoRow("Device Name", deviceInfo.deviceName)
                    DetailInfoRow("RAM Usage", "${phoneInfoViewModel.formatBytes(deviceInfo.usedRam)} / ${phoneInfoViewModel.formatBytes(deviceInfo.totalRam)}")
                    // Correctly show Internal Storage
                    deviceInfo.internalStorage?.let {
                        DetailInfoRow(
                            "Internal Storage",
                            "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}"
                        )
                    }

                    // Correctly show External Storage if it exists
                    deviceInfo.externalStorage?.let {
                        DetailInfoRow(
                            "SD Card",
                            "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}"
                        )
                    }
                }
            }

            // --- System Section ---
            item {
                DetailCategoryCard(title = "System", icon = Icons.Default.PhoneAndroid) {
                    DetailInfoRow("Android Version", deviceInfo.androidVersion)
                    DetailInfoRow("Security Patch", deviceInfo.securityPatch)
                    DetailInfoRow("Kernel Version", deviceInfo.kernelVersion)
                    DetailInfoRow("First Online", phoneInfoViewModel.formatTimestampToDate(deviceInfo.firstInstallTime))
                    DetailInfoRow("Uptime", phoneInfoViewModel.formatMillisToUptime(deviceInfo.uptime))
                    DetailInfoRow("Root Access", if (deviceInfo.isRooted) "Yes" else "No")
                }
            }

            // --- CPU Card (NEW) ---
            item {
                DetailCategoryCard(title = "Processor", icon = Icons.Default.Memory) { // Using Memory icon for CPU
                    DetailInfoRow("Processor", deviceInfo.processorName)
                    DetailInfoRow("Architecture", deviceInfo.cpuArchitecture)
                    DetailInfoRow("Cores", "${deviceInfo.coreCount}")
                    if (deviceInfo.coreFrequencies != "N/A") { // Only show if we could read the frequencies
                        DetailInfoRow("Core Speeds", deviceInfo.coreFrequencies)
                    }
                }
            }

            // --- WiFi Section ---
            item {
                val context = LocalContext.current // Get the context to launch the browser
                DetailCategoryCard(
                    title = "WiFi",
                    icon = getWifiIcon(deviceInfo.isWifiConnected, deviceInfo.wifiSignalStrength)
                ) {
                // This row is always visible
                DetailInfoRow("Status", if (deviceInfo.isWifiConnected) "Connected" else "Disconnected")

                // --- These details will ONLY appear if WiFi is connected ---
                if (deviceInfo.isWifiConnected) {
                    DetailInfoRow("Network Name", deviceInfo.wifiSsid)
                    DetailInfoRow("IP Address", deviceInfo.ipAddress)

                    // Add a spacer for better layout
                    Spacer(modifier = Modifier.height(8.dp))

                    // Button to run an external speed test
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.speedtest.net/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Run Internet Speed Test", color = Color.White)
                    }
                }
            }
            }


            // --- Mobile Network Card (CORRECTED) ---
            if (deviceInfo.simInfos.isNotEmpty()) {
                item {
                    DetailCategoryCard(
                        title = "Mobile Network",
                        icon = Icons.Default.SignalCellularAlt
                    ) {                // Loop through each found SIM card from the ViewModel
                        deviceInfo.simInfos.forEachIndexed { index, simInfo ->
                            // Add a divider if this is not the first SIM in the list
                            if (index > 0) {
                                Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                            }

                            // Smart label: Show "SIM" for one, "SIM 1/2" for multiple
                            val simLabel = if (deviceInfo.simInfos.size > 1) "SIM ${index + 1}" else "SIM"

                            Text(
                                text = "$simLabel: ${simInfo.operatorName}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            DetailInfoRow("Network Type", simInfo.networkType)

                            // "Click to reveal" phone number
                            simInfo.phoneNumber?.let { number ->
                                var isNumberVisible by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Phone Number", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                                    Text(
                                        text = if (isNumberVisible) number else "Show Number",
                                        color = Color.White,
                                        fontWeight = if (isNumberVisible) FontWeight.SemiBold else FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { isNumberVisible = !isNumberVisible }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Battery Section ---
            item {
                DetailCategoryCard(
                    title = "Battery",
                    iconContent = {
                        BatteryWithChargingOverlay(
                            level = deviceInfo.batteryLevel,
                            isCharging = deviceInfo.isCharging,
                            thunderOffsetX = 0.dp,
                            thunderOffsetY = 0.dp
                        )
                    }
                ) {
                    DetailInfoRowWithIconInValue("Level", "${deviceInfo.batteryLevel}%", icon = if (deviceInfo.isCharging) Icons.Default.Bolt else null)
                    HealthInfoRow(healthStatus = deviceInfo.batteryHealth)
                    DetailInfoRow("Temperature", "${deviceInfo.batteryTemperature}°C")
                    DetailInfoRow("Technology", deviceInfo.batteryTechnology)
                }
            }




            // --- Display Section ---
            item {
                DetailCategoryCard(title = "Display", icon = Icons.Default.StayCurrentPortrait) {
                    DetailInfoRow("Resolution", deviceInfo.screenResolution)
                    DetailInfoRow("Density", "${deviceInfo.screenDensity} DPI")
                    DetailInfoRow("Refresh Rate", "%.0f Hz".format(deviceInfo.refreshRate))
                }
            }


            // --- Sensors Section ---
            item {
                var isSensorsExpanded by remember { mutableStateOf(false) }

                DetailCategoryCard(title = "Sensors", icon = Icons.Default.Sensors) {
                    // Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${deviceInfo.sensors.size} Sensors found",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isSensorsExpanded) "Hide" else "View All",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isSensorsExpanded = !isSensorsExpanded }
                                .padding(4.dp)
                        )
                    }

                    // Expandable list of sensors
                    AnimatedVisibility(visible = isSensorsExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            deviceInfo.sensors.forEach { sensorName ->
                                DetailInfoRow(label = sensorName, value = "")
                            }
                        }
                    }
                }
            }
        }
    }
}


// Helper Functions
/**
 * NEW Composable for the Health row that shows an AlertDialog on click.
 */
@Composable
fun HealthInfoRow(healthStatus: String) {
    // State to control if the dialog is shown
    var showDialog by remember { mutableStateOf(false) }

    // --- The Dialog Composable ---
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("About Battery Health") },
            text = {
                Text(
                    "This shows the battery's current operational status, not its long-term " +
                            "capacity. 'Good' means the battery is functioning " +
                            "without any immediate hardware issues like overheating or failure."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it")
                }
            },
            containerColor = Color(0xFF34495E), // A dark, matching color for the dialog
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }

    // --- The UI Row ---
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label and Info Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Make the whole group clickable
            modifier = Modifier.clickable { showDialog = true }
        ) {
            Text(text = "Health", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Info about battery health",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // Value
        Text(text = healthStatus, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
fun DetailCategoryCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassBrush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f))
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glassBrush)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Divider(color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

//Overloaded Function for Battery Section --
@Composable
fun DetailCategoryCard(
    title: String,
    iconContent: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassBrush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glassBrush)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            iconContent() // 👈 This allows composable icons like overlay battery
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Divider(color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}


@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DetailInfoRowWithIconInValue(label: String, value: String, icon: ImageVector?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Charging",
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun getWifiIcon(isConnected: Boolean, level: Int): ImageVector {
    if (!isConnected) return Icons.Default.WifiOff
    return when (level) {
        0 -> Icons.Default.Wifi1Bar
        1 -> Icons.Default.Wifi2Bar
        2 -> Icons.Default.Wifi
        3 -> Icons.Default.Wifi
        else -> Icons.Default.WifiOff
    }
}

@Composable
fun getBatteryIcon(level: Int): ImageVector {
    return when {
        level > 95 -> Icons.Default.BatteryFull
        level > 80 -> Icons.Default.Battery6Bar
        level > 60 -> Icons.Default.Battery5Bar
        level > 40 -> Icons.Default.Battery4Bar
        level > 25 -> Icons.Default.Battery3Bar
        level > 10 -> Icons.Default.Battery2Bar
        else -> Icons.Default.Battery1Bar
    }
}

@Composable
fun BatteryWithChargingOverlay(
    level: Int,
    isCharging: Boolean,
    thunderOffsetX: Dp = 0.dp,   // 👈 adjust left/right position
    thunderOffsetY: Dp = 0.dp    // 👈 adjust up/down position
) {
    Box(contentAlignment = Alignment.Center) {
        // Base battery icon (based on level)
        Icon(
            imageVector = getBatteryIcon(level),
            contentDescription = "Battery Level",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )

        // Overlay thunder icon (only visible when charging)
        if (isCharging) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Charging",
                tint = Color.Yellow,
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = thunderOffsetX, y = thunderOffsetY) // fine-tune position if needed
            )
        }
    }
}




