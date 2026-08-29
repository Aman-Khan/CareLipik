package com.carelipik.app.data.local

import android.content.Context
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.domain.repository.ConsultationReportRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps approved report files encrypted and materializes only short-lived cache copies. */
class EncryptedConsultationReportRepository(context: Context) : ConsultationReportRepository {
    private val applicationContext = context.applicationContext
    private val reportDirectory = File(applicationContext.filesDir, REPORT_DIRECTORY)
    private val accessDirectory = File(applicationContext.cacheDir, ACCESS_DIRECTORY)

    override suspend fun list(
        consultationId: String
    ): List<ConsultationReportArtifact> = withContext(Dispatchers.IO) {
        val directory = consultationDirectory(consultationId)
        directory.listFiles { file -> file.extension == FILE_EXTENSION }.orEmpty()
            .filter { it.length() in 1..MAX_ENCRYPTED_REPORT_BYTES }
            .mapNotNull { file ->
                runCatching { decode(decrypt(file.readBytes())).artifact }.getOrNull()
            }
            .filter { it.consultationId == consultationId }
            .sortedByDescending(ConsultationReportArtifact::generatedAtMillis)
    }

    override suspend fun save(
        consultationId: String,
        exportedFile: ExportedConsultationFile
    ): ConsultationReportArtifact = withContext(Dispatchers.IO) {
        validateConsultationId(consultationId)
        val source = File(exportedFile.localPath)
        require(source.isFile) { "The generated report file is unavailable." }
        require(source.length() in 1..MAX_REPORT_BYTES) { "The generated report is too large." }
        val artifact = ConsultationReportArtifact(
            consultationId = consultationId,
            format = exportedFile.format,
            generatedAtMillis = System.currentTimeMillis(),
            displayName = safeDisplayName(exportedFile.displayName, exportedFile.format),
            sizeBytes = source.length()
        )
        val targetDirectory = consultationDirectory(consultationId).apply { mkdirs() }
        val target = File(targetDirectory, "${exportedFile.format.name}.$FILE_EXTENSION")
        val pending = File(targetDirectory, "${target.name}.pending")
        pending.writeBytes(encrypt(encode(StoredReport(artifact, source.readBytes()))))
        if (!pending.renameTo(target)) {
            pending.copyTo(target, overwrite = true)
            check(pending.delete()) { "Could not finish saving the approved report." }
        }
        artifact
    }

    override suspend fun materialize(
        artifact: ConsultationReportArtifact
    ): ExportedConsultationFile = withContext(Dispatchers.IO) {
        cleanupStaleAccessFiles()
        val stored = readStored(artifact.consultationId, artifact.format)
        require(stored.artifact == artifact) { "The saved report metadata has changed." }
        val directory = File(accessDirectory, artifact.consultationId).apply { mkdirs() }
        val output = File(directory, artifact.displayName)
        val pending = File(directory, "${artifact.displayName}.pending")
        pending.writeBytes(stored.content)
        if (!pending.renameTo(output)) {
            pending.copyTo(output, overwrite = true)
            check(pending.delete()) { "Could not prepare the approved report." }
        }
        ExportedConsultationFile(
            localPath = output.absolutePath,
            displayName = artifact.displayName,
            sizeBytes = output.length(),
            mimeType = artifact.format.mimeType,
            format = artifact.format
        )
    }

    override suspend fun delete(
        artifact: ConsultationReportArtifact
    ) = withContext(Dispatchers.IO) {
        val target = reportFile(artifact.consultationId, artifact.format)
        if (target.isFile) {
            require(readStored(artifact.consultationId, artifact.format).artifact == artifact) {
                "A newer saved report has replaced this report."
            }
            target.delete()
        }
        File(accessDirectory, artifact.consultationId).deleteRecursively()
        deleteDirectoryIfEmpty(consultationDirectory(artifact.consultationId))
    }

    override suspend fun deleteForConsultation(
        consultationId: String
    ) = withContext(Dispatchers.IO) {
        consultationDirectory(consultationId).deleteRecursively()
        File(accessDirectory, validateConsultationId(consultationId)).deleteRecursively()
        Unit
    }

    private fun readStored(
        consultationId: String,
        format: ConsultationExportFormat
    ): StoredReport {
        val file = reportFile(consultationId, format)
        require(file.isFile) { "The saved report is unavailable." }
        require(file.length() in 1..MAX_ENCRYPTED_REPORT_BYTES) {
            "The encrypted report has an invalid size."
        }
        return decode(decrypt(file.readBytes())).also { stored ->
            require(stored.artifact.consultationId == consultationId) {
                "The saved report consultation does not match."
            }
            require(stored.artifact.format == format) { "The saved report format does not match." }
        }
    }

    private fun reportFile(consultationId: String, format: ConsultationExportFormat): File =
        File(consultationDirectory(consultationId), "${format.name}.$FILE_EXTENSION")

    private fun consultationDirectory(consultationId: String): File =
        File(reportDirectory, validateConsultationId(consultationId))

    private fun validateConsultationId(id: String): String = runCatching {
        UUID.fromString(id).toString()
    }.getOrElse { throw IllegalArgumentException("Invalid consultation identifier.") }

    private fun safeDisplayName(
        displayName: String,
        format: ConsultationExportFormat
    ): String {
        val fallback = "carelipik-approved-report.${format.extension}"
        val candidate = File(displayName).name.take(MAX_DISPLAY_NAME_CHARACTERS)
        return candidate.takeIf { it.isNotBlank() && !it.endsWith(".pending") } ?: fallback
    }

    private fun encode(stored: StoredReport): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(FORMAT_VERSION)
            output.writeUTF(stored.artifact.consultationId)
            output.writeUTF(stored.artifact.format.name)
            output.writeLong(stored.artifact.generatedAtMillis)
            output.writeUTF(stored.artifact.displayName)
            output.writeLong(stored.artifact.sizeBytes)
            output.writeInt(stored.content.size)
            output.write(stored.content)
        }
        bytes.toByteArray()
    }

    private fun decode(bytes: ByteArray): StoredReport =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported saved report format." }
            val consultationId = validateConsultationId(input.readUTF())
            val formatName = input.readUTF()
            val format = ConsultationExportFormat.entries.firstOrNull { it.name == formatName }
                ?: error("Unsupported report type.")
            val generatedAtMillis = input.readLong()
            val displayName = safeDisplayName(input.readUTF(), format)
            val sizeBytes = input.readLong()
            val contentSize = input.readInt()
            require(contentSize in 1..MAX_REPORT_BYTES.toInt()) { "Invalid report size." }
            require(sizeBytes == contentSize.toLong()) { "Saved report size does not match." }
            val content = ByteArray(contentSize).also(input::readFully)
            require(input.read() == -1) { "Unexpected saved report data." }
            StoredReport(
                artifact = ConsultationReportArtifact(
                    consultationId = consultationId,
                    format = format,
                    generatedAtMillis = generatedAtMillis,
                    displayName = displayName,
                    sizeBytes = sizeBytes
                ),
                content = content
            )
        }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plainText)
    }

    private fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > IV_BYTES) { "Invalid encrypted report data." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, cipherText.copyOfRange(0, IV_BYTES))
        )
        return cipher.doFinal(cipherText.copyOfRange(IV_BYTES, cipherText.size))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", KEYSTORE).run {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                        android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                    ).setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun cleanupStaleAccessFiles() {
        val cutoff = System.currentTimeMillis() - MAX_ACCESS_AGE_MILLIS
        accessDirectory.walkBottomUp()
            .filter { it.isFile && (it.lastModified() < cutoff || it.name.endsWith(".pending")) }
            .forEach(File::delete)
        accessDirectory.listFiles().orEmpty().filter(File::isDirectory).forEach(::deleteDirectoryIfEmpty)
    }

    private fun deleteDirectoryIfEmpty(directory: File) {
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    private data class StoredReport(
        val artifact: ConsultationReportArtifact,
        val content: ByteArray
    )

    companion object {
        const val REPORT_DIRECTORY = "approved_consultation_reports"
        const val ACCESS_DIRECTORY = "approved_report_access"
        private const val FILE_EXTENSION = "clr"
        private const val FORMAT_VERSION = 1
        private const val MAX_REPORT_BYTES = 20L * 1024L * 1024L
        private const val MAX_ENCRYPTED_REPORT_BYTES = MAX_REPORT_BYTES + 1024L * 1024L
        private const val MAX_DISPLAY_NAME_CHARACTERS = 180
        private const val MAX_ACCESS_AGE_MILLIS = 60L * 60L * 1_000L
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "carelipik-approved-report-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
