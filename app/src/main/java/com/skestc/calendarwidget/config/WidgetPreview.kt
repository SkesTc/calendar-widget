package com.skestc.calendarwidget.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.settings.AgendaLayout
import com.skestc.calendarwidget.settings.Appearance
import com.skestc.calendarwidget.settings.FontChoice
import com.skestc.calendarwidget.settings.HeaderButton
import com.skestc.calendarwidget.settings.WeekStart
import com.skestc.calendarwidget.theme.DayNight
import com.skestc.calendarwidget.theme.DynamicSpec
import com.skestc.calendarwidget.theme.ThemeSpecs
import com.skestc.calendarwidget.theme.resolveCornerDp
import com.skestc.calendarwidget.theme.specFor
import com.skestc.calendarwidget.theme.WidgetThemeId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

enum class WidgetKind { AGENDA, MONTH, TIMELINE, NEXT, WEEK }

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
fun WidgetPreview(
    appearance: Appearance,
    kind: WidgetKind,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    agendaLayout: AgendaLayout = AgendaLayout.LIST,
    headerButtons: List<HeaderButton> = HeaderButton.entries,
    weekStart: WeekStart = WeekStart.TODAY,
) {
    val context = LocalContext.current
    val dynamicSpec = remember { DynamicSpec.resolve(context) }
    val spec = specFor(appearance) ?: dynamicSpec
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
                .height(
                    when (kind) {
                        WidgetKind.MONTH -> 260.dp
                        WidgetKind.NEXT -> 64.dp
                        WidgetKind.WEEK -> 170.dp
                        else -> 220.dp
                    },
                )
                .padding(12.dp),
        ) {
            // 標題列按鈕跟 widget 一樣依勾選顯示（預覽的寬度足夠，全部分開畫）。
            val buttons: @Composable () -> Unit = { PreviewHeaderButtons(palette, headerButtons) }
            when (kind) {
                WidgetKind.AGENDA -> AgendaPreview(palette, cornerDp, ::text, agendaLayout, buttons)
                WidgetKind.MONTH -> MonthPreview(palette, ::text, buttons)
                WidgetKind.TIMELINE -> TimelinePreview(palette, cornerDp, ::text, buttons)
                WidgetKind.NEXT -> NextPreview(palette, ::text)
                WidgetKind.WEEK -> WeekPreview(palette, ::text, weekStart, buttons)
            }
        }
    }
}

private typealias TextStyler = (Int, Color, FontWeight, TextAlign?) -> TextStyle

/** 標題列右邊的按鈕：勾選的搜尋、重新整理、設定，加上「＋」。 */
@Composable
private fun PreviewHeaderButtons(palette: PreviewPalette, buttons: List<HeaderButton>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (button in buttons) {
            PreviewIconButton(
                icon = when (button) {
                    HeaderButton.SEARCH -> R.drawable.ic_search
                    HeaderButton.REFRESH -> R.drawable.ic_refresh
                    HeaderButton.SETTINGS -> R.drawable.ic_settings
                },
                background = palette.card,
                tint = palette.onCard,
            )
        }
        PreviewIconButton(R.drawable.ic_add, palette.primary, palette.onPrimary, size = 32.dp)
    }
}

@Composable
private fun PreviewIconButton(icon: Int, background: Color, tint: Color, size: Dp = 28.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}

/** 圓潤粗體的大號數字（widget 上的 ExpressiveText）。 */
@Composable
private fun roundedNumerals(): FontFamily? {
    val context = LocalContext.current
    return remember {
        runCatching { FontFamily(Font(path = "fonts/google_sans_flex_rounded_bold.ttf", assetManager = context.assets)) }.getOrNull()
    }
}

private data class PreviewEvent(val time: String, val title: Int, val detail: String, val color: Color, val past: Boolean = false)

@Composable
private fun AgendaPreview(
    palette: PreviewPalette,
    cornerDp: Float,
    text: TextStyler,
    layout: AgendaLayout,
    buttons: @Composable () -> Unit,
) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val rounded = roundedNumerals()
    val events = listOf(
        PreviewEvent("09:30", R.string.sample_standup, "09:30 – 10:00 · " + stringResource(R.string.sample_room), Color(0xFF4285F4), past = true),
        PreviewEvent("12:00", R.string.sample_lunch, "12:00 – 13:00", Color(0xFF0B8043)),
        PreviewEvent("14:00", R.string.sample_project, "14:00 – 15:00 · " + stringResource(R.string.sample_online), Color(0xFFE67C73)),
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                today.dayOfMonth.toString(),
                style = text(40, palette.primary, FontWeight.Bold, null).copy(fontFamily = rounded ?: FontFamily.Default),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    today.dayOfWeek.getDisplayName(DayTextStyle.FULL, locale),
                    maxLines = 1,
                    style = text(16, palette.onSurface, FontWeight.Bold, null),
                )
                Text(
                    DateTimeFormatter.ofPattern("yyyy/MM", locale).format(today),
                    style = text(12, palette.onSurfaceVariant, FontWeight.Normal, null),
                )
            }
            buttons()
        }
        Spacer(Modifier.height(8.dp))
        when (layout) {
            AgendaLayout.LIST -> {
                Text(stringResource(R.string.today_word), style = text(12, palette.primary, FontWeight.Bold, null))
                Spacer(Modifier.height(4.dp))
                // 同一天的卡片連成一組：外側大圓角、內側小圓角（已結束的沒有底色）。
                val big = if (cornerDp > 0f) 16.dp else 0.dp
                val small = if (cornerDp > 0f) 4.dp else 0.dp
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    events.forEachIndexed { i, event ->
                        val shape = when {
                            event.past -> RoundedCornerShape(0.dp)
                            i == 1 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
                            else -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
                        }
                        val color = if (event.past) palette.onSurfaceVariant else palette.onCard
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (event.past) Color.Transparent else palette.card)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(if (event.past) palette.outline else event.color))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(event.title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(14, color, FontWeight.Medium, null))
                                Text(event.detail, maxLines = 1, style = text(12, color, FontWeight.Normal, null))
                            }
                        }
                    }
                }
            }
            AgendaLayout.TIMELINE -> {
                val cardShape = RoundedCornerShape(if (cornerDp > 0f) 16.dp else 0.dp)
                Text(stringResource(R.string.today_word), style = text(12, palette.primary, FontWeight.Bold, null))
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimelinePreviewRow(palette, cardShape, text, events[0].time, stringResource(events[0].title), events[0].detail, events[0].color, past = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.now), Modifier.width(46.dp), style = text(12, palette.primary, FontWeight.Bold, null))
                        Box(Modifier.weight(1f).height(2.dp).background(palette.primary))
                    }
                    TimelinePreviewRow(palette, cardShape, text, events[1].time, stringResource(events[1].title), events[1].detail, events[1].color, past = false)
                }
            }
            AgendaLayout.DAYS -> {
                // 每天一列：左邊星期與大號日期，右邊行程；今天用卡片色標示。
                for (offset in 0..1) {
                    val date = today.plusDays(offset.toLong())
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(if (cornerDp > 0f) 16.dp else 0.dp))
                            .background(if (offset == 0) palette.card else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.dayOfWeek.getDisplayName(DayTextStyle.SHORT, locale), style = text(10, palette.onSurface, FontWeight.Medium, TextAlign.Center))
                            Text(
                                date.dayOfMonth.toString(),
                                style = text(18, if (offset == 0) palette.primary else palette.onSurface, FontWeight.Bold, TextAlign.Center)
                                    .copy(fontFamily = rounded ?: FontFamily.Default),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            val dayEvents = if (offset == 0) events.drop(1) else listOf(events[0].copy(past = false))
                            for (event in dayEvents) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(event.color))
                                    Text(
                                        "${event.time}  ${stringResource(event.title)}",
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 5.dp),
                                        style = text(12, palette.onSurface, FontWeight.Normal, null),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPreview(palette: PreviewPalette, text: TextStyler, buttons: @Composable () -> Unit) {
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
            Text(title, Modifier.weight(1f), maxLines = 1, style = text(16, palette.onSurface, FontWeight.Bold, null))
            PreviewIconButton(R.drawable.ic_chevron_left, palette.card, palette.onCard)
            Spacer(Modifier.width(4.dp))
            PreviewIconButton(R.drawable.ic_chevron_right, palette.card, palette.onCard)
            Spacer(Modifier.width(4.dp))
            buttons()
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
                                    if (isToday || (cell.inMonth && cell.eventColors.isNotEmpty())) FontWeight.Bold else FontWeight.Normal,
                                    TextAlign.Center,
                                ),
                            )
                        }
                        Row(Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            cell.eventColors.forEach {
                                Box(Modifier.padding(horizontal = 1.5.dp).size(6.dp).clip(CircleShape).background(Color(it)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelinePreview(palette: PreviewPalette, cornerDp: Float, text: TextStyler, buttons: @Composable () -> Unit) {
    val cardShape = RoundedCornerShape(if (cornerDp > 0f) 16.dp else 0.dp)
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                today.dayOfMonth.toString(),
                style = text(34, palette.primary, FontWeight.Bold, null).copy(fontFamily = roundedNumerals() ?: FontFamily.Default),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.today_word),
                Modifier.weight(1f),
                style = text(16, palette.onSurface, FontWeight.Bold, null),
            )
            buttons()
        }
        Spacer(Modifier.height(2.dp))
        TimelinePreviewRow(
            palette, cardShape, text, "09:30", stringResource(R.string.sample_standup), "09:30 – 10:00",
            Color(0xFF4285F4), past = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.now), Modifier.width(46.dp), style = text(12, palette.primary, FontWeight.Bold, null))
            Box(Modifier.weight(1f).height(2.dp).background(palette.primary))
        }
        Text(
            stringResource(R.string.preview_gap),
            Modifier.padding(start = 72.dp),
            style = text(12, palette.onSurfaceVariant, FontWeight.Normal, null),
        )
        TimelinePreviewRow(
            palette, cardShape, text, "14:00", stringResource(R.string.sample_project),
            "14:00 – 15:00 · " + stringResource(R.string.sample_online), Color(0xFF0B8043), past = false,
        )
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

@Composable
private fun NextPreview(palette: PreviewPalette, text: TextStyler) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color(0xFF4285F4)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stringResource(R.string.preview_next_when), style = text(11, palette.onSurfaceVariant, FontWeight.Bold, null))
            Text(stringResource(R.string.sample_project), maxLines = 1, style = text(15, palette.onSurface, FontWeight.Bold, null))
        }
    }
}

@Composable
private fun WeekPreview(palette: PreviewPalette, text: TextStyler, weekStart: WeekStart, buttons: @Composable () -> Unit) {
    val today = LocalDate.now()
    val first = weekStart.firstDay(today)
    val locale = Locale.getDefault()
    val samples = mapOf(
        0 to listOf(R.string.sample_standup to Color(0xFF4285F4)),
        1 to listOf(R.string.sample_dentist to Color(0xFFE67C73)),
        3 to listOf(R.string.sample_book_club to Color(0xFF0B8043), R.string.sample_gym to Color(0xFF8E24AA)),
        4 to listOf(R.string.sample_dinner to Color(0xFFE67C73)),
        6 to listOf(R.string.sample_hike to Color(0xFF0B8043)),
    )
    val range = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                "${range.format(first)} – ${range.format(first.plusDays(6))}",
                Modifier.weight(1f),
                maxLines = 1,
                style = text(14, palette.onSurface, FontWeight.Bold, null),
            )
            buttons()
        }
        Row(Modifier.fillMaxSize()) {
            for (offset in 0 until 7) {
                val date = first.plusDays(offset.toLong())
                val isToday = date == today
                val dayColor = when (date.dayOfWeek) {
                    DayOfWeek.SUNDAY -> palette.sunday
                    DayOfWeek.SATURDAY -> palette.saturday
                    else -> palette.onSurface
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(DayTextStyle.NARROW, locale),
                        style = text(10, dayColor, FontWeight.Medium, TextAlign.Center),
                    )
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isToday) palette.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            style = text(13, if (isToday) palette.onPrimary else dayColor, FontWeight.Bold, TextAlign.Center),
                        )
                    }
                    for ((title, color) in samples[offset].orEmpty()) {
                        Text(
                            stringResource(title),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color.copy(alpha = 0.3f))
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                            style = text(9, palette.onSurface, FontWeight.Medium, null),
                        )
                    }
                }
            }
        }
    }
}

private val SAMPLE_COLORS = listOf(0xFF4285F4.toInt(), 0xFF0B8043.toInt(), 0xFFE67C73.toInt(), 0xFFF6BF26.toInt())
