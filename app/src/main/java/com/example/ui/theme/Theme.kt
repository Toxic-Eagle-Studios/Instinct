package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val InstinctDarkColorScheme = darkColorScheme(
  primary = ElectricLime,
  onPrimary = OnAccent,
  primaryContainer = GraphiteCard,
  onPrimaryContainer = ElectricLime,
  secondary = CyanElectric,
  onSecondary = JetBlack,
  secondaryContainer = GraphiteSurface,
  onSecondaryContainer = CyanElectric,
  tertiary = ToxicGreenGlow,
  onTertiary = JetBlack,
  background = JetBlack,
  onBackground = TextPrimary,
  surface = DarkCharcoal,
  onSurface = TextPrimary,
  surfaceVariant = GraphiteSurface,
  onSurfaceVariant = TextSecondary,
  outline = GraphiteBorder,
  error = DeepRedWarning,
  onError = TextPrimary
)

private val InstinctLightColorScheme = lightColorScheme(
  primary = ElectricLimeDim,
  onPrimary = JetBlack,
  primaryContainer = Color(0xFFE8FDF0),
  onPrimaryContainer = Color(0xFF005228),
  secondary = Color(0xFF00838F),
  onSecondary = Color.White,
  background = Color(0xFFF6F8FA),
  onBackground = Color(0xFF1F2328),
  surface = Color.White,
  onSurface = Color(0xFF1F2328),
  surfaceVariant = Color(0xFFEAEFF5),
  onSurfaceVariant = Color(0xFF57606A),
  outline = Color(0xFFD0D7DE),
  error = DeepRedWarning
)

@Composable
fun InstinctTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) InstinctDarkColorScheme else InstinctLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
