package com.example.salaryapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SalaryTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme())
        darkColorScheme(primary = Color(0xFF8AB4FF), primaryContainer = Color(0xFF123A70))
    else
        lightColorScheme(primary = Color(0xFF0B2E59), primaryContainer = Color(0xFFD6E4FA))
    MaterialTheme(colorScheme = colors, content = content)
}
