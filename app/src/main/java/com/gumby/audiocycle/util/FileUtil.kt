package com.gumby.audiocycle.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.gumby.audiocycle.model.ConversionFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileUtil {

    data class UriMetadata(
        val fileName: String,
        val fileSize: Long
    )

    fun getUriMetadata(context: Context, uri: Uri): UriMetadata {
        var fileName = "unknown_file"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }
        return UriMetadata(fileName, fileSize)
    }

    suspend fun copyUriToCache(context: Context, uri: Uri, fileName: String): File =
        withContext(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val tempFile = File(cacheDir, "input_$sanitizedName")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Unable to open input stream for URI: $uri")

            tempFile
        }

    fun createTempOutputFile(context: Context, format: ConversionFormat): File {
        val cacheDir = context.cacheDir
        val timeStamp = System.currentTimeMillis()
        return File(cacheDir, "output_${timeStamp}.${format.extension}")
    }

    suspend fun writeTempToDestinationUri(
        context: Context,
        tempOutputFile: File,
        destinationUri: Uri
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destinationUri, "w")?.use { outputStream ->
            tempOutputFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Failed to open output stream for destination")
    }

    fun getShareableUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceAtMost(units.size - 1)
        return String.format("%.2f %s", sizeInBytes / Math.pow(1024.0, index.toDouble()), units[index])
    }
}
