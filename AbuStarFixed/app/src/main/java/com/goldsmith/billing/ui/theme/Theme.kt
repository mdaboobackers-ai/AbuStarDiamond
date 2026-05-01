package com.goldsmith.billing.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Aura Lumina Color Palette ────────────────────────────────────────────────
object AuraColors {
    // Surfaces
    val Surface              = Color(0xFF131313)
    val SurfaceDim           = Color(0xFF131313)
    val SurfaceBright        = Color(0xFF393939)
    val SurfaceContainerLowest = Color(0xFF0E0E0E)
    val SurfaceContainerLow  = Color(0xFF1C1B1B)
    val SurfaceContainer     = Color(0xFF201F1F)
    val SurfaceContainerHigh = Color(0xFF2A2A2A)
    val SurfaceContainerHighest = Color(0xFF353534)
    val OnSurface            = Color(0xFFE5E2E1)
    val OnSurfaceVariant     = Color(0xFFD0C5AF)

    // Primary (Gold)
    val Primary              = Color(0xFFF2CA50)
    val PrimaryContainer     = Color(0xFFD4AF37)
    val OnPrimary            = Color(0xFF3C2F00)
    val OnPrimaryContainer   = Color(0xFF554300)
    val PrimaryFixedDim      = Color(0xFFE9C349)

    // Secondary
    val Secondary            = Color(0xFFC8C6C5)
    val SecondaryContainer   = Color(0xFF474746)
    val OnSecondary          = Color(0xFF313030)
    val OnSecondaryContainer = Color(0xFFB7B5B4)

    // Outline
    val Outline              = Color(0xFF99907C)
    val OutlineVariant       = Color(0xFF4D4635)

    // Error
    val Error                = Color(0xFFFFB4AB)
    val ErrorContainer       = Color(0xFF93000A)
    val OnError              = Color(0xFF690005)

    // Background
    val Background           = Color(0xFF131313)
    val OnBackground         = Color(0xFFE5E2E1)

    // Glass overlays
    val GlassWhite5          = Color(0x0DFFFFFF)
    val GlassWhite10         = Color(0x1AFFFFFF)
    val GlassWhite12         = Color(0x1FFFFFFF)
    val GlassWhite20         = Color(0x33FFFFFF)
    val GlassBorder          = Color(0x1AFFFFFF)

    // Gold glow
    val GoldGlow             = Color(0x33D4AF37)
}

// ─── Typography ───────────────────────────────────────────────────────────────
val GoldsmithTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.96).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.32).sp
    )
)

// ─── Material3 Dark Color Scheme ──────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary             = AuraColors.Primary,
    onPrimary           = AuraColors.OnPrimary,
    primaryContainer    = AuraColors.PrimaryContainer,
    onPrimaryContainer  = AuraColors.OnPrimaryContainer,
    secondary           = AuraColors.Secondary,
    onSecondary         = AuraColors.OnSecondary,
    secondaryContainer  = AuraColors.SecondaryContainer,
    onSecondaryContainer = AuraColors.OnSecondaryContainer,
    surface             = AuraColors.Surface,
    onSurface           = AuraColors.OnSurface,
    onSurfaceVariant    = AuraColors.OnSurfaceVariant,
    surfaceVariant      = AuraColors.SurfaceContainerHighest,
    background          = AuraColors.Background,
    onBackground        = AuraColors.OnBackground,
    error               = AuraColors.Error,
    onError             = AuraColors.OnError,
    errorContainer      = AuraColors.ErrorContainer,
    outline             = AuraColors.Outline,
    outlineVariant      = AuraColors.OutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary             = Color(0xFF735C00),
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0xFFFFE088),
    onPrimaryContainer  = Color(0xFF241A00),
    background          = Color(0xFFF8F6F0),
    onBackground        = Color(0xFF1C1B1B),
    surface             = Color(0xFFF8F6F0),
    onSurface           = Color(0xFF1C1B1B)
)

@Composable
fun GoldsmithBillingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GoldsmithTypography,
        content = content
    )
}
