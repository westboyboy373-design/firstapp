package com.westboytech.camera.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.westboytech.camera.audio.AudioManager
import com.westboytech.camera.audio.BackgroundTrack
import com.westboytech.camera.theme.WestboyColors

// Bundle these as royalty-free/owned sample assets under res/raw or
// assets/ — see the licensing flag in the design doc regarding any
// track library shipped with the app.
private val sampleTrackNames = listOf("neon_drive", "midnight_loop", "retro_wave")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerSheet(audioManager: AudioManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected by audioManager.selectedTrack.collectAsState()

    val tracks = sampleTrackNames.map { name ->
        BackgroundTrack(
            title = name.replace('_', ' ').replaceFirstChar { it.uppercase() },
            uri = Uri.parse("android.resource://${context.packageName}/raw/$name")
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.westboytech.camera.theme.WestboyColors.Background) {
        LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
            items(tracks) { track ->
                ListItem(
                    headlineContent = { Text(track.title, color = Color.White) },
                    leadingContent = { Icon(Icons.Filled.MusicNote, null, tint = Color.White) },
                    trailingContent = {
                        if (selected == track) Icon(androidx.compose.material.icons.Icons.Filled.Check, null, tint = WestboyColors.NeonCyan)
                    },
                    colors = ListItemDefaults.colors(containerColor = WestboyColors.Background),
                    modifier = Modifier.clickableTrack { audioManager.loadTrack(track); onDismiss() }
                )
            }
        }
    }
}

private fun Modifier.clickableTrack(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
