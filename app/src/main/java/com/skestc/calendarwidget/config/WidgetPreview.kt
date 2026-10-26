package com.skestc.calendarwidget.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.settings.Appearance
import com.skestc.calendarwidget.settings.FontChoice
import com.skestc.calendarwidget.theme.DayNight
import com.skestc.calendarwidget.theme.DynamicSpec
import com.skestc.calendarwidget.theme.ThemeSpecs
import com.skestc.calendarwidget.theme.resolveCornerDp
import com.skestc.calendarwidget.theme.WidgetThemeId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

enum class WidgetKind { AGENDA, MONTH, TIMELINE }

private class PreviewPalette(
    val background: Color,
    val card: Color,
    val onCard: Color,
    val primary: Color,
    val onPrimary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val sunday: Color,
    val saturday: Color,
)

/**
 * 設定頁上方的即時預覽。用 Compose 畫出和 widget 相同的配色與排版（近似），
 * 背後墊一張示意桌布，才看得出透明度與毛玻璃的效果。
 */
@Composable
fun WidgetPreview(appearance: Appearance, kind: WidgetKind, darkTheme: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dynamicSpec = remember { DynamicSpec.resolve(context) }
    val spec = ThemeSpecs.fixed(appearance.theme) ?: dynamicSpec
    fun DayNight.pick() = Color(if (darkTheme) night else day)
    val palette = PreviewPalette(
        background = spec.background.pick(),
        card = spec.card.pick(),
        onCard = spec.onCard.pick(),
        primary = spec.primary.pick(),
        onPrimary = spec.onPrimary.pick(),
        onSurface = spec.onSurface.pick(),
        onSurfaceVariant = spec.onSurfaceVariant.pick(),
        outline = spec.outline.pick(),
        sunday = spec.sunday.pick(),
        saturday = spec.saturday.pick(),
    )

    val fontFamily = when {
        appearance.font == FontChoice.SERIF -> FontFamily.Serif
        appearance.font == FontChoice.THEME && spec.serifByDefault -> FontFamily.Serif
        appearance.font == FontChoice.MONO -> FontFamily.Monospace
        appearance.font == FontChoice.SANS -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
    val scale = appearance.textScalePercent / 100f
    fun text(size: Int, color: Color, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null) =
        TextStyle(
            color = color,
            fontSize = (size * scale).sp,
            fontWeight = weight,
            fontFamily = fontFamily,
            textAlign = align ?: TextAlign.Unspecified,
        )

    val cornerDp = resolveCornerDp(context, appearance.cornerDp)
    val shape = RoundedCornerShape(cornerDp.dp)
    val widgetModifier = if (appearance.theme == WidgetThemeId.GLASS) {
        // 與 res/drawable(-night)/widget_glass_bg.xml 相同的漸層與亮邊。
        val (start, end, stroke) = if (darkTheme) {
            Triple(Color(0xA6101318), Color(0x73101318), Color(0x40FFFFFF))
        } else {
            Triple(Color(0xA6FFFFFF), Color(0x73FFFFFF), Color(0xB3FFFFFF))
        }
        Modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(start, end)))
            .border(1.dp, stroke, shape)
    } else {
        Modifier
            .clip(shape)
            .background(palette.background.copy(alpha = palette.background.alpha * appearance.backgroundOpacity / 100f))
    }

    // 示意桌布。
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6A5ACD), Color(0xFFE07A8F), Color(0xFF3FB6A8)),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = widgetModifier
                .fillMaxWidth()
                .height(if (kind == WidgetKind.MONTH) 260.dp else 190.dp)
                .padding(12.dp),
        ) {
            when (kind) {
                WidgetKind.AGENDA -> AgendaPreview(palette, cornerDp, ::text)
                WidgetKind.MONTH -> MonthPreview(palette, ::text)
                WidgetKind.TIMELINE -> TimelinePreview(palette, cornerDp, ::text)
            }
        }
    }
}

private typealias TextStyler = (Int, Color, FontWeight, TextAlign?) -> TextStyle

@Composable
private fun AgendaPreview(palette: PreviewPalette, cornerDp: Float, text: TextStyler) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val cardShape = RoundedCornerShape(min((cornerDp / 2).dp, 12.dp))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(today.dayOfMonth.toString(), style = text(34, palette.primary, FontWeight.Bold, null))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    today.dayOfWeek.getDisplayName(DayTextStyle.FULL, locale),
                    style = text(14, palette.onSurface, FontWeight.Medium, null),
                )
                Text(
                    DateTimeFormatter.ofPattern("yyyy/MM", locale).format(today),
                    style = text(12, palette.onSurfaceVariant, FontWeight.Normal, null),
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(palette.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = text(20, palette.onPrimary, FontWeight.Bold, null))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("今天", style = text(12, palette.primary, FontWeight.Medium, null))
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((title, time, color) in SAMPLE_AGENDA) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(palette.card)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = text(14, palette.onCard, FontWeight.Medium, null),
                        )
                        Text(time, style = text(12, palette.onCard, FontWeight.Normal, null))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPreview(palette: PreviewPalette, text: TextStyler) {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val data = remember(month) {
        val zone = ZoneId.systemDefault()
        val sample = listOf(-3, 2, 2, 5, 9, 12).mapIndexed { i, offset ->
            val day = today.plusDays(offset.toLong())
            val start = day.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli()
            CalendarEvent(i.toLong(), "", start, start + 3_600_000, false, SAMPLE_COLORS[i % SAMPLE_COLORS.size], null, "")
        }
        MonthBuilder(zone, locale).build(month, today, sample)
    }
    val title = DateTimeFormatter
        .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale)
        .format(month)

    fun weekend(day: DayOfWeek): Color? = when (day) {
        DayOfWeek.SUNDAY -> palette.sunday
        DayOfWeek.SATURDAY -> palette.saturday
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = text(16, palette.onSurface, FontWeight.Bold, null))
            for (label in listOf("‹", "›")) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(palette.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = text(16, palette.onCard, FontWeight.Bold, null))
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            for (i in 0 until 7) {
                val day = data.firstDayOfWeek.plus(i.toLong())
                Text(
                    day.getDisplayName(DayTextStyle.NARROW, locale),
                    Modifier.weight(1f),
                    style = text(11, weekend(day) ?: palette.onSurfaceVariant, FontWeight.Medium, TextAlign.Center),
                )
            }
        }
        for (week in data.weeks) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (cell in week) {
                    val isToday = cell.date == today
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isToday) palette.primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell.date.dayOfMonth.toString(),
                                style = text(
                                    12,
                                    when {
                                        isToday -> palette.onPrimary
                                        !cell.inMonth -> palette.outline
                                        else -> weekend(cell.date.dayOfWeek) ?: palette.onSurface
                                    },
                                    if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    TextAlign.Center,
                                ),
                            )
                        }
                        Row(Modifier.height(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            cell.eventColors.forEach {
                                Box(Modifier.padding(horizontal = 1.dp).size(4.dp).clip(CircleShape).background(Color(it)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelinePreview(palette: PreviewPalette, cornerDp: Float, text: TextStyler) {
    val cardShape = RoundedCornerShape(min((cornerDp / 2).dp, 12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("今天", style = text(16, palette.onSurface, FontWeight.Bold, null))
        Spacer(Modifier.height(2.dp))
        TimelinePreviewRow(palette, cardShape, text, "09:30", "晨會", "09:30 – 10:00", Color(0xFF4285F4), past = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("現在", Modifier.width(46.dp), style = text(12, palette.primary, FontWeight.Bold, null))
            Box(Modifier.weight(1f).height(2.dp).background(palette.primary))
        }
        Text(
            "空檔 2 小時",
            Modifier.padding(start = 72.dp),
            style = text(12, palette.onSurfaceVariant, FontWeight.Normal, null),
        )
        TimelinePreviewRow(palette, cardShape, text, "14:00", "專案討論", "14:00 – 15:00 · 線上", Color(0xFF0B8043), past = false)
    }
}

@Composable
private fun TimelinePreviewRow(
    palette: PreviewPalette,
    cardShape: RoundedCornerShape,
    text: TextStyler,
    time: String,
    title: String,
    detail: String,
    dot: Color,
    past: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            time,
            Modifier.width(46.dp),
            style = text(12, if (past) palette.outline else palette.onSurface, FontWeight.Normal, null),
        )
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (past) palette.outline else dot))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(cardShape)
                .background(if (past) Color.Transparent else palette.card)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val color = if (past) palette.outline else palette.onCard
            Text(title, style = text(14, color, FontWeight.Medium, null))
            Text(detail, style = text(12, color, FontWeight.Normal, null))
        }
    }
}

private val SAMPLE_COLORS = listOf(0xFF4285F4.toInt(), 0xFF0B8043.toInt(), 0xFFE67C73.toInt(), 0xFFF6BF26.toInt())

private val SAMPLE_AGENDA = listOf(
    Triple("晨會", "09:30 – 10:00 · 會議室 A", Color(0xFF4285F4)),
    Triple("午餐約", "12:00 – 13:00", Color(0xFF0B8043)),
)
