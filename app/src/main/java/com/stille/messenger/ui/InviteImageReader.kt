package com.stille.messenger.ui

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.stille.messenger.service.validInviteInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read a user-selected QR image in memory. Never writes the image or decoded secret. */
internal suspend fun readInviteImage(resolver: ContentResolver, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) inSampleSize *= 2
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))), mapOf(DecodeHintType.TRY_HARDER to true))
            result.text.trim().takeIf(::validInviteInput)
        } finally { bitmap.recycle() }
    } catch (_: Exception) { null }
}
