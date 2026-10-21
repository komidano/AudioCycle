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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    private val ffmpegConverter = FFmpegConverter()

    private val _selectedOptions = MutableStateFlow<ConversionOptions?>(null)
    val selectedOptions: StateFlow<ConversionOptions?> = _selectedOptions.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _filePickerEvents = MutableSharedFlow<Unit>()
    val filePickerEvents: SharedFlow<Unit> = _filePickerEvents.asSharedFlow()

    private var conversionJob: Job? = null
    private var tempInputFile: File? = null
    private var tempOutputFile: File? = null

    fun triggerFilePicker() {
        viewModelScope.launch {
            _filePickerEvents.emit(Unit)
        }
    }

    fun selectFile(context: Context, uri: Uri) {
        val metadata = FileUtil.getUriMetadata(context, uri)
        val defaultOutputName = if (metadata.fileName.contains(".")) {
            metadata.fileName.substringBeforeLast(".") + ".mp3"
        } else {
            metadata.fileName + ".mp3"
        }

        _selectedOptions.value = ConversionOptions(
            sourceUri = uri,
            sourceFileName = metadata.fileName,
            sourceFileSize = metadata.fileSize,
            outputFileName = defaultOutputName
        )
        _conversionState.value = ConversionState.Idle
    }

    fun updateOutputFileName(name: String) {
        _selectedOptions.value = _selectedOptions.value?.copy(outputFileName = name)
    }

    fun updateTargetFormat(format: ConversionFormat) {
        val current = _selectedOptions.value ?: return
        val currentBase = if (current.outputFileName.contains(".")) {
            current.outputFileName.substringBeforeLast(".")
        } else {
            current.outputFileName
        }
        val newName = "$currentBase.${format.extension}"

        _selectedOptions.value = current.copy(
            targetFormat = format,
            outputFileName = newName
        )
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

    fun startConversion(context: Context, destinationUri: Uri) {
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
                        FileUtil.writeTempToDestinationUri(appContext, outCache, destinationUri)

                        _conversionState.value = ConversionState.Success(
                            outputFileUri = destinationUri,
                            outputFilePath = destinationUri.toString(),
                            outputFileName = options.outputFileName.ifBlank { "converted.${options.targetFormat.extension}" },
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
                cleanupTempFiles()
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
