package com.westboytech.camera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westboytech.camera.theme.Branding
import com.westboytech.camera.theme.WestboyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WestboyColors.Background) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(Branding.APP_NAME, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("Recording", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Text("Resolution cap: 1080p", color = Color.White, modifier = Modifier.padding(top = 4.dp))
            Text("Frame rate: 60fps (falls back to 30fps)", color = Color.White)
            Spacer(Modifier.height(20.dp))
            Text(Branding.CREDITS_TITLE, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Text(
                Branding.CREDITS_BODY,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = WestboyColors.NeonCyan
            )
        }
    }
}
