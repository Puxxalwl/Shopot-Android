package com.stille.messenger.ui.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** Exclusive-right/bottom pixel bounds of the white central mask. */
data class QrMarkBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/** In-memory, opaque black/white pixels. Contains no stored copy of the invite text. */
class InviteQrImage internal constructor(
    val size: Int,
    val moduleCount: Int,
    val pixelsPerModule: Int,
    val quietZoneModules: Int,
    val markBounds: QrMarkBounds?,
    private val pixels: IntArray,
) {
    val isDecorated: Boolean get() = markBounds != null

    /** Return an owned copy; mutation cannot change this image or its verified pixels. */
    fun copyArgbPixels(): IntArray = pixels.copyOf()

    fun argbAt(x: Int, y: Int): Int {
        require(x in 0 until size && y in 0 until size)
        return pixels[y * size + x]
    }
}

/** Errors never include the invite, an encoder exception, or decoded content. */
class InviteQrException internal constructor(message: String) : IllegalArgumentException(message)

/**
 * Pure JVM, synchronous CPU renderer. Call off the main thread (or use InviteQrCode).
 * No network, disk writes, logging, caches, URI wrapping, trimming or normalization.
 * Every returned raster has been decoded back to the EXACT supplied string.
 */
object InviteQrRenderer {
    const val QUIET_ZONE_MODULES = 4
    const val PIXELS_PER_MODULE = 8
    const val BLACK: Int = -0x1000000
    const val WHITE: Int = -0x1
    private const val MAX_UTF8_BYTES = 900 // Conservative detector-verified bound for H-level decorated previews.

    /**
     * Encode UTF-8 with ECC H and four white modules on each edge. The original
     * symbol is painted over a white center mask, then the FINAL raster is decoded.
     * If decoration breaks decoding, a separately verified plain QR is returned.
     * Throws [InviteQrException] for empty/oversized or unrenderable inputs.
     */
    fun render(rawInvite: String): InviteQrImage = renderVerified(rawInvite, ::decodesExactly)

    // The validation seam is internal, solely to exercise fallback deterministically.
    internal fun renderVerified(
        rawInvite: String,
        verify: (InviteQrImage, String) -> Boolean,
    ): InviteQrImage {
        if (rawInvite.isEmpty()) throw InviteQrException("Invite is empty")
        if (rawInvite.toByteArray(Charsets.UTF_8).size > MAX_UTF8_BYTES) {
            throw InviteQrException("Invite exceeds QR capacity")
        }
        val matrix = try {
            Encoder.encode(
                rawInvite,
                ErrorCorrectionLevel.H,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
            ).matrix
        } catch (_: WriterException) {
            throw InviteQrException("Invite exceeds QR capacity")
        }
        val modules = matrix.width
        val size = (modules + 2 * QUIET_ZONE_MODULES) * PIXELS_PER_MODULE
        val pixels = IntArray(size * size) { WHITE }
        for (my in 0 until modules) {
            for (mx in 0 until modules) {
                if (matrix.get(mx, my).toInt() != 1) continue
                val left = (mx + QUIET_ZONE_MODULES) * PIXELS_PER_MODULE
                val top = (my + QUIET_ZONE_MODULES) * PIXELS_PER_MODULE
                for (y in top until top + PIXELS_PER_MODULE) {
                    pixels.fill(BLACK, y * size + left, y * size + left + PIXELS_PER_MODULE)
                }
            }
        }
        val plain = InviteQrImage(size, modules, PIXELS_PER_MODULE, QUIET_ZONE_MODULES, null, pixels)
        val decorated = decorate(plain)
        if (verify(decorated, rawInvite)) return decorated
        if (verify(plain, rawInvite)) return plain
        throw InviteQrException("QR verification failed")
    }

    private fun decorate(plain: InviteQrImage): InviteQrImage {
        val pixels = plain.copyArgbPixels()
        // An odd, module-aligned mask <= 17% of the symbol's side. This is NOT
        // assumed safe just because H exists: alignment/data modules may be hit.
        // Keep the mark visible while preserving a generous amount of H-level redundancy on large payloads.
        var maskModules = (plain.moduleCount * 0.11).toInt().coerceAtLeast(3)
        if (maskModules % 2 == 0) maskModules--
        val side = maskModules * PIXELS_PER_MODULE
        val start = (plain.size - side) / 2
        val bounds = QrMarkBounds(start, start, side, side)
        for (y in start until start + side) {
            pixels.fill(WHITE, y * plain.size + start, y * plain.size + start + side)
        }
        val markHeight = side - PIXELS_PER_MODULE
        val markWidth = (markHeight * StilleSymbol.width / StilleSymbol.height).coerceAtLeast(1)
        val left = (plain.size - markWidth) / 2
        val top = (plain.size - markHeight) / 2
        for (y in 0 until markHeight) {
            for (x in 0 until markWidth) {
                if (StilleSymbol.isBlack(x * StilleSymbol.width / markWidth, y * StilleSymbol.height / markHeight)) {
                    pixels[(top + y) * plain.size + left + x] = BLACK
                }
            }
        }
        return InviteQrImage(
            plain.size, plain.moduleCount, PIXELS_PER_MODULE, QUIET_ZONE_MODULES, bounds, pixels,
        )
    }

    internal fun decodesExactly(image: InviteQrImage, rawInvite: String): Boolean = try {
        val luminance = RGBLuminanceSource(image.size, image.size, image.copyArgbPixels())
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
        val reader = QRCodeReader()
        try {
            reader.decode(BinaryBitmap(HybridBinarizer(luminance)), hints).text == rawInvite
        } catch (_: ReaderException) {
            // Global thresholding is a useful second pass for large, perfectly
            // binary rasters where HybridBinarizer can lose the outer finder edge.
            try { reader.reset(); reader.decode(BinaryBitmap(GlobalHistogramBinarizer(luminance)), hints).text == rawInvite }
            catch (_: ReaderException) { false }
        }
    } catch (_: ReaderException) {
        false
    }
}
