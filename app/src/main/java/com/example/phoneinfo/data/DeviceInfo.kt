package com.example.phoneinfo.data



data class DeviceInfo(
    // Home
    val deviceName: String = "",
    val totalRam: Long = 0L,
    val usedRam: Long = 0L,
    val internalStorage: StorageInfo? = null,
    val externalStorage: StorageInfo? = null,

    // Hardware Identity
    val manufacturer: String = "",
    val brand: String = "",
    val model: String = "",
    val deviceCodename: String = "",
    val board: String = "",
    val hardwareId: String = "",
    val product: String = "",
    val buildFingerprint: String = "",
    val buildId: String = "",
    val buildType: String = "",

    // System
    val androidVersion: String = "",
    val securityPatch: String = "",
    val kernelVersion: String = "",
    val uptime: Long = 0L,
    val isRooted: Boolean = false,
    val firstInstallTime: Long = 0L,
    val sdkVersion: Int = 0,
    val buildNumber: String = "",
    val bootloaderVersion: String = "",
    val basebandVersion: String = "",
    val systemLanguage: String = "",
    val timezone: String = "",

    // CPU
    val processorName: String = "",
    val cpuArchitecture: String = "",
    val coreCount: Int = 0,
    val coreFrequencies: String = "",
    val cpuGovernor: String = "",
    val supportedAbis: String = "",

    // Battery
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val batteryHealth: String = "",
    val batteryTemperature: Float = 0f,
    val batteryTechnology: String = "",
    val batteryVoltage: Int = 0,
    val chargingSource: String = "N/A",

    // Display
    val screenResolution: String = "",
    val screenDensity: Int = 0,
    val refreshRate: Float = 0f,
    val screenSizeInches: String = "",
    val densityBucket: String = "",
    val hdrCapabilities: String = "N/A",
    val fontScale: Float = 1f,
    val orientation: String = "",
    val nightMode: String = "",

    // Camera
    val cameraInfos: List<CameraInfo> = emptyList(),

    // Bluetooth
    val bluetoothName: String = "N/A",
    val isBluetoothSupported: Boolean = false,
    val isBluetoothEnabled: Boolean = false,

    // WiFi
    val isWifiConnected: Boolean = false,
    val wifiSsid: String = "N/A",
    val wifiSignalStrength: Int = 0,
    val ipAddress: String = "N/A",
    val wifiFrequency: Int = 0,
    val wifiLinkSpeed: Int = 0,
    val wifiBssid: String = "N/A",
    val wifiChannel: String = "N/A",
    val wifiStandard: String = "N/A",
    val macAddress: String = "N/A",

    // Mobile Network
    val simInfos: List<SimInfo> = emptyList(),

    // Sensors
    val sensors: List<String> = emptyList(),
    val sensorCount: Int = 0,

    // Thermal
    val cpuTemperature: String = "N/A",

    // Memory (expanded)
    val availableRam: Long = 0L,
    val lowMemoryThreshold: Long = 0L,
    val isLowMemory: Boolean = false,
    val totalSwap: Long = 0L,
    val freeSwap: Long = 0L,
    val zramUsage: String = "N/A",

    // GPU
    val openGlVersion: String = "N/A",

    // Installed Apps
    val totalApps: Int = 0,
    val systemApps: Int = 0,
    val userApps: Int = 0,

    // Java VM
    val vmName: String = "N/A",
    val vmVersion: String = "N/A",
    val vmHeapSize: Long = 0L,
    val vmMaxHeap: Long = 0L,
    val vmFreeHeap: Long = 0L,
)