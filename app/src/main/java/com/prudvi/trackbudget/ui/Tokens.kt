/* Hallmark · pre-emit critique: P5 H5 E4 S5 R4 V5 */
/* Hallmark · macrostructure: Receipt Ledger · tone: playful-deadpan · anchor hue: ultramarine */
package com.prudvi.trackbudget.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.model.AppThemePreference

@Immutable
data class ReceiptsColors(
    val paper: Color,
    val paperRaised: Color,
    val sunk: Color,
    val ink: Color,
    val inkSoft: Color,
    val fade: Color,
    val rule: Color,
    val ruleHard: Color,
    val ultramarine: Color,
    val ultramarineOn: Color,
    val ultramarineTint: Color,
    val chrome: Color,
    val chromeOn: Color,
    val chromeTint: Color,
    val chilli: Color,
    val chilliOn: Color,
    val chilliTint: Color,
    val scrim: Color,
    val stampVeil: Color,
    val categoryOnDark: Color,
    val isDark: Boolean,
)

private val LightColors = ReceiptsColors(
    paper = Color(0xFFE9E7DE),
    paperRaised = Color(0xFFE2DFD4),
    sunk = Color(0xFFDCD8CB),
    ink = Color(0xFF15181A),
    inkSoft = Color(0xFF3C4241),
    fade = Color(0xFF5F645C),
    rule = Color(0xFFCFCCBF),
    ruleHard = Color(0xFFB3AF9F),
    ultramarine = Color(0xFF2B3FD9),
    ultramarineOn = Color(0xFFFFFFFF),
    ultramarineTint = Color(0xFFDDE0F7),
    chrome = Color(0xFFF5C518),
    chromeOn = Color(0xFF15181A),
    chromeTint = Color(0xFFF6EBC4),
    chilli = Color(0xFFB92D19),
    chilliOn = Color(0xFFFFFFFF),
    chilliTint = Color(0xFFF7DDD7),
    scrim = Color(0xE6DCD8CB),
    stampVeil = Color(0xF5E9E7DE),
    categoryOnDark = Color(0xFFFFFFFF),
    isDark = false,
)

private val DarkColors = ReceiptsColors(
    paper = Color(0xFF131618),
    paperRaised = Color(0xFF1A1E20),
    sunk = Color(0xFF0D0F11),
    ink = Color(0xFFE7E5DC),
    inkSoft = Color(0xFFBEC0B7),
    fade = Color(0xFF8A8F87),
    rule = Color(0xFF2A2F31),
    ruleHard = Color(0xFF414749),
    ultramarine = Color(0xFF8B97FF),
    ultramarineOn = Color(0xFF0D0F11),
    ultramarineTint = Color(0xFF1E2440),
    chrome = Color(0xFFF2C630),
    chromeOn = Color(0xFF15181A),
    chromeTint = Color(0xFF332B10),
    chilli = Color(0xFFFF6B52),
    chilliOn = Color(0xFF15181A),
    chilliTint = Color(0xFF3A1D16),
    scrim = Color(0xE60D0F11),
    stampVeil = Color(0xF5131618),
    categoryOnDark = Color(0xFFFFFFFF),
    isDark = true,
)

val LocalReceiptsColors = staticCompositionLocalOf { LightColors }
val receiptsColors: ReceiptsColors
    @Composable get() = LocalReceiptsColors.current

object ReceiptsSpace {
    val none = 0.dp
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val screen = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x12 = 48.dp
    val x16 = 64.dp
}

object ReceiptsRadius {
    val none = 0.dp
    val small = 4.dp
    val pill = 99.dp
}

object ReceiptsMotion {
    const val KEY = 90
    const val TAB = 160
    const val SHEET = 280
    const val STAMP = 380
    const val HERO = 520
    const val GOAL = 600
    const val RAIL = 700
}

@OptIn(ExperimentalTextApi::class)
object ReceiptsFonts {
    val archivo = FontFamily(
        Font(
            resId = R.font.archivo_variable,
            weight = FontWeight.Black,
            variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.width(125f)),
        ),
        Font(
            resId = R.font.archivo_variable,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(118f)),
        ),
        Font(
            resId = R.font.archivo_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.width(105f)),
        ),
    )
    val instrumentSans = FontFamily(
        Font(
            resId = R.font.instrument_sans_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.instrument_sans_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
    )
    val splineSansMono = FontFamily(
        Font(
            resId = R.font.spline_sans_mono_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            resId = R.font.spline_sans_mono_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            resId = R.font.spline_sans_mono_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )
}

object ReceiptsType {
    val hero = TextStyle(fontFamily = ReceiptsFonts.archivo, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 48.sp, letterSpacing = (-1.4).sp)
    val display = TextStyle(fontFamily = ReceiptsFonts.archivo, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp)
    val title = TextStyle(fontFamily = ReceiptsFonts.archivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp)
    val heading = TextStyle(fontFamily = ReceiptsFonts.archivo, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = ReceiptsFonts.instrumentSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = ReceiptsFonts.instrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val amount = TextStyle(fontFamily = ReceiptsFonts.splineSansMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
    val meta = TextStyle(fontFamily = ReceiptsFonts.instrumentSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = ReceiptsFonts.splineSansMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.76.sp, fontFeatureSettings = "tnum")
    val stamp = TextStyle(fontFamily = ReceiptsFonts.splineSansMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.sp, fontFeatureSettings = "tnum")
}

private val ReceiptTypography = Typography(
    displayLarge = ReceiptsType.hero,
    displayMedium = ReceiptsType.display,
    headlineLarge = ReceiptsType.title,
    headlineMedium = ReceiptsType.heading,
    titleMedium = ReceiptsType.bodyStrong,
    bodyLarge = ReceiptsType.body,
    bodyMedium = ReceiptsType.meta,
    labelSmall = ReceiptsType.label,
)

private val ReceiptShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.small),
    small = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.small),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.small),
    large = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.small),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.small),
)

@Composable
fun ReceiptsTheme(
    preference: AppThemePreference = AppThemePreference.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = preference == AppThemePreference.DARK || preference == AppThemePreference.SYSTEM && isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.ultramarine,
            onPrimary = colors.ultramarineOn,
            secondary = colors.chrome,
            onSecondary = colors.chromeOn,
            error = colors.chilli,
            onError = colors.chilliOn,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paperRaised,
            onSurface = colors.ink,
            outline = colors.rule,
        )
    } else {
        lightColorScheme(
            primary = colors.ultramarine,
            onPrimary = colors.ultramarineOn,
            secondary = colors.chrome,
            onSecondary = colors.chromeOn,
            error = colors.chilli,
            onError = colors.chilliOn,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paperRaised,
            onSurface = colors.ink,
            outline = colors.rule,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalReceiptsColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = ReceiptTypography, shapes = ReceiptShapes, content = content)
    }
}

@Immutable
data class CategoryVisual(val mark: String, val color: Color)

private val CategoryColors = mapOf(
    "food" to CategoryVisual("FD", Color(0xFFD9822B)),
    "groceries" to CategoryVisual("GR", Color(0xFF3E7D5A)),
    "shopping" to CategoryVisual("SH", Color(0xFFB5478A)),
    "transport" to CategoryVisual("TR", Color(0xFF2F6FA8)),
    "bills" to CategoryVisual("BL", Color(0xFF7A5BC4)),
    "rent" to CategoryVisual("RN", Color(0xFFA8501F)),
    "entertainment" to CategoryVisual("EN", Color(0xFFC43D6B)),
    "subscription" to CategoryVisual("SU", Color(0xFF4E63C9)),
    "medical" to CategoryVisual("MD", Color(0xFFCC3A2E)),
    "travel" to CategoryVisual("TV", Color(0xFF258178)),
    "gaming" to CategoryVisual("GM", Color(0xFF5B4FC7)),
    "fitness" to CategoryVisual("FT", Color(0xFF4F8F3C)),
    "personal" to CategoryVisual("PR", Color(0xFFA9548F)),
    "services" to CategoryVisual("SV", Color(0xFF3D6E9C)),
    "smallshops" to CategoryVisual("SS", Color(0xFFA75F27)),
    "logistics" to CategoryVisual("LG", Color(0xFF357F92)),
    "insurance" to CategoryVisual("IS", Color(0xFF6A5FA8)),
    "pet" to CategoryVisual("PT", Color(0xFFC06A45)),
    "cash" to CategoryVisual("CA", Color(0xFF8A8A4A)),
    "misc" to CategoryVisual("MI", Color(0xFF7A8189)),
    "transfers" to CategoryVisual("TF", Color(0xFF5A626B)),
    "repayments" to CategoryVisual("RP", Color(0xFF2B7F6E)),
    "investment" to CategoryVisual("IV", Color(0xFF3F7A66)),
    "refund" to CategoryVisual("RF", Color(0xFF2B3FD9)),
    "income" to CategoryVisual("IN", Color(0xFF2B3FD9)),
)

fun categoryVisual(id: String?): CategoryVisual = CategoryColors[id] ?: CategoryVisual("??", Color(0xFF7A8189))

@Composable
fun categoryMarkTextColor(background: Color): Color {
    val darkInk = LightColors.ink
    val inkContrast = contrastRatio(background.luminance(), darkInk.luminance())
    val lightContrast = contrastRatio(background.luminance(), LightColors.categoryOnDark.luminance())
    return if (inkContrast >= lightContrast) darkInk else LightColors.categoryOnDark
}

private fun contrastRatio(first: Float, second: Float): Float =
    (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
