package com.yy.lottosim.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 体彩红 + 奖金金 品牌配色
private val LightColors = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    onPrimaryContainer = Color(0xFF7F0000),
    secondary = Color(0xFFF9A825),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFFFFF8E1),
    onSecondaryContainer = Color(0xFF57441A),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFFDF8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5EDEC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEF9A9A),
    onPrimary = Color(0xFF5F0000),
    primaryContainer = Color(0xFF8E0000),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF3E2E00),
    background = Color(0xFF1A1211),
    surface = Color(0xFF211817),
    surfaceVariant = Color(0xFF2C2221)
)

@Composable
fun LottoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
