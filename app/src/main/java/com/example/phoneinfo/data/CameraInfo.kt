package com.example.phoneinfo.data

data class CameraInfo(
    val id: String,
    val facing: String,
    val megapixels: String,
    val hasFlash: Boolean,
    val focalLengths: String,
    val apertures: String,
    val opticalStabilization: Boolean,
    val supportedModes: List<String>
)
