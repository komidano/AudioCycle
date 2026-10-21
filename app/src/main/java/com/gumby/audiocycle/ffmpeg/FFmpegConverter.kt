package com.gumby.audiocycle.ffmpeg

import com.gumby.audiocycle.model.ConversionFormat
import com.gumby.audiocycle.model.ConversionOptions
import io.microshow.rxffmpeg.RxFFmpegInvoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class FFmpegConverter {

    suspend fun convert(
        inputFilePath: String,
        outputFilePath: String,
        options: ConversionOptions,
        onProgress: (percent: Int, timeElapsedSec: Long, logSnippet: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val commandList = buildFFmpegCommandList(inputFilePath, outputFilePath, options)
        val commandArray = commandList.toTypedArray()
        val startTimeMs = System.currentTimeMillis()

        suspendCancellableCoroutine { continuation ->
            val listener = object : RxFFmpegInvoke.IFFmpegListener {
                override fun onFinish() {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }

                override fun onProgress(progress: Int, progressTime: Long) {
                    val percent = progress.coerceIn(0, 100)
                    val realElapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000
                    onProgress(percent, realElapsedSec, "Converting...")
                }

                override fun onCancel() {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Conversion cancelled")))
                    }
                }

                override fun onError(message: String?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception(message ?: "FFmpeg conversion error")))
                    }
                }
            }

            RxFFmpegInvoke.getInstance().runCommandAsync(commandArray, listener)

            continuation.invokeOnCancellation {
                RxFFmpegInvoke.getInstance().exit()
            }
        }
    }

    fun cancel() {
        RxFFmpegInvoke.getInstance().exit()
    }

    private fun buildFFmpegCommandList(
        inputFilePath: String,
        outputFilePath: String,
        options: ConversionOptions
    ): List<String> {
        val list = mutableListOf("ffmpeg", "-y", "-i", inputFilePath)

        val codecParts = options.targetFormat.ffmpegCodecArgs.split(" ")
        list.addAll(codecParts)

        if (options.targetFormat != ConversionFormat.WAV && options.targetFormat != ConversionFormat.FLAC) {
            list.add("-b:a")
            list.add("${options.bitrateKbps}k")
        }

        list.add("-ar")
        list.add(options.sampleRateHz.toString())

        list.add("-ac")
        list.add(options.channels.toString())

        list.add(outputFilePath)

        return list
    }
}
