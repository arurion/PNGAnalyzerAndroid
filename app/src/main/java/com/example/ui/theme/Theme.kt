package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GithubDarkAccent,
    onPrimary = GithubDarkBg,
    secondary = GithubDarkAccent,
    background = GithubDarkBg,
    onBackground = GithubDarkTextMain,
    surface = GithubDarkSurface,
    onSurface = GithubDarkTextMain,
    outline = GithubDarkBorder,
    error = GithubDarkDanger
)

private val LightColorScheme = lightColorScheme(
    primary = GithubLightAccent,
    onPrimary = Color.White,
    secondary = GithubLightAccent,
    background = GithubLightBg,
    onBackground = GithubLightTextMain,
    surface = GithubLightSurface,
    onSurface = GithubLightTextMain,
    outline = GithubLightBorder,
    error = GithubLightDanger
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
