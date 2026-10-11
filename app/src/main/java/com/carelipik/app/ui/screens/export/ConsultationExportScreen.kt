package com.carelipik.app.ui.screens.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carelipik.app.ui.components.ConsultationScreenHeader
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.model.HandwrittenSignature
import com.carelipik.app.domain.model.SignaturePoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConsultationExportScreen(
    uiState: ConsultationExportUiState,
    onFormatSelected: (ConsultationExportFormat) -> Unit,
    onElectronicSignatureChanged: (Boolean) -> Unit,
    onSignerNameChanged: (String) -> Unit,
    onHandwrittenSignatureChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ConsultationScreenHeader(
            title = "Export approved note",
            subtitle = "Generate an encrypted report linked to this consultation history.",
            currentStep = 7,
            totalSteps = 7,
            onBack = onBack,
            backEnabled = uiState.status != ConsultationExportStatus.Generating
        )
        PrivacyCard()
        val consultation = uiState.consultation
        if (consultation != null) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Approved consultation", fontWeight = FontWeight.Bold)
                    Text(consultation.patientName)
                    Text(
                        consultation.visitReason.ifBlank { "No visit reason recorded" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Report: ${consultation.draft.noteFormat.displayName} • " +
                            consultation.draft.noteLanguage.displayName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        ExportFormatOptions(
            selected = uiState.selectedFormat,
            enabled = uiState.status != ConsultationExportStatus.Generating,
            onSelected = onFormatSelected
        )
        ElectronicSignatureOptions(
            enabled = uiState.electronicallySign,
            signerName = uiState.signerName,
            isEditable = uiState.status != ConsultationExportStatus.Generating,
            onEnabledChanged = onElectronicSignatureChanged,
            onSignerNameChanged = onSignerNameChanged,
            signature = uiState.handwrittenSignature,
            onSignatureChanged = onHandwrittenSignatureChanged
        )
        when (uiState.status) {
            ConsultationExportStatus.Empty -> Text(
                "No approved consultation is available for export.",
                color = MaterialTheme.colorScheme.error
            )
            ConsultationExportStatus.Generating -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("Creating ${uiState.selectedFormat.displayName} privately on this device...")
            }
            ConsultationExportStatus.Generated -> GeneratedExportCard(
                displayName = uiState.exportedFile?.displayName.orEmpty(),
                sizeBytes = uiState.exportedFile?.sizeBytes ?: 0L,
                formatName = uiState.exportedFile?.format?.displayName.orEmpty(),
                isSavedToHistory = uiState.savedArtifact != null,
                persistenceWarning = uiState.persistenceWarning,
                exportedFile = uiState.exportedFile,
                onShare = onShare
            )
            ConsultationExportStatus.Error -> Text(
                uiState.errorMessage ?: "The export could not be created.",
                color = MaterialTheme.colorScheme.error
            )
            ConsultationExportStatus.ReadyToGenerate -> Unit
        }
        if (uiState.status != ConsultationExportStatus.Generated) {
            Button(
                onClick = onGenerate,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth().testTag("generate_consultation_export")
            ) {
                Text(
                    if (uiState.status == ConsultationExportStatus.Error) {
                        "Try again"
                    } else {
                        "Create ${consultation?.draft?.noteFormat?.displayName.orEmpty()} as " +
                            uiState.selectedFormat.displayName
                    }
                )
            }
        }
        OutlinedButton(
            onClick = onFinish,
            enabled = uiState.status != ConsultationExportStatus.Generating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Finish")
        }
    }
}

@Composable
private fun ElectronicSignatureOptions(
    enabled: Boolean,
    signerName: String,
    isEditable: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onSignerNameChanged: (String) -> Unit,
    signature: String,
    onSignatureChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    enabled = isEditable
                )
                Text("Electronically sign this export", fontWeight = FontWeight.Bold)
            }
            if (enabled) {
                OutlinedTextField(
                    value = signerName,
                    onValueChange = onSignerNameChanged,
                    enabled = isEditable,
                    label = { Text("Signer name") },
                    supportingText = {
                        Text("Name attached to the handwritten signature")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HandwrittenSignaturePad(
                    encodedSignature = signature,
                    enabled = isEditable,
                    onSignatureChanged = onSignatureChanged
                )
                Text(
                    "Sign with a finger or stylus. This is a handwritten electronic signature, " +
                        "not a certificate-backed digital signature.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun HandwrittenSignaturePad(
    encodedSignature: String,
    enabled: Boolean,
    onSignatureChanged: (String) -> Unit,
    showClearAction: Boolean = true
) {
    var strokes by remember(encodedSignature) {
        mutableStateOf(HandwrittenSignature.decode(encodedSignature))
    }
    var activeStroke by remember { mutableStateOf<List<SignaturePoint>>(emptyList()) }
    val rasterBitmap = remember(encodedSignature) {
        HandwrittenSignature.decodeRaster(encodedSignature)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Draw signature", style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
        ) {
            rasterBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Saved signature",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, encodedSignature) {
                    if (!enabled || encodedSignature.isNotBlank()) return@pointerInput
                    var current = mutableListOf<SignaturePoint>()
                    var existing = strokes
                    detectDragGestures(
                        onDragStart = { offset ->
                            existing = strokes
                            current = mutableListOf(
                                SignaturePoint(offset.x / size.width, offset.y / size.height)
                            )
                            activeStroke = current.toList()
                        },
                        onDrag = { change, _ ->
                            current.add(
                                SignaturePoint(
                                    change.position.x / size.width,
                                    change.position.y / size.height
                                )
                            )
                            activeStroke = current.toList()
                        },
                        onDragEnd = {
                            if (current.size >= 2) {
                                strokes = existing + listOf(activeStroke)
                                onSignatureChanged(HandwrittenSignature.encode(strokes))
                            }
                            activeStroke = emptyList()
                        },
                        onDragCancel = { activeStroke = emptyList() }
                    )
                }
            ) {
                strokes.forEach { stroke ->
                    stroke.zipWithNext().forEach { (start, end) ->
                        drawLine(
                            color = androidx.compose.ui.graphics.Color.Black,
                            start = Offset(start.x * size.width, start.y * size.height),
                            end = Offset(end.x * size.width, end.y * size.height),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                activeStroke.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Black,
                        start = Offset(start.x * size.width, start.y * size.height),
                        end = Offset(end.x * size.width, end.y * size.height),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        if (enabled && encodedSignature.isNotBlank()) {
            Text(
                "Clear the saved signature before drawing a replacement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showClearAction) {
            TextButton(
                onClick = {
                    strokes = emptyList()
                    activeStroke = emptyList()
                    onSignatureChanged("")
                },
                enabled = enabled && encodedSignature.isNotBlank()
            ) { Text("Clear signature") }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Share carefully", fontWeight = FontWeight.Bold)
            Text(
                "Exports contain sensitive clinical information. CareLipik stores an encrypted " +
                    "copy with approved history and creates a temporary readable copy only for " +
                    "opening or sharing. Consultation audio is never included."
            )
        }
    }
}

@Composable
private fun ExportFormatOptions(
    selected: ConsultationExportFormat,
    enabled: Boolean,
    onSelected: (ConsultationExportFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Export file format",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ConsultationExportFormat.entries.forEach { format ->
            Card(
                onClick = { onSelected(format) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("export_format_${format.name}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == format) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(format.displayName, fontWeight = FontWeight.Bold)
                    Text(format.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GeneratedExportCard(
    displayName: String,
    sizeBytes: Long,
    formatName: String,
    isSavedToHistory: Boolean,
    persistenceWarning: String?,
    exportedFile: com.carelipik.app.domain.export.ExportedConsultationFile?,
    onShare: () -> Unit
) {
    var showPreview by remember(exportedFile?.localPath) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("$formatName ready", fontWeight = FontWeight.Bold)
            Text(displayName, style = MaterialTheme.typography.bodySmall)
            Text("${(sizeBytes / 1_024L).coerceAtLeast(1L)} KB")
            Text(
                if (isSavedToHistory) {
                    "Encrypted copy saved with consultation history"
                } else {
                    persistenceWarning ?: "History copy is unavailable"
                },
                color = if (isSavedToHistory) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().testTag("share_consultation_export")
            ) {
                Text("Share or save file")
            }
            OutlinedButton(
                onClick = { showPreview = !showPreview },
                enabled = exportedFile != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showPreview) "Hide preview" else "Preview report")
            }
            if (showPreview && exportedFile != null) {
                ExportPreview(exportedFile)
            }
        }
    }
}

@Composable
private fun ExportPreview(file: com.carelipik.app.domain.export.ExportedConsultationFile) {
    if (file.format == ConsultationExportFormat.ClinicalPdf) {
        val pageCount by produceState(initialValue = 0, file.localPath) {
            value = withContext(Dispatchers.IO) { pdfPageCount(file.localPath) }
        }
        var pageIndex by remember(file.localPath) { mutableStateOf(0) }
        val preview by produceState<Bitmap?>(initialValue = null, file.localPath, pageIndex) {
            value = withContext(Dispatchers.IO) { renderPdfPage(file.localPath, pageIndex) }
        }
        preview?.let { bitmap ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF report preview page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { pageIndex -= 1 },
                        enabled = pageIndex > 0
                    ) { Text("Previous") }
                    Text("Page ${pageIndex + 1} of ${pageCount.coerceAtLeast(1)}")
                    OutlinedButton(
                        onClick = { pageIndex += 1 },
                        enabled = pageIndex + 1 < pageCount
                    ) { Text("Next") }
                }
            }
        } ?: CircularProgressIndicator()
    } else {
        val preview by produceState(initialValue = "Loading preview…", file.localPath) {
            value = withContext(Dispatchers.IO) {
                runCatching { File(file.localPath).readText().take(20_000) }
                    .getOrElse { "Preview unavailable: ${it.message}" }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(preview, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun pdfPageCount(path: String): Int = runCatching {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
    }
}.getOrDefault(0)

private fun renderPdfPage(path: String, pageIndex: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@use null
            renderer.openPage(pageIndex).use { page ->
                val width = 900
                val height = (width.toFloat() / page.width * page.height).toInt()
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()
