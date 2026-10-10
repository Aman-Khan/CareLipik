package com.carelipik.app.data.transcription

import android.app.Service
import android.content.Intent
import android.os.*
import java.util.concurrent.Executors

/** Private process: native driver aborts and blocked GPU calls cannot freeze the consultation UI. */
open class QwenInferenceService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private var handle = 0L
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        val reply = message.replyTo
        if (message.what == HELLO) {
            reply.send(Message.obtain(null, HELLO).apply { arg1 = Process.myPid() })
        } else {
            val data = Bundle(message.data)
            val operation = message.what
            worker.execute {
                try {
                    val result = when (operation) {
                        OPEN -> {
                            val cache = java.io.File(codeCacheDir, "qwen-opencl").apply { mkdirs() }
                            android.system.Os.setenv("GGML_OPENCL_KERNEL_CACHE_DIR", cache.absolutePath, true)
                            NativeQwen3.loadLibrary()
                            handle = NativeQwen3.open(data.getString("path")!!,
                                applicationInfo.nativeLibraryDir, data.getBoolean("gpu"),
                                NativeCancellationChecker({}, { detail ->
                                    reply.send(Message.obtain(null, DETAIL).apply {
                                        this.data = Bundle().apply { putString("detail", detail) }
                                    })
                                }))
                            "ready".toByteArray()
                        }
                        GENERATE -> NativeQwen3.generate(handle, data.getByteArray("prompt")!!, data.getBoolean("report"))
                        else -> error("Unknown Qwen operation")
                    }
                    reply.send(Message.obtain(null, RESULT).apply {
                        this.data = Bundle().apply { putByteArray("result", result) }
                    })
                } catch (error: Throwable) {
                    TranscriptionDiagnostics.event("Qwen worker failure: ${error.javaClass.simpleName}: ${error.message}")
                    runCatching { reply.send(Message.obtain(null, ERROR).apply {
                        this.data = Bundle().apply { putString("error", error.message ?: "Native Qwen inference failed") }
                    }) }
                }
            }
        }
        true
    })

    override fun onBind(intent: Intent) = incoming.binder
    override fun onDestroy() {
        worker.shutdownNow()
        // Also releases native allocations if a driver call is blocked.
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    internal companion object {
        const val HELLO = 1
        const val OPEN = 2
        const val GENERATE = 3
        const val RESULT = 4
        const val ERROR = 5
        const val DETAIL = 6
    }
}

/** Separate process prevents a dying GPU service from racing a CPU fallback bind. */
class QwenCpuInferenceService : QwenInferenceService()
