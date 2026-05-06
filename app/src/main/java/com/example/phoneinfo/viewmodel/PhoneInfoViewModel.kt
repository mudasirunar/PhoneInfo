package com.example.phoneinfo.viewmodel

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
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
    private val _allAppsSize = MutableStateFlow(0L)
    val allAppsSize = _allAppsSize.asStateFlow()
    private val _installedAppsSize = MutableStateFlow(0L)
    val installedAppsSize = _installedAppsSize.asStateFlow()
    private val _systemAppsSize = MutableStateFlow(0L)
    val systemAppsSize = _systemAppsSize.asStateFlow()

    private fun updateStorageSizes(appsList: List<AppDetail>) {
        var all = 0L
        var installed = 0L
        var system = 0L
        for (app in appsList) {
            val size = app.size.coerceAtLeast(0L)
            all += size
            if (app.isSystemApp) {
                system += size
            } else {
                installed += size
            }
        }
        _allAppsSize.value = all
        _installedAppsSize.value = installed
        _systemAppsSize.value = system
    }

    fun loadApps(forceRefresh: Boolean = false) {
        if (!forceRefresh && (_appDetails.value.isNotEmpty() || _isLoadingApps.value)) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val packageManager = context.packageManager
            val apps = packageManager.getInstalledPackages(0)
            
            val systemInstallTime = try {
                packageManager.getPackageInfo("android", 0).firstInstallTime
            } catch (e: Exception) {
                Build.TIME
            }.coerceAtLeast(Build.TIME)

            val appList = mutableListOf<AppDetail>()
            for (packInfo in apps) {
                val appInfo = packInfo.applicationInfo ?: continue
                
                if (appInfo.packageName.contains("android.auto_generated") ||
                    appInfo.packageName.contains("android.stub") ||
                    appInfo.packageName.contains(".test", ignoreCase = true)) continue
                
                val isSystemApp = isPreloadedOrSystemApp(packageManager, packInfo, systemInstallTime)
                
                val name = packageManager.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName
                val versionName = packInfo.versionName ?: "Unknown"
                var installTime = packInfo.firstInstallTime
                if (installTime < Build.TIME) {
                    installTime = Build.TIME
                }
                
                // Quick size fallback initially
                var size = appInfo.sourceDir?.let { File(it).length() } ?: 0L
                if (appInfo.publicSourceDir != null && appInfo.publicSourceDir != appInfo.sourceDir) {
                    size += File(appInfo.publicSourceDir).length()
                }
                appInfo.splitSourceDirs?.forEach { splitDir ->
                    size += File(splitDir).length()
                }

                appList.add(AppDetail(name, packageName, versionName, size, installTime, isSystemApp, totalSize = -1L, isPermissionDenied = false))
            }
            val sortedList = appList.sortedBy { it.name.lowercase(Locale.getDefault()) }
            _appDetails.value = sortedList
            updateStorageSizes(sortedList)
            _isLoadingApps.value = false
            
            // Asynchronously fetch accurate sizes to prevent blocking UI
            fetchAppSizes(sortedList)
        }
    }

    private fun isPreloadedOrSystemApp(packageManager: PackageManager, packInfo: android.content.pm.PackageInfo, systemInstallTime: Long): Boolean {
        val appInfo = packInfo.applicationInfo ?: return false
        var isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 || 
                       (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        
        if (!isSystem) {
            if (appInfo.sourceDir != null && !appInfo.sourceDir!!.startsWith("/data/app/")) {
                isSystem = true
            } else if (packInfo.firstInstallTime <= systemInstallTime + 180000) { // 3 mins window
                val installer = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        packageManager.getInstallSourceInfo(appInfo.packageName).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getInstallerPackageName(appInfo.packageName)
                    }
                } catch (e: Exception) { null }
                
                if (installer != "com.android.vending") {
                    isSystem = true
                }
            }
        }
        return isSystem
    }

    private fun fetchAppSizes(initialList: List<AppDetail>) {
        viewModelScope.launch(Dispatchers.IO) {
            var currentList = initialList.toList()
            var isAuthError = false
            for ((index, app) in initialList.withIndex()) {
                var newAppSize = app.appSize
                var newDataSize = app.dataSize
                var newCacheSize = app.cacheSize
                var newTotalSize = app.totalSize

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                        val storageStats = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, android.os.Process.myUserHandle())
                        newAppSize = storageStats.appBytes
                        newCacheSize = storageStats.cacheBytes
                        newDataSize = storageStats.dataBytes - newCacheSize

                        newTotalSize = newAppSize + storageStats.dataBytes
                    }
                } catch (e: SecurityException) {
                    // If we hit a security error, we know the permission is missing
                    isAuthError = true
                    break
                } catch (e: Exception) { /* Handle app not found */ }
                
                if (newTotalSize == -1L) {
                    newTotalSize = app.size
                }
                
                if (newTotalSize != app.totalSize || newAppSize != app.appSize || newDataSize != app.dataSize || newCacheSize != app.cacheSize) {
                    currentList = currentList.map { 
                        if (it.packageName == app.packageName) it.copy(size = newTotalSize, appSize = newAppSize, dataSize = newDataSize, cacheSize = newCacheSize, totalSize = newTotalSize) else it 
                    }
                    if (index % 10 == 0) { // Batch updates to avoid overwhelming recompositions
                        _appDetails.value = currentList
                        updateStorageSizes(currentList)
                    }
                }
            }
            if (isAuthError) {
                // Update all apps to show "Permission Required" status
                _appDetails.value = currentList.map { it.copy(isPermissionDenied = true) }
            } else {
                _appDetails.value = currentList
                updateStorageSizes(currentList)
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadStaticDeviceInfo()
        }

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                updateDeviceInfo()
                delay(2000) // Update interval
            }
        }
    }

    private fun loadStaticDeviceInfo() {
        val packageManager = context.packageManager
        var firstInstallTime = try {
            packageManager.getPackageInfo("android", 0).firstInstallTime
        } catch (e: Exception) {
            Build.TIME
        }
        
        // If the 'android' package was flashed without a real-time clock, it often defaults to 2009.
        // We fallback to the ROM's build time, since the device cannot be online before its OS was built.
        if (firstInstallTime < Build.TIME) {
            firstInstallTime = Build.TIME
        }

        val displayMetrics = context.resources.displayMetrics
        val screenSizeInches = String.format("%.2f", Math.sqrt(Math.pow((displayMetrics.widthPixels / displayMetrics.xdpi).toDouble(), 2.0) + Math.pow((displayMetrics.heightPixels / displayMetrics.ydpi).toDouble(), 2.0)))

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val availableSensors = sensorManager.getSensorList(Sensor.TYPE_ALL).map { cleanSensorName(it.name) }

        val apps = packageManager.getInstalledApplications(0)
        var systemAppsCount = 0
        var userAppsCount = 0

        for (app in apps) {
            // 1. Skip if it's a test package
            if (app.packageName.contains(".test", ignoreCase = true)) continue

            // 2. Filter out Pixel/Android stubs and auto-generated services
            if (app.packageName.contains("android.auto_generated") ||
                app.packageName.contains("android.stub")) continue

            // 3. Filter for launchable apps: If it's a system app but has no drawer icon, don't count it
            val isSystemApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Increment the counts using your existing variables
            if (!isSystemApp) {
                userAppsCount++
            } else {
                systemAppsCount++
            }
        }
        val totalAppsCount = systemAppsCount + userAppsCount

        val cameraInfos = getCameraInfos()

        _deviceInfo.value = _deviceInfo.value.copy(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
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
            androidVersion = Build.VERSION.RELEASE,
            isRooted = isDeviceRooted(),
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            kernelVersion = System.getProperty("os.version") ?: "N/A",
            firstInstallTime = firstInstallTime,
            sdkVersion = Build.VERSION.SDK_INT,
            buildNumber = Build.DISPLAY,
            bootloaderVersion = Build.BOOTLOADER,
            basebandVersion = Build.getRadioVersion() ?: "N/A",
            systemLanguage = Locale.getDefault().displayLanguage,
            timezone = TimeZone.getDefault().id,
            processorName = getCpuInfo(),
            cpuArchitecture = System.getProperty("os.arch") ?: "N/A",
            coreCount = Runtime.getRuntime().availableProcessors(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            screenResolution = "${displayMetrics.heightPixels} x ${displayMetrics.widthPixels}",
            screenDensity = displayMetrics.densityDpi,
            screenSizeInches = screenSizeInches,
            sensors = availableSensors,
            sensorCount = availableSensors.size,
            totalApps = totalAppsCount,
            systemApps = systemAppsCount,
            userApps = userAppsCount,
            vmName = System.getProperty("java.vm.name") ?: "N/A",
            vmVersion = System.getProperty("java.vm.version") ?: "N/A",
            vmMaxHeap = Runtime.getRuntime().maxMemory(),
            cameraInfos = cameraInfos
        )
    }

    private fun updateDeviceInfo() {

        // --- Storage (Internal and External) ---
        val internalStatFs = StatFs(Environment.getDataDirectory().path)
        val rawTotalInternal = internalStatFs.blockCountLong * internalStatFs.blockSizeLong
        val freeInternal = internalStatFs.availableBlocksLong * internalStatFs.blockSizeLong
        
        val advertisedTotalInternal = getAdvertisedStorage(rawTotalInternal)
        val usedInternal = advertisedTotalInternal - freeInternal
        val internalStorageInfo =
            StorageInfo(total = advertisedTotalInternal, used = usedInternal)

        var externalStorageInfo: StorageInfo? = null
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        if (externalDirs.size > 1 && externalDirs[1] != null && Environment.isExternalStorageRemovable(externalDirs[1])) {
            val sdCardPath = externalDirs[1].path
            val statFs = StatFs(sdCardPath)
            val rawTotalExternal = statFs.blockCountLong * statFs.blockSizeLong
            val freeExternal = statFs.availableBlocksLong * statFs.blockSizeLong
            
            val advertisedTotalExternal = getAdvertisedStorage(rawTotalExternal)
            val usedExternal = advertisedTotalExternal - freeExternal
            externalStorageInfo = StorageInfo(total = advertisedTotalExternal, used = usedExternal)
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

        // Update State
        _deviceInfo.value = _deviceInfo.value.copy(
            totalRam = memoryInfo.totalMem,
            usedRam = memoryInfo.totalMem - memoryInfo.availMem,
            internalStorage = internalStorageInfo,
            externalStorage = externalStorageInfo,

            // Battery Info
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            batteryHealth = batteryHealth,
            batteryTemperature = batteryTemp,
            batteryTechnology = batteryTech,
            batteryVoltage = batteryVoltage,
            chargingSource = chargingSource,

            // Display Info
            refreshRate = refreshRate,
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
            
            // Thermal & Speeds
            cpuTemperature = getCpuTemperature(),
            coreFrequencies = getCpuCoreSpeeds(),
            
            // Memory
            availableRam = memoryInfo.availMem,
            lowMemoryThreshold = memoryInfo.threshold,
            isLowMemory = memoryInfo.lowMemory,
            
            // Java VM
            vmHeapSize = Runtime.getRuntime().totalMemory(),
            vmFreeHeap = Runtime.getRuntime().freeMemory(),
            
            // System Time
            uptime = SystemClock.elapsedRealtime()
        )
    }

    // --- New Helper Functions ---
    private fun getAdvertisedStorage(rawBytes: Long): Long {
        val gb = 1000L * 1000L * 1000L
        val tiers = listOf(8L, 16L, 32L, 64L, 128L, 256L, 512L, 1024L, 2048L, 4096L)
        for (tier in tiers) {
            val tierBytes = tier * gb
            if (rawBytes <= tierBytes) {
                return tierBytes
            }
        }
        return rawBytes
    }

    private fun getCpuCoreSpeeds(): String {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            val freqs = mutableListOf<String>()
            for (i in 0 until cores) {
                val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                if (freqFile.exists()) {
                    val freq = freqFile.readText().trim().toLongOrNull()
                    if (freq != null && freq > 0) {
                        freqs.add(String.format("%.2f GHz", freq / 1000000.0))
                    } else {
                        freqs.add("Offline")
                    }
                } else {
                    freqs.add("Offline")
                }
            }
            if (freqs.isEmpty()) return "N/A"
            
            // Group them for a cleaner UI (e.g., "4x 1.80 GHz \n 4x 2.40 GHz")
            val grouped = freqs.groupingBy { it }.eachCount()
            grouped.entries.joinToString("\n") { "${it.value}x ${it.key}" }
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getCpuTemperature(): String {
        return try {
            val temp = File("/sys/class/thermal/thermal_zone0/temp").readText().trim().toFloat()
            "%.1f °C".format(temp / 1000f)
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
            val cpuinfo = File("/proc/cpuinfo").readText()
            var hardwareName = "N/A"
            for (line in cpuinfo.lines()) {
                if (line.startsWith("Hardware", ignoreCase = true)) {
                    hardwareName = line.substringAfter(":").trim()
                    break
                }
            }
            
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

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
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
        return Formatter.formatFileSize(context, bytes)
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
