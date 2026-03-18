package com.yi.actioncamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class AppTheme {
    WHITE, BLACK, GREEN
}

private val WhiteColorScheme = lightColorScheme(
    primary = WhitePrimary,
    onPrimary = WhiteOnPrimary,
    surface = WhiteSurface,
    onSurface = WhiteOnSurface,
    background = WhiteBackground,
    onBackground = WhiteOnBackground,
    primaryContainer = WhitePrimaryContainer,
    onPrimaryContainer = WhiteOnPrimaryContainer,
    secondaryContainer = WhiteSecondaryContainer,
    onSecondaryContainer = WhiteOnSecondaryContainer
)

private val BlackColorScheme = darkColorScheme(
    primary = BlackPrimary,
    onPrimary = BlackOnPrimary,
    surface = BlackSurface,
    onSurface = BlackOnSurface,
    background = BlackBackground,
    onBackground = BlackOnBackground,
    primaryContainer = BlackPrimaryContainer,
    onPrimaryContainer = BlackOnPrimaryContainer,
    secondaryContainer = BlackSecondaryContainer,
    onSecondaryContainer = BlackOnSecondaryContainer
)

private val GreenColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    surface = GreenSurface,
    onSurface = GreenOnSurface,
    background = GreenBackground,
    onBackground = GreenOnBackground,
    primaryContainer = GreenPrimaryContainer,
    onPrimaryContainer = GreenOnPrimaryContainer,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = GreenOnSecondaryContainer
)

@Composable
fun YiPilotTheme(
    appTheme: AppTheme = AppTheme.WHITE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.WHITE -> WhiteColorScheme
        AppTheme.BLACK -> BlackColorScheme
        AppTheme.GREEN -> GreenColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
