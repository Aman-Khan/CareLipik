package com.carelipik.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = CareBackgroundDark,
    primaryContainer = CarePrimaryContainerDark,
    onPrimaryContainer = CareOnPrimaryContainerDark,
    secondary = Slate80,
    onSecondary = CareBackgroundDark,
    secondaryContainer = CareSecondaryContainerDark,
    onSecondaryContainer = CareOnSecondaryContainerDark,
    tertiary = Coral80,
    onTertiary = CareBackgroundDark,
    tertiaryContainer = CareTertiaryContainerDark,
    onTertiaryContainer = CareOnTertiaryContainerDark,
    background = CareBackgroundDark,
    onBackground = CareOnSurfaceDark,
    surface = CareSurfaceDark,
    onSurface = CareOnSurfaceDark,
    surfaceVariant = CareSurfaceVariantDark,
    onSurfaceVariant = CareOnSurfaceVariantDark,
    outline = CareOutlineDark,
    outlineVariant = CareOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = CareSurfaceLight,
    primaryContainer = CarePrimaryContainerLight,
    onPrimaryContainer = CareOnPrimaryContainerLight,
    secondary = Slate40,
    onSecondary = CareSurfaceLight,
    secondaryContainer = CareSecondaryContainerLight,
    onSecondaryContainer = CareOnSecondaryContainerLight,
    tertiary = Coral40,
    onTertiary = CareSurfaceLight,
    tertiaryContainer = CareTertiaryContainerLight,
    onTertiaryContainer = CareOnTertiaryContainerLight,
    background = CareBackgroundLight,
    onBackground = CareOnSurfaceLight,
    surface = CareSurfaceLight,
    onSurface = CareOnSurfaceLight,
    surfaceVariant = CareSurfaceVariantLight,
    onSurfaceVariant = CareOnSurfaceVariantLight,
    outline = CareOutlineLight,
    outlineVariant = CareOutlineVariantLight
)

@Composable
fun CareLipikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
