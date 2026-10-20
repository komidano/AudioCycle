package com.gumby.audiocycle.model

import android.net.Uri

sealed interface ConversionState {
    object Idle : ConversionState
    object Preparing : ConversionState
    data class Converting(
        val progressPercent: Int,
        val timeElapsedSec: Long,
        val logSnippet: String = ""
    ) : ConversionState
    data class Success(
        val outputFileUri: Uri,
        val outputFilePath: String,
        val outputFileName: String,
        val outputFormat: ConversionFormat
    ) : ConversionState
    data class Error(
        val message: String
    ) : ConversionState
}
