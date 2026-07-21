package com.westboytech.camera.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westboytech.camera.audio.AudioManager
import com.westboytech.camera.audio.BackgroundTrack

/**
 * Lets the user pick a background-music track straight from the
 * device's own audio library via the system document picker (Storage
 * Access Framework). This does NOT require the READ_MEDIA_AUDIO /
 * READ_EXTERNAL_STORAGE runtime permission — the system picker runs in
 * its own process and only hands back a URI the user explicitly chose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerSheet(audioManager: AudioManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected by audioManager.selectedTrack.collectAsState()

    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val title = queryDisplayName(context, uri) ?: "Selected track"
            audioManager.loadTrack(BackgroundTrack(title = title, uri = uri))
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.westboytech.camera.theme.WestboyColors.Background) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Background music", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { Text("Choose from your music library", color = Color.White) },
                leadingContent = { Icon(Icons.Filled.LibraryMusic, null, tint = Color.White) },
                trailingContent = {
                    if (selected != null) Icon(Icons.Filled.Check, null, tint = com.westboytech.camera.theme.WestboyColors.NeonCyan)
                },
                colors = ListItemDefaults.colors(containerColor = com.westboytech.camera.theme.WestboyColors.Background),
                modifier = Modifier.clickable { pickAudioLauncher.launch(arrayOf("audio/*")) }
            )
            selected?.let {
                Spacer(Modifier.height(4.dp))
                Text("Current: ${it.title}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) return it.getString(nameIndex)
    }
    return null
}
