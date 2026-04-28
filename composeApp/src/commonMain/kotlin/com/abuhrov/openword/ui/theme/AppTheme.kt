package com.abuhrov.openword.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

@Composable
fun AppTheme(
    fontSizeScale: Float,
    currentFont: FontFamily?,
    content: @Composable () -> Unit
) {
    val appTypography = remember(currentFont, fontSizeScale) {
        Typography().run {
            val fontFamily = currentFont ?: FontFamily.Default
        copy(
            displayLarge = displayLarge.copy(fontFamily = fontFamily, fontSize = displayLarge.fontSize * fontSizeScale),
            displayMedium = displayMedium.copy(fontFamily = fontFamily, fontSize = displayMedium.fontSize * fontSizeScale),
            displaySmall = displaySmall.copy(fontFamily = fontFamily, fontSize = displaySmall.fontSize * fontSizeScale),
            headlineLarge = headlineLarge.copy(fontFamily = fontFamily, fontSize = headlineLarge.fontSize * fontSizeScale),
            headlineMedium = headlineMedium.copy(fontFamily = fontFamily, fontSize = headlineMedium.fontSize * fontSizeScale),
            headlineSmall = headlineSmall.copy(fontFamily = fontFamily, fontSize = headlineSmall.fontSize * fontSizeScale),
            titleLarge = titleLarge.copy(fontFamily = fontFamily, fontSize = titleLarge.fontSize * fontSizeScale),
            titleMedium = titleMedium.copy(fontFamily = fontFamily, fontSize = titleMedium.fontSize * fontSizeScale),
            titleSmall = titleSmall.copy(fontFamily = fontFamily, fontSize = titleSmall.fontSize * fontSizeScale),
            bodyLarge = bodyLarge.copy(fontFamily = fontFamily, fontSize = bodyLarge.fontSize * fontSizeScale),
            bodyMedium = bodyMedium.copy(fontFamily = fontFamily, fontSize = bodyMedium.fontSize * fontSizeScale),
            bodySmall = bodySmall.copy(fontFamily = fontFamily, fontSize = bodySmall.fontSize * fontSizeScale),
            labelLarge = labelLarge.copy(fontFamily = fontFamily, fontSize = labelLarge.fontSize * fontSizeScale),
            labelMedium = labelMedium.copy(fontFamily = fontFamily, fontSize = labelMedium.fontSize * fontSizeScale),
            labelSmall = labelSmall.copy(fontFamily = fontFamily, fontSize = labelSmall.fontSize * fontSizeScale)
        )
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Primary,
            onPrimary = AppColors.OnPrimary,
            primaryContainer = AppColors.PrimaryContainer,
            background = AppColors.Background
        ),
        typography = appTypography,
        content = content
    )
}
