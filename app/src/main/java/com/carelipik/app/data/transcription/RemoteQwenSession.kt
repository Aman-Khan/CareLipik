package com.carelipik.app.data.transcription

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Synchronous worker-thread API over a private service, with hard wall-clock deadlines. */
internal class RemoteQwenSession(
    private val context: Context,
    private val gpu: Boolean,
    private val checkCancelled: () -> Unit,
    private val onDetail: (String) -> Unit
) : Closeable {
    private val replies = LinkedBlockingQueue<Message>()
    @Volatile private var remote: Messenger? = null
    @Volatile private var pid = 0
    @Volatile private var closed = false
    private var bound = false
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == QwenInferenceService.HELLO) pid = message.arg1
        replies.offer(Message.obtain(message))
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            remote = Messenger(binder)
            runCatching { remote!!.send(Message.obtain(null, QwenInferenceService.HELLO).apply { replyTo = receiver }) }
                .onFailure { fail("Qwen worker could not connect") }
        }
        override fun onServiceDisconnected(name: ComponentName) { fail("Qwen native worker stopped or crashed") }
        override fun onBindingDied(name: ComponentName) { fail("Qwen native worker binding died") }
        override fun onNullBinding(name: ComponentName) { fail("Qwen native worker binding unavailable") }
    }

    init {
        try {
            val service = if (gpu) QwenInferenceService::class.java else QwenCpuInferenceService::class.java
            bound = context.bindService(Intent(context, service), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Could not start Qwen worker" }
            await(20_000, QwenInferenceService.HELLO)
        } catch (error: Throwable) { close(); throw error }
    }

    fun open(path: String, gpu: Boolean) {
        request(QwenInferenceService.OPEN, Bundle().apply { putString("path", path); putBoolean("gpu", gpu) }, 100_000)
    }

    fun generate(prompt: ByteArray, report: Boolean = false): ByteArray = request(QwenInferenceService.GENERATE,
        Bundle().apply { putByteArray("prompt", prompt); putBoolean("report", report) }, 60_000)

    private fun request(operation: Int, data: Bundle, timeoutMs: Long): ByteArray {
        remote!!.send(Message.obtain(null, operation).apply { this.data = data; replyTo = receiver })
        return await(timeoutMs, QwenInferenceService.RESULT).data.getByteArray("result")!!
    }

    private fun await(timeoutMs: Long, expected: Int): Message {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            checkCancelled()
            val message = replies.poll(200, TimeUnit.MILLISECONDS) ?: continue
            when (message.what) {
                QwenInferenceService.DETAIL -> onDetail(message.data.getString("detail").orEmpty())
                QwenInferenceService.ERROR -> error(message.data.getString("error") ?: "Qwen worker failed")
                expected -> return message
            }
        }
        error("Qwen worker timed out after ${timeoutMs / 1000}s; it will be stopped")
    }

    private fun fail(reason: String) {
        replies.offer(Message.obtain(null, QwenInferenceService.ERROR).apply {
            data = Bundle().apply { putString("error", reason) }
        })
    }

    override fun close() {
        closed = true
        val workerPid = pid
        if (bound) { runCatching { context.unbindService(connection) }; bound = false }
        if (workerPid != 0) { Process.killProcess(workerPid); pid = 0 }
        remote = null
    }
}
