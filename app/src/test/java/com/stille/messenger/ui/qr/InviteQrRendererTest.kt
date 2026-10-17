package com.stille.messenger.ui.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.EncodeHintType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InviteQrRendererTest {
    @Test
    fun roundTripsRawInvitesAcrossLengthsWithRealCentralSymbol() {
        var decoratedCount = 0
        for (length in listOf(1, 16, 64, 128, 256, 512, 900)) {
            val payload = inviteOfLength(length)
            val image = InviteQrRenderer.render(payload)
            assertRoundTrip(image, payload)
            assertOpaqueMonochromeAndQuietZone(image)
            if (image.isDecorated) {
                decoratedCount++
                assertActualSymbolMask(image, payload)
            }
        }
        // Do not let an implementation that silently always skips the mark pass.
        assertTrue("Expected decorated QR at multiple payload lengths", decoratedCount >= 3)
    }

    @Test
    fun integerScaledPreviewRasterStillRoundTrips() {
        for (length in listOf(64, 256, 900)) {
            val payload = inviteOfLength(length)
            val image = InviteQrRenderer.render(payload)
            // Match the Canvas nearest-neighbor, integer-module downscaling.
            val totalModules = image.moduleCount + image.quietZoneModules * 2
            val scale = 3
            val side = totalModules * scale
            val pixels = IntArray(side * side) { index ->
                val x = index % side
                val y = index / side
                image.argbAt(
                    ((x + 0.5) * image.size / side).toInt(),
                    ((y + 0.5) * image.size / side).toInt(),
                )
            }
            val source = RGBLuminanceSource(side, side, pixels)
            val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
            assertEquals(payload, decoded.text)
        }
    }

    @Test
    fun preservesWhitespaceUnicodeAndPunctuationWithoutUriWrapping() {
        for (payload in listOf(
            "  raw invite\nwith trailing whitespace  ",
            "stille:приглашение/東京/🔒?token=a+b%2F=&raw=true",
            "a\u0000b\n\tC",
        )) {
            assertRoundTrip(InviteQrRenderer.render(payload), payload)
        }
    }

    @Test
    fun fallbackIsPlainAndIndependentlyDecodableWhenDecoratedVerificationFails() {
        val payload = inviteOfLength(512)
        val attempts = mutableListOf<Boolean>()
        val image = InviteQrRenderer.renderVerified(payload) { candidate, expected ->
            attempts += candidate.isDecorated
            // Force the failure branch, but still actually decode the plain result.
            !candidate.isDecorated && InviteQrRenderer.decodesExactly(candidate, expected)
        }
        assertEquals(listOf(true, false), attempts)
        assertFalse(image.isDecorated)
        assertRoundTrip(image, payload)
        assertOpaqueMonochromeAndQuietZone(image)
        assertMatchesPlainMatrix(image, payload)
    }

    @Test
    fun neverReturnsAnUnverifiedImage() {
        var attempts = 0
        try {
            InviteQrRenderer.renderVerified("private-test-invite") { _, _ ->
                attempts++
                false
            }
            fail("Expected verification failure")
        } catch (error: InviteQrException) {
            assertEquals("QR verification failed", error.message)
            assertEquals(2, attempts)
            assertEquals(null, error.cause)
        }
    }

    @Test
    fun rejectsEmptyAndOversizedInputWithoutLeakingItInDiagnostics() {
        for (payload in listOf("", "sensitive".repeat(200), "🔒".repeat(400))) {
            try {
                InviteQrRenderer.render(payload)
                fail("Expected bounded input rejection")
            } catch (error: InviteQrException) {
                assertFalse(error.message.orEmpty().contains("sensitive"))
                assertFalse(error.message.orEmpty().contains("🔒"))
                assertEquals(null, error.cause)
            }
        }
    }

    @Test
    fun returnedPixelsAreDefensiveCopies() {
        val image = InviteQrRenderer.render("unchanged")
        val pixels = image.copyArgbPixels()
        pixels.fill(0)
        assertRoundTrip(image, "unchanged")
        assertEquals(InviteQrRenderer.WHITE, image.argbAt(0, 0))
    }

    private fun assertRoundTrip(image: InviteQrImage, expected: String) {
        // Decode the real final ARGB raster through the image detector, not the
        // encoder matrix and not PURE_BARCODE (which would bypass detection).
        val source = RGBLuminanceSource(image.size, image.size, image.copyArgbPixels())
        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true),
        )
        assertEquals(expected, decoded.text)
        assertEquals("H", decoded.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL])
    }

    private fun assertOpaqueMonochromeAndQuietZone(image: InviteQrImage) {
        assertTrue(image.quietZoneModules >= 4)
        val border = image.quietZoneModules * image.pixelsPerModule
        for (y in 0 until image.size) {
            for (x in 0 until image.size) {
                val pixel = image.argbAt(x, y)
                assertTrue(pixel == InviteQrRenderer.BLACK || pixel == InviteQrRenderer.WHITE)
                if (x < border || y < border || x >= image.size - border || y >= image.size - border) {
                    assertEquals(InviteQrRenderer.WHITE, pixel)
                }
            }
        }
    }

    private fun assertActualSymbolMask(image: InviteQrImage, payload: String) {
        val bounds = image.markBounds
        assertNotNull(bounds)
        bounds!!
        assertEquals((image.size - bounds.width) / 2, bounds.left)
        assertEquals((image.size - bounds.height) / 2, bounds.top)
        val markHeight = bounds.height - image.pixelsPerModule
        val markWidth = markHeight * StilleSymbol.width / StilleSymbol.height
        val left = (image.size - markWidth) / 2
        val top = (image.size - markHeight) / 2
        var blackPixels = 0
        for (y in bounds.top until bounds.top + bounds.height) {
            for (x in bounds.left until bounds.left + bounds.width) {
                val black = x in left until left + markWidth && y in top until top + markHeight &&
                    StilleSymbol.isBlack(
                        (x - left) * StilleSymbol.width / markWidth,
                        (y - top) * StilleSymbol.height / markHeight,
                    )
                if (black) blackPixels++
                assertEquals(if (black) InviteQrRenderer.BLACK else InviteQrRenderer.WHITE, image.argbAt(x, y))
            }
        }
        assertTrue("The actual symbol must be painted", blackPixels > 0)
        assertMatchesPlainMatrix(image, payload, outsideMarkOnly = true)
    }

    private fun assertMatchesPlainMatrix(image: InviteQrImage, payload: String, outsideMarkOnly: Boolean = false) {
        val matrix = Encoder.encode(
            payload, ErrorCorrectionLevel.H, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
        ).matrix
        var changedInsideMask = false
        val bounds = image.markBounds
        for (y in 0 until image.size) {
            for (x in 0 until image.size) {
                val mx = x / image.pixelsPerModule - image.quietZoneModules
                val my = y / image.pixelsPerModule - image.quietZoneModules
                val black = mx in 0 until matrix.width && my in 0 until matrix.height && matrix.get(mx, my).toInt() == 1
                val expected = if (black) InviteQrRenderer.BLACK else InviteQrRenderer.WHITE
                val inside = bounds != null && x in bounds.left until bounds.left + bounds.width &&
                    y in bounds.top until bounds.top + bounds.height
                if (outsideMarkOnly && inside) {
                    changedInsideMask = changedInsideMask || expected != image.argbAt(x, y)
                } else {
                    assertEquals(expected, image.argbAt(x, y))
                }
            }
        }
        if (outsideMarkOnly) assertTrue("Decoration must mask real QR modules", changedInsideMask)
    }

    private fun inviteOfLength(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789-_ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return buildString(length) {
            // Deterministic nonuniform payload; lowercase forces QR byte mode.
            for (i in 0 until length) append(alphabet[(i * 37 + i / 7) % alphabet.length])
        }
    }
}
