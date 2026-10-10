package com.carelipik.app.ui.screens.transcript

import android.media.MediaPlayer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

internal class RegionPlaybackController {
    var player by mutableStateOf<MediaPlayer?>(null)
    var playing by mutableStateOf(false)
    var key: String? = null
}

internal val LocalRegionPlayback = compositionLocalOf<RegionPlaybackController?> { null }

/** Playback is local and bounded to the original source interval, including verification context. */
@Composable
internal fun AudioRegionReplayButton(audioPath: String?, startMs: Long, endMs: Long) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val fallback = remember { RegionPlaybackController() }
    val controller = LocalRegionPlayback.current ?: fallback
    val key = "$audioPath:$startMs:$endMs"
    var player by controller::player
    var playing by controller::playing
    var error by remember(audioPath, startMs, endMs) { mutableStateOf<String?>(null) }
    fun stop() {
        val previous = player
        player = null
        playing = false
        controller.key = null
        previous?.release()
    }
    DisposableEffect(audioPath, startMs, endMs, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (controller.key == key) stop()
        }
    }
    LaunchedEffect(playing, player) {
        val current = player
        if (playing && current != null && controller.key == key) {
            while (player === current && playing && controller.key == key) {
                if (runCatching { current.currentPosition.toLong() >= endMs }.getOrDefault(true)) {
                    stop()
                    break
                }
                delay(40)
            }
        }
    }
    OutlinedButton(enabled = audioPath != null && startMs >= 0 && endMs > startMs, onClick = {
        if (player != null && controller.key == key) stop()
        else {
            stop()
            error = null
            val current = MediaPlayer()
            player = current
            controller.key = key
            try {
                current.setDataSource(requireNotNull(audioPath))
                current.setOnPreparedListener {
                    if (player === current) {
                        if (startMs >= current.duration || endMs > current.duration + 100L) {
                            error = "Audio interval is unavailable."
                            stop()
                        } else current.seekTo(startMs, MediaPlayer.SEEK_CLOSEST)
                    }
                }
                current.setOnSeekCompleteListener {
                    if (player === current) {
                        current.start()
                        playing = true
                    }
                }
                current.setOnCompletionListener { if (player === current) stop() }
                current.setOnErrorListener { _, _, _ ->
                    if (player === current) {
                        error = "Recording could not be played."
                        stop()
                    }
                    true
                }
                current.prepareAsync()
            } catch (_: Exception) {
                error = "Recording could not be played."
                stop()
            }
        }
    }) { Text(if (player != null && controller.key == key) "Stop audio" else "Replay this audio region") }
    error?.let { Text(it) }
}
