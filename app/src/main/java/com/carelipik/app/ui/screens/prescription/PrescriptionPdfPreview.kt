package com.carelipik.app.ui.screens.prescription

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.carelipik.app.data.prescription.AndroidPrescriptionPdfRenderer
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PrescriptionPdfPreview(state: PrescriptionUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val pdf by produceState<Result<ByteArray>?>(null, state.source) {
        value = null
        pageIndex = 0
        if (state.source.isBlank()) return@produceState
        delay(250)
        value = withContext(Dispatchers.Default) {
            runCatching { AndroidPrescriptionPdfRenderer.render(state.source) }
        }
    }
    val bytes = pdf?.getOrNull()
    val rendered by produceState<Result<Pair<Bitmap, Int>>?>(null, bytes, pageIndex) {
        value = null
        if (bytes == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File.createTempFile("rx-preview-", ".pdf", context.cacheDir)
                try {
                    file.writeBytes(bytes)
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap to renderer.pageCount
                            }
                        }
                    }
                } finally { file.delete() }
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val selectedBytes = exportBytes
        exportBytes = null
        if (uri != null && selectedBytes != null) scope.launch {
            exportMessage = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destination unavailable")
                    output.use { it.write(selectedBytes) }
                }.fold({ "Prescription PDF exported." }, { "Could not export the prescription PDF." })
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Prescription PDF preview", style = MaterialTheme.typography.titleLarge)
            if (state.savedTemplate.isNotBlank() && state.savedTemplate != PrescriptionTemplates.common) {
                Text("Letterhead text is included. Complex LaTeX styling and images require external compilation.")
            }
            val page = rendered?.getOrNull()
            Box(Modifier.fillMaxWidth().aspectRatio(595f / 842f)) {
                when {
                    pdf?.isFailure == true || rendered?.isFailure == true -> Text("Preview unavailable. Check the prescription document.")
                    page == null -> Text("Preparing PDF preview…")
                    else -> {
                        val bitmap = page.first
                        Image(bitmap.asImageBitmap(), "Prescription PDF page ${pageIndex + 1}",
                            Modifier.fillMaxSize())
                    }
                }
            }
            rendered?.getOrNull()?.second?.let { count ->
                if (count > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("Previous") }
                    Text("Page ${pageIndex + 1} of $count")
                    OutlinedButton(onClick = { pageIndex++ }, enabled = pageIndex + 1 < count) { Text("Next") }
                }
            }
            val complete = state.medicines.all {
                it.name.isNotBlank() && it.dose.isNotBlank() && it.frequency.isNotBlank() && it.duration.isNotBlank()
            }
            Button(onClick = { exportBytes = bytes; export.launch("prescription.pdf") },
                enabled = bytes != null && complete && !state.busy) { Text("Export prescription PDF") }
            if (!complete) Text("Complete each medicine's name, dose, timing and duration before export.")
            exportMessage?.let { Text(it) }
        }
    }
}
