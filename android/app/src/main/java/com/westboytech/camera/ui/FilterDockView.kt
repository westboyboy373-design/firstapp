package com.westboytech.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westboytech.camera.filters.FilterType
import com.westboytech.camera.theme.WestboyColors
import com.westboytech.camera.theme.glassPanel

@Composable
fun FilterDockView(selected: FilterType, onSelect: (FilterType) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier
            .glassPanel(cornerRadius = 24)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(FilterType.entries.toList()) { filter ->
            FilterThumbnail(filter = filter, isSelected = filter == selected) { onSelect(filter) }
        }
    }
}

@Composable
private fun FilterThumbnail(filter: FilterType, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brushFor(filter))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) WestboyColors.NeonCyan else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .then(
                    if (isSelected) Modifier.shadow(8.dp, RoundedCornerShape(12.dp), spotColor = WestboyColors.NeonCyan)
                    else Modifier
                )
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = filter.label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

private fun brushFor(filter: FilterType): Brush = when (filter) {
    FilterType.NONE -> Brush.verticalGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.2f)))
    FilterType.CLASSIC_BW -> Brush.verticalGradient(listOf(Color.Black, Color.White.copy(alpha = 0.8f)))
    FilterType.CYBERPUNK -> Brush.linearGradient(listOf(Color(0xFFFF3D9A), Color(0xFF8B2FE0), Color(0xFF2F7BFF)))
    FilterType.VINTAGE_90S -> Brush.verticalGradient(listOf(Color(0xFFCC8844), Color(0xFF7A4A2E)))
    FilterType.CINEMATIC -> Brush.verticalGradient(listOf(Color.Gray.copy(alpha = 0.6f), Color.Black))
}
