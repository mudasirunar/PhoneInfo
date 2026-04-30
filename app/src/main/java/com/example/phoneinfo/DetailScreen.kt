package com.example.phoneinfo

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.phoneinfo.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSpeedTest: () -> Unit,
    phoneInfoViewModel: PhoneInfoViewModel = viewModel(
        factory = PhoneInfoViewModel.PhoneInfoViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val context = LocalContext.current
    val deviceInfo by phoneInfoViewModel.deviceInfo.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Calculate 80% of screen height in pixels for the scroll distance
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val scrollDistancePx = with(density) { (screenHeightDp * 0.65f).toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Advanced Details", 
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent, // Let the global background show through
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {
            // --- Basic Info Section ---
            item {
                DetailGlassCard(title = "Overview", icon = Icons.Default.Memory, accentColor = AccentPurple) {
                    DetailInfoRow("Device Name", deviceInfo.deviceName)
                    DetailInfoRow("RAM Usage", "${phoneInfoViewModel.formatBytes(deviceInfo.usedRam)} / ${phoneInfoViewModel.formatBytes(deviceInfo.totalRam)}")
                    deviceInfo.internalStorage?.let {
                        DetailInfoRow("Internal Storage", "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}")
                    }
                    deviceInfo.externalStorage?.let {
                        DetailInfoRow("SD Card", "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}")
                    }
                }
            }

            // --- System Section ---
            item {
                DetailGlassCard(title = "System", icon = Icons.Default.PhoneAndroid, accentColor = AccentCyan) {
                    DetailInfoRow("Android Version", deviceInfo.androidVersion)
                    DetailInfoRow("Security Patch", deviceInfo.securityPatch)
                    DetailInfoRow("Kernel Version", deviceInfo.kernelVersion)
                    DetailInfoRow("First Online", phoneInfoViewModel.formatTimestampToDate(deviceInfo.firstInstallTime))
                    DetailInfoRow("Uptime", phoneInfoViewModel.formatMillisToUptime(deviceInfo.uptime))
                    DetailInfoRow("Root Access", if (deviceInfo.isRooted) "Yes" else "No", valueColor = if (deviceInfo.isRooted) AccentPink else TextPrimary)
                }
            }

            // --- CPU Card ---
            item {
                DetailGlassCard(title = "Processor", icon = Icons.Default.Memory, accentColor = AccentOrange) { 
                    DetailInfoRow("Processor", deviceInfo.processorName)
                    DetailInfoRow("Architecture", deviceInfo.cpuArchitecture)
                    DetailInfoRow("Cores", "${deviceInfo.coreCount}")
                    if (deviceInfo.coreFrequencies != "N/A") { 
                        DetailInfoRow("Core Speeds", deviceInfo.coreFrequencies)
                    }
                }
            }

            // --- WiFi Section ---
            item {
                WifiCard(
                    isWifiConnected = deviceInfo.isWifiConnected,
                    wifiSignalStrength = deviceInfo.wifiSignalStrength,
                    wifiSsid = deviceInfo.wifiSsid,
                    ipAddress = deviceInfo.ipAddress,
                    onNavigateToSpeedTest = onNavigateToSpeedTest
                )
            }

            // --- Mobile Network Card ---
            if (deviceInfo.simInfos.isNotEmpty()) {
                item {
                    DetailGlassCard(
                        title = "Mobile Network",
                        icon = Icons.Default.SignalCellularAlt,
                        accentColor = AccentPurple
                    ) {                
                        deviceInfo.simInfos.forEachIndexed { index, simInfo ->
                            if (index > 0) {
                                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))
                            }

                            val simLabel = if (deviceInfo.simInfos.size > 1) "SIM ${index + 1}" else "SIM"

                            Text(
                                text = "$simLabel: ${simInfo.operatorName}",
                                color = AccentPink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            DetailInfoRow("Network Type", simInfo.networkType)

                            simInfo.phoneNumber?.let { number ->
                                var isNumberVisible by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Phone Number", color = TextSecondary, fontSize = 15.sp)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isNumberVisible) GlassBackground else AccentPurple.copy(alpha = 0.2f))
                                            .clickable { isNumberVisible = !isNumberVisible }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isNumberVisible) number else "Reveal",
                                            color = if (isNumberVisible) TextPrimary else AccentPurple,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Battery Section ---
            item {
                DetailGlassCard(
                    title = "Battery",
                    iconContent = {
                        BatteryWithChargingOverlay(
                            level = deviceInfo.batteryLevel,
                            isCharging = deviceInfo.isCharging,
                            accentColor = AccentGreen
                        )
                    },
                    accentColor = AccentGreen
                ) {
                    DetailInfoRowWithIconInValue(
                        "Level", 
                        "${deviceInfo.batteryLevel}%", 
                        icon = if (deviceInfo.isCharging) Icons.Default.Bolt else null,
                        iconTint = AccentGreen
                    )
                    HealthInfoRow(healthStatus = deviceInfo.batteryHealth)
                    DetailInfoRow("Temperature", "${deviceInfo.batteryTemperature}°C")
                    DetailInfoRow("Technology", deviceInfo.batteryTechnology)
                }
            }

            // --- Display Section ---
            item {
                DetailGlassCard(title = "Display", icon = Icons.Default.StayCurrentPortrait, accentColor = AccentCyan) {
                    DetailInfoRow("Resolution", deviceInfo.screenResolution)
                    DetailInfoRow("Density", "${deviceInfo.screenDensity} DPI")
                    DetailInfoRow("Refresh Rate", "%.0f Hz".format(deviceInfo.refreshRate))
                }
            }

            // --- Sensors Section ---
            item {
                var isSensorsExpanded by remember { mutableStateOf(false) }

                DetailGlassCard(title = "Sensors", icon = Icons.Default.Sensors, accentColor = AccentOrange) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${deviceInfo.sensors.size} Sensors found",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassBackgroundHighlight)
                                .clickable { 
                                    isSensorsExpanded = !isSensorsExpanded 
                                    if (isSensorsExpanded) {
                                        coroutineScope.launch {
                                            // Smoothly scroll down dynamically based on screen height
                                            delay(150)
                                            listState.animateScrollBy(scrollDistancePx, animationSpec = tween(400))
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isSensorsExpanded) "Hide" else "View All",
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isSensorsExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(bottom = 12.dp))
                            deviceInfo.sensors.forEach { sensorName ->
                                Text(
                                    text = "• $sensorName",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiCard(
    isWifiConnected: Boolean,
    wifiSignalStrength: Int,
    wifiSsid: String,
    ipAddress: String,
    onNavigateToSpeedTest:() -> Unit
){
    val wifiAccentColor = if (isWifiConnected) AccentGreen else TextSecondary
    DetailGlassCard(
        title = "WiFi",
        iconContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(wifiAccentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (isWifiConnected) {
                    // Background dimmed full WiFi icon
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = wifiAccentColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                // Foreground actual strength WiFi icon
                Icon(
                    imageVector = getWifiIcon(isWifiConnected, wifiSignalStrength),
                    contentDescription = "WiFi Status",
                    tint = wifiAccentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        accentColor = wifiAccentColor
    ) {
        DetailInfoRow(
            "Status",
            if (isWifiConnected) "Connected" else "Disconnected",
            valueColor = if (isWifiConnected) AccentGreen else TextSecondary
        )

        if (isWifiConnected) {
            DetailInfoRow("Network Name", wifiSsid)
            DetailInfoRow("IP Address", ipAddress)

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNavigateToSpeedTest,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassBackgroundHighlight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Run Internet Speed Test", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// --- Helper Components ---
@Composable
fun HealthInfoRow(healthStatus: String) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("About Battery Health", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This shows the battery's current operational status, not its long-term " +
                    "capacity. 'Good' means the battery is functioning " +
                    "without any immediate hardware issues like overheating or failure.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BgGradientMiddle,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDialog = true }
                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(text = "Health", color = TextSecondary, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Info about battery health",
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = healthStatus, 
            color = if (healthStatus.equals("Good", ignoreCase = true)) AccentGreen else AccentPink, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DetailGlassCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    DetailGlassCardBase(
        title = title,
        iconContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(24.dp))
            }
        },
        content = content
    )
}

@Composable
fun DetailGlassCard(
    title: String,
    iconContent: @Composable () -> Unit,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    DetailGlassCardBase(title = title, iconContent = iconContent, content = content)
}

@Composable
private fun DetailGlassCardBase(
    title: String,
    iconContent: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(GlassBackgroundHighlight, GlassBackground)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(colors = listOf(GlassBorder, Color.Transparent)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                iconContent()
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = GlassBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun DetailInfoRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label, 
            color = TextSecondary, 
            fontSize = 15.sp, 
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value, 
            color = valueColor, 
            fontSize = 15.sp, 
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1.5f).padding(start = 16.dp)
        )
    }
}

@Composable
fun DetailInfoRowWithIconInValue(label: String, value: String, icon: ImageVector?, iconTint: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label, 
            color = TextSecondary, 
            fontSize = 15.sp, 
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1.5f).padding(start = 16.dp)
        ) {
            Text(
                text = value, 
                color = TextPrimary, 
                fontSize = 16.sp, 
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Icon",
                    tint = iconTint,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 6.dp)
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
    accentColor: Color,
    thunderOffsetX: Dp = 0.dp,
    thunderOffsetY: Dp = 0.dp
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = getBatteryIcon(level),
                contentDescription = "Battery Level",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )

            if (isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Charging",
                    tint = Color.White,
                    modifier = Modifier
                        .size(14.dp)
                        .offset(x = thunderOffsetX, y = thunderOffsetY)
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DetailScreenPreview(){
    PhoneInfoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
            contentAlignment = Alignment.Center
        ) {
            WifiCard(
                isWifiConnected = true,
                wifiSignalStrength = 2,
                wifiSsid = "Mudasir",
                ipAddress =  "19.0.0.01",
                onNavigateToSpeedTest = {}
            )
        }
    }
}
