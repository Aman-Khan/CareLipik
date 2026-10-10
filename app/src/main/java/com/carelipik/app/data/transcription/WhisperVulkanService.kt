package com.carelipik.app.data.transcription

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SharedMemory
import androidx.annotation.Keep
import java.nio.ByteOrder
import java.util.concurrent.Executors

@Keep
internal object NativeWhisperVulkan {
    fun loadLibrary() = System.loadLibrary("carelipik_whisper")
    external fun open(path: String, gpu: Boolean, checker: NativeCancellationChecker): Long
    external fun recognize(handle: Long, audio: FloatArray, language: String): ByteArray
}

/** Native driver failures are contained in a private, disposable process. */
class WhisperVulkanService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private var handle = 0L
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        val operation = message.what
        val reply = message.replyTo
        if (operation == HELLO) {
            reply.send(Message.obtain(null, HELLO).apply { arg1 = Process.myPid() })
        } else {
            val data = Bundle(message.data)
            worker.execute {
                try {
                    val result = when (operation) {
                        OPEN -> {
                            NativeWhisperVulkan.loadLibrary()
                            handle = NativeWhisperVulkan.open(data.getString("path")!!, data.getBoolean("gpu", true),
                                NativeCancellationChecker({}, { detail ->
                                    reply.send(Message.obtain(null, DETAIL).apply {
                                        this.data = Bundle().apply { putString("detail", detail) }
                                    })
                                }))
                            "ready".toByteArray()
                        }
                        RECOGNIZE -> {
                            @Suppress("DEPRECATION")
                            val memory = data.getParcelable<SharedMemory>("audio")!!
                            val samples = try {
                                require(memory.size in 4..(PcmWaveAudio.sampleRate * 25 * 4) && memory.size % 4 == 0)
                                val mapped = memory.mapReadOnly()
                                try {
                                    FloatArray(memory.size / 4).also {
                                        mapped.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
                                    }
                                } finally { SharedMemory.unmap(mapped) }
                            } finally { memory.close() }
                            NativeWhisperVulkan.recognize(handle, samples, data.getString("language")!!)
                        }
                        else -> error("Unknown Whisper Vulkan operation")
                    }
                    require(result.size <= 256 * 1024) { "Whisper Vulkan response exceeded the IPC limit" }
                    reply.send(Message.obtain(null, RESULT).apply {
                        this.data = Bundle().apply { putByteArray("result", result) }
                    })
                } catch (error: Throwable) {
                    TranscriptionDiagnostics.event("Whisper Vulkan worker failure: ${error.javaClass.simpleName}: ${error.message}")
                    runCatching { reply.send(Message.obtain(null, ERROR).apply {
                        this.data = Bundle().apply { putString("error", error.message ?: "Whisper Vulkan failed") }
                    }) }
                }
            }
        }
        true
    })

    override fun onBind(intent: Intent) = incoming.binder
    override fun onDestroy() {
        worker.shutdownNow()
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    internal companion object {
        const val HELLO = 1
        const val OPEN = 2
        const val RECOGNIZE = 3
        const val RESULT = 4
        const val ERROR = 5
        const val DETAIL = 6
    }
}
