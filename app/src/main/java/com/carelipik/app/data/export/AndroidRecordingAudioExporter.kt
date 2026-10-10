package com.carelipik.app.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.carelipik.app.data.audio.WaveAudioInspector
import com.carelipik.app.domain.export.RecordingAudioExporter
import com.carelipik.app.domain.export.RecordingAudioExportResult
import com.carelipik.app.domain.model.RecordedAudio
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidRecordingAudioExporter(context: Context) : RecordingAudioExporter {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver

    override suspend fun export(audio: RecordedAudio, destinationUri: String): RecordingAudioExportResult =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(destinationUri)
            if (uri.scheme != "content") {
                return@withContext RecordingAudioExportResult.Failure("Choose a location using the Save file dialog.")
            }
            try {
                val source = File(audio.localPath)
                WaveAudioInspector.inspectCompatiblePcm(source)
                val size = source.inputStream().buffered().use { input ->
                    val output = resolver.openOutputStream(uri, "wt")
                        ?: error("The selected location could not be opened.")
                    output.use { input.copyTo(it) }
                }
                RecordingAudioExportResult.Success(size)
            } catch (cancelled: CancellationException) {
                removeIncompleteDocument(uri)
                throw cancelled
            } catch (error: Exception) {
                removeIncompleteDocument(uri)
                RecordingAudioExportResult.Failure(
                    "Could not download the recording. " +
                        (error.message ?: "Choose another location and try again.")
                )
            }
        }

    private fun removeIncompleteDocument(uri: Uri) {
        // CreateDocument returns a new document, so a failed copy can safely be removed.
        runCatching {
            if (DocumentsContract.isDocumentUri(applicationContext, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                resolver.delete(uri, null, null)
            }
        }
    }
}
