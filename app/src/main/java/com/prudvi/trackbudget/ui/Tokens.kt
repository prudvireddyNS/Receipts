package com.prudvi.trackbudget.ui

import android.app.Activity
import android.os.Build
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
import androidx.compose.ui.graphics.lerp
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
    val warm: Color,
    val cyan: Color,
    val yellow: Color,
    val purple: Color,
    val pink: Color,
    val mint: Color,
    val ink: Color,
    val inkSoft: Color,
    val fade: Color,
    val pinkTint: Color,
    val mintTint: Color,
    val paperRaised: Color,
    val sunk: Color,
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
    /** True for the strict black/white/gray Light and Dark modes — category colors desaturate to gray. */
    val monochrome: Boolean = false,
)

/** "Colorful" — the signature vibrant Receipts look. Default theme. */
private val ColorfulPalette = ReceiptsColors(
    paper = Color(0xFFFFFDF6),
    warm = Color(0xFFFFF3DE),
    cyan = Color(0xFFDFF7F9),
    yellow = Color(0xFFFFD23F),
    purple = Color(0xFF7B4DFF),
    pink = Color(0xFFFF3D7F),
    mint = Color(0xFF2FD18C),
    ink = Color(0xFF14121F),
    inkSoft = Color(0xFF3A3550),
    fade = Color(0xFF6E6880),
    pinkTint = Color(0xFFFFE7EF),
    mintTint = Color(0xFFE7F9EF),
    paperRaised = Color(0xFFFFFDF6),
    sunk = Color(0xFFFFF3DE),
    rule = Color(0xFF14121F),
    ruleHard = Color(0xFF14121F),
    ultramarine = Color(0xFF7B4DFF),
    ultramarineOn = Color(0xFFFFF7EC),
    ultramarineTint = Color(0xFFF0EBFF),
    chrome = Color(0xFFFFD23F),
    chromeOn = Color(0xFF14121F),
    chromeTint = Color(0xFFFFF3DE),
    chilli = Color(0xFFFF3D7F),
    chilliOn = Color(0xFFFFF7EC),
    chilliTint = Color(0xFFFFE7EF),
    scrim = Color(0xB314121F),
    stampVeil = Color(0xF5FFFDF6),
    categoryOnDark = Color(0xFFFFF7EC),
    isDark = false,
)

/** "Subtle" — the same warm paper receipt look, with every accent turned down to a quiet, muted tone. */
private val SubtlePalette = ReceiptsColors(
    paper = Color(0xFFFAF8F3),
    warm = Color(0xFFF1ECE1),
    cyan = Color(0xFFE7EEEC),
    yellow = Color(0xFFE4C77E),
    purple = Color(0xFF8E82AE),
    pink = Color(0xFFC98999),
    mint = Color(0xFF7FAB97),
    ink = Color(0xFF2E2B36),
    inkSoft = Color(0xFF54506A),
    fade = Color(0xFF8B8696),
    pinkTint = Color(0xFFF1E3E6),
    mintTint = Color(0xFFE9EFE9),
    paperRaised = Color(0xFFFAF8F3),
    sunk = Color(0xFFF1ECE1),
    rule = Color(0xFF2E2B36),
    ruleHard = Color(0xFF2E2B36),
    ultramarine = Color(0xFF8E82AE),
    ultramarineOn = Color(0xFFFAF8F3),
    ultramarineTint = Color(0xFFE9E5F0),
    chrome = Color(0xFFE4C77E),
    chromeOn = Color(0xFF2E2B36),
    chromeTint = Color(0xFFF1ECE1),
    chilli = Color(0xFFC98999),
    chilliOn = Color(0xFFFAF8F3),
    chilliTint = Color(0xFFF1E3E6),
    scrim = Color(0xB32E2B36),
    stampVeil = Color(0xF5FAF8F3),
    categoryOnDark = Color(0xFFFAF8F3),
    isDark = false,
)

/** Strict black/white/gray, light background. No hue anywhere — hierarchy comes from value + weight. */
private val LightPalette = ReceiptsColors(
    paper = Color(0xFFFFFFFF),
    warm = Color(0xFFF1F1F1),
    cyan = Color(0xFFEDEDED),
    yellow = Color(0xFF383838),
    purple = Color(0xFF666666),
    pink = Color(0xFF4D4D4D),
    mint = Color(0xFF999999),
    ink = Color(0xFF121212),
    inkSoft = Color(0xFF3E3E3E),
    fade = Color(0xFF6E6E6E),
    pinkTint = Color(0xFFEEEEEE),
    mintTint = Color(0xFFF2F2F2),
    paperRaised = Color(0xFFFFFFFF),
    sunk = Color(0xFFF1F1F1),
    rule = Color(0xFF121212),
    ruleHard = Color(0xFF121212),
    ultramarine = Color(0xFF666666),
    ultramarineOn = Color(0xFFFFFFFF),
    ultramarineTint = Color(0xFFECECEC),
    chrome = Color(0xFF383838),
    chromeOn = Color(0xFFFFFFFF),
    chromeTint = Color(0xFFECECEC),
    chilli = Color(0xFF4D4D4D),
    chilliOn = Color(0xFFFFFFFF),
    chilliTint = Color(0xFFEEEEEE),
    scrim = Color(0xB3000000),
    stampVeil = Color(0xF5FFFFFF),
    categoryOnDark = Color(0xFFFFFFFF),
    isDark = false,
    monochrome = true,
)

/** Strict black/white/gray, dark background — the mirror of [LightPalette]. */
private val DarkPalette = ReceiptsColors(
    paper = Color(0xFF121212),
    warm = Color(0xFF1D1D1D),
    cyan = Color(0xFF1A1A1A),
    yellow = Color(0xFFC7C7C7),
    purple = Color(0xFF999999),
    pink = Color(0xFFB2B2B2),
    mint = Color(0xFF666666),
    ink = Color(0xFFF2F2F2),
    inkSoft = Color(0xFFC9C9C9),
    fade = Color(0xFF8F8F8F),
    pinkTint = Color(0xFF242424),
    mintTint = Color(0xFF1E1E1E),
    paperRaised = Color(0xFF121212),
    sunk = Color(0xFF1D1D1D),
    rule = Color(0xFFF2F2F2),
    ruleHard = Color(0xFFF2F2F2),
    ultramarine = Color(0xFF999999),
    ultramarineOn = Color(0xFF121212),
    ultramarineTint = Color(0xFF202020),
    chrome = Color(0xFFC7C7C7),
    chromeOn = Color(0xFF121212),
    chromeTint = Color(0xFF202020),
    chilli = Color(0xFFB2B2B2),
    chilliOn = Color(0xFF121212),
    chilliTint = Color(0xFF242424),
    scrim = Color(0xCC000000),
    stampVeil = Color(0xF5121212),
    categoryOnDark = Color(0xFFF2F2F2),
    isDark = true,
    monochrome = true,
)

val LocalReceiptsColors = staticCompositionLocalOf { ColorfulPalette }
val receiptsColors: ReceiptsColors
    @Composable get() = LocalReceiptsColors.current

object ReceiptsSpace {
    val none = 0.dp
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val screen = 20.dp
    val x8 = 32.dp
    val x12 = 48.dp
}

object ReceiptsRadius {
    val none = 0.dp
    val tiny = 8.dp
    val small = 10.dp
    val medium = 14.dp
    val card = 16.dp
    val large = 20.dp
    val hero = 26.dp
    val pill = 99.dp
}

object ReceiptsStroke {
    val width = 2.5.dp
}

object ReceiptsMotion {
    const val KEY = 110
    const val TAB = 340
    const val SHEET = 420
    const val ENTER = 460
    const val STAGGER = 55
    const val HERO = 700
}

@OptIn(ExperimentalTextApi::class)
object ReceiptsFonts {
    val display = FontFamily(
        Font(R.font.bricolage_grotesque_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.bricolage_grotesque_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.bricolage_grotesque_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )
    val body = FontFamily(
        Font(R.font.outfit_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.outfit_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.outfit_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val mono = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )

    val instrumentSans = body
    val splineSansMono = mono
}

object ReceiptsType {
    val hero = TextStyle(fontFamily = ReceiptsFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-2).sp)
    val recapHero = TextStyle(fontFamily = ReceiptsFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 54.sp, letterSpacing = (-2.2).sp)
    val display = TextStyle(fontFamily = ReceiptsFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = ReceiptsFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp)
    val heading = TextStyle(fontFamily = ReceiptsFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp)
    val button = TextStyle(fontFamily = ReceiptsFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp)
    val body = TextStyle(fontFamily = ReceiptsFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val bodyStrong = TextStyle(fontFamily = ReceiptsFonts.body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
    val meta = TextStyle(fontFamily = ReceiptsFonts.body, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 16.sp)
    val amount = TextStyle(fontFamily = ReceiptsFonts.mono, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 17.sp, fontFeatureSettings = "tnum")
    val label = TextStyle(fontFamily = ReceiptsFonts.mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.4.sp, fontFeatureSettings = "tnum")
    val stamp = TextStyle(fontFamily = ReceiptsFonts.mono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 12.sp, letterSpacing = 1.sp, fontFeatureSettings = "tnum")
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
    small = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.medium),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.card),
    large = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.large),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(ReceiptsRadius.hero),
)

@Composable
fun ReceiptsTheme(
    preference: AppThemePreference = AppThemePreference.COLORFUL,
    content: @Composable () -> Unit,
) {
    val dark = preference == AppThemePreference.DARK
    val target = when (preference) {
        AppThemePreference.COLORFUL -> ColorfulPalette
        AppThemePreference.SUBTLE -> SubtlePalette
        AppThemePreference.LIGHT -> LightPalette
        AppThemePreference.DARK -> DarkPalette
    }
    // Switching themes wipes across the whole app instead of snapping.
    val fromState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(target) }
    val from = fromState.value
    val blend = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (fromState.value !== target) {
            blend.snapTo(0f)
            blend.animateTo(1f, androidx.compose.animation.core.tween(ReceiptsMotion.SHEET, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            fromState.value = target
        }
    }
    val colors = if (from === target || blend.value >= 1f) target else lerpPalette(from, target, blend.value)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.purple,
            onPrimary = colors.ultramarineOn,
            secondary = colors.yellow,
            onSecondary = colors.chromeOn,
            error = colors.pink,
            onError = colors.chilliOn,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paperRaised,
            onSurface = colors.ink,
            outline = colors.rule,
        )
    } else {
        lightColorScheme(
            primary = colors.purple,
            onPrimary = colors.ultramarineOn,
            secondary = colors.yellow,
            onSecondary = colors.chromeOn,
            error = colors.pink,
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
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
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

private fun lerpPalette(from: ReceiptsColors, to: ReceiptsColors, t: Float): ReceiptsColors {
    fun c(a: Color, b: Color) = lerp(a, b, t)
    return ReceiptsColors(
        paper = c(from.paper, to.paper),
        warm = c(from.warm, to.warm),
        cyan = c(from.cyan, to.cyan),
        yellow = c(from.yellow, to.yellow),
        purple = c(from.purple, to.purple),
        pink = c(from.pink, to.pink),
        mint = c(from.mint, to.mint),
        ink = c(from.ink, to.ink),
        inkSoft = c(from.inkSoft, to.inkSoft),
        fade = c(from.fade, to.fade),
        pinkTint = c(from.pinkTint, to.pinkTint),
        mintTint = c(from.mintTint, to.mintTint),
        paperRaised = c(from.paperRaised, to.paperRaised),
        sunk = c(from.sunk, to.sunk),
        rule = c(from.rule, to.rule),
        ruleHard = c(from.ruleHard, to.ruleHard),
        ultramarine = c(from.ultramarine, to.ultramarine),
        ultramarineOn = c(from.ultramarineOn, to.ultramarineOn),
        ultramarineTint = c(from.ultramarineTint, to.ultramarineTint),
        chrome = c(from.chrome, to.chrome),
        chromeOn = c(from.chromeOn, to.chromeOn),
        chromeTint = c(from.chromeTint, to.chromeTint),
        chilli = c(from.chilli, to.chilli),
        chilliOn = c(from.chilliOn, to.chilliOn),
        chilliTint = c(from.chilliTint, to.chilliTint),
        scrim = c(from.scrim, to.scrim),
        stampVeil = c(from.stampVeil, to.stampVeil),
        categoryOnDark = c(from.categoryOnDark, to.categoryOnDark),
        isDark = if (t < 0.5f) from.isDark else to.isDark,
        monochrome = if (t < 0.5f) from.monochrome else to.monochrome,
    )
}

@Immutable
data class CategoryVisual(val mark: String, val color: Color)

private val CategoryColors = mapOf(
    "food" to CategoryVisual("FD", Color(0xFFFF8A3D)),
    "groceries" to CategoryVisual("GR", Color(0xFF2FD18C)),
    "shopping" to CategoryVisual("SH", Color(0xFFFF3D7F)),
    "transport" to CategoryVisual("TR", Color(0xFF22C7D6)),
    "travel" to CategoryVisual("TR", Color(0xFF22C7D6)),
    "income" to CategoryVisual("IN", Color(0xFF7B4DFF)),
    "refund" to CategoryVisual("RF", Color(0xFF7B4DFF)),
    "transfers" to CategoryVisual("IN", Color(0xFF7B4DFF)),
    "repayments" to CategoryVisual("IN", Color(0xFF7B4DFF)),
    "bills" to CategoryVisual("BL", Color(0xFF7B4DFF)),
    "rent" to CategoryVisual("RN", Color(0xFFFF8A3D)),
    "entertainment" to CategoryVisual("EN", Color(0xFFFFD23F)),
    "subscription" to CategoryVisual("SU", Color(0xFF7B4DFF)),
    "medical" to CategoryVisual("MD", Color(0xFFFF3D7F)),
    "gaming" to CategoryVisual("GM", Color(0xFF7B4DFF)),
    "fitness" to CategoryVisual("FT", Color(0xFF2FD18C)),
    "personal" to CategoryVisual("PR", Color(0xFFFF3D7F)),
    "services" to CategoryVisual("SV", Color(0xFF22C7D6)),
    "smallshops" to CategoryVisual("SS", Color(0xFFFF8A3D)),
    "logistics" to CategoryVisual("LG", Color(0xFF22C7D6)),
    "insurance" to CategoryVisual("IS", Color(0xFF7B4DFF)),
    "pet" to CategoryVisual("PT", Color(0xFFFF8A3D)),
    "cash" to CategoryVisual("CA", Color(0xFFFFD23F)),
    "investment" to CategoryVisual("IV", Color(0xFF2FD18C)),
    "misc" to CategoryVisual("MI", Color(0xFFDFD9EC)),
)

private fun categoryVisualBase(id: String?): CategoryVisual = CategoryColors[id] ?: CategoryVisual("??", Color(0xFFDFD9EC))

private val MonochromeRamp = listOf(
    Color(0xFF3A3A3A), Color(0xFF525252), Color(0xFF696969), Color(0xFF7E7E7E),
    Color(0xFF919191), Color(0xFFA6A6A6), Color(0xFF454545), Color(0xFF616161),
)

private fun monochromeShade(id: String?, dark: Boolean): Color {
    val index = kotlin.math.abs((id ?: "misc").hashCode()) % MonochromeRamp.size
    val shade = MonochromeRamp[index]
    return if (dark) Color(1f - shade.red, 1f - shade.green, 1f - shade.blue, shade.alpha) else shade
}

private fun desaturate(color: Color, amount: Float): Color {
    val gray = color.luminance()
    return lerp(color, Color(gray, gray, gray, color.alpha), amount)
}

/** Category color, adapted to the active theme: grayscale in monochrome Light/Dark, muted in Subtle. */
@Composable
fun categoryVisual(id: String?): CategoryVisual {
    val colors = receiptsColors
    val base = categoryVisualBase(id)
    return when {
        colors.monochrome -> base.copy(color = monochromeShade(id, colors.isDark))
        colors === SubtlePalette -> base.copy(color = desaturate(base.color, 0.55f))
        else -> base
    }
}

@Composable
fun categoryMarkTextColor(background: Color): Color {
    val colors = receiptsColors
    if (!colors.monochrome && (background == Color(0xFFFF3D7F) || background == Color(0xFF7B4DFF))) return Color(0xFFFFF7EC)
    val inkContrast = contrastRatio(background.luminance(), colors.ink.luminance())
    val oppositeContrast = contrastRatio(background.luminance(), colors.paper.luminance())
    return if (inkContrast >= oppositeContrast) colors.ink else colors.paper
}

private fun contrastRatio(first: Float, second: Float): Float =
    (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
