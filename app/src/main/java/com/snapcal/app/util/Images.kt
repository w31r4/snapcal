package com.snapcal.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.snapcal.app.ai.ImagePayload
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object Images {
    /** Long edge for upload; ~1.5 MP is plenty for food recognition and keeps requests small/cheap. */
    private const val MAX_EDGE = 1568

    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * Decode [uri], fix EXIF rotation, downscale, re-encode as JPEG.
     * Returns null if the uri can't be read (e.g. the user cancelled the camera).
     */
    fun loadForUpload(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null in bounds-only mode; success is signalled via outWidth/outHeight.
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val bmp = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    fun toPayload(bytes: ByteArray) = ImagePayload(bytes, "image/jpeg")

    /** Persist the (already downscaled) photo so history can show it. */
    fun saveMealPhoto(context: Context, bytes: ByteArray): String {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        f.writeBytes(bytes)
        return f.absolutePath
    }

    fun clearCaptureCache(context: Context) {
        File(context.cacheDir, "capture").listFiles()?.forEach { it.delete() }
    }
}
