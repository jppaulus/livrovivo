package com.livrovivo.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta "Livro Encantado" (Quente, Lúdica, Acolhedora e Mágica)
val FairyPurple = Color(0xFF5E49E2)           // Roxo Mágico Crepúsculo
val FairyPurpleLight = Color(0xFFEDE9FE)      // Névoa Suave Lilás
val FairyGold = Color(0xFFFFB300)             // Dourado Brilho de Estrela
val FairyGoldLight = Color(0xFFFFF3D6)        // Dourado Papiro
val FairyParchment = Color(0xFFFFFDF7)        // Textura Livro Antigo Quentinho
val FairyCardBg = Color(0xFFFFFFFF)           // Branco Livro Encantado
val FairyEmerald = Color(0xFF10B981)          // Floresta Mágica
val FairyCoral = Color(0xFFFF6584)            // Rosa Doçura
val FairyNight = Color(0xFF181528)            // Noite Estrelada
val FairyNightSurface = Color(0xFF26213D)     // Superfície Noturna Macia

val StoryTextPrimary = Color(0xFF231F32)      // Tinta Livro Escura Acolhedora
val StoryTextSecondary = Color(0xFF5D5775)    // Tinta Livro Suave

private val LightColorScheme = lightColorScheme(
    primary = FairyPurple,
    onPrimary = Color.White,
    primaryContainer = FairyPurpleLight,
    onPrimaryContainer = Color(0xFF2C1985),
    secondary = FairyGold,
    onSecondary = Color(0xFF332000),
    secondaryContainer = FairyGoldLight,
    onSecondaryContainer = Color(0xFF5A3A00),
    background = FairyParchment,
    surface = FairyCardBg,
    onBackground = StoryTextPrimary,
    onSurface = StoryTextPrimary,
    tertiary = FairyCoral,
    tertiaryContainer = Color(0xFFFFEEF1)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1F1254),
    primaryContainer = Color(0xFF3B2F7A),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = FairyGold,
    onSecondary = Color(0xFF332000),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE8B3),
    tertiary = Color(0xFFFF8FA6),
    tertiaryContainer = Color(0xFF5C2333),
    onTertiaryContainer = Color(0xFFFFD9E0),
    background = FairyNight,
    surface = FairyNightSurface,
    surfaceVariant = Color(0xFF332C52),
    onSurfaceVariant = Color(0xFFD6D0EA),
    onBackground = Color(0xFFF5F3FF),
    onSurface = Color(0xFFF5F3FF)
)

val StoryTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    )
)

val StoryShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

@Composable
fun LivroVivoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StoryTypography,
        shapes = StoryShapes,
        content = content
    )
}
