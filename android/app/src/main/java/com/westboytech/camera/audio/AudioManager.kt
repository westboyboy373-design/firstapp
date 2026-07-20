package com.westboytech.camera.audio

import android.content.Context
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BackgroundTrack(val title: String, val uri: Uri)

/**
 * Owns ONLY background-music playback for real-time monitoring while
 * recording. There is no `AudioRecord`/`MediaRecorder` mic input anywhere
 * in this class — that omission is what keeps the exported file free of
 * room noise. [Visualizer] taps the ExoPlayer's own output session, not
 * the microphone, to drive the waveform UI.
 */
class AudioManager(private val context: Context) {

    private var player: ExoPlayer? = null
    private var visualizer: Visualizer? = null

    private val _selectedTrack = MutableStateFlow<BackgroundTrack?>(null)
    val selectedTrack: StateFlow<BackgroundTrack?> = _selectedTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _amplitudes = MutableStateFlow(FloatArray(40))
    val amplitudes: StateFlow<FloatArray> = _amplitudes

    fun loadTrack(track: BackgroundTrack) {
        _selectedTrack.value = track
        player?.release()
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(track.uri))
            prepare()
        }
    }

    /** Starts playback for live monitoring. Call at the same moment
     * video recording begins so playback position stays in sync with
     * what VideoMuxer will bake into the exported file. */
    fun startMonitoring() {
        val exoPlayer = player ?: return
        exoPlayer.seekTo(0)
        exoPlayer.play()
        _isPlaying.value = true
        attachVisualizer(exoPlayer.audioSessionId)
    }

    fun stopMonitoring() {
        player?.pause()
        _isPlaying.value = false
        visualizer?.release()
        visualizer = null
    }

    private fun attachVisualizer(audioSessionId: Int) {
        visualizer?.release()
        if (audioSessionId == 0) return // C.AUDIO_SESSION_ID_UNSET
        visualizer = Visualizer(audioSessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    waveform ?: return
                    _amplitudes.value = bucketize(waveform, bucketCount = 40)
                }
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            enabled = true
        }
    }

    private fun bucketize(waveform: ByteArray, bucketCount: Int): FloatArray {
        val bucketSize = (waveform.size / bucketCount).coerceAtLeast(1)
        val buckets = FloatArray(bucketCount)
        for (b in 0 until bucketCount) {
            val start = b * bucketSize
            val end = (start + bucketSize).coerceAtMost(waveform.size)
            if (start >= end) continue
            var sum = 0f
            for (i in start until end) {
                // PCM 8-bit unsigned centered at 128
                sum += kotlin.math.abs(waveform[i].toInt() - 128)
            }
            buckets[b] = ((sum / (end - start)) / 128f).coerceIn(0f, 1f)
        }
        return buckets
    }

    /** Releases the audio hardware/output loop — call on app background. */
    fun release() {
        visualizer?.release()
        visualizer = null
        player?.release()
        player = null
        _isPlaying.value = false
    }
}
