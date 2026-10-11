package com.carelipik.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class PrescriptionImageImporter(private val context: Context) {
    fun import(uri: Uri): Result<String> = runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error("The prescription image could not be opened.")
        val largest = max(source.width, source.height)
        val bitmap = if (largest > 1800) {
            val scale = 1800f / largest
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt(),
                (source.height * scale).toInt(),
                true
            )
        } else source
        val directory = File(context.cacheDir, "prescription_attachments").apply { mkdirs() }
        val file = File(directory, "prescription_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
        }
        file.absolutePath
    }
}
