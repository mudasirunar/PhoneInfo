package com.example.phoneinfo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.phoneinfo.viewmodel.PhoneInfoViewModel
import com.example.phoneinfo.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToDetails: () -> Unit,
    phoneInfoViewModel: PhoneInfoViewModel = viewModel(
        factory = PhoneInfoViewModel.PhoneInfoViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val deviceInfo by phoneInfoViewModel.deviceInfo.collectAsState()
    val scrollState = rememberScrollState()

    var showBatteryHealthInfoDialog by remember {mutableStateOf(false)}
    if (showBatteryHealthInfoDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryHealthInfoDialog = false },
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
                TextButton(onClick = { showBatteryHealthInfoDialog = false }) {
                    Text("Got it", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BgGradientMiddle,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section
        Column{
            Text(
                text = "Phone Info",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Text(
                text = "Detailed system insights",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = AccentCyan,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(vertical = 12.dp)
            ) {
                // Central Glass Card
                GlassmorphicCard {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Device Name Header
                        Text(
                            text = deviceInfo.deviceName,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        SimpleInfoRow(
                            title = "Processor",
                            value = deviceInfo.processorName,
                            icon = Icons.Default.Memory,
                            accentColor = AccentOrange
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(20.dp))

                        // RAM Section
                        val usedRamFormatted = phoneInfoViewModel.formatBytes(deviceInfo.usedRam)
                        val totalRamFormatted = phoneInfoViewModel.formatBytes(deviceInfo.totalRam)
                        val ramPercentage =
                            if (deviceInfo.totalRam > 0) deviceInfo.usedRam.toFloat() / deviceInfo.totalRam.toFloat() else 0f


                        MetricRow(
                            title = "RAM",
                            value = "$usedRamFormatted / $totalRamFormatted",
                            percentage = ramPercentage,
                            accentColor = AccentPurple,
                            icon = Icons.Default.DeveloperBoard
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(20.dp))

                        // Internal Storage Section
                        deviceInfo.internalStorage?.let {
                            val usedPercentage =
                                if (it.total > 0) it.used.toFloat() / it.total.toFloat() else 0f
                            MetricRow(
                                title = "Internal Storage",
                                value = "${phoneInfoViewModel.formatBytes(it.used)} / ${
                                    phoneInfoViewModel.formatBytes(
                                        it.total
                                    )
                                }",
                                percentage = usedPercentage,
                                accentColor = AccentCyan,
                                icon = Icons.Default.Storage
                            )
                        }

                        deviceInfo.externalStorage?.let {
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(20.dp))

                            val usedPercentage =
                                if (it.total > 0) it.used.toFloat() / it.total.toFloat() else 0f
                            MetricRow(
                                title = "SD Card",
                                value = "${phoneInfoViewModel.formatBytes(it.used)} / ${
                                    phoneInfoViewModel.formatBytes(
                                        it.total
                                    )
                                }",
                                percentage = usedPercentage,
                                accentColor = AccentGreen,
                                icon = Icons.Default.Storage
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BatteryWithChargingOverlay(
                                    level = deviceInfo.batteryLevel,
                                    isCharging = deviceInfo.isCharging,
                                    accentColor = AccentGreen
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        showBatteryHealthInfoDialog = true
                                    }
                                ) {
                                    Text("Battery Health", color = TextSecondary, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = deviceInfo.batteryHealth,
                                color = if (deviceInfo.batteryHealth == "Good") AccentGreen else AccentPink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(20.dp))

                        SimpleInfoRow(
                            title = "Refresh Rate",
                            value = "%.0f Hz".format(deviceInfo.refreshRate),
                            icon = Icons.Default.StayCurrentPortrait,
                            accentColor = AccentCyan
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(20.dp))

                        SimpleInfoRow(
                            title = "Sensors",
                            value = "${deviceInfo.sensorCount} found",
                            icon = Icons.Default.Sensors,
                            accentColor = AccentOrange
                        )
                    }
                }
            }
        }
        val buttonBackgroundBrush = remember {
            Brush.linearGradient(
                colors = listOf(
                    GlassBackgroundHighlight,
                    GlassBackground
                )
            )
        }

        Button(
            onClick = onNavigateToDetails,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .height(64.dp)

                .clip(RoundedCornerShape(32.dp))
                .background(buttonBackgroundBrush)
                .border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Advanced Details",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
fun GlassmorphicCard(content: @Composable () -> Unit) {
    val backgroundBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                GlassBackgroundHighlight,
                GlassBackground
            )
        )
    }
    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                GlassBorder,
                Color.Transparent
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(backgroundBrush)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(32.dp)
            )
    ) {
        content()
    }
}

@Composable
fun MetricRow(
    title: String,
    value: String,
    percentage: Float,
    accentColor: Color,
    icon: ImageVector
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = accentColor,
            trackColor = GlassBackgroundHighlight,
            strokeCap = StrokeCap.Round,
        )
    }
}


@Composable
fun SimpleInfoRow(title: String, value: String, icon: ImageVector, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(icon = icon, color = accentColor)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, color = TextSecondary, fontSize = 16.sp)
        }
        Text(text = value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IconBox(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview(){
    PhoneInfoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {

        }
    }
}