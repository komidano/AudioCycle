package com.gumby.audiocycle.model

import android.net.Uri

data class ConversionOptions(
    val sourceUri: Uri,
    val sourceFileName: String,
    val sourceFileSize: Long = 0L,
    val outputFileName: String = "",
    val targetFormat: ConversionFormat = ConversionFormat.MP3,
    val bitrateKbps: Int = 192,
    val sampleRateHz: Int = 44100,
    val channels: Int = 2
)
