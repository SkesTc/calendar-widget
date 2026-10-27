package com.skestc.calendarwidget.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.TitleShortener
import com.skestc.calendarwidget.data.WeekBuilder
import com.skestc.calendarwidget.data.WeekDay
import com.skestc.calendarwidget.data.WeekLayout
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.Desaturate
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.ExpressiveText
import com.skestc.calendarwidget.theme.cardBackground
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayTextStyle
import java.util.Date
import java.time.format.DateTimeFormatter
import java.util.Locale

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface WeekState {
    data object NoPermission : WeekState
    data class Loaded(
        val today: LocalDate,
        val days: List<WeekDay>,
        /** 已結束（end ≤ now）的行程用淡色顯示。 */
        val now: Long = System.currentTimeMillis(),
    ) : WeekState
}

/** 一週 widget：從今天起 7 天；扁長時並排成 7 欄、直長時每天一列。假日紅字、可顯示農曆。 */
class WeekWidget : GlanceAppWidget() {

    companion object {
        /** 先多載一些，畫面依實際大小決定顯示幾筆。 */
        private const val MAX_PER_DAY = 8
    }

    /**
     * 依實際大小畫：直長時改成每天一列、扁長時 7 欄，每天顯示幾筆也依高度計算（[WeekLayout]）。
     * Responsive 只會用宣告的尺寸，大 widget 會留下大片空白。
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = CalendarRepository(context)
        val initialSettings = WidgetSettings.from(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id),
        )
        val initial = load(context, repository, initialSettings)

        provideContent {
            val settings = WidgetSettings.from(currentState<Preferences>())
            val state = rememberWidgetData(settings, initialSettings, initial) {
                load(context, repository, it)
            }
            GlanceTheme {
                ProvideWidgetStyle(settings.appearance) {
                    WeekContent(state)
                }
            }
        }
    }

    private suspend fun load(context: Context, repository: CalendarRepository, settings: WidgetSettings): WeekState {
        if (!repository.hasPermission()) return WeekState.NoPermission
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 前後各多抓一天，涵蓋以 UTC 午夜計的全天事件。
        val start = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repository.instances(start, end, settings.hiddenCalendarIds)
        val labeler = if (settings.showLunar) DayLabeler(IcuLunarSource()) else null
        val now = System.currentTimeMillis()
        // 下一個行程結束時重畫，讓它變成淡色。
        events.filter { !it.allDay && it.end > now }.minOfOrNull { it.end }
            ?.let { DayChangeScheduler.scheduleBoundary(context, it) }
        return WeekState.Loaded(today, WeekBuilder(zone).build(events, today, 7, MAX_PER_DAY, labeler), now)
    }
}

@Composable
internal fun WeekContent(state: WeekState) {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(style)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        when (state) {
            WeekState.NoPermission -> PermissionPrompt()
            is WeekState.Loaded -> {
                val size = LocalSize.current
                if (size.height >= HEADER_MIN_HEIGHT) {
                    // 夠高：上方標題列（日期範圍＋搜尋、設定、新增），下方放一週。
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        WeekHeader(state)
                        WeekBody(state, size.width.value, size.height.value - HEADER_HEIGHT.value)
                    }
                } else {
                    // 4×2 這種扁長的：按鈕直排在右側，不佔用行程的高度。
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            WeekBody(state, size.width.value - RAIL_WIDTH.value, size.height.value)
                        }
                        Spacer(GlanceModifier.width(4.dp))
                        Column(
                            modifier = GlanceModifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchButton(RAIL_BUTTON)
                            Spacer(GlanceModifier.height(4.dp))
                            SettingsButton(RAIL_BUTTON)
                            Spacer(GlanceModifier.height(4.dp))
                            AddEventButton(RAIL_BUTTON)
                        }
                    }
                }
            }
        }
    }
}

private val HEADER_MIN_HEIGHT = 140.dp
/** 標題列（含下方間距）佔的高度：版面計算要扣掉。 */
private val HEADER_HEIGHT = 34.dp
private val RAIL_BUTTON = 26.dp
private val RAIL_WIDTH = 30.dp

/** 標題列：這 7 天的日期範圍，右側是搜尋、設定、新增行程。 */
@Composable
private fun WeekHeader(state: WeekState.Loaded) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    val range = "${format.format(state.days.first().date)} – ${format.format(state.days.last().date)}"
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT).padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = range,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = style.text(14, style.colors.onSurface, FontWeight.Bold),
        )
        SearchButton(30.dp)
        Spacer(GlanceModifier.width(4.dp))
        SettingsButton(30.dp)
        Spacer(GlanceModifier.width(4.dp))
        AddEventButton(30.dp)
    }
}

/** 依可用的寬高選版面：直長時每天一列，扁長時 7 欄並排。 */
@Composable
private fun WeekBody(state: WeekState.Loaded, widthDp: Float, heightDp: Float) {
    if (WeekLayout.useRows(widthDp, heightDp)) {
        WeekRows(state, WeekLayout.linesPerRow(heightDp))
    } else {
        val showLabel = heightDp >= 140f
        val visible = WeekLayout.chipsPerColumn(heightDp, showLabel)
        Row(modifier = GlanceModifier.fillMaxSize()) {
            for (day in state.days) {
                WeekDayColumn(
                    day = day,
                    now = state.now,
                    isToday = day.date == state.today,
                    visible = visible,
                    showLabel = showLabel,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                )
            }
        }
    }
}

/** 直長版面：每天一列，左邊日期、右邊整行寬度的行程（時間＋標題）。 */
@Composable
private fun WeekRows(state: WeekState.Loaded, lines: Int) {
    val style = LocalWidgetStyle.current
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        for (day in state.days) {
            val isToday = day.date == state.today
            val color = dayColor(day)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .then(
                        // 今天整列用卡片色標示。
                        if (isToday) {
                            GlanceModifier.cardBackground(style)
                        } else {
                            GlanceModifier
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clickable(openInCalendar(openDay(day.date))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = GlanceModifier.width(44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()),
                        style = style.text(10, color, FontWeight.Medium, TextAlign.Center),
                    )
                    ExpressiveText(
                        text = day.date.dayOfMonth.toString(),
                        size = 18,
                        color = if (isToday) style.colors.primary else color,
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight().padding(start = 6.dp)) {
                    val label = if (day.holiday == HolidayKind.MAKEUP_WORKDAY) glanceString(R.string.makeup_workday) else day.label
                    // 假日一定顯示；農曆、節氣只在還有空行時顯示，不擠掉行程。
                    val showLabel = label != null && (day.holiday != null || day.events.size < lines)
                    if (showLabel && label != null) {
                        Text(
                            text = label,
                            maxLines = 1,
                            style = style.text(
                                11,
                                if (day.holiday == HolidayKind.DAY_OFF) style.colors.sunday else style.colors.onSurfaceVariant,
                            ),
                        )
                    }
                    val room = (if (showLabel) lines - 1 else lines).coerceAtLeast(1)
                    val shown = day.events.take(room)
                    for (event in shown) {
                        // 今天已經結束的行程：色條轉灰、文字用次要色。
                        val past = !event.allDay && event.end <= state.now
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = GlanceModifier
                                    .width(3.dp)
                                    .height(12.dp)
                                    .background(ColorProvider(Color(if (past) Desaturate.color(event.color) else event.color)))
                                    .cornerRadius(2.dp),
                            ) {}
                            val title = TitleShortener.compact(event.title)
                            Text(
                                text = if (event.allDay) title else "${timeFormat.format(Date(event.begin))}  $title",
                                maxLines = 1,
                                modifier = GlanceModifier.padding(start = 5.dp),
                                style = style.text(12, if (past) style.colors.onSurfaceVariant else style.colors.onSurface),
                            )
                        }
                    }
                    val hidden = day.more + (day.events.size - shown.size)
                    if (hidden > 0) {
                        Text(text = "+$hidden", style = style.text(10, style.colors.onSurfaceVariant))
                    }
                }
            }
        }
    }
}

/** 日期的顏色：放假日、週日用假日色，週六用週六色；補班日即使是週末也用一般色。 */
@Composable
private fun dayColor(day: WeekDay): ColorProvider {
    val colors = LocalWidgetStyle.current.colors
    return when {
        day.holiday == HolidayKind.DAY_OFF -> colors.sunday
        day.holiday == HolidayKind.MAKEUP_WORKDAY -> colors.onSurface
        day.date.dayOfWeek == DayOfWeek.SUNDAY -> colors.sunday
        day.date.dayOfWeek == DayOfWeek.SATURDAY -> colors.saturday
        else -> colors.onSurface
    }
}

@Composable
private fun WeekDayColumn(
    day: WeekDay,
    now: Long,
    isToday: Boolean,
    visible: Int,
    showLabel: Boolean,
    modifier: GlanceModifier,
) {
    val style = LocalWidgetStyle.current
    val dayColor = dayColor(day)

    Column(
        modifier = modifier.padding(horizontal = 1.dp).clickable(openInCalendar(openDay(day.date))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.date.dayOfWeek.getDisplayName(DayTextStyle.NARROW, Locale.getDefault()),
            style = style.text(10, dayColor, FontWeight.Medium, TextAlign.Center),
        )
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .then(
                    if (isToday) {
                        GlanceModifier.background(style.colors.primary).cornerRadius(12.dp)
                    } else {
                        GlanceModifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = style.text(
                    size = 13,
                    color = if (isToday) style.colors.onPrimary else dayColor,
                    weight = FontWeight.Bold,
                    align = TextAlign.Center,
                ),
            )
        }
        val label = if (day.holiday == HolidayKind.MAKEUP_WORKDAY) glanceString(R.string.makeup_workday) else day.label
        if (showLabel && label != null) {
            Text(
                text = label,
                maxLines = 1,
                style = style.text(
                    size = 8,
                    color = if (day.holiday == HolidayKind.DAY_OFF) style.colors.sunday else style.colors.onSurfaceVariant,
                    align = TextAlign.Center,
                ),
            )
        }
        val shown = day.events.take(visible)
        for (event in shown) EventChip(event, past = !event.allDay && event.end <= now)
        val hidden = day.more + (day.events.size - shown.size)
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = style.text(9, style.colors.onSurfaceVariant, align = TextAlign.Center),
            )
        }
    }
}

/** 行程色塊：用事件顏色的淡色當底，左右空間很窄所以只放標題。 */
@Composable
private fun EventChip(event: CalendarEvent, past: Boolean) {
    val style = LocalWidgetStyle.current
    val color = if (past) Desaturate.color(event.color) else event.color
    Text(
        text = TitleShortener.compact(event.title),
        maxLines = 1,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .background(ColorProvider(Color(color).copy(alpha = if (past) 0.18f else 0.30f)))
            .cornerRadius(4.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        style = style.text(9, if (past) style.colors.onSurfaceVariant else style.colors.onSurface, FontWeight.Medium),
    )
}

private fun openDay(date: LocalDate): Intent {
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return Intent(
        Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build(),
    )
}
