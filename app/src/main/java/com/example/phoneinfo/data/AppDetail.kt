package com.example.phoneinfo.data

data class AppDetail(
    val name: String,
    val packageName: String,
    val versionName: String,
    val size: Long,
    val installTime: Long,
    val isSystemApp: Boolean,
    val appSize: Long = 0L,
    val dataSize: Long = 0L,
    val cacheSize: Long = 0L,
    val totalSize: Long = 0L,
    val isPermissionDenied: Boolean = false
)
