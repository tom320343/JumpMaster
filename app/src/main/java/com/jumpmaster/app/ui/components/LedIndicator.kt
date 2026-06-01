package com.jumpmaster.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jumpmaster.app.ui.theme.TextMuted
import com.jumpmaster.app.ui.theme.AppTypography

@Composable
fun LedIndicator(
    color: Color,
    active: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) color else Color(0xFF1A1A22))
                .then(
                    if (active) Modifier.border(1.dp, color, CircleShape)
                    else Modifier.border(1.dp, Color(0xFF2A2A35), CircleShape)
                )
                .shadow(
                    elevation = if (active) 8.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = if (active) color else Color.Transparent,
                    spotColor = if (active) color else Color.Transparent
                )
        )
        Text(
            text = label,
            style = AppTypography.monoSmall,
            color = if (active) Color(0xFFCCCCCC) else TextMuted
        )
    }
}
