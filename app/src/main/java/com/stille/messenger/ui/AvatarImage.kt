package com.stille.messenger.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import com.stille.coreapi.Limits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reject decompression bombs before Coil sees peer-supplied image bytes. */
internal fun validAvatarDimensions(data: ByteArray): Boolean {
    if (data.size !in 1..Limits.AVATAR_PAYLOAD) return false
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    return options.outWidth in 1..1024 && options.outHeight in 1..1024
}

/** Bounded selection, EXIF orientation, center crop, JPEG without metadata; memory only. */
suspend fun prepareAvatar(resolver: ContentResolver, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val source = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                if (output.size() + n > 8 * 1024 * 1024) return@withContext null
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options) ?: return@withContext null
        try {
            val orientation = try {
                ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            } catch (_: Exception) { 1 }
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try {
                val side = minOf(upright.width, upright.height)
                val x = (upright.width - side) / 2
                val y = (upright.height - side) / 2
                for (size in listOf(256, 192, 128, 96)) {
                    ensureActive()
                    val target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(target).apply {
                            drawColor(Color.WHITE)
                            drawBitmap(upright, Rect(x, y, x + side, y + side), Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
                        }
                        for (quality in listOf(88, 76, 64, 52, 40)) {
                            val output = ByteArrayOutputStream()
                            if (!target.compress(Bitmap.CompressFormat.JPEG, quality, output)) return@withContext null
                            if (output.size() in 1..Limits.AVATAR_PAYLOAD) return@withContext output.toByteArray()
                        }
                    } finally { target.recycle() }
                }
                null
            } finally { if (upright !== decoded) upright.recycle() }
        } finally { decoded.recycle() }
    } catch (e: CancellationException) { throw e
    } catch (_: Exception) { null }
}
