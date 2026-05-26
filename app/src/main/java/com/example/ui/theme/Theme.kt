package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MusicTheme(
    themeOption: ThemeColorOption = ThemeColorOption.DARK_SLATE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeOption) {
        ThemeColorOption.DARK_SLATE -> darkColorScheme(
            primary = CarbonPrimary,
            secondary = CarbonSecondary,
            background = CarbonBackground,
            surface = CarbonSurface,
            onPrimary = CarbonOnPrimary,
            onBackground = CarbonOnPrimary,
            onSurface = CarbonOnPrimary,
            primaryContainer = CarbonSecondary,
            surfaceVariant = CarbonSecondary
        )
        ThemeColorOption.DEEP_BLUE -> darkColorScheme(
            primary = OceanPrimary,
            secondary = OceanSecondary,
            background = OceanBackground,
            surface = OceanSurface,
            onPrimary = OceanOnPrimary,
            onBackground = OceanOnPrimary,
            onSurface = OceanOnPrimary,
            primaryContainer = OceanSecondary,
            surfaceVariant = OceanSecondary
        )
        ThemeColorOption.EMERALD -> darkColorScheme(
            primary = EmeraldPrimary,
            secondary = EmeraldSecondary,
            background = EmeraldBackground,
            surface = EmeraldSurface,
            onPrimary = EmeraldOnPrimary,
            onBackground = EmeraldOnPrimary,
            onSurface = EmeraldOnPrimary,
            primaryContainer = EmeraldSecondary,
            surfaceVariant = EmeraldSecondary
        )
        ThemeColorOption.AMBER -> darkColorScheme(
            primary = AmberPrimary,
            secondary = AmberSecondary,
            background = AmberBackground,
            surface = AmberSurface,
            onPrimary = AmberOnPrimary,
            onBackground = AmberOnPrimary,
            onSurface = AmberOnPrimary,
            primaryContainer = AmberSecondary,
            surfaceVariant = AmberSecondary
        )
        ThemeColorOption.COSMIC -> darkColorScheme(
            primary = CosmicPrimary,
            secondary = CosmicSecondary,
            background = CosmicBackground,
            surface = CosmicSurface,
            onPrimary = CosmicOnPrimary,
            onBackground = CosmicOnPrimary,
            onSurface = CosmicOnPrimary,
            primaryContainer = CosmicSecondary,
            surfaceVariant = CosmicSecondary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
