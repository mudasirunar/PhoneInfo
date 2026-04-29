package com.example.phoneinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.phoneinfo.PhoneInfoViewModel

@Composable
fun HomeScreen(
    onNavigateToDetails: () -> Unit,
    phoneInfoViewModel: PhoneInfoViewModel = viewModel(
        factory = PhoneInfoViewModel.PhoneInfoViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val deviceInfo by phoneInfoViewModel.deviceInfo.collectAsState()

    // A beautiful gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2C3E50),
            Color(0xFF4CA1AF)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Title
            Text(
                text = "Device Overview",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 64.dp)
            )

            // Info Section
            InfoCard(deviceInfo = deviceInfo, viewModel = phoneInfoViewModel)

            // Button to navigate
            Button(
                onClick = onNavigateToDetails,
                shape = RoundedCornerShape(50), // Makes it pill-shaped
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
            ) {
                Text(
                    text = "See Advanced Info",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun InfoCard(deviceInfo: DeviceInfo, viewModel: PhoneInfoViewModel) {
    // This creates the "glassmorphism" effect
    val glassBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.3f),
            Color.White.copy(alpha = 0.1f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glassBrush)
            .padding(24.dp)
    ) {
        // --- Device Name ---
        InfoRow("Device Name", deviceInfo.deviceName)
        InfoDivider()

        // --- RAM Usage ---
        val usedRamFormatted = viewModel.formatBytes(deviceInfo.usedRam)
        val totalRamFormatted = viewModel.formatBytes(deviceInfo.totalRam)
        InfoRow("RAM Usage", "$usedRamFormatted / $totalRamFormatted")
        InfoDivider()

        // --- Storage ---
        deviceInfo.internalStorage?.let {
            StorageInfo(
                label = "Internal Storage",
                usedStorage = it.used,
                totalStorage = it.total,
                formatBytes = viewModel::formatBytes
            )
        }

        deviceInfo.externalStorage?.let {
            InfoDivider()
            StorageInfo(
                label = "SD Card",
                usedStorage = it.used,
                totalStorage = it.total,
                formatBytes = viewModel::formatBytes
            )
        }
    }
}

@Composable
fun StorageInfo(label: String, usedStorage: Long, totalStorage: Long, formatBytes: (Long) -> String) {
    val usedPercentage = if (totalStorage > 0) usedStorage.toFloat() / totalStorage.toFloat() else 0f
    val usedStorageFormatted = formatBytes(usedStorage)
    val totalStorageFormatted = formatBytes(totalStorage)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light)
            Text(
                text = "$usedStorageFormatted / $totalStorageFormatted",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { usedPercentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoDivider() {
    Divider(
        color = Color.White.copy(alpha = 0.3f),
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}
