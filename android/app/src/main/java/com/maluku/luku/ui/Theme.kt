package com.maluku.luku.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Z {
    val bg = Color(0xFF0B0E13)
    val panel = Color(0xFF151A22)
    val panel2 = Color(0xFF1E2430)
    val accent = Color(0xFF7C5CFF)
    val focus = Color(0xFFFFD600)
    val text = Color(0xFFFFFFFF)
    val dim = Color(0xFF9AA3B2)
    val good = Color(0xFF22B365)
    val bad = Color(0xFFFF6B6B)
}

@Composable
fun LukuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Z.accent,
            background = Z.bg,
            surface = Z.panel,
            onPrimary = Color.White,
            onBackground = Z.text,
            onSurface = Z.text,
        ),
        content = content,
    )
}
