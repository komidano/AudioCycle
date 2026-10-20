package com.gumby.audiocycle.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

    suspend fun saveToPublicMusicFolder(
        context: Context,
        tempOutputFile: File,
        baseName: String,
        format: ConversionFormat
    ): Uri = withContext(Dispatchers.IO) {
        val cleanBaseName = baseName.substringBeforeLast(".")
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val outputFileName = "${cleanBaseName}.${format.extension}"

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, outputFileName)
            put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AudioCycle")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(itemUri)?.use { outputStream ->
            tempOutputFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Failed to write to MediaStore output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }

        itemUri
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
