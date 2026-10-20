package com.gumby.audiocycle

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gumby.audiocycle.ffmpeg.FFmpegConverter
import com.gumby.audiocycle.model.ConversionFormat
import com.gumby.audiocycle.model.ConversionOptions
import com.gumby.audiocycle.model.ConversionState
import com.gumby.audiocycle.util.FileUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    private val ffmpegConverter = FFmpegConverter()

    private val _selectedOptions = MutableStateFlow<ConversionOptions?>(null)
    val selectedOptions: StateFlow<ConversionOptions?> = _selectedOptions.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private var conversionJob: Job? = null
    private var tempInputFile: File? = null
    private var tempOutputFile: File? = null

    fun selectFile(context: Context, uri: Uri) {
        val metadata = FileUtil.getUriMetadata(context, uri)
        _selectedOptions.value = ConversionOptions(
            sourceUri = uri,
            sourceFileName = metadata.fileName,
            sourceFileSize = metadata.fileSize
        )
        _conversionState.value = ConversionState.Idle
    }

    fun updateTargetFormat(format: ConversionFormat) {
        _selectedOptions.value = _selectedOptions.value?.copy(targetFormat = format)
    }

    fun updateBitrate(bitrateKbps: Int) {
        _selectedOptions.value = _selectedOptions.value?.copy(bitrateKbps = bitrateKbps)
    }

    fun updateSampleRate(sampleRateHz: Int) {
        _selectedOptions.value = _selectedOptions.value?.copy(sampleRateHz = sampleRateHz)
    }

    fun updateChannels(channels: Int) {
        _selectedOptions.value = _selectedOptions.value?.copy(channels = channels)
    }

    fun startConversion(context: Context) {
        val options = _selectedOptions.value ?: return
        if (_conversionState.value is ConversionState.Converting || _conversionState.value is ConversionState.Preparing) {
            return
        }

        conversionJob = viewModelScope.launch {
            try {
                _conversionState.value = ConversionState.Preparing

                val appContext = context.applicationContext
                val inCache = FileUtil.copyUriToCache(appContext, options.sourceUri, options.sourceFileName)
                tempInputFile = inCache

                val outCache = FileUtil.createTempOutputFile(appContext, options.targetFormat)
                tempOutputFile = outCache

                _conversionState.value = ConversionState.Converting(
                    progressPercent = 0,
                    timeElapsedSec = 0,
                    logSnippet = "Starting conversion engine..."
                )

                val result = ffmpegConverter.convert(
                    inputFilePath = inCache.absolutePath,
                    outputFilePath = outCache.absolutePath,
                    options = options,
                    onProgress = { percent, timeSec, log ->
                        _conversionState.value = ConversionState.Converting(
                            progressPercent = percent,
                            timeElapsedSec = timeSec,
                            logSnippet = log
                        )
                    }
                )

                result.fold(
                    onSuccess = {
                        val publicUri = FileUtil.saveToPublicMusicFolder(
                            context = appContext,
                            tempOutputFile = outCache,
                            baseName = options.sourceFileName,
                            format = options.targetFormat
                        )

                        _conversionState.value = ConversionState.Success(
                            outputFileUri = publicUri,
                            outputFilePath = outCache.absolutePath,
                            outputFileName = "${options.sourceFileName.substringBeforeLast(".")}.${options.targetFormat.extension}",
                            outputFormat = options.targetFormat
                        )
                    },
                    onFailure = { error ->
                        _conversionState.value = ConversionState.Error(
                            message = error.localizedMessage ?: "Conversion failed."
                        )
                    }
                )
            } catch (e: Exception) {
                _conversionState.value = ConversionState.Error(
                    message = e.localizedMessage ?: "Unexpected error occurred during conversion."
                )
            } finally {
                cleanupTempInput()
            }
        }
    }

    fun cancelConversion() {
        ffmpegConverter.cancel()
        conversionJob?.cancel()
        _conversionState.value = ConversionState.Idle
        cleanupTempFiles()
    }

    fun resetState() {
        _conversionState.value = ConversionState.Idle
    }

    private fun cleanupTempInput() {
        try {
            tempInputFile?.delete()
            tempInputFile = null
        } catch (_: Exception) {}
    }

    private fun cleanupTempFiles() {
        cleanupTempInput()
        try {
            tempOutputFile?.delete()
            tempOutputFile = null
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        cancelConversion()
    }
}
