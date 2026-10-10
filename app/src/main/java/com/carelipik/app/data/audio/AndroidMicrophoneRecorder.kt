package com.carelipik.app.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.recording.AudioImportResult
import com.carelipik.app.domain.recording.ConsultationRecorder
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Captures mono PCM audio into a private-cache WAV file and reports live microphone energy. */
class AndroidMicrophoneRecorder(private val context: Context) : ConsultationRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _amplitude = MutableStateFlow(0f)
    private val _recordedAudio = MutableStateFlow<RecordedAudio?>(null)
    private val _isPlaying = MutableStateFlow(false)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()
    override val recordedAudio: StateFlow<RecordedAudio?> = _recordedAudio.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var isPaused = false
    @Volatile private var shouldStop = false

    init {
        cleanupStaleTemporaryAudio()
    }

    override fun start() {
        discard()
        val directory = File(context.cacheDir, "consultation_audio").apply { mkdirs() }
        outputFile = File(directory, "consultation-${UUID.randomUUID()}.wav")
        startCapture()
    }

    override fun pause() {
        isPaused = true
        audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
        _amplitude.value = 0f
    }

    override fun resume() {
        val recorder = audioRecord ?: return
        recorder.startRecording()
        isPaused = false
    }

    override fun stop() {
        shouldStop = true
        audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
        _amplitude.value = 0f
    }

    override fun discard() {
        stopPlayback()
        shouldStop = true
        audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
        val fileToDelete = outputFile
        outputFile = null
        captureJob?.cancel()
        captureJob = null
        fileToDelete?.delete()
        _recordedAudio.value = null
        _amplitude.value = 0f
    }

    override fun play() {
        val path = _recordedAudio.value?.localPath ?: return
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener {
                _isPlaying.value = false
                it.release()
                if (mediaPlayer === it) mediaPlayer = null
            }
            prepare()
            start()
        }
        _isPlaying.value = true
    }

    override fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
    }

    override suspend fun importAudio(sourceUri: String): AudioImportResult =
        withContext(Dispatchers.IO) {
            stopPlayback()
            val uri = runCatching { Uri.parse(sourceUri) }.getOrNull()
                ?: return@withContext AudioImportResult.Failure("The selected file is unavailable.")
            val directory = File(context.cacheDir, "consultation_audio").apply { mkdirs() }
            val importedFile = File(directory, "import-${UUID.randomUUID()}.wav")
            try {
                val displayName = queryDisplayName(uri) ?: "Imported consultation.wav"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    importedFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_IMPORTED_AUDIO_BYTES) {
                                "Select a WAV file smaller than 100 MB."
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("The selected file could not be opened.")
                val metadata = WaveAudioInspector.inspectCompatiblePcm(importedFile)
                val audio = RecordedAudio(
                    localPath = importedFile.absolutePath,
                    sizeBytes = importedFile.length(),
                    displayName = displayName,
                    durationMillis = metadata.durationMillis,
                    source = RecordedAudioSource.Imported
                )
                val previousFile = outputFile
                outputFile = importedFile
                _recordedAudio.value = audio
                if (previousFile != importedFile) previousFile?.delete()
                AudioImportResult.Success(audio)
            } catch (error: Exception) {
                importedFile.delete()
                AudioImportResult.Failure(
                    error.message ?: "The selected WAV file could not be imported."
                )
            }
        }

    override suspend fun restoreAudio(audio: RecordedAudio): AudioImportResult =
        withContext(Dispatchers.IO) {
            val file = File(audio.localPath)
            try {
                // Only accept private temporary files, never transfer ownership of a saved archive.
                require(file.canonicalFile.parentFile == File(context.cacheDir, "consultation_audio").canonicalFile)
                val metadata = WaveAudioInspector.inspectCompatiblePcm(file)
                stopPlayback()
                val previous = outputFile
                outputFile = file
                val restored = audio.copy(sizeBytes = file.length(), durationMillis = metadata.durationMillis)
                _recordedAudio.value = restored
                if (previous != file) previous?.delete()
                AudioImportResult.Success(restored)
            } catch (error: Exception) {
                if (file.canonicalFile.parentFile == File(context.cacheDir, "consultation_audio").canonicalFile) {
                    file.delete()
                }
                AudioImportResult.Failure(error.message ?: "The saved recording could not be opened.")
            }
        }

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission is required" }
        val file = checkNotNull(outputFile)
        val sampleRate = 16_000
        val minimumSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minimumSize > 0) { "Microphone configuration is unavailable" }
        val bufferSize = minimumSize.coerceAtLeast(sampleRate / 5)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Microphone could not be initialized"
        }
        shouldStop = false
        isPaused = false
        audioRecord = recorder
        // Attach to this capture session: suppression happens before PCM is written, without
        // removing silent frames or shifting the timestamps used by speaker alignment.
        val noiseSuppressor = createNoiseSuppressor(recorder.audioSessionId)
        try {
            recorder.startRecording()
        } catch (error: Exception) {
            noiseSuppressor?.release()
            recorder.release()
            audioRecord = null
            throw error
        }
        captureJob = scope.launch {
            try {
                RandomAccessFile(file, "rw").use { output ->
                    writeWaveHeader(output, sampleRate, dataSize = 0)
                    val samples = ShortArray(bufferSize / 2)
                    val bytes = ByteArray(samples.size * 2)
                    try {
                        while (isActive && !shouldStop) {
                            if (isPaused) {
                                delay(50)
                                continue
                            }
                            val count = recorder.read(samples, 0, samples.size)
                            if (count <= 0) continue
                            var squareTotal = 0.0
                            repeat(count) { index ->
                                val sample = samples[index].toInt()
                                bytes[index * 2] = sample.toByte()
                                bytes[index * 2 + 1] = (sample shr 8).toByte()
                                squareTotal += sample.toDouble() * sample.toDouble()
                            }
                            output.write(bytes, 0, count * 2)
                            val normalizedRms = (sqrt(squareTotal / count) / Short.MAX_VALUE).toFloat()
                            val level = (normalizedRms * 8f).coerceIn(0f, 1f)
                            _amplitude.value = (_amplitude.value * 0.35f) + (level * 0.65f)
                        }
                    } finally {
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                        _amplitude.value = 0f
                        val dataSize = (output.length() - WAVE_HEADER_SIZE).coerceAtLeast(0)
                        writeWaveHeader(output, sampleRate, dataSize)
                        if (dataSize > 0 && outputFile === file && file.exists()) {
                            _recordedAudio.value = RecordedAudio(
                                localPath = file.absolutePath,
                                sizeBytes = output.length(),
                                durationMillis = dataSize * 1_000L / (sampleRate * 2L)
                            )
                        }
                    }
                }
            } finally {
                noiseSuppressor?.release()
                recorder.release()
                if (audioRecord === recorder) audioRecord = null
            }
        }
    }

    private fun createNoiseSuppressor(sessionId: Int): NoiseSuppressor? {
        var effect: NoiseSuppressor? = null
        return try {
            if (!NoiseSuppressor.isAvailable()) {
                Log.i("CareLipikRecording", "Noise suppression unavailable; recording original microphone audio")
                null
            } else {
                effect = NoiseSuppressor.create(sessionId)
                if (effect != null && effect.setEnabled(true) == AudioEffect.SUCCESS && effect.enabled) {
                    Log.i("CareLipikRecording", "Capture noise suppression enabled")
                    effect
                } else {
                    effect?.release()
                    Log.w("CareLipikRecording", "Noise suppression could not be enabled; recording original microphone audio")
                    null
                }
            }
        } catch (_: Exception) {
            effect?.release()
            Log.w("CareLipikRecording", "Noise suppression initialization failed; recording original microphone audio")
            null
        }
    }

    private fun writeWaveHeader(file: RandomAccessFile, sampleRate: Int, dataSize: Long) {
        file.seek(0)
        file.writeBytes("RIFF")
        file.writeLittleEndianInt((dataSize + 36).toInt())
        file.writeBytes("WAVEfmt ")
        file.writeLittleEndianInt(16)
        file.writeLittleEndianShort(1)
        file.writeLittleEndianShort(1)
        file.writeLittleEndianInt(sampleRate)
        file.writeLittleEndianInt(sampleRate * 2)
        file.writeLittleEndianShort(2)
        file.writeLittleEndianShort(16)
        file.writeBytes("data")
        file.writeLittleEndianInt(dataSize.toInt())
        file.seek(file.length())
    }

    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
    }

    private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
        write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun cleanupStaleTemporaryAudio() {
        val expirationTime = System.currentTimeMillis() - TEMPORARY_AUDIO_MAX_AGE_MILLIS
        File(context.cacheDir, "consultation_audio")
            .listFiles()
            ?.filter { it.isFile && it.lastModified() < expirationTime }
            ?.forEach(File::delete)
    }

    fun close() {
        discard()
        scope.cancel()
    }

    private companion object {
        const val WAVE_HEADER_SIZE = 44L
        const val MAX_IMPORTED_AUDIO_BYTES = 100L * 1024L * 1024L
        const val TEMPORARY_AUDIO_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
