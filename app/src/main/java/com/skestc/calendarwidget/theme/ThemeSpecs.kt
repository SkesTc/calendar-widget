package com.skestc.calendarwidget.theme

import kotlin.math.pow

/** 同一個顏色角色在淺色／深色模式下的值（ARGB）。 */
data class DayNight(val day: Long, val night: Long) {
    constructor(both: Long) : this(both, both)
}

/**
 * 一套 widget 配色。純 Kotlin、不依賴 Android，方便做對比度單元測試；
 * Glance 與 Compose 端再各自轉成 ColorProvider / Color。
 */
data class ThemeSpec(
    val background: DayNight,
    /** 事件卡片、按鈕底色。 */
    val card: DayNight,
    val onCard: DayNight,
    val primary: DayNight,
    val onPrimary: DayNight,
    val onSurface: DayNight,
    val onSurfaceVariant: DayNight,
    /** 月曆裡不屬於本月的日期。 */
    val outline: DayNight,
    val sunday: DayNight,
    val saturday: DayNight,
    /** 字型設定為「跟隨主題」時是否用襯線字。 */
    val serifByDefault: Boolean = false,
)

/** 顯示名稱在各語系的 strings.xml（theme_ 開頭）。 */
enum class WidgetThemeId {
    DYNAMIC,
    AMOLED,
    MORANDI,
    JOURNAL,
    GLASS,

    /** 使用者自選主色與背景色，其餘顏色由 [CustomTheme.derive] 推算。 */
    CUSTOM;

    /** 毛玻璃用半透明漸層圖片當背景，不套用背景透明度設定。 */
    val supportsOpacity: Boolean get() = this != GLASS

    companion object {
        fun fromKey(key: String?): WidgetThemeId = entries.firstOrNull { it.name == key } ?: DYNAMIC
    }
}

object ThemeSpecs {

    /** Android 12 以下沒有動態取色時的 Material You 替代配色（Google 藍）。 */
    val BASELINE = ThemeSpec(
        background = DayNight(0xFFF3F6FC, 0xFF1B1C1F),
        card = DayNight(0xFFDCE3F5, 0xFF2F3542),
        onCard = DayNight(0xFF131C2B, 0xFFE2E7F5),
        primary = DayNight(0xFF1A5FC4, 0xFF9DC1FF),
        onPrimary = DayNight(0xFFFFFFFF, 0xFF002F68),
        onSurface = DayNight(0xFF1B1C1F, 0xFFE3E2E6),
        onSurfaceVariant = DayNight(0xFF4A4F5A, 0xFFC1C6D3),
        outline = DayNight(0xFF8A8F99, 0xFF7C818C),
        sunday = DayNight(0xFFC5221F, 0xFFFF8A80),
        saturday = DayNight(0xFF1A5FC4, 0xFF9DC1FF),
    )

    /** 純黑背景，OLED 螢幕最省電；深淺模式都一樣。 */
    val AMOLED = ThemeSpec(
        background = DayNight(0xFF000000),
        card = DayNight(0xFF161616),
        onCard = DayNight(0xFFF1F3F4),
        primary = DayNight(0xFF8AB4F8),
        onPrimary = DayNight(0xFF0B1D3A),
        onSurface = DayNight(0xFFFFFFFF),
        onSurfaceVariant = DayNight(0xFFA8ADB3),
        outline = DayNight(0xFF5F6368),
        sunday = DayNight(0xFFFF8A80),
        saturday = DayNight(0xFF8AB4F8),
    )

    /** 低彩度的灰調大地色。 */
    val MORANDI = ThemeSpec(
        background = DayNight(0xFFF2EEE9, 0xFF2E2A27),
        card = DayNight(0xFFE4DCD3, 0xFF3D3733),
        onCard = DayNight(0xFF3F3630, 0xFFEDE6DF),
        primary = DayNight(0xFF7A6857, 0xFFCDBBA8),
        onPrimary = DayNight(0xFFFFFFFF, 0xFF3A2E24),
        onSurface = DayNight(0xFF3F3630, 0xFFEDE6DF),
        onSurfaceVariant = DayNight(0xFF6F645B, 0xFFB8ADA2),
        outline = DayNight(0xFFA59A90, 0xFF7D736A),
        sunday = DayNight(0xFFA0564C, 0xFFE0A097),
        saturday = DayNight(0xFF566F86, 0xFFA5BACB),
    )

    /** 米色紙張、深褐墨水、紅色印章感的主色，預設用襯線字。 */
    val JOURNAL = ThemeSpec(
        background = DayNight(0xFFFBF6EA, 0xFF2B2620),
        card = DayNight(0xFFF1E6CC, 0xFF3A332A),
        onCard = DayNight(0xFF3B3024, 0xFFF1E9DA),
        primary = DayNight(0xFFB03A2E, 0xFFEE8C80),
        onPrimary = DayNight(0xFFFFFFFF, 0xFF3D0D08),
        onSurface = DayNight(0xFF3B3024, 0xFFF1E9DA),
        onSurfaceVariant = DayNight(0xFF6E604E, 0xFFC4B8A6),
        outline = DayNight(0xFFB3A58F, 0xFF7A6E5E),
        sunday = DayNight(0xFFB03A2E, 0xFFEE8C80),
        saturday = DayNight(0xFF2B5F8F, 0xFF97BEE0),
        serifByDefault = true,
    )

    /**
     * 毛玻璃：背景是 res/drawable(-night)/widget_glass_bg.xml 的半透明漸層，
     * 這裡的 [ThemeSpec.background] 只用在設定頁預覽與對比度估算（漸層的平均色）。
     */
    val GLASS = ThemeSpec(
        background = DayNight(0x8CFFFFFF, 0x8C101318),
        card = DayNight(0x80FFFFFF, 0x26FFFFFF),
        onCard = DayNight(0xFF16181C, 0xFFFFFFFF),
        primary = DayNight(0xFF1A5FC4, 0xFF9DC1FF),
        onPrimary = DayNight(0xFFFFFFFF, 0xFF002F68),
        onSurface = DayNight(0xFF16181C, 0xFFFFFFFF),
        onSurfaceVariant = DayNight(0xFF3C4048, 0xFFD5D9E0),
        outline = DayNight(0xFF6B7079, 0xFF9AA0A8),
        sunday = DayNight(0xFFB3261E, 0xFFFF9C94),
        saturday = DayNight(0xFF1A5FC4, 0xFF9DC1FF),
    )

    /**
     * 固定配色的主題。Material You 要在執行時依桌布取色、自訂主題要看使用者選的顏色
     * （[CustomTheme.derive]），所以這兩個回傳 null。
     */
    fun fixed(id: WidgetThemeId): ThemeSpec? = when (id) {
        WidgetThemeId.DYNAMIC, WidgetThemeId.CUSTOM -> null
        WidgetThemeId.AMOLED -> AMOLED
        WidgetThemeId.MORANDI -> MORANDI
        WidgetThemeId.JOURNAL -> JOURNAL
        WidgetThemeId.GLASS -> GLASS
    }
}

/**
 * 自訂主題：只讓使用者選主色和背景色，其他顏色自動推算，
 * 而且保證和 [ThemeSpecs] 內建主題一樣的對比度（內文 4.5:1、強調色 3:1）。
 */
object CustomTheme {
    const val DEFAULT_PRIMARY = 0xFF1A73E8
    const val DEFAULT_BACKGROUND = 0xFFFFFFFF

    private const val SOFT_DARK = 0xFF1B1C1F
    private const val SOFT_LIGHT = 0xFFF1F3F4
    private const val BLACK = 0xFF000000
    private const val WHITE = 0xFFFFFFFF

    fun derive(primary: Long, background: Long): ThemeSpec {
        val bg = opaque(background)
        val onSurface = readableOn(bg)
        val onSurfaceVariant = listOf(0.72, 0.85)
            .map { mix(onSurface, bg, it) }
            .firstOrNull { Contrast.ratio(it, bg) >= 4.5 } ?: onSurface
        val accent = ensureContrast(opaque(primary), bg, onSurface, 3.0)
        val card = mix(accent, bg, 0.14)
        return ThemeSpec(
            background = DayNight(bg),
            card = DayNight(card),
            onCard = DayNight(readableOn(card)),
            primary = DayNight(accent),
            onPrimary = DayNight(readableOn(accent)),
            onSurface = DayNight(onSurface),
            onSurfaceVariant = DayNight(onSurfaceVariant),
            outline = DayNight(mix(onSurface, bg, 0.45)),
            sunday = DayNight(pickAccent(listOf(0xFFC5221F, 0xFFFF8A80), bg, onSurface)),
            saturday = DayNight(pickAccent(listOf(0xFF1A5FC4, 0xFF9DC1FF), bg, onSurface)),
        )
    }

    /**
     * 在 [background] 上看得清楚的文字色：優先用柔和的深灰／淺灰，不夠時用純黑／純白。
     * 任何顏色和純黑或純白之一的對比度至少 4.58:1，所以一定找得到。
     */
    fun readableOn(background: Long): Long {
        val dark = if (Contrast.ratio(SOFT_DARK, background) >= 4.5) SOFT_DARK else BLACK
        val light = if (Contrast.ratio(SOFT_LIGHT, background) >= 4.5) SOFT_LIGHT else WHITE
        return if (Contrast.ratio(dark, background) >= Contrast.ratio(light, background)) dark else light
    }

    /** 把 [color] 慢慢往 [toward]（文字色）混，直到和背景的對比度達到 [min]。 */
    private fun ensureContrast(color: Long, background: Long, toward: Long, min: Double): Long {
        var amount = 0.0
        var result = color
        while (Contrast.ratio(result, background) < min && amount < 1.0) {
            amount = minOf(1.0, amount + 0.05)
            result = mix(toward, color, amount)
        }
        return result
    }

    private fun pickAccent(candidates: List<Long>, background: Long, toward: Long): Long =
        ensureContrast(candidates.maxBy { Contrast.ratio(it, background) }, background, toward, 3.0)

    /** [top] 以 [amount]（0～1）的比例疊在 [bottom] 上，結果不透明。 */
    fun mix(top: Long, bottom: Long, amount: Double): Long {
        val alpha = Math.round(amount.coerceIn(0.0, 1.0) * 255)
        return Contrast.composite((alpha shl 24) or (top and 0xFFFFFF), opaque(bottom))
    }

    private fun opaque(color: Long): Long = 0xFF000000 or (color and 0xFFFFFF)
}

/** WCAG 2.x 對比度計算。 */
object Contrast {

    /** 把半透明的 [top] 疊在不透明的 [bottom] 上。 */
    fun composite(top: Long, bottom: Long): Long {
        val a = ((top ushr 24) and 0xFF) / 255.0
        fun ch(shift: Int): Long {
            val t = (top ushr shift) and 0xFF
            val b = (bottom ushr shift) and 0xFF
            return Math.round(t * a + b * (1 - a))
        }
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun luminance(argb: Long): Double {
        fun lin(shift: Int): Double {
            val c = ((argb ushr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
