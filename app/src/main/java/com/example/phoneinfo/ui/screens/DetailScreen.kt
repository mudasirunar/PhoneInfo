package com.example.phoneinfo.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.phoneinfo.viewmodel.PhoneInfoViewModel
import com.example.phoneinfo.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSpeedTest: () -> Unit,
    onNavigateToApps: () -> Unit,
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
                    DetailInfoRow("Android Version", deviceInfo.androidVersion)
                    DetailInfoRow("RAM", "${phoneInfoViewModel.formatBytes(deviceInfo.usedRam)} / ${phoneInfoViewModel.formatBytes(deviceInfo.totalRam)}")
                    deviceInfo.internalStorage?.let {
                        DetailInfoRow("Internal Storage", "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}")
                    }
                    deviceInfo.externalStorage?.let {
                        DetailInfoRow("SD Card", "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}")
                    }
                }
            }

            // --- Hardware Identity ---
            item {
                var isHardwareExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "Hardware Identity", 
                    icon = Icons.Default.QrCodeScanner, 
                    accentColor = AccentBlue,
                    expandable = true,
                    isExpanded = isHardwareExpanded,
                    onExpandToggle = { isHardwareExpanded = !isHardwareExpanded }
                ) {
                    DetailInfoRow("Manufacturer", deviceInfo.manufacturer)
                    DetailInfoRow("Build ID", deviceInfo.buildId)
                    DetailInfoRow("Build Type", deviceInfo.buildType)

                    AnimatedVisibility(
                        visible = isHardwareExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DetailInfoRow("Brand", deviceInfo.brand)
                            DetailInfoRow("Model", deviceInfo.model)
                            DetailInfoRow("Codename", deviceInfo.deviceCodename)
                            DetailInfoRow("Board", deviceInfo.board)
                            DetailInfoRow("Hardware", deviceInfo.hardwareId)
                            DetailInfoRow("Product", deviceInfo.product)
                            DetailInfoRowWrap("Build Fingerprint", deviceInfo.buildFingerprint)
                        }
                    }
                }
            }

            // --- System Section ---
            item {
                var isSystemExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "System", 
                    icon = Icons.Default.PhoneAndroid, 
                    accentColor = AccentCyan,
                    expandable = true,
                    isExpanded = isSystemExpanded,
                    onExpandToggle = { isSystemExpanded = !isSystemExpanded }
                ) {
                    DetailInfoRow("Security Patch", deviceInfo.securityPatch)
                    DetailInfoRow("Uptime", phoneInfoViewModel.formatMillisToUptime(deviceInfo.uptime))
                    DetailInfoRow("First Online", phoneInfoViewModel.formatTimestampToDate(deviceInfo.firstInstallTime))
                    
                    AnimatedVisibility(
                        visible = isSystemExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DetailInfoRow("SDK Level", "API ${deviceInfo.sdkVersion}")
                            DetailInfoRow("Build Number", deviceInfo.buildNumber)
                            DetailInfoRow("Kernel Version", deviceInfo.kernelVersion)
                            DetailInfoRow("Bootloader", deviceInfo.bootloaderVersion)
                            DetailInfoRow("Baseband", deviceInfo.basebandVersion)
                            DetailInfoRow("Language", deviceInfo.systemLanguage)
                            DetailInfoRow("Timezone", deviceInfo.timezone)
                            DetailInfoRow("Root Access", if (deviceInfo.isRooted) "Yes" else "No", valueColor = if (deviceInfo.isRooted) AccentPink else TextPrimary)
                        }
                    }
                }
            }

            // --- CPU Card ---
            item {
                var isCpuExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "Processor", 
                    icon = Icons.Default.Memory, 
                    accentColor = AccentOrange,
                    expandable = true,
                    isExpanded = isCpuExpanded,
                    onExpandToggle = { isCpuExpanded = !isCpuExpanded }
                ) { 
                    DetailInfoRow("Processor", deviceInfo.processorName)
                    DetailInfoRow("Architecture", deviceInfo.cpuArchitecture)
                    DetailInfoRow("Cores", "${deviceInfo.coreCount}")
                    
                    AnimatedVisibility(
                        visible = isCpuExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            if (deviceInfo.coreFrequencies != "N/A") { 
                                DetailInfoRow("Core Speeds", deviceInfo.coreFrequencies)
                            }
                            DetailInfoRow("Supported ABIs", deviceInfo.supportedAbis)
                            if (deviceInfo.cpuTemperature != "N/A") {
                                DetailInfoRow("CPU Temperature", deviceInfo.cpuTemperature, valueColor = AccentOrange)
                            }
                        }
                    }
                }
            }
            
            // --- Memory Card ---
            item {
                var isMemoryExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "Memory", 
                    icon = Icons.Default.SdStorage, 
                    accentColor = AccentTeal,
                    expandable = true,
                    isExpanded = isMemoryExpanded,
                    onExpandToggle = { isMemoryExpanded = !isMemoryExpanded }
                ) {
                    DetailInfoRow("Total RAM", phoneInfoViewModel.formatBytes(deviceInfo.totalRam))
                    DetailInfoRow("Used RAM", phoneInfoViewModel.formatBytes(deviceInfo.usedRam))
                    DetailInfoRow("Available RAM", phoneInfoViewModel.formatBytes(deviceInfo.availableRam))

                    AnimatedVisibility(
                        visible = isMemoryExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DetailInfoRow("Low Memory Threshold", phoneInfoViewModel.formatBytes(deviceInfo.lowMemoryThreshold))
                            DetailInfoRow("Is Low Memory", if (deviceInfo.isLowMemory) "Yes" else "No", valueColor = if (deviceInfo.isLowMemory) AccentPink else TextPrimary)
                        }
                    }
                }
            }

            // --- Camera Card ---
            if (deviceInfo.cameraInfos.isNotEmpty()) {
                item {
                    var isCameraExpanded by rememberSaveable { mutableStateOf(false) }
                    DetailGlassCard(
                        title = "Cameras",
                        icon = Icons.Default.CameraAlt,
                        accentColor = AccentYellow,
                        expandable = true,
                        isExpanded = isCameraExpanded,
                        onExpandToggle = { isCameraExpanded = !isCameraExpanded }
                    ) {
                        deviceInfo.cameraInfos.forEachIndexed { index, camera ->
                            if (index > 0) {
                                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))
                            }
                            Text(
                                text = "Camera ID: ${camera.id}",
                                color = AccentYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            DetailInfoRow("Facing", camera.facing)
                            DetailInfoRow("Resolution", camera.megapixels)

                            AnimatedVisibility(
                                visible = isCameraExpanded,
                                enter = expandVertically(animationSpec = tween(300)),
                                exit = shrinkVertically(animationSpec = tween(300))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DetailInfoRow("Flash Available", if (camera.hasFlash) "Yes" else "No")
                                    DetailInfoRow("Focal Lengths", camera.focalLengths)
                                    DetailInfoRow("Apertures", camera.apertures)
                                    DetailInfoRow("OIS Supported", if (camera.opticalStabilization) "Yes" else "No")
                                }
                            }
                        }
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
                    wifiFrequency = deviceInfo.wifiFrequency,
                    wifiLinkSpeed = deviceInfo.wifiLinkSpeed,
                    wifiBssid = deviceInfo.wifiBssid,
                    macAddress = deviceInfo.macAddress,
                    onNavigateToSpeedTest = onNavigateToSpeedTest
                )
            }

            // --- Bluetooth Card ---
            item {
                DetailGlassCard(title = "Bluetooth", icon = Icons.Default.Bluetooth, accentColor = AccentBlue) {
                    DetailInfoRow("Supported", if (deviceInfo.isBluetoothSupported) "Yes" else "No")
                    DetailInfoRow("Enabled", if (deviceInfo.isBluetoothEnabled) "Yes" else "No")
                    DetailInfoRow("Name", deviceInfo.bluetoothName)
                }
            }

            // --- Mobile Network Card ---
            if (deviceInfo.simInfos.isNotEmpty()) {
                item {
                    var isSimExpanded by rememberSaveable { mutableStateOf(false) }
                    DetailGlassCard(
                        title = "Mobile Network",
                        icon = Icons.Default.SignalCellularAlt,
                        accentColor = AccentPurple,
                        expandable = true,
                        isExpanded = isSimExpanded,
                        onExpandToggle = { isSimExpanded = !isSimExpanded }
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
                            DetailInfoRow("Country ISO", simInfo.countryIso)
                            
                            simInfo.phoneNumber?.let { number ->
                                var isNumberVisible by rememberSaveable { mutableStateOf(false) }

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

                            AnimatedVisibility(
                                visible = isSimExpanded,
                                enter = expandVertically(animationSpec = tween(300)),
                                exit = shrinkVertically(animationSpec = tween(300))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DetailInfoRow("State", simInfo.simState)
                                    DetailInfoRow("Roaming", if (simInfo.isRoaming) "Yes" else "No")
                                }
                            }
                        }
                    }
                }
            }

            // --- Battery Section ---
            item {
                var isBatteryExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "Battery",
                    iconContent = {
                        BatteryWithChargingOverlay(
                            level = deviceInfo.batteryLevel,
                            isCharging = deviceInfo.isCharging,
                            accentColor = AccentGreen
                        )
                    },
                    accentColor = AccentGreen,
                    expandable = true,
                    isExpanded = isBatteryExpanded,
                    onExpandToggle = { isBatteryExpanded = !isBatteryExpanded }
                ) {
                    DetailInfoRowWithIconInValue(
                        "Level", 
                        "${deviceInfo.batteryLevel}%", 
                        icon = if (deviceInfo.isCharging) Icons.Default.Bolt else null,
                        iconTint = AccentGreen
                    )
                    HealthInfoRow(healthStatus = deviceInfo.batteryHealth)
                    DetailInfoRow("Temperature", "${deviceInfo.batteryTemperature}°C")

                    AnimatedVisibility(
                        visible = isBatteryExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DetailInfoRow("Voltage", "${deviceInfo.batteryVoltage} mV")
                            DetailInfoRow("Charging Source", deviceInfo.chargingSource)
                            DetailInfoRow("Technology", deviceInfo.batteryTechnology)
                        }
                    }
                }
            }

            // --- Display Section ---
            item {
                var isDisplayExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "Display", 
                    icon = Icons.Default.StayCurrentPortrait, 
                    accentColor = AccentCyan,
                    expandable = true,
                    isExpanded = isDisplayExpanded,
                    onExpandToggle = { isDisplayExpanded = !isDisplayExpanded }
                ) {
                    DetailInfoRow("Resolution", deviceInfo.screenResolution)
                    DetailInfoRow("Physical Size", "${deviceInfo.screenSizeInches}\"")
                    DetailInfoRow("Refresh Rate", "%.0f Hz".format(deviceInfo.refreshRate))

                    AnimatedVisibility(
                        visible = isDisplayExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DetailInfoRow("Density", "${deviceInfo.screenDensity} DPI")
                            DetailInfoRow("Font Scale", "${deviceInfo.fontScale}x")
                            DetailInfoRow("Orientation", deviceInfo.orientation)
                            DetailInfoRow("Night Mode", deviceInfo.nightMode)
                        }
                    }
                }
            }

            // --- Apps Section ---
            item {
                DetailGlassCard(
                    title = "Applications", 
                    icon = Icons.Default.Apps, 
                    accentColor = AccentTeal,
                    expandable = true,
                    isExpanded = false,
                    customActionText = "View Apps",
                    onExpandToggle = onNavigateToApps
                ) {
                    DetailInfoRow("Total Apps", "${deviceInfo.totalApps}")
                    DetailInfoRow("System Apps", "${deviceInfo.systemApps}")
                    DetailInfoRow("User Apps", "${deviceInfo.userApps}")
                }
            }

            // --- Java VM Section ---
            item {
                var isJavaExpanded by rememberSaveable { mutableStateOf(false) }
                var showJavaDialog by remember { mutableStateOf(false) }

                if (showJavaDialog) {
                    AlertDialog(
                        onDismissRequest = { showJavaDialog = false },
                        title = { Text("About Java Runtime", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "The Java Virtual Machine (VM) executes your Android apps. " +
                                "The Heap Size indicates how much RAM is currently allocated specifically " +
                                "to the VM, which impacts how smoothly heavy apps can run before " +
                                "running out of memory.",
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showJavaDialog = false }) {
                                Text("Got it", color = AccentPurple, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = BgGradientMiddle,
                        titleContentColor = TextPrimary,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    )
                }

                DetailGlassCard(
                    title = "Java Runtime", 
                    icon = Icons.Default.Code, 
                    accentColor = AccentPurple,
                    expandable = true,
                    isExpanded = isJavaExpanded,
                    onExpandToggle = { isJavaExpanded = !isJavaExpanded },
                    onInfoClick = { showJavaDialog = true }
                ) {
                    DetailInfoRow("VM Name", deviceInfo.vmName)
                    DetailInfoRow("VM Version", deviceInfo.vmVersion)
                    DetailInfoRow("Heap Size", phoneInfoViewModel.formatBytes(deviceInfo.vmHeapSize))

                    AnimatedVisibility(
                        visible = isJavaExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DetailInfoRow("Max Heap", phoneInfoViewModel.formatBytes(deviceInfo.vmMaxHeap))
                            DetailInfoRow("Free Heap", phoneInfoViewModel.formatBytes(deviceInfo.vmFreeHeap))
                        }
                    }
                }
            }
            
            // --- Sensors Section ---
            item {
                var isSensorsExpanded by rememberSaveable { mutableStateOf(false) }
                DetailGlassCard(
                    title = "Sensors", 
                    icon = Icons.Default.Sensors, 
                    accentColor = AccentOrange,
                    expandable = true,
                    isExpanded = isSensorsExpanded,
                    onExpandToggle = { 
                        isSensorsExpanded = !isSensorsExpanded 
                        if (isSensorsExpanded) {
                            coroutineScope.launch {
                                // Smoothly scroll down dynamically based on screen height
                                delay(150)
                                listState.animateScrollBy(scrollDistancePx, animationSpec = tween(400))
                            }
                        }
                    }
                ) {
                    DetailInfoRow("Total Sensors", "${deviceInfo.sensorCount} found")
                    
                    if (deviceInfo.sensors.isNotEmpty()) {
                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                        
                        Column {
                            // Show up to 5 sensors always
                            val initialSensors = deviceInfo.sensors.take(5)
                            initialSensors.forEach { sensorName ->
                                Text(
                                    text = "• $sensorName",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            
                            // Show the rest if expanded
                            val remainingSensors = deviceInfo.sensors.drop(5)
                            if (remainingSensors.isNotEmpty()) {
                                AnimatedVisibility(
                                    visible = isSensorsExpanded,
                                    enter = expandVertically(animationSpec = tween(300)),
                                    exit = shrinkVertically(animationSpec = tween(300))
                                ) {
                                    Column {
                                        remainingSensors.forEach { sensorName ->
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
        }
    }
}

@Composable
fun WifiCard(
    isWifiConnected: Boolean,
    wifiSignalStrength: Int,
    wifiSsid: String,
    ipAddress: String,
    wifiFrequency: Int,
    wifiLinkSpeed: Int,
    wifiBssid: String,
    macAddress: String,
    onNavigateToSpeedTest:() -> Unit
){
    var isWifiExpanded by rememberSaveable { mutableStateOf(false) }
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
        accentColor = wifiAccentColor,
        expandable = isWifiConnected,
        isExpanded = isWifiExpanded,
        onExpandToggle = { isWifiExpanded = !isWifiExpanded }
    ) {
        DetailInfoRow(
            "Status",
            if (isWifiConnected) "Connected" else "Disconnected",
            valueColor = if (isWifiConnected) AccentGreen else TextSecondary
        )

        if (isWifiConnected) {
            DetailInfoRow("Network Name", wifiSsid)
            DetailInfoRow("IP Address", ipAddress)
            
            AnimatedVisibility(
                visible = isWifiExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                    DetailInfoRow("Frequency", "${wifiFrequency} MHz")
                    DetailInfoRow("Link Speed", "${wifiLinkSpeed} Mbps")
                    DetailInfoRow("BSSID", wifiBssid)
                    DetailInfoRow("MAC Address", macAddress)
                }
            }

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
                    Text("Got it", color = AccentGreen, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(text = "Health", color = TextSecondary, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { showDialog = true }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info about battery health",
                    tint = AccentGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
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
    expandable: Boolean = false,
    isExpanded: Boolean = false,
    customActionText: String? = null,
    onExpandToggle: () -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
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
        accentColor = accentColor,
        expandable = expandable,
        isExpanded = isExpanded,
        customActionText = customActionText,
        onExpandToggle = onExpandToggle,
        onInfoClick = onInfoClick,
        content = content
    )
}

@Composable
fun DetailGlassCard(
    title: String,
    iconContent: @Composable () -> Unit,
    accentColor: Color,
    expandable: Boolean = false,
    isExpanded: Boolean = false,
    customActionText: String? = null,
    onExpandToggle: () -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DetailGlassCardBase(
        title = title, 
        iconContent = iconContent, 
        accentColor = accentColor,
        expandable = expandable,
        isExpanded = isExpanded,
        customActionText = customActionText,
        onExpandToggle = onExpandToggle,
        onInfoClick = onInfoClick,
        content = content
    )
}

@Composable
private fun DetailGlassCardBase(
    title: String,
    iconContent: @Composable () -> Unit,
    accentColor: Color = AccentCyan,
    expandable: Boolean = false,
    isExpanded: Boolean = false,
    customActionText: String? = null,
    onExpandToggle: () -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundBrush = remember {
        Brush.linearGradient(
            colors = listOf(GlassBackgroundHighlight, GlassBackground)
        )
    }
    val borderBrush = remember {
        Brush.linearGradient(colors = listOf(GlassBorder, Color.Transparent))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundBrush)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    if (onInfoClick != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onInfoClick() }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Info about $title",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                if (expandable) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassBackgroundHighlight)
                            .clickable { onExpandToggle() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = customActionText ?: if (isExpanded) "View Less" else "View More",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
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
            textAlign = TextAlign.End,
            lineHeight = 20.sp, // improved spacing for wrapped text
            modifier = Modifier.weight(1.5f).padding(start = 16.dp)
        )
    }
}

@Composable
fun DetailInfoRowWrap(label: String, value: String, valueColor: Color = TextPrimary) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label, 
            color = TextSecondary, 
            fontSize = 15.sp
        )
        Text(
            text = value, 
            color = valueColor, 
            fontSize = 15.sp, 
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp
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
                textAlign = TextAlign.End,
                lineHeight = 20.sp
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
                wifiFrequency = 2400,
                wifiLinkSpeed = 150,
                wifiBssid = "00:11:22:33:44:55",
                macAddress = "AA:BB:CC:DD:EE:FF",
                onNavigateToSpeedTest = {}
            )
        }
    }
}
