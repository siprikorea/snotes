package com.siprikorea.snotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.siprikorea.snotes.resources.Res
import com.siprikorea.snotes.resources.pretendard_regular
import org.jetbrains.compose.resources.Font

/** Notion 과 비슷한 차분한 회색 톤. */
@Immutable
data class NoteColors(
    val sidebar: Color,
    val hover: Color,
    val selected: Color,
    val text: Color,
    val secondaryText: Color,
    val faint: Color,
    val divider: Color,
    val calloutBg: Color,
    val codeBg: Color,
    val inlineCode: Color,
    val link: Color,
    val accent: Color,
)

private val LightNoteColors = NoteColors(
    sidebar = Color(0xFFF7F7F5),
    hover = Color(0x0F37352F),
    selected = Color(0x1A37352F),
    text = Color(0xFF37352F),
    secondaryText = Color(0xFF787774),
    faint = Color(0xFFB4B4B0),
    divider = Color(0xFFE9E9E7),
    calloutBg = Color(0xFFF1F1EF),
    codeBg = Color(0xFFF7F6F3),
    inlineCode = Color(0xFFEB5757),
    link = Color(0xFF2383E2),
    accent = Color(0xFF2383E2),
)

private val DarkNoteColors = NoteColors(
    sidebar = Color(0xFF202020),
    hover = Color(0x14FFFFFF),
    selected = Color(0x22FFFFFF),
    text = Color(0xFFE6E6E5),
    secondaryText = Color(0xFF9B9B9B),
    faint = Color(0xFF5A5A5A),
    divider = Color(0xFF2F2F2F),
    calloutBg = Color(0xFF2A2A2A),
    codeBg = Color(0xFF262626),
    inlineCode = Color(0xFFFF7369),
    link = Color(0xFF529CCA),
    accent = Color(0xFF529CCA),
)

val LocalNoteColors = staticCompositionLocalOf { LightNoteColors }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2383E2),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF37352F),
    surface = Color.White,
    onSurface = Color(0xFF37352F),
    surfaceVariant = Color(0xFFF7F7F5),
    onSurfaceVariant = Color(0xFF787774),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF1F1EF),
    outline = Color(0xFFD3D1CB),
    outlineVariant = Color(0xFFE9E9E7),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF529CCA),
    onPrimary = Color.White,
    background = Color(0xFF191919),
    onBackground = Color(0xFFE6E6E5),
    surface = Color(0xFF191919),
    onSurface = Color(0xFFE6E6E5),
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF9B9B9B),
    surfaceContainer = Color(0xFF252525),
    surfaceContainerLow = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF252525),
    surfaceContainerHighest = Color(0xFF2F2F2F),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2F2F2F),
)

@Composable
private fun typography(): Typography {
    // Pretendard 는 한글을 포함한다(웹은 시스템 폰트가 없어 반드시 필요). 굵기는 합성한다.
    val family = FontFamily(Font(Res.font.pretendard_regular, FontWeight.Normal))
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
}

@Composable
fun SNotesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalNoteColors provides if (dark) DarkNoteColors else LightNoteColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = typography(),
            content = content,
        )
    }
}

val noteColors: NoteColors @Composable get() = LocalNoteColors.current
