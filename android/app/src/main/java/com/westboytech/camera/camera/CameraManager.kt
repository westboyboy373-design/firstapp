package com.westboytech.camera.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.hardware.camera2.CaptureRequest

enum class CameraFacing { FRONT, BACK }
enum class AspectRatioPreset(val label: String) {
    NINE_BY_SIXTEEN("9:16"), ONE_BY_ONE("1:1"), SIXTEEN_BY_NINE("16:9")
}

private const val TAG = "CameraManager"

/**
 * Owns the CameraX pipeline: preview + [VideoCapture] (via CameraX's
 * built-in Recorder). Recording is captured WITHOUT the device
 * microphone (`withAudioEnabled(false)`) — this app never opens the
 * mic, on purpose, so exported clips can never contain room noise.
 * Background music is played back live for on-screen monitoring only
 * via AudioManager; it is not (yet) baked into the exported file.
 *
 * Binding is defensive: some budget/OEM camera HALs (LIMITED hardware
 * level, e.g. many MediaTek-based Samsung A-series phones) reject the
 * manual Camera2Interop capture-request options (forced 60fps, locked
 * AE/AF) at session-configuration time. Rather than let that crash the
 * app, a rejected bind is retried once WITHOUT those extras so preview
 * and recording still work — just without the 60fps/AE-lock polish.
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    var preview: Preview? = null
        private set
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _lastSavedUri = MutableStateFlow<android.net.Uri?>(null)
    val lastSavedUri: StateFlow<android.net.Uri?> = _lastSavedUri

    private val _recordError = MutableStateFlow<String?>(null)
    val recordError: StateFlow<String?> = _recordError

    private val _bindError = MutableStateFlow<String?>(null)
    val bindError: StateFlow<String?> = _bindError

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio

    private val _facing = MutableStateFlow(CameraFacing.BACK)
    val facing: StateFlow<CameraFacing> = _facing

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn

    // Preferred, but not required — see bindInternal()'s fallback.
    private val preferredFpsRange = Range(60, 60)

    /** analyzer param kept for call-site compatibility; no longer bound
     * (there was no real GPU filter pipeline consuming it — the export
     * path now goes through CameraX's own Recorder). */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        analyzer: ImageAnalysis.Analyzer? = null
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            bindInternal(provider, lifecycleOwner, applyCamera2Extras = true)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindInternal(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        applyCamera2Extras: Boolean
    ) {
        try {
            val selector = if (_facing.value == CameraFacing.BACK)
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            val previewBuilder = Preview.Builder()
            if (applyCamera2Extras) {
                val camera2Ext = Camera2Interop.Extender(previewBuilder)
                camera2Ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, preferredFpsRange
                )
                camera2Ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
                camera2Ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON
                )
            }
            val previewUseCase = previewBuilder.build()

            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val videoCaptureUseCase = VideoCapture.withOutput(recorder)

            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, videoCaptureUseCase)
            preview = previewUseCase
            videoCapture = videoCaptureUseCase
            _bindError.value = null
        } catch (e: Exception) {
            if (applyCamera2Extras) {
                // Likely an OEM/LIMITED-hardware-level camera rejecting the
                // manual FPS/AE/AF options. Retry with plain defaults.
                Log.w(TAG, "Camera bind failed with Camera2 extras, retrying without them", e)
                bindInternal(provider, lifecycleOwner, applyCamera2Extras = false)
            } else {
                Log.e(TAG, "Camera bind failed even without Camera2 extras", e)
                _bindError.value = "Couldn't start the camera on this device."
            }
        }
    }

    fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
        _zoomRatio.value = clamped
    }

    /** No-ops safely on devices/lenses with no flash unit (e.g. most
     * front cameras, and some budget rear cameras) instead of throwing. */
    fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        val newState = !_isTorchOn.value
        cam.cameraControl.enableTorch(newState)
        _isTorchOn.value = newState
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, analyzer: ImageAnalysis.Analyzer? = null) {
        _facing.value = if (_facing.value == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        _isTorchOn.value = false
        bindToLifecycle(lifecycleOwner, analyzer)
    }

    fun lockFocusAndExposure(point: MeteringPoint) {
        val cam = camera ?: return
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /** Starts recording to the device's Movies gallery (no mic audio). */
    @Suppress("MissingPermission")
    fun startRecording() {
        val videoCap = videoCapture ?: run {
            _recordError.value = "Camera isn't ready yet."
            return
        }
        if (activeRecording != null) return

        val name = "Westboy_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WestboyCamera")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            activeRecording = videoCap.output
                .prepareRecording(context, outputOptions)
                // withAudioEnabled() is intentionally NOT called — no mic capture.
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            _isRecording.value = true
                            _recordError.value = null
                        }
                        is VideoRecordEvent.Finalize -> {
                            _isRecording.value = false
                            activeRecording = null
                            if (event.hasError()) {
                                _recordError.value = "Recording failed (code ${event.error})."
                                Log.e(TAG, "Video capture error: ${event.error}", event.cause)
                            } else {
                                _lastSavedUri.value = event.outputResults.outputUri
                            }
                        }
                        else -> Unit
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _recordError.value = "Couldn't start recording on this device."
            activeRecording = null
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    /** Called from Activity.onStop()/onPause() — releases the camera
     * hardware loop immediately when backgrounded, per spec. */
    fun releaseAll() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
    }
}
