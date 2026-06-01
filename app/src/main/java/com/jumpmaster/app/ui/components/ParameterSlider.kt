package com.jumpmaster.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jumpmaster.app.ui.theme.AppTypography
import com.jumpmaster.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    unit: String,
    color: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = AppTypography.caption, color = TextSecondary)
            Text(
                text = "${formatValue(value, step)}$unit",
                style = AppTypography.mono,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1A1A25))
            )
            // Active track
            val pct = (value - min) / (max - min)
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.6f))
            )
            // Slider
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = min..max,
                steps = ((max - min) / step - 1).toInt().coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatValue(value: Float, step: Float): String {
    return when {
        step < 0.01f -> String.format("%.3f", value)
        step < 0.1f -> String.format("%.2f", value)
        step < 1f -> String.format("%.1f", value)
        else -> String.format("%.0f", value)
    }
}
