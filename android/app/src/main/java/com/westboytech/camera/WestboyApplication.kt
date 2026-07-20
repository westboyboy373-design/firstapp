package com.westboytech.camera

import android.app.Application

/**
 * Runtime CAMERA permission must still be requested from MainActivity
 * (e.g. via androidx.activity.result.contract.ActivityResultContracts
 * .RequestPermission()) before CameraManager.bindToLifecycle() is
 * called — this class only hosts app-wide singletons if/when needed.
 */
class WestboyApplication : Application()
