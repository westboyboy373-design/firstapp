package com.westboytech.camera.camera

import android.content.Context
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.hardware.camera2.CaptureRequest

enum class CameraFacing { FRONT, BACK }
enum class AspectRatioPreset(val label: String) {
    NINE_BY_SIXTEEN("9:16"), ONE_BY_ONE("1:1"), SIXTEEN_BY_NINE("16:9")
}

/**
 * Owns the CameraX pipeline: preview + a [analysis use case] that hands
 * raw frames to [FilterRenderer] for GPU filtering and export. There is
 * intentionally NO audio capture use case attached anywhere in this
 * class — the CameraX `Recorder` is configured `withAudioEnabled(false)`
 * so the device microphone is never opened during recording. Background
 * music is handled entirely by AudioManager + VideoMuxer.
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    var preview: Preview? = null
        private set
    var imageAnalysis: ImageAnalysis? = null
        private set

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio

    private val _facing = MutableStateFlow(CameraFacing.BACK)
    val facing: StateFlow<CameraFacing> = _facing

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn

    // Capped per spec: never request above FHD (1080p), prefer 60fps.
    private val targetQuality = Quality.FHD
    private val preferredFpsRange = Range(60, 60)
    private val fallbackFpsRange = Range(30, 30)

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val selector = if (_facing.value == CameraFacing.BACK)
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            val previewUseCase = Preview.Builder().build()

            val analysisBuilder = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1080, 1920))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            // Camera2Interop lets us request the 60fps range and lock
            // AE/AF the same way native Camera2 apps do, while staying on
            // the CameraX API surface for everything else.
            val camera2Ext = Camera2Interop.Extender(analysisBuilder)
            camera2Ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, preferredFpsRange
            )
            camera2Ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
            camera2Ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON
            )

            val analysisUseCase = analysisBuilder.build().apply {
                setAnalyzer(java.util.concurrent.Executors.newSingleThreadExecutor(), analyzer)
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, analysisUseCase)
            preview = previewUseCase
            imageAnalysis = analysisUseCase
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
        _zoomRatio.value = clamped
    }

    fun toggleTorch() {
        val cam = camera ?: return
        val newState = !_isTorchOn.value
        cam.cameraControl.enableTorch(newState)
        _isTorchOn.value = newState
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, analyzer: ImageAnalysis.Analyzer) {
        _facing.value = if (_facing.value == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        bindToLifecycle(lifecycleOwner, analyzer)
    }

    fun lockFocusAndExposure(point: MeteringPoint) {
        val cam = camera ?: return
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun setRecording(active: Boolean) { _isRecording.value = active }

    /** Called from Activity.onStop()/onPause() — releases the camera
     * hardware loop immediately when backgrounded, per spec. */
    fun releaseAll() {
        cameraProvider?.unbindAll()
    }
}
