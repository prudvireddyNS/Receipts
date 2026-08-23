/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V5 */
/* Hallmark · macrostructure: Workbench · tone: austere · anchor hue: brass */
package com.prudvi.trackbudget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.prudvi.trackbudget.R
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF101820)
val Surface = Color(0xFF161E27)
val SurfaceHigh = Color(0xFF1A222D)
val Rule = Color(0xFF202A36)
val RuleStrong = Color(0xFF2A3441)
val TextPrimary = Color(0xFFE8EBEE)
val TextSecondary = Color(0xFF94A3B4)
val TextMuted = Color(0xFF5F6E7E)
val Brass = Color(0xFFD9A63C)
val BrassDim = Color(0xFF2A2115)
val Clay = Color(0xFFE0705A)
val ClayDim = Color(0xFF241813)
val Mint = Color(0xFF3FA894)
val MintDim = Color(0xFF12211D)

val Inter = FontFamily(Font(R.font.inter_variable))
val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk_variable))

private val TrackColors = darkColorScheme(
    primary = Brass,
    onPrimary = Ink,
    primaryContainer = BrassDim,
    onPrimaryContainer = Brass,
    secondary = Mint,
    onSecondary = Ink,
    error = Clay,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Rule,
)

@Composable
fun TrackBudgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrackColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 49.sp),
            headlineLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 35.sp),
            headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
            titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
            labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.7.sp),
        ),
        content = content,
    )
}
