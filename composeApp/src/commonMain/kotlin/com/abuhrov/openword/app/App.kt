package com.abuhrov.openword.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.abuhrov.openword.app.navigation.Route
import com.abuhrov.openword.home.presentation.HomeScreen
import com.abuhrov.openword.loadAppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var currentFont by remember { mutableStateOf<FontFamily?>(null) }

    LaunchedEffect(Unit) {
        currentFont = loadAppFont()
    }

    val appTypography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = currentFont),
            displayMedium = displayMedium.copy(fontFamily = currentFont),
            displaySmall = displaySmall.copy(fontFamily = currentFont),
            headlineLarge = headlineLarge.copy(fontFamily = currentFont),
            headlineMedium = headlineMedium.copy(fontFamily = currentFont),
            headlineSmall = headlineSmall.copy(fontFamily = currentFont),
            titleLarge = titleLarge.copy(fontFamily = currentFont),
            titleMedium = titleMedium.copy(fontFamily = currentFont),
            titleSmall = titleSmall.copy(fontFamily = currentFont),
            bodyLarge = bodyLarge.copy(fontFamily = currentFont),
            bodyMedium = bodyMedium.copy(fontFamily = currentFont),
            bodySmall = bodySmall.copy(fontFamily = currentFont),
            labelLarge = labelLarge.copy(fontFamily = currentFont),
            labelMedium = labelMedium.copy(fontFamily = currentFont),
            labelSmall = labelSmall.copy(fontFamily = currentFont)
        )
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF5D4037),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD7CCC8),
            background = Color(0xFFF5F5F5)
        ),
        typography = appTypography
    ) {
        val navController = rememberNavController()

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.Home
            ) {
                composable<Route.Home> {
                    HomeScreen()
                }
            }
        }
    }
}