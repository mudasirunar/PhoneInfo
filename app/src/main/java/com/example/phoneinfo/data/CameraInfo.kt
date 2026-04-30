package com.example.phoneinfo.data

data class CameraInfo(
    val id: String,
    val facing: String,          // "Front" / "Back" / "External"
    val megapixels: String,      // "12.2 MP"
    val hasFlash: Boolean,
    val focalLengths: String,    // e.g. "4.3 mm"
    val apertures: String,       // e.g. "f/1.8"
    val opticalStabilization: Boolean,
    val supportedModes: List<String>  // "Auto Focus", "HDR", etc.
)
