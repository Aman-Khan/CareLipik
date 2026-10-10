package com.carelipik.app.data.transcription

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SharedMemory
import android.os.SystemClock
import android.system.OsConstants
import java.io.Closeable
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Off-main-thread client. Audio uses anonymous shared memory, never Binder-sized arrays or files. */
internal class RemoteWhisperVulkanSession(private val context: Context,
    private val checkCancelled: () -> Unit, private val onDetail: (String) -> Unit) : Closeable {
    private val replies = LinkedBlockingQueue<Message>()
    @Volatile private var remote: Messenger? = null
    @Volatile private var pid = 0
    @Volatile private var closed = false
    private var bound = false
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == WhisperVulkanService.HELLO) pid = message.arg1
        replies.offer(Message.obtain(message))
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            remote = Messenger(binder)
            runCatching { remote!!.send(Message.obtain(null, WhisperVulkanService.HELLO).apply { replyTo = receiver }) }
                .onFailure { fail("Whisper Vulkan worker could not connect") }
        }
        override fun onServiceDisconnected(name: ComponentName) { fail("Whisper Vulkan worker stopped or crashed") }
        override fun onBindingDied(name: ComponentName) { fail("Whisper Vulkan binding died") }
        override fun onNullBinding(name: ComponentName) { fail("Whisper Vulkan binding unavailable") }
    }
    init {
        try {
            bound = context.bindService(Intent(context, WhisperVulkanService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Could not start Whisper Vulkan worker" }
            await(20_000, WhisperVulkanService.HELLO)
        } catch (error: Throwable) { close(); throw error }
    }
    fun open(path: String, gpu: Boolean = true) {
        request(WhisperVulkanService.OPEN, Bundle().apply { putString("path", path); putBoolean("gpu", gpu) }, 120_000)
    }
    fun recognize(samples: FloatArray, language: String): ByteArray {
        require(samples.isNotEmpty() && samples.size <= PcmWaveAudio.sampleRate * 25)
        return SharedMemory.create("carelipik-whisper-audio", samples.size * 4).use { memory ->
            val mapped = memory.mapReadWrite()
            try { mapped.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(samples) }
            finally { SharedMemory.unmap(mapped) }
            check(memory.setProtect(OsConstants.PROT_READ)) { "Could not protect Whisper audio buffer" }
            request(WhisperVulkanService.RECOGNIZE, Bundle().apply {
                putParcelable("audio", memory); putString("language", language)
            }, 130_000)
        }
    }
    private fun request(operation: Int, data: Bundle, timeout: Long): ByteArray {
        checkCancelled()
        remote!!.send(Message.obtain(null, operation).apply { this.data = data; replyTo = receiver })
        return await(timeout, WhisperVulkanService.RESULT).data.getByteArray("result")!!
    }
    private fun await(timeout: Long, expected: Int): Message {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            checkCancelled()
            val message = replies.poll(200, TimeUnit.MILLISECONDS) ?: continue
            when (message.what) {
                WhisperVulkanService.DETAIL -> onDetail(message.data.getString("detail").orEmpty())
                WhisperVulkanService.ERROR -> error(message.data.getString("error") ?: "Whisper Vulkan failed")
                expected -> return message
            }
        }
        error("Whisper Vulkan worker timed out; switching to Sherpa CPU")
    }
    private fun fail(reason: String) {
        replies.offer(Message.obtain(null, WhisperVulkanService.ERROR).apply {
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
