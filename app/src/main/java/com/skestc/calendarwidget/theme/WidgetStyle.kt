package com.skestc.calendarwidget.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.settings.Appearance
import com.skestc.calendarwidget.settings.FontChoice
import androidx.glance.color.ColorProvider as DayNightColorProvider

/** widget 用到的顏色角色（Glance ColorProvider）。 */
class WidgetColors(
    val background: ColorProvider?,
    val backgroundImage: ImageProvider?,
    val card: ColorProvider,
    val onCard: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val outline: ColorProvider,
    val sunday: ColorProvider,
    val saturday: ColorProvider,
)

class WidgetStyle(
    val colors: WidgetColors,
    val corner: Dp,
    private val textScale: Float,
    private val fontFamily: FontFamily?,
) {
    /** 事件卡片的圓角：跟著外框，但不超過 12dp。 */
    val cardCorner: Dp get() = minOf(corner / 2, 12.dp)

    fun sp(size: Int): TextUnit = (size * textScale).sp

    fun text(
        size: Int,
        color: ColorProvider,
        weight: FontWeight? = null,
        align: TextAlign? = null,
    ) = TextStyle(
        color = color,
        fontSize = sp(size),
        fontWeight = weight,
        textAlign = align,
        fontFamily = fontFamily,
    )
}

val LocalWidgetStyle = staticCompositionLocalOf<WidgetStyle> {
    error("WidgetStyle not provided; wrap content in ProvideWidgetStyle")
}

/** 必須放在 GlanceTheme 裡面：Material You 主題直接沿用 GlanceTheme 的動態色。 */
@Composable
fun ProvideWidgetStyle(appearance: Appearance, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val spec = ThemeSpecs.fixed(appearance.theme)
    val colors = if (spec == null) dynamicColors(context, appearance) else fixedColors(spec, appearance)
    val serif = appearance.font == FontChoice.SERIF ||
        (appearance.font == FontChoice.THEME && spec?.serifByDefault == true)
    val fontFamily = when {
        serif -> FontFamily.Serif
        appearance.font == FontChoice.MONO -> FontFamily.Monospace
        appearance.font == FontChoice.SANS -> FontFamily.SansSerif
        else -> null
    }
    val style = WidgetStyle(
        colors = colors,
        corner = appearance.cornerDp.dp,
        textScale = appearance.textScalePercent / 100f,
        fontFamily = fontFamily,
    )
    CompositionLocalProvider(LocalWidgetStyle provides style, content = content)
}

/** widget 外框背景：毛玻璃用圖片，其他用顏色，再套上圓角。 */
fun GlanceModifier.widgetBackground(style: WidgetStyle): GlanceModifier {
    val image = style.colors.backgroundImage
    val color = style.colors.background
    val withBackground = when {
        image != null -> background(image)
        color != null -> background(color)
        else -> this
    }
    return withBackground.cornerRadius(style.corner)
}

private fun DayNight.provider(alpha: Float = 1f): ColorProvider = DayNightColorProvider(
    day = Color(day).let { it.copy(alpha = it.alpha * alpha) },
    night = Color(night).let { it.copy(alpha = it.alpha * alpha) },
)

private fun fixedColors(spec: ThemeSpec, appearance: Appearance): WidgetColors {
    val glass = appearance.theme == WidgetThemeId.GLASS
    return WidgetColors(
        background = if (glass) null else spec.background.provider(appearance.backgroundOpacity / 100f),
        backgroundImage = if (glass) ImageProvider(R.drawable.widget_glass_bg) else null,
        card = spec.card.provider(),
        onCard = spec.onCard.provider(),
        primary = spec.primary.provider(),
        onPrimary = spec.onPrimary.provider(),
        onSurface = spec.onSurface.provider(),
        onSurfaceVariant = spec.onSurfaceVariant.provider(),
        outline = spec.outline.provider(),
        sunday = spec.sunday.provider(),
        saturday = spec.saturday.provider(),
    )
}

/**
 * Material You：文字等顏色直接用 GlanceTheme 的資源色，換桌布時系統會自動跟著換。
 * 背景要半透明時才改用解析後的顏色（下次 widget 更新時才會跟上新桌布）。
 */
@Composable
private fun dynamicColors(context: Context, appearance: Appearance): WidgetColors {
    val c = GlanceTheme.colors
    val background = if (appearance.backgroundOpacity >= 100) {
        c.widgetBackground
    } else {
        DynamicSpec.background(context).provider(appearance.backgroundOpacity / 100f)
    }
    return WidgetColors(
        background = background,
        backgroundImage = null,
        card = c.secondaryContainer,
        onCard = c.onSecondaryContainer,
        primary = c.primary,
        onPrimary = c.onPrimary,
        onSurface = c.onSurface,
        onSurfaceVariant = c.onSurfaceVariant,
        outline = c.outline,
        sunday = c.error,
        saturday = c.tertiary,
    )
}

/** 把 Android 12+ 的系統動態色解析成 [ThemeSpec]，給半透明背景與設定頁預覽使用。 */
object DynamicSpec {

    fun background(context: Context): DayNight =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 與 Glance 的 widgetBackground 相同：淺色 accent2_50、深色 neutral1_900。
            DayNight(
                context.color(android.R.color.system_accent2_50),
                context.color(android.R.color.system_neutral1_900),
            )
        } else {
            ThemeSpecs.BASELINE.background
        }

    fun resolve(context: Context): ThemeSpec {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ThemeSpecs.BASELINE
        fun pair(day: Int, night: Int) = DayNight(context.color(day), context.color(night))
        return ThemeSpec(
            background = background(context),
            card = pair(android.R.color.system_accent2_100, android.R.color.system_accent2_700),
            onCard = pair(android.R.color.system_accent2_900, android.R.color.system_accent2_100),
            primary = pair(android.R.color.system_accent1_600, android.R.color.system_accent1_200),
            onPrimary = pair(android.R.color.system_accent1_0, android.R.color.system_accent1_800),
            onSurface = pair(android.R.color.system_neutral1_900, android.R.color.system_neutral1_100),
            onSurfaceVariant = pair(android.R.color.system_neutral2_700, android.R.color.system_neutral2_200),
            outline = pair(android.R.color.system_neutral2_500, android.R.color.system_neutral2_400),
            sunday = ThemeSpecs.BASELINE.sunday,
            saturday = pair(android.R.color.system_accent3_600, android.R.color.system_accent3_200),
        )
    }

    private fun Context.color(id: Int): Long = getColor(id).toLong() and 0xFFFFFFFFL
}
