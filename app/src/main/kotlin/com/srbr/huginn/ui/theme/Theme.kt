package com.srbr.huginn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SamsungBlue      = Color(0xFF1428A0)
val SamsungBlueDark  = Color(0xFF0F1F7A)
val SamsungBlueLight = Color(0xFF2539C8)
val SamsungBluePale  = Color(0xFFE8EEFF)
val DarkBackground   = Color(0xFF0D0D1A)
val DarkSurface      = Color(0xFF16213E)
val MutedText        = Color(0xFF9198B0)
val SubtleText       = Color(0xFF555E7A)
val SuccessGreen     = Color(0xFF00C882)
val ErrorRed         = Color(0xFFFF3B5C)

private val DarkColors = darkColorScheme(
    primary         = SamsungBlue,
    onPrimary       = Color.White,
    secondary       = SamsungBlueLight,
    background      = DarkBackground,
    surface         = DarkSurface,
    onBackground    = Color.White,
    onSurface       = Color.White,
    error           = ErrorRed,
)

@Composable
fun HuginnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content     = content
    )
}
