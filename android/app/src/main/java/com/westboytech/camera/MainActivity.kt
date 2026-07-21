package com.westboytech.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import com.westboytech.camera.theme.WestboyTheme
import com.westboytech.camera.theme.WestboyColors
import com.westboytech.camera.ui.CameraScreen
import com.westboytech.camera.ui.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WestboyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = WestboyColors.Background) {
                    RootNavigation()
                }
            }
        }
    }
}

@Composable
private fun RootNavigation() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1800)
        showSplash = false
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "splash-to-camera"
    ) { splash ->
        if (splash) SplashScreen() else CameraScreen()
    }
}
