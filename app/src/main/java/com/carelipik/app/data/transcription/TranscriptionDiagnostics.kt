package com.carelipik.app.data.transcription

import android.content.Context
import android.util.Log
import com.carelipik.app.BuildConfig
import java.io.File

internal object TranscriptionDiagnostics {
    const val TAG = "CareLipikTranscription"
    fun event(message: String) { Log.i(TAG, message) }
    fun content(context: Context, label: String, text: String) {
        if (BuildConfig.DEBUG && File(context.noBackupFilesDir, "qwen-debug-content.enabled").isFile) {
            text.chunked(3000).forEachIndexed { index, chunk -> Log.d("CareLipikQwenResult", "$label[$index] $chunk") }
        }
    }
}
