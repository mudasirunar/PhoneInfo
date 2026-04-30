package com.example.phoneinfo.data

data class SimInfo(
    val operatorName: String,
    val networkType: String,
    val phoneNumber: String?,
    val countryIso: String = "N/A",
    val mccMnc: String = "N/A",
    val isRoaming: Boolean = false,
    val simState: String = "Unknown",
    val slotIndex: Int = -1
)
