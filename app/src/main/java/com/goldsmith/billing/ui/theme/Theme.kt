package com.goldsmith.billing.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Aura Lumina Color Palette ────────────────────────────────────────────────
object AuraColors {
    // Surfaces
    var Surface by mutableStateOf(Color(0xFF131313))
    var SurfaceDim by mutableStateOf(Color(0xFF131313))
    var SurfaceBright by mutableStateOf(Color(0xFF393939))
    var SurfaceContainerLowest by mutableStateOf(Color(0xFF0E0E0E))
    var SurfaceContainerLow by mutableStateOf(Color(0xFF1C1B1B))
    var SurfaceContainer by mutableStateOf(Color(0xFF201F1F))
    var SurfaceContainerHigh by mutableStateOf(Color(0xFF2A2A2A))
    var SurfaceContainerHighest by mutableStateOf(Color(0xFF353534))
    var OnSurface by mutableStateOf(Color(0xFFE5E2E1))
    var OnSurfaceVariant by mutableStateOf(Color(0xFFD0C5AF))

    // Primary (Gold)
    var Primary by mutableStateOf(Color(0xFFF2CA50))
    var PrimaryContainer by mutableStateOf(Color(0xFFD4AF37))
    var OnPrimary by mutableStateOf(Color(0xFF3C2F00))
    var OnPrimaryContainer by mutableStateOf(Color(0xFF554300))
    var PrimaryFixedDim by mutableStateOf(Color(0xFFE9C349))

    // Secondary
    var Secondary by mutableStateOf(Color(0xFFC8C6C5))
    var SecondaryContainer by mutableStateOf(Color(0xFF474746))
    var OnSecondary by mutableStateOf(Color(0xFF313030))
    var OnSecondaryContainer by mutableStateOf(Color(0xFFB7B5B4))

    // Outline
    var Outline by mutableStateOf(Color(0xFF99907C))
    var OutlineVariant by mutableStateOf(Color(0xFF4D4635))

    // Error
    var Error by mutableStateOf(Color(0xFFFFB4AB))
    var ErrorContainer by mutableStateOf(Color(0xFF93000A))
    var OnError by mutableStateOf(Color(0xFF690005))

    // Background
    var Background by mutableStateOf(Color(0xFF131313))
    var OnBackground by mutableStateOf(Color(0xFFE5E2E1))

    // Glass overlays
    var GlassWhite5 by mutableStateOf(Color(0x0DFFFFFF))
    var GlassWhite10 by mutableStateOf(Color(0x1AFFFFFF))
    var GlassWhite12 by mutableStateOf(Color(0x1FFFFFFF))
    var GlassWhite20 by mutableStateOf(Color(0x33FFFFFF))
    var GlassBorder by mutableStateOf(Color(0x1AFFFFFF))

    // Gold glow
    var GoldGlow by mutableStateOf(Color(0x33D4AF37))

    fun apply(dark: Boolean) {
        if (dark) {
            Surface = Color(0xFF131313); SurfaceDim = Color(0xFF131313); SurfaceBright = Color(0xFF393939)
            SurfaceContainerLowest = Color(0xFF0E0E0E); SurfaceContainerLow = Color(0xFF1C1B1B)
            SurfaceContainer = Color(0xFF201F1F); SurfaceContainerHigh = Color(0xFF2A2A2A); SurfaceContainerHighest = Color(0xFF353534)
            OnSurface = Color(0xFFE5E2E1); OnSurfaceVariant = Color(0xFFD0C5AF)
            Primary = Color(0xFFF2CA50); PrimaryContainer = Color(0xFFD4AF37); OnPrimary = Color(0xFF3C2F00); OnPrimaryContainer = Color(0xFF554300)
            Secondary = Color(0xFFC8C6C5); SecondaryContainer = Color(0xFF474746); OnSecondary = Color(0xFF313030); OnSecondaryContainer = Color(0xFFB7B5B4)
            Outline = Color(0xFF99907C); OutlineVariant = Color(0xFF4D4635); Error = Color(0xFFFFB4AB); ErrorContainer = Color(0xFF93000A); OnError = Color(0xFF690005)
            Background = Color(0xFF131313); OnBackground = Color(0xFFE5E2E1)
            GlassWhite5 = Color(0x0DFFFFFF); GlassWhite10 = Color(0x1AFFFFFF); GlassWhite12 = Color(0x1FFFFFFF); GlassWhite20 = Color(0x33FFFFFF); GlassBorder = Color(0x1AFFFFFF)
            GoldGlow = Color(0x33D4AF37); PrimaryFixedDim = Color(0xFFE9C349)
        } else {
            Surface = Color(0xFFFFFBF2); SurfaceDim = Color(0xFFE8E1D2); SurfaceBright = Color(0xFFFFFFFF)
            SurfaceContainerLowest = Color(0xFFFFFFFF); SurfaceContainerLow = Color(0xFFFFF8EA)
            SurfaceContainer = Color(0xFFF8F0DF); SurfaceContainerHigh = Color(0xFFF1E7D4); SurfaceContainerHighest = Color(0xFFE8DCC5)
            OnSurface = Color(0xFF211B10); OnSurfaceVariant = Color(0xFF6E6147)
            Primary = Color(0xFF8B6F00); PrimaryContainer = Color(0xFFC49A20); OnPrimary = Color(0xFFFFFFFF); OnPrimaryContainer = Color(0xFF2B2100)
            Secondary = Color(0xFF625B49); SecondaryContainer = Color(0xFFE9DFC8); OnSecondary = Color(0xFFFFFFFF); OnSecondaryContainer = Color(0xFF1F1B12)
            Outline = Color(0xFF8D8067); OutlineVariant = Color(0xFFD3C6AA); Error = Color(0xFFBA1A1A); ErrorContainer = Color(0xFFFFDAD6); OnError = Color(0xFFFFFFFF)
            Background = Color(0xFFFFFBF2); OnBackground = Color(0xFF211B10)
            GlassWhite5 = Color(0x99FFFFFF); GlassWhite10 = Color(0xFFEFE5D1); GlassWhite12 = Color(0xFFF6EBD6); GlassWhite20 = Color(0xFFD9C9A9); GlassBorder = Color(0xFFD7C7A8)
            GoldGlow = Color(0x44C49A20); PrimaryFixedDim = Color(0xFFC49A20)
        }
    }
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
    SideEffect { AuraColors.apply(darkTheme) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GoldsmithTypography,
        content = content
    )
}
