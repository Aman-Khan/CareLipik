package com.carelipik.app.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.carelipik.app.domain.model.RecordedAudio
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
        recorder.startRecording()
        captureJob = scope.launch {
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
                    recorder.release()
                    audioRecord = null
                    _amplitude.value = 0f
                    val dataSize = (output.length() - WAVE_HEADER_SIZE).coerceAtLeast(0)
                    writeWaveHeader(output, sampleRate, dataSize)
                    if (dataSize > 0 && outputFile === file && file.exists()) {
                        _recordedAudio.value = RecordedAudio(file.absolutePath, output.length())
                    }
                }
            }
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

    fun close() {
        discard()
        scope.cancel()
    }

    private companion object {
        const val WAVE_HEADER_SIZE = 44L
    }
}
