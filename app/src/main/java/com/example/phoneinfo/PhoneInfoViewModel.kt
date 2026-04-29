package com.example.phoneinfo

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data classes for new structured data
data class SimInfo(
    val operatorName: String,
    val networkType: String,
    val phoneNumber: String?
)

data class StorageInfo(
    val total: Long,
    val used: Long,
)

data class DeviceInfo(
    // Home
    val deviceName: String = "",
    val totalRam: Long = 0L,
    val usedRam: Long = 0L,
    val internalStorage: StorageInfo? = null,
    val externalStorage: StorageInfo? = null, // Can be null if no SD card

    // System
    val androidVersion: String = "",
    val securityPatch: String = "",
    val kernelVersion: String = "",
    val uptime: Long = 0L,
    val isRooted: Boolean = false, // New
    val firstInstallTime: Long = 0L,

    // CPU
    val processorName: String = "", // New
    val cpuArchitecture: String = "", // New
    val coreCount: Int = 0, // New
    val coreFrequencies: String = "",

    // Battery
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val batteryHealth: String = "",
    val batteryTemperature: Float = 0f,
    val batteryTechnology: String = "",

    // Display
    val screenResolution: String = "",
    val screenDensity: Int = 0,
    val refreshRate: Float = 0f,

    // WiFi
    val isWifiConnected: Boolean = false,
    val wifiSsid: String = "N/A",
    val wifiSignalStrength: Int = 0,
    val ipAddress: String = "N/A", // New

    // Mobile Network
    val simInfos: List<SimInfo> = emptyList(), // New list for multi-sim

    // Sensors
    val sensors: List<String> = emptyList(),
)
class PhoneInfoViewModel(private val context: Context) : ViewModel() {

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo = _deviceInfo.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                updateDeviceInfo()
                delay(2000) // Update interval
            }
        }
    }

    private fun updateDeviceInfo() {

        // --- System Info ---
        val packageManager = context.packageManager
        val firstInstallTime = try {
            // "android" is the package name for the base OS
            packageManager.getPackageInfo("android", 0).firstInstallTime
        } catch (e: Exception) {
            0L // Return 0 if it can't be found
        }

        // --- Storage (Internal and External) ---
        val internalStatFs = StatFs(Environment.getDataDirectory().path)
        val totalInternal = internalStatFs.blockCountLong * internalStatFs.blockSizeLong
        val freeInternal = internalStatFs.availableBlocksLong * internalStatFs.blockSizeLong
        val internalStorageInfo = StorageInfo(total = totalInternal, used = totalInternal - freeInternal)

        var externalStorageInfo: StorageInfo? = null
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        // The second directory is usually the external SD card.
        if (externalDirs.size > 1 && externalDirs[1] != null && Environment.isExternalStorageRemovable(externalDirs[1])) {
            val sdCardPath = externalDirs[1].path
            val statFs = StatFs(sdCardPath)
            val totalExternal = statFs.blockCountLong * statFs.blockSizeLong
            val freeExternal = statFs.availableBlocksLong * statFs.blockSizeLong
            externalStorageInfo = StorageInfo(total = totalExternal, used = totalExternal - freeExternal)
        }

        // --- SIM Info (Dual SIM support) ---
        val simInfos = mutableListOf<SimInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val activeSubscriptions: List<SubscriptionInfo> = subscriptionManager.activeSubscriptionInfoList ?: emptyList()

            for (subInfo in activeSubscriptions) {
                val specificTelephonyManager = telephonyManager.createForSubscriptionId(subInfo.subscriptionId)
                val networkType = getNetworkTypeName(specificTelephonyManager.dataNetworkType)
                // Get phone number only if permission is granted
                val phoneNumber = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                    subInfo.number
                } else {
                    null // Set to null if permission is denied
                }

                simInfos.add(
                    SimInfo(
                        operatorName = subInfo.carrierName.toString(),
                        networkType = networkType,
                        phoneNumber = if (phoneNumber.isNullOrEmpty()) "Number not available" else phoneNumber
                    )
                )
            }
        }
        // RAM
        val memoryInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memoryInfo)

        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        val batteryHealthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val batteryHealth = when(batteryHealthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        val batteryTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10f) ?: 0f
        val batteryTech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"

        // --- WiFi Details ---
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        var wifiSsid = "Not Connected"
        var wifiSignalLevel = 0
        var ipAddress = "N/A"
        if (isWifiConnected) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            wifiSignalLevel = WifiManager.calculateSignalLevel(wifiInfo.rssi, 4)
            ipAddress = android.text.format.Formatter.formatIpAddress(wifiInfo.ipAddress)

            if (wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>") {
                wifiSsid = wifiInfo.ssid.removePrefix("\"").removeSuffix("\"")
            } else {
                wifiSsid = "Enable Location Services"
            }
        }
        // Display
        val displayMetrics = context.resources.displayMetrics
        val refreshRate = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY).refreshRate

        // Sensors
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val availableSensors = sensorManager.getSensorList(Sensor.TYPE_ALL).map { cleanSensorName(it.name) }

        // Update State
        _deviceInfo.value = _deviceInfo.value.copy(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            totalRam = memoryInfo.totalMem,
            usedRam = memoryInfo.totalMem - memoryInfo.availMem,
            internalStorage = internalStorageInfo,
            externalStorage = externalStorageInfo,

            // System Info
            androidVersion = Build.VERSION.RELEASE,
            isRooted = isDeviceRooted(),
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            kernelVersion = System.getProperty("os.version") ?: "N/A",
            uptime = SystemClock.elapsedRealtime(),
            firstInstallTime = firstInstallTime,

            // CPU
            processorName = getCpuInfo(),
            cpuArchitecture = System.getProperty("os.arch") ?: "N/A",
            coreCount = Runtime.getRuntime().availableProcessors(),
            coreFrequencies = getCpuFrequencies(),

            // Battery Info
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            batteryHealth = batteryHealth,
            batteryTemperature = batteryTemp,
            batteryTechnology = batteryTech,

            // Display Info
            screenResolution = "${displayMetrics.heightPixels} x ${displayMetrics.widthPixels}",
            screenDensity = displayMetrics.densityDpi,
            refreshRate = refreshRate,

            // WiFi Info
            isWifiConnected = isWifiConnected,
            wifiSsid = wifiSsid,
            wifiSignalStrength = wifiSignalLevel,
            ipAddress = ipAddress,

            // Mobile
            simInfos = simInfos,

            // Sensors
            sensors = availableSensors
        )
    }

    // --- New Helper Functions ---
    private fun getCpuFrequencies(): String {
        try {
            val coreFrequencies = mutableMapOf<Long, Int>()
            val coreCount = Runtime.getRuntime().availableProcessors()
            for (i in 0 until coreCount) {
                val reader = BufferedReader(FileReader("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"))
                val maxFreq = reader.readLine().toLong() / 1000 // Convert from KHz to MHz
                reader.close()
                coreFrequencies[maxFreq] = (coreFrequencies[maxFreq] ?: 0) + 1
            }
            // Format the map into a readable string like "4x 2000 MHz, 4x 1800 MHz"
            return coreFrequencies.entries
                .sortedByDescending { it.key }
                .joinToString(", ") { "${it.value}x ${it.key} MHz" }
        } catch (e: Exception) {
            e.printStackTrace()
            return "N/A" // Return N/A if files can't be read
        }
    }

    fun formatTimestampToDate(timestamp: Long): String {
        if (timestamp == 0L) return "N/A"
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(date)
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su")
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun getCpuInfo(): String {
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("Hardware")) {
                    return line!!.split(":")[1].trim()
                }
            }
            "N/A"
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "Unknown"
        }
    }


    /**
     * NEW: Helper function to make sensor names more human-readable.
     * It removes common hardware prefixes.
     */
    private fun cleanSensorName(name: String): String {
        return name.split(" ").filterNot {
            it.equals("qti", ignoreCase = true) ||
                    it.equals("qualcomm", ignoreCase = true) ||
                    it.equals("invensense", ignoreCase = true) ||
                    it.equals("bosch", ignoreCase = true) ||
                    it.equals("lge", ignoreCase = true) ||
                    it.startsWith("oem", ignoreCase = true)
        }.joinToString(" ")
    }

    // Helper Functions
    fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes / (1024.0 * 1024.0)
        val df = DecimalFormat("#.##")
        return when {
            gb >= 1 -> "${df.format(gb)} GB"
            mb >= 1 -> "${df.format(mb)} MB"
            else -> "${bytes / 1024} KB"
        }
    }

    fun formatMillisToUptime(millis: Long): String {
        val seconds = millis / 1000
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val minutes = (seconds % 3600) / 60
        return "${days}d ${hours}h ${minutes}m"
    }

    // ViewModel Factory remains the same
    class PhoneInfoViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhoneInfoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PhoneInfoViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
