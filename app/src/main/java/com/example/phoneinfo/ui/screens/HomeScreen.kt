package com.example.phoneinfo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = "System Monitor",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Real-time diagnostics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentCyan,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

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

                    // RAM Section
                    val usedRamFormatted = phoneInfoViewModel.formatBytes(deviceInfo.usedRam)
                    val totalRamFormatted = phoneInfoViewModel.formatBytes(deviceInfo.totalRam)
                    val ramPercentage = if (deviceInfo.totalRam > 0) deviceInfo.usedRam.toFloat() / deviceInfo.totalRam.toFloat() else 0f
                    
                    MetricRow(
                        title = "RAM",
                        value = "$usedRamFormatted / $totalRamFormatted",
                        percentage = ramPercentage,
                        accentColor = AccentPurple,
                        icon = Icons.Default.DeveloperBoard
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Internal Storage Section
                    deviceInfo.internalStorage?.let {
                        val usedPercentage = if (it.total > 0) it.used.toFloat() / it.total.toFloat() else 0f
                        MetricRow(
                            title = "Internal Storage",
                            value = "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}",
                            percentage = usedPercentage,
                            accentColor = AccentCyan,
                            icon = Icons.Default.Storage
                        )
                    }
                    
                    deviceInfo.externalStorage?.let {
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val usedPercentage = if (it.total > 0) it.used.toFloat() / it.total.toFloat() else 0f
                        MetricRow(
                            title = "SD Card",
                            value = "${phoneInfoViewModel.formatBytes(it.used)} / ${phoneInfoViewModel.formatBytes(it.total)}",
                            percentage = usedPercentage,
                            accentColor = AccentGreen,
                            icon = Icons.Default.Storage
                        )
                    }
                }
            }

            Button(
                onClick = onNavigateToDetails,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .height(64.dp)

                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                GlassBackgroundHighlight,
                                GlassBackground
                            )
                        )
                    )
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
                        text = "Advanced Analytics",
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
}

@Composable
fun GlassmorphicCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        GlassBackgroundHighlight,
                        GlassBackground
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorder,
                        Color.Transparent
                    )
                ),
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


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview(){
    PhoneInfoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {
            MetricRow(
                title = "RAM",
                value = "34",
                percentage = 21.2f,
                accentColor = AccentPurple,
                icon = Icons.Default.DeveloperBoard
            )
        }
    }
}