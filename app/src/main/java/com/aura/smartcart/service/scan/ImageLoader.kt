package com.aura.smartcart.service.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageLoader {

    /** Decodes [uri] scaled down so the longest side is about [maxDim] px (keeps memory and upload small). */
    fun loadScaled(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Copies a scanned bill image into app storage so it stays available after the scanner's cache is cleared. */
    fun copyToBillsFolder(context: Context, uri: Uri): String? = try {
        val dir = File(context.filesDir, "bills").apply { mkdirs() }
        val out = File(dir, "bill_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.absolutePath
    } catch (_: Exception) {
        null
    }
}
