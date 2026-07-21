package com.westboytech.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.westboytech.camera.audio.AudioManager
import com.westboytech.camera.camera.AspectRatioPreset
import com.westboytech.camera.camera.CameraManager
import com.westboytech.camera.filters.FilterType
import com.westboytech.camera.theme.Branding
import com.westboytech.camera.theme.WestboyColors
import com.westboytech.camera.theme.glassPanel
import kotlin.math.abs

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { CameraManager(context) }
    val audioManager = remember { AudioManager(context) }

    // --- Runtime permissions --------------------------------------------
    // CAMERA is always required. WRITE_EXTERNAL_STORAGE is a dangerous
    // permission that must be requested at runtime on API 26-28 (pre
    // scoped-storage) for MediaStore video saves to succeed; on API 29+
    // it's not needed (and isn't even declared for those versions).
    val needsStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val requiredPermissions = remember {
        if (needsStoragePermission)
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else
            arrayOf(Manifest.permission.CAMERA)
    }

    fun permissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var hasCameraPermission by remember { mutableStateOf(permissionsGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasCameraPermission = results.values.all { it } }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    var currentFilter by remember { mutableStateOf(FilterType.NONE) }
    var aspectRatio by remember { mutableStateOf(AspectRatioPreset.NINE_BY_SIXTEEN) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isGridVisible by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var showTrackPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var amplitudes by remember { mutableStateOf(FloatArray(40)) }

    val isRecording by cameraManager.isRecording.collectAsState()
    val recordError by cameraManager.recordError.collectAsState()
    val lastSavedUri by cameraManager.lastSavedUri.collectAsState()
    val bindError by cameraManager.bindError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(recordError) {
        recordError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(bindError) {
        bindError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(lastSavedUri) {
        if (lastSavedUri != null) snackbarHostState.showSnackbar("Saved to Movies/WestboyCamera")
    }

    DisposableEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraManager.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            // Release camera + audio hardware loops immediately when leaving composition.
            cameraManager.releaseAll()
            audioManager.release()
        }
    }

    val transformState = rememberTransformableState { zoomChange, _, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
        cameraManager.setZoom(zoom)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Camera access is needed to shoot video.",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                    Text("Grant camera access")
                }
            }
            return@Box
        }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    cameraManager.preview?.setSurfaceProvider(this.surfaceProvider)
                }
            },
            update = { view ->
                cameraManager.preview?.setSurfaceProvider(view.surfaceProvider)
            },
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (abs(dragAmount) > 40) {
                            currentFilter = if (dragAmount < 0) FilterType.next(currentFilter)
                            else FilterType.previous(currentFilter)
                        }
                    }
                }
        )

        // Watermark
        Text(
            text = Branding.WATERMARK_TEXT,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 130.dp)
        )

        // Top control bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .glassPanel(cornerRadius = 18)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { isTorchOn = !isTorchOn; cameraManager.toggleTorch() }) {
                Icon(if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, null, tint = Color.White)
            }
            IconButton(onClick = { isGridVisible = !isGridVisible }) {
                Icon(Icons.Filled.GridOn, null, tint = Color.White)
            }
            IconButton(onClick = { showTrackPicker = true }) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White)
            }
            Text(aspectRatio.label, color = Color.White, fontSize = 13.sp)
            IconButton(onClick = { cameraManager.switchCamera(lifecycleOwner) }) {
                Icon(Icons.Filled.Cameraswitch, null, tint = Color.White)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, null, tint = Color.White)
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPlayingAudio) {
                WaveformView(amplitudes = amplitudes, modifier = Modifier.padding(bottom = 8.dp))
            }
            FilterDockView(
                selected = currentFilter,
                onSelect = { currentFilter = it },
                modifier = Modifier.padding(bottom = 10.dp)
            )
            RecordButton(isRecording = isRecording, progress = 0f) {
                if (isRecording) {
                    cameraManager.stopRecording()
                    audioManager.stopMonitoring()
                    isPlayingAudio = false
                } else {
                    cameraManager.startRecording()
                    audioManager.startMonitoring()
                    isPlayingAudio = true
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showTrackPicker) {
        TrackPickerSheet(audioManager = audioManager, onDismiss = { showTrackPicker = false })
    }
    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}
