package com.example.fintrack.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val cs = if (Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) }
    else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = cs, content = content)
}

val palette = listOf(
    Color(0xFFEF6C00), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFD81B60),
    Color(0xFF00897B), Color(0xFF43A047), Color(0xFF3949AB), Color(0xFF757575)
)
