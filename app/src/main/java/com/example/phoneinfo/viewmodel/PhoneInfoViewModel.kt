package com.example.phoneinfo.viewmodel

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import android.text.format.Formatter
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.phoneinfo.data.AppDetail
import com.example.phoneinfo.data.CameraInfo
import com.example.phoneinfo.data.DeviceInfo
import com.example.phoneinfo.data.SimInfo
import com.example.phoneinfo.data.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PhoneInfoViewModel(private val context: Context) : ViewModel() {

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo = _deviceInfo.asStateFlow()

    private val _appDetails = MutableStateFlow<List<AppDetail>>(emptyList())
    val appDetails = _appDetails.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps = _isLoadingApps.asStateFlow()

    fun loadApps() {
        if (_appDetails.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val packageManager = context.packageManager
            val apps = packageManager.getInstalledPackages(0)
            
            val launchIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchablePackages = packageManager.queryIntentActivities(launchIntent, 0).map { it.activityInfo.packageName }.toSet()

            val appList = mutableListOf<AppDetail>()
            for (packInfo in apps) {
                val appInfo = packInfo.applicationInfo ?: continue
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 || 
                                  (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                // Filter out test apps
                if (appInfo.packageName.contains(".test", ignoreCase = true)) continue
                
                if (!isSystemApp || launchablePackages.contains(appInfo.packageName)) {
                    val name = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    val versionName = packInfo.versionName ?: "Unknown"
                    val installTime = packInfo.firstInstallTime
                    
                    var size = 0L
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val storageStatsManager = context.getSystemService(android.content.Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                            val storageStats = storageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, appInfo.uid)
                            size = storageStats.appBytes + storageStats.dataBytes + storageStats.cacheBytes
                        } else {
                            size = (appInfo.sourceDir?.let { File(it).length() } ?: 0L) + 
                                   (appInfo.publicSourceDir?.let { File(it).length() } ?: 0L)
                        }
                    } catch (e: Exception) {
                        size = (appInfo.sourceDir?.let { File(it).length() } ?: 0L)
                    }

                    appList.add(AppDetail(name, packageName, versionName, size, installTime, isSystemApp))
                }
            }
            _appDetails.value = appList.sortedBy { it.name.lowercase(Locale.getDefault()) }
            _isLoadingApps.value = false
        }
    }

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
            packageManager.getPackageInfo("android", 0).firstInstallTime
        } catch (e: Exception) {
            0L
        }

        // --- Storage (Internal and External) ---
        val internalStatFs = StatFs(Environment.getDataDirectory().path)
        val totalInternal = internalStatFs.blockCountLong * internalStatFs.blockSizeLong
        val freeInternal = internalStatFs.availableBlocksLong * internalStatFs.blockSizeLong
        val internalStorageInfo =
            StorageInfo(total = totalInternal, used = totalInternal - freeInternal)

        var externalStorageInfo: StorageInfo? = null
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
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
            val activeSubscriptions: List<SubscriptionInfo> = try {
                subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            } catch (e: Exception) { emptyList() }

            for (subInfo in activeSubscriptions) {
                val specificTelephonyManager = telephonyManager.createForSubscriptionId(subInfo.subscriptionId)
                val networkType = getNetworkTypeName(specificTelephonyManager.dataNetworkType)
                val phoneNumber = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                    try { subInfo.number } catch (e: Exception) { null }
                } else null
                
                simInfos.add(
                    SimInfo(
                        operatorName = subInfo.carrierName.toString(),
                        networkType = networkType,
                        phoneNumber = if (phoneNumber.isNullOrEmpty()) "Number not available" else phoneNumber,
                        countryIso = specificTelephonyManager.simCountryIso?.uppercase() ?: "N/A",
                        isRoaming = specificTelephonyManager.isNetworkRoaming,
                        simState = getSimStateName(specificTelephonyManager.simState),
                        slotIndex = subInfo.simSlotIndex
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
        val batteryVoltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }


        // --- WiFi Details ---
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        var wifiSsid = "Not Connected"
        var wifiSignalLevel = 0
        var ipAddress = "N/A"
        var wifiFrequency = 0
        var wifiLinkSpeed = 0
        var wifiBssid = "N/A"
        var macAddress = "N/A"
        if (isWifiConnected) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            wifiSignalLevel = WifiManager.calculateSignalLevel(wifiInfo.rssi, 4)
            ipAddress = Formatter.formatIpAddress(wifiInfo.ipAddress)
            wifiFrequency = wifiInfo.frequency
            wifiLinkSpeed = wifiInfo.linkSpeed
            wifiBssid = wifiInfo.bssid ?: "N/A"
            macAddress = try { wifiInfo.macAddress ?: "N/A" } catch (e: Exception) { "N/A" }

            if (wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>") {
                wifiSsid = wifiInfo.ssid.removePrefix("\"").removeSuffix("\"")
            } else {
                wifiSsid = "Enable Location Services"
            }
        }
        
        // Display
        val displayMetrics = context.resources.displayMetrics
        val refreshRate = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY).refreshRate
        val screenSizeInches = String.format("%.2f", Math.sqrt(Math.pow((displayMetrics.widthPixels / displayMetrics.xdpi).toDouble(), 2.0) + Math.pow((displayMetrics.heightPixels / displayMetrics.ydpi).toDouble(), 2.0)))
        val configuration = context.resources.configuration
        val fontScale = configuration.fontScale
        val orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
        val nightMode = if ((configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "Yes" else "No"

        // Sensors
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val availableSensors = sensorManager.getSensorList(Sensor.TYPE_ALL).map { cleanSensorName(it.name) }
        
        // Bluetooth
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val isBluetoothSupported = bluetoothAdapter != null
        val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
        val bluetoothName = try { 
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bluetoothAdapter?.name ?: "N/A" 
            } else "Permission Denied"
        } catch (e: SecurityException) { "Permission Denied" }

        // Apps
        val apps = packageManager.getInstalledApplications(0)
        var systemAppsCount = 0
        var userAppsCount = 0
        
        val launchIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePackages = packageManager.queryIntentActivities(launchIntent, 0).map { it.activityInfo.packageName }.toSet()

        for (app in apps) {
            val isSystemApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystemApp) {
                userAppsCount++
            } else if (launchablePackages.contains(app.packageName)) {
                systemAppsCount++
            }
        }
        val totalAppsCount = systemAppsCount + userAppsCount

        // Update State
        _deviceInfo.value = _deviceInfo.value.copy(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            totalRam = memoryInfo.totalMem,
            usedRam = memoryInfo.totalMem - memoryInfo.availMem,
            internalStorage = internalStorageInfo,
            externalStorage = externalStorageInfo,

            // Hardware
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            deviceCodename = Build.DEVICE,
            board = Build.BOARD,
            hardwareId = Build.HARDWARE,
            product = Build.PRODUCT,
            buildFingerprint = Build.FINGERPRINT,
            buildId = Build.ID,
            buildType = Build.TYPE,

            // System Info
            androidVersion = Build.VERSION.RELEASE,
            isRooted = isDeviceRooted(),
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            kernelVersion = System.getProperty("os.version") ?: "N/A",
            uptime = SystemClock.elapsedRealtime(),
            firstInstallTime = firstInstallTime,
            sdkVersion = Build.VERSION.SDK_INT,
            buildNumber = Build.DISPLAY,
            bootloaderVersion = Build.BOOTLOADER,
            basebandVersion = Build.getRadioVersion() ?: "N/A",
            systemLanguage = Locale.getDefault().displayLanguage,
            timezone = TimeZone.getDefault().id,

            // CPU
            processorName = getCpuInfo(),
            cpuArchitecture = System.getProperty("os.arch") ?: "N/A",
            coreCount = Runtime.getRuntime().availableProcessors(),
            coreFrequencies = getCpuFrequencies(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),

            // Battery Info
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            batteryHealth = batteryHealth,
            batteryTemperature = batteryTemp,
            batteryTechnology = batteryTech,
            batteryVoltage = batteryVoltage,
            chargingSource = chargingSource,

            // Display Info
            screenResolution = "${displayMetrics.heightPixels} x ${displayMetrics.widthPixels}",
            screenDensity = displayMetrics.densityDpi,
            refreshRate = refreshRate,
            screenSizeInches = screenSizeInches,
            fontScale = fontScale,
            orientation = orientation,
            nightMode = nightMode,

            // WiFi Info
            isWifiConnected = isWifiConnected,
            wifiSsid = wifiSsid,
            wifiSignalStrength = wifiSignalLevel,
            ipAddress = ipAddress,
            wifiFrequency = wifiFrequency,
            wifiLinkSpeed = wifiLinkSpeed,
            wifiBssid = wifiBssid,
            macAddress = macAddress,

            // Bluetooth
            bluetoothName = bluetoothName,
            isBluetoothSupported = isBluetoothSupported,
            isBluetoothEnabled = isBluetoothEnabled,

            // Mobile
            simInfos = simInfos,

            // Sensors
            sensors = availableSensors,
            sensorCount = availableSensors.size,
            
            // Thermal
            cpuTemperature = getCpuTemperature(),
            
            // Memory
            availableRam = memoryInfo.availMem,
            lowMemoryThreshold = memoryInfo.threshold,
            isLowMemory = memoryInfo.lowMemory,
            
            // Apps
            totalApps = totalAppsCount,
            systemApps = systemAppsCount,
            userApps = userAppsCount,
            
            // Java VM
            vmName = System.getProperty("java.vm.name") ?: "N/A",
            vmVersion = System.getProperty("java.vm.version") ?: "N/A",
            vmHeapSize = Runtime.getRuntime().totalMemory(),
            vmMaxHeap = Runtime.getRuntime().maxMemory(),
            vmFreeHeap = Runtime.getRuntime().freeMemory(),
            
            // Camera
            cameraInfos = getCameraInfos()
        )
    }

    // --- New Helper Functions ---
    private fun getCpuTemperature(): String {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            process.waitFor()
            if (line != null) {
                val temp = line.toFloat() / 1000.0f
                "%.1f °C".format(temp)
            } else "N/A"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getCameraInfos(): List<CameraInfo> {
        val cameraInfos = mutableListOf<CameraInfo>()
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (cameraId in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                
                // Get Focal Lengths
                val focalLengthsArray = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focalLengths = focalLengthsArray?.joinToString(", ") { "${it}mm" } ?: "N/A"
                
                // Get Apertures
                val aperturesArray = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                val apertures = aperturesArray?.joinToString(", ") { "f/$it" } ?: "N/A"
                
                // Optical Stabilization
                val oisArray = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                val hasOis = oisArray?.any { it != CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF } ?: false
                
                // Simple Megapixel estimation (just array size * something, or active array size)
                val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val megapixels = if (activeArray != null) {
                    val mp = (activeArray.width() * activeArray.height()) / 1_000_000.0
                    "%.1f MP".format(mp)
                } else "N/A"

                cameraInfos.add(CameraInfo(
                    id = cameraId,
                    facing = facing,
                    megapixels = megapixels,
                    hasFlash = hasFlash,
                    focalLengths = focalLengths,
                    apertures = apertures,
                    opticalStabilization = hasOis,
                    supportedModes = emptyList() // Omitted for brevity
                ))
            }
        } catch (e: Exception) {
            // Ignore
        }
        return cameraInfos
    }

    private fun getSimStateName(state: Int): String {
        return when (state) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "Absent"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
            else -> "Unknown"
        }
    }

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
        // Try Build.SOC_MODEL first (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = Build.SOC_MODEL
            if (!socModel.isNullOrBlank() && socModel != Build.UNKNOWN) {
                return socModel
            }
        }

        return try {
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var hardwareName = "N/A"
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("Hardware", ignoreCase = true)) {
                    hardwareName = line!!.substringAfter(":").trim()
                    break
                }
            }
            reader.close()
            process.waitFor()
            
            if (hardwareName == "N/A" || hardwareName.isBlank()) {
                val hardware = Build.HARDWARE
                if (!hardware.isNullOrBlank() && hardware != Build.UNKNOWN) {
                    hardwareName = hardware
                }
            }
            
            hardwareName
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
