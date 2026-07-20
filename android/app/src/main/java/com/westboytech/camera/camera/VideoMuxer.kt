package com.westboytech.camera.camera

import android.media.*
import android.net.Uri
import android.content.Context
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes GPU-filtered video frames with [MediaCodec] and muxes them
 * together with PCM/AAC audio read directly from the selected background
 * track file via [MediaExtractor] — never from a live microphone input.
 * Both streams are written against the same presentation-time origin
 * (first video frame timestamp = t0) so playback stays in sync with what
 * the user heard during monitoring.
 */
class VideoMuxer(private val context: Context, private val width: Int, private val height: Int) {

    private var videoEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var firstFramePts = -1L
    var outputFile: File? = null
        private set

    fun start(backgroundTrackUri: Uri?) {
        val out = File(context.cacheDir, "westboy_${System.currentTimeMillis()}.mp4")
        outputFile = out
        muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 12_000_000) // high bitrate, 1080p cap
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        if (backgroundTrackUri != null) {
            muxAudioTrack(backgroundTrackUri)
        }
    }

    /** Surface the GPU filter renderer draws into; encoder pulls frames from here. */
    fun inputSurface(): android.view.Surface? = videoEncoder?.createInputSurface()

    /** Drains encoded video frames into the muxer. Call on each new
     * frame availability (e.g. from a dedicated encoder-drain thread). */
    fun drainVideoEncoder(endOfStream: Boolean = false) {
        val encoder = videoEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        if (endOfStream) encoder.signalEndOfInputStream()

        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoTrackIndex = muxer?.addTrack(encoder.outputFormat) ?: -1
                    maybeStartMuxer()
                }
                outIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outIndex) ?: continue
                    if (bufferInfo.size > 0 && muxerStarted) {
                        if (firstFramePts < 0) firstFramePts = bufferInfo.presentationTimeUs
                        bufferInfo.presentationTimeUs -= firstFramePts
                        muxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun muxAudioTrack(uri: Uri) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                extractor.selectTrack(i)
                audioFormat = fmt
                break
            }
        }
        val fmt = audioFormat ?: return
        audioTrackIndex = muxer?.addTrack(fmt) ?: -1
        maybeStartMuxer()

        // Copy sample data straight from source track to output container —
        // no decode/re-encode needed since we're not mixing/altering it.
        Thread {
            val buffer = ByteBuffer.allocate(1 shl 20)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.apply {
                    size = sampleSize
                    presentationTimeUs = extractor.sampleTime
                    flags = extractor.sampleFlags
                    offset = 0
                }
                if (muxerStarted) muxer?.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            extractor.release()
        }.start()
    }

    private fun maybeStartMuxer() {
        val needsAudio = audioTrackIndex >= 0
        val ready = videoTrackIndex >= 0 && (!needsAudio || audioTrackIndex >= 0)
        if (ready && !muxerStarted) {
            muxer?.start()
            muxerStarted = true
        }
    }

    fun stop() {
        drainVideoEncoder(endOfStream = true)
        videoEncoder?.stop()
        videoEncoder?.release()
        muxer?.stop()
        muxer?.release()
        muxerStarted = false
        firstFramePts = -1
    }
}
