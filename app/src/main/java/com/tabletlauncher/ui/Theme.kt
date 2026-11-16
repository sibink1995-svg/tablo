package com.tabletlauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4F8),
            background = Color(0xFF101418),
            surface = Color(0xFF1B1F27),
            onSurface = Color(0xFFE6E8EC),
        ),
        content = content,
    )
}
