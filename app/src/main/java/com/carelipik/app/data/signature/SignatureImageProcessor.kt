package com.carelipik.app.data.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.carelipik.app.domain.model.HandwrittenSignature
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Converts a photographed/scanned signature to compact black ink on a white background. */
class SignatureImageProcessor(private val context: Context) {
    fun process(
        uri: Uri,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ): Result<String> = runCatching {
        val decoded = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error("The selected image could not be opened.")
        require(cropRight - cropLeft >= 0.05f && cropBottom - cropTop >= 0.05f)
        val selected = Bitmap.createBitmap(
            decoded,
            (decoded.width * cropLeft.coerceIn(0f, 0.95f)).toInt(),
            (decoded.height * cropTop.coerceIn(0f, 0.95f)).toInt(),
            (decoded.width * (cropRight - cropLeft).coerceIn(0.05f, 1f)).toInt()
                .coerceAtMost(decoded.width - (decoded.width * cropLeft).toInt()),
            (decoded.height * (cropBottom - cropTop).coerceIn(0.05f, 1f)).toInt()
                .coerceAtMost(decoded.height - (decoded.height * cropTop).toInt())
        )
        val scaled = selected.scaleDown(900)
        val luminance = IntArray(scaled.width * scaled.height)
        val histogram = IntArray(256)
        scaled.getPixels(luminance, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        luminance.indices.forEach { index ->
            val pixel = luminance[index]
            val value = (
                0.299 * Color.red(pixel) +
                    0.587 * Color.green(pixel) +
                    0.114 * Color.blue(pixel)
                ).toInt().coerceIn(0, 255)
            luminance[index] = value
            histogram[value]++
        }
        val threshold = otsuThreshold(histogram, luminance.size).coerceIn(70, 225)
        var left = scaled.width
        var top = scaled.height
        var right = -1
        var bottom = -1
        var inkCount = 0
        luminance.forEachIndexed { index, value ->
            if (value < threshold) {
                val x = index % scaled.width
                val y = index / scaled.width
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)
                inkCount++
            }
        }
        require(inkCount >= 30 && right >= left && bottom >= top) {
            "No clear signature was found. Use dark ink on plain paper and try again."
        }
        val padding = max(8, min(scaled.width, scaled.height) / 40)
        left = (left - padding).coerceAtLeast(0)
        top = (top - padding).coerceAtLeast(0)
        right = (right + padding).coerceAtMost(scaled.width - 1)
        bottom = (bottom + padding).coerceAtMost(scaled.height - 1)
        val croppedWidth = right - left + 1
        val croppedHeight = bottom - top + 1
        val outputWidth = 480
        val outputHeight = 180
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.WHITE)
        val fitScale = min(
            (outputWidth - 24f) / croppedWidth,
            (outputHeight - 24f) / croppedHeight
        )
        val drawnWidth = max(1, (croppedWidth * fitScale).toInt())
        val drawnHeight = max(1, (croppedHeight * fitScale).toInt())
        val startX = (outputWidth - drawnWidth) / 2
        val startY = (outputHeight - drawnHeight) / 2
        val pixels = IntArray(outputWidth * outputHeight) { Color.WHITE }
        for (targetY in 0 until drawnHeight) {
            val sourceY = top + (targetY * croppedHeight / drawnHeight)
            for (targetX in 0 until drawnWidth) {
                val sourceX = left + (targetX * croppedWidth / drawnWidth)
                val gray = luminance[sourceY * scaled.width + sourceX]
                pixels[(startY + targetY) * outputWidth + startX + targetX] =
                    if (gray < threshold) Color.BLACK else Color.WHITE
            }
        }
        output.setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        val bytes = ByteArrayOutputStream().use { stream ->
            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
            stream.toByteArray()
        }
        require(bytes.size < 42_000) { "The processed signature is too complex. Try a cleaner photo." }
        HandwrittenSignature.encodeRaster(bytes)
    }

    private fun Bitmap.scaleDown(maxSide: Int): Bitmap {
        val largest = max(width, height)
        if (largest <= maxSide) return this
        val scale = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        val totalIntensity = histogram.indices.sumOf { it.toLong() * histogram[it] }
        var backgroundWeight = 0L
        var backgroundIntensity = 0L
        var maximumVariance = -1.0
        var selected = 160
        for (threshold in histogram.indices) {
            backgroundWeight += histogram[threshold]
            if (backgroundWeight == 0L) continue
            val foregroundWeight = total.toLong() - backgroundWeight
            if (foregroundWeight == 0L) break
            backgroundIntensity += threshold.toLong() * histogram[threshold]
            val backgroundMean = backgroundIntensity.toDouble() / backgroundWeight
            val foregroundMean = (totalIntensity - backgroundIntensity).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight *
                (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > maximumVariance) {
                maximumVariance = variance
                selected = threshold
            }
        }
        return selected
    }
}
