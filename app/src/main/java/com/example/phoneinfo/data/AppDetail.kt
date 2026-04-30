package com.example.phoneinfo.data

data class AppDetail(
    val name: String,
    val packageName: String,
    val versionName: String,
    val size: Long,
    val installTime: Long,
    val isSystemApp: Boolean
)
