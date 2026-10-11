package com.carelipik.app.domain.model

import android.util.Base64

data class SignaturePoint(val x: Float, val y: Float)

object HandwrittenSignature {
    private const val RASTER_PREFIX = "png:"

    fun encode(strokes: List<List<SignaturePoint>>): String = strokes
        .filter { it.size >= 2 }
        .joinToString("|") { stroke ->
            stroke.joinToString(";") { point -> "${point.x.coerceIn(0f, 1f)},${point.y.coerceIn(0f, 1f)}" }
        }

    fun decode(encoded: String): List<List<SignaturePoint>> = encoded
        .takeUnless(::isRaster)
        .orEmpty()
        .split('|')
        .mapNotNull { stroke ->
            stroke.split(';').mapNotNull { pair ->
                val values = pair.split(',')
                if (values.size != 2) null else {
                    val x = values[0].toFloatOrNull()
                    val y = values[1].toFloatOrNull()
                    if (x == null || y == null) null else SignaturePoint(x, y)
                }
            }.takeIf { it.size >= 2 }
        }

    fun encodeRaster(pngBytes: ByteArray): String =
        RASTER_PREFIX + Base64.encodeToString(pngBytes, Base64.NO_WRAP)

    fun decodeRaster(encoded: String): ByteArray? = if (isRaster(encoded)) {
        runCatching {
            Base64.decode(encoded.removePrefix(RASTER_PREFIX), Base64.DEFAULT)
        }.getOrNull()
    } else {
        null
    }

    fun isRaster(encoded: String): Boolean = encoded.startsWith(RASTER_PREFIX)
}
