package com.example.pawsociety.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileHelper {

    /**
     * Convert Uri to File
     */
    fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile(
                "upload_${System.currentTimeMillis()}",
                ".jpg",
                context.cacheDir
            )

            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compress image file - HIGH QUALITY settings
     * @return Compressed file or original if compression fails
     */
    fun compressImage(file: File, maxWidth: Int = 1024, quality: Int = 90): File {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Calculate inSampleSize
            var inSampleSize = 1
            if (options.outWidth > maxWidth) {
                inSampleSize = (options.outWidth.toFloat() / maxWidth).toInt()
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)

            // Compress bitmap with HIGH QUALITY
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            val compressedFile = File.createTempFile(
                "compressed_${System.currentTimeMillis()}",
                ".jpg",
                file.parentFile
            )

            FileOutputStream(compressedFile).use { out ->
                out.write(outputStream.toByteArray())
            }

            // Recycle bitmap to free memory
            bitmap.recycle()

            compressedFile
        } catch (e: Exception) {
            e.printStackTrace()
            file // Return original if compression fails
        }
    }

    /**
     * Get file size in bytes
     */
    fun getFileSize(file: File): Long {
        return file.length()
    }

    /**
     * Get file size in human readable format
     */
    fun getFileSizeReadable(file: File): String {
        val size = getFileSize(file)
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }

    /**
     * Delete file
     */
    fun deleteFile(file: File): Boolean {
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Clear cache directory
     */
    fun clearCache(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}