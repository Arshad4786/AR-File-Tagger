package com.example.arfiletagger

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MyApplication : Application() {

    private val TAG = "MyApplication"

    override fun onCreate() {
        super.onCreate()
        // Perform any app-wide initialization here if needed
        Log.d(TAG, "MyApplication created.")
    }

    /**
     * Saves a Bitmap to the app's internal storage.
     * @param bitmap The bitmap to save.
     * @param filename The desired filename (without extension, will add .png).
     * @return true if successful, false otherwise.
     */
    fun saveBitmapToInternalStorage(bitmap: Bitmap, filename: String): Boolean {
        val file = File(filesDir, "$filename.png") // Using .png for lossless compression
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // Compress to PNG
            }
            Log.d(TAG, "Bitmap '$filename' saved to internal storage.")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error saving bitmap to internal storage: ${e.message}", e)
            false
        }
    }

    /**
     * Loads a Bitmap from the app's internal storage.
     * @param filename The filename of the bitmap (without extension, assumes .png).
     * @return The loaded Bitmap, or null if an error occurs.
     */
    fun loadBitmapFromInternalStorage(filename: String): Bitmap? {
        val file = File(filesDir, "$filename.png")
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: IOException) {
                Log.e(TAG, "Error loading bitmap from internal storage: ${e.message}", e)
                null
            }
        } else {
            Log.d(TAG, "Bitmap file not found for loading: $filename")
            null
        }
    }

    /**
     * Lists all image files saved in the app's internal storage.
     * @return A list of filenames (without extension).
     */
    fun listSavedImageFiles(): List<String> {
        val files = filesDir.listFiles { file -> file.isFile && file.name.endsWith(".png") }
        val fileNames = files?.map { it.name.substringBeforeLast(".") } ?: emptyList()
        Log.d(TAG, "Listed ${fileNames.size} saved image files.")
        return fileNames
    }

    /**
     * Deletes a bitmap from the app's internal storage.
     * @param filename The filename of the bitmap (without extension, assumes .png).
     * @return true if successful, false otherwise.
     */
    fun deleteBitmapFromInternalStorage(filename: String): Boolean {
        val file = File(filesDir, "$filename.png")
        return if (file.exists()) {
            try {
                file.delete().also {
                    if (it) Log.d(TAG, "Deleted bitmap from internal storage: $filename")
                    else Log.w(TAG, "Failed to delete bitmap from internal storage: $filename")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting bitmap from internal storage: ${e.message}", e)
                false
            }
        } else {
            Log.d(TAG, "Bitmap not found for deletion: $filename")
            false
        }
    }
}
