package com.jumpmaster.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppTypography {
    val h1 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    val h2 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val small = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 2.sp)
    val mono = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    val monoSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    val monoLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
}
