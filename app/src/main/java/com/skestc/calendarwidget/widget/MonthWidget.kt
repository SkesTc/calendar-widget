package com.skestc.calendarwidget.widget

import android.content.ContentUris
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
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.data.DayCell
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.MonthData
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.Desaturate
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.cardBackground
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface MonthState {
    data object NoPermission : MonthState
    data class Loaded(
        val data: MonthData,
        val monthOffset: Int,
        /** 選取的日期與當天行程，顯示在月曆下方（widget 夠高時）。 */
        val selected: LocalDate = data.today,
        val selectedEvents: List<CalendarEvent> = emptyList(),
        /** 已結束的行程用淡色顯示。 */
        val now: Long = System.currentTimeMillis(),
    ) : MonthState
}

/** 月曆 widget：有事件的日子會打上日曆顏色的小圓點，可以切換月份；配色依外觀設定。 */
class MonthWidget : GlanceAppWidget() {

    companion object {
        /** 顯示農曆／假日小字的最小高度。 */
        val LABELS_MIN_HEIGHT = 260.dp
        /** 下方加上選取日期行程的最小高度。 */
        val PANEL_MIN_HEIGHT = 380.dp
        /** 平板、摺疊機展開：行程改放在月曆右邊。 */
        val WIDE_MIN_WIDTH = 480.dp
    }

    /**
     * 月曆的版面最大（約 40 格，每格有數字、小字、圓點和點擊動作），用 Exact：
     * 只依 widget 的實際大小畫（Android 12+ 直向、橫向各一份），改大小時 Glance 會自動重畫。
     * Responsive 會把每個宣告尺寸的版面一次全部送給桌面，月曆可能超過 Binder 的 1MB 上限而無法更新；
     * 而且 LocalSize 會是宣告的尺寸而不是實際大小，版面門檻很容易對不上。
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = CalendarRepository(context)
        val initialSettings = WidgetSettings.from(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id),
        )
        val initial = load(repository, initialSettings)

        provideContent {
            val settings = WidgetSettings.from(currentState<Preferences>())
            val state = rememberWidgetData(settings, initialSettings, initial) {
                load(repository, it)
            }
            GlanceTheme {
                ProvideWidgetStyle(settings.appearance) {
                    MonthContent(state)
                }
            }
        }
    }

    private suspend fun load(repository: CalendarRepository, settings: WidgetSettings): MonthState {
        if (!repository.hasPermission()) return MonthState.NoPermission
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today).plusMonths(settings.monthOffset.toLong())
        val builder = MonthBuilder(zone)
        val range = builder.gridRange(month)
        // 前後各多抓一天，涵蓋以 UTC 午夜計的全天事件。
        val start = range.start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = range.endInclusive.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repository.instances(start, end, settings.hiddenCalendarIds)
        val labeler = if (settings.showLunar) DayLabeler(IcuLunarSource()) else null
        val selected = builder.selectedDate(month, today, settings.selectedEpochDay?.let(LocalDate::ofEpochDay))
        return MonthState.Loaded(
            data = builder.build(month, today, events, labeler),
            monthOffset = settings.monthOffset,
            selected = selected,
            selectedEvents = builder.eventsOn(events, selected),
        )
    }
}

@Composable
internal fun MonthContent(state: MonthState) {
    val style = LocalWidgetStyle.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(style)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        when (state) {
            MonthState.NoPermission -> PermissionPrompt()
            is MonthState.Loaded -> {
                val size = LocalSize.current
                val compact = size.width < 250.dp
                val wide = size.width >= MonthWidget.WIDE_MIN_WIDTH
                // 格子夠高才放農曆／假日小字（約每格 40dp 以上）。
                val showLabels = !compact && size.height >= MonthWidget.LABELS_MIN_HEIGHT
                // 再高一點（約 4×5 以上）時，點日期改成在下方列出當天行程。
                val panelBelow = !wide && !compact && size.height >= MonthWidget.PANEL_MIN_HEIGHT
                val selectable = wide || panelBelow
                if (wide) {
                    // 平板、摺疊機：月曆在左、選取日期的行程在右。
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            MonthGrid(state, compact = false, showLabels = showLabels, selectable = true)
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        DayPanel(
                            date = state.selected,
                            events = state.selectedEvents,
                            now = state.now,
                            maxEvents = 6,
                            modifier = GlanceModifier.width(200.dp).fillMaxHeight(),
                        )
                    }
                } else {
                    MonthGrid(state, compact, showLabels, selectable)
                    if (panelBelow) {
                        DayPanel(
                            date = state.selected,
                            events = state.selectedEvents,
                            now = state.now,
                            maxEvents = 3,
                            modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 標題、星期列與日期格子；放在 Column 裡，週列平分剩下的高度。 */
@Composable
private fun ColumnScope.MonthGrid(state: MonthState.Loaded, compact: Boolean, showLabels: Boolean, selectable: Boolean) {
    MonthHeader(state.data.month, state.monthOffset)
    WeekdayRow(state.data.firstDayOfWeek, compact)
    for (week in state.data.weeks) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            for (day in week) {
                DayCellView(
                    day = day,
                    today = state.data.today,
                    compact = compact,
                    showLabel = showLabels,
                    selected = selectable && day.date == state.selected,
                    selectInWidget = selectable,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, monthOffset: Int) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    val title = DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale)
        .format(month)
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight(),
            style = style.text(16, style.colors.onSurface, FontWeight.Bold),
        )
        if (monthOffset != 0) {
            NavButton(glanceString(R.string.back_to_this_month), delta = 0)
            Spacer(GlanceModifier.width(4.dp))
        }
        NavButton("‹", delta = -1)
        Spacer(GlanceModifier.width(4.dp))
        NavButton("›", delta = 1)
        Spacer(GlanceModifier.width(4.dp))
        AddEventButton()
    }
}

@Composable
private fun NavButton(label: String, delta: Int) {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .size(32.dp)
            .background(style.colors.card)
            .cornerRadius(16.dp)
            .clickable(
                actionRunCallback<ChangeMonthAction>(
                    actionParametersOf(ChangeMonthAction.DeltaKey to delta),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = style.text(if (delta == 0) 13 else 18, style.colors.onCard, FontWeight.Bold),
        )
    }
}

@Composable
private fun WeekdayRow(firstDayOfWeek: DayOfWeek, compact: Boolean) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        for (i in 0 until 7) {
            val day = firstDayOfWeek.plus(i.toLong())
            Text(
                text = day.getDisplayName(DayTextStyle.NARROW, locale),
                modifier = GlanceModifier.defaultWeight(),
                style = style.text(
                    size = if (compact) 10 else 11,
                    color = weekendColor(day) ?: style.colors.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    align = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun DayCellView(
    day: DayCell,
    today: LocalDate,
    compact: Boolean,
    showLabel: Boolean,
    selected: Boolean,
    selectInWidget: Boolean,
    modifier: GlanceModifier,
) {
    val style = LocalWidgetStyle.current
    val isToday = day.date == today
    val circle = when {
        compact -> 22.dp
        showLabel -> 24.dp
        else -> 28.dp
    }

    val textColor = when {
        isToday -> style.colors.onPrimary
        !day.inMonth -> style.colors.outline
        day.holiday == HolidayKind.DAY_OFF -> style.colors.sunday
        // 補班日就算是週末也用一般顏色。
        day.holiday == HolidayKind.MAKEUP_WORKDAY -> style.colors.onSurface
        else -> weekendColor(day.date.dayOfWeek) ?: style.colors.onSurface
    }

    Column(
        modifier = modifier.clickable(
            if (selectInWidget) {
                actionRunCallback<SelectDayAction>(actionParametersOf(SelectDayAction.EpochDayKey to day.date.toEpochDay()))
            } else {
                openInCalendar(openDayIntent(day.date))
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(circle)
                .then(
                    when {
                        isToday -> GlanceModifier.background(style.colors.primary).cornerRadius(circle / 2)
                        // 選取的（非今天）日期用卡片色圓底標示。
                        selected -> GlanceModifier.background(style.colors.card).cornerRadius(circle / 2)
                        else -> GlanceModifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = style.text(
                    size = if (compact) 12 else 14,
                    color = textColor,
                    weight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    align = TextAlign.Center,
                ),
            )
        }
        if (showLabel) DayLabelText(day)
        EventDots(day.eventColors, dimmed = !day.inMonth)
    }
}

@Composable
private fun DayLabelText(day: DayCell) {
    val style = LocalWidgetStyle.current
    val text = when (day.holiday) {
        HolidayKind.MAKEUP_WORKDAY -> glanceString(R.string.makeup_workday)
        else -> day.label
    } ?: return
    val color = when {
        !day.inMonth -> style.colors.outline
        day.holiday == HolidayKind.DAY_OFF -> style.colors.sunday
        day.labelHighlighted -> style.colors.primary
        else -> style.colors.onSurfaceVariant
    }
    Text(
        text = text,
        maxLines = 1,
        style = style.text(9, color, align = TextAlign.Center),
    )
}

@Composable
private fun EventDots(colors: List<Int>, dimmed: Boolean) {
    // 沒有事件時也保留一樣的高度，讓每一格的日期數字對齊。
    Row(
        modifier = GlanceModifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (colors.isEmpty()) {
            Spacer(GlanceModifier.size(5.dp))
        }
        colors.forEachIndexed { index, color ->
            if (index > 0) Spacer(GlanceModifier.width(2.dp))
            val dotColor = Color(color).let { if (dimmed) it.copy(alpha = 0.4f) else it }
            Box(
                modifier = GlanceModifier
                    .size(5.dp)
                    .background(ColorProvider(dotColor))
                    .cornerRadius(3.dp),
            ) {}
        }
    }
}

/** 選取那天的日期與行程（下方或右側）；點行程開啟該事件、點標題開啟那一天。 */
@Composable
private fun DayPanel(date: LocalDate, events: List<CalendarEvent>, now: Long, maxEvents: Int, modifier: GlanceModifier) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    val title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdEEEE"), locale).format(date)
    Column(
        modifier = modifier
            .cardBackground(style)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(openInCalendar(openDayIntent(date))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = GlanceModifier.defaultWeight(),
                style = style.text(12, style.colors.onCard, FontWeight.Bold),
            )
            Text(
                text = glanceString(R.string.month_open_day),
                style = style.text(11, style.colors.onCard),
            )
        }
        if (events.isEmpty()) {
            Text(text = glanceString(R.string.month_day_empty), style = style.text(12, style.colors.onCard))
        }
        val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
        for (event in events.take(maxEvents)) {
            // 已結束的行程：圓點轉灰、文字用次要色。
            val past = !event.allDay && event.end <= now
            val openEvent = Intent(
                Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId),
            ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp).clickable(openInCalendar(openEvent)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .background(ColorProvider(Color(if (past) Desaturate.color(event.color) else event.color)))
                        .cornerRadius(3.dp),
                ) {}
                Text(
                    text = (if (event.allDay) glanceString(R.string.all_day) else timeFormat.format(Date(event.begin))) +
                        "  " + event.title,
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 6.dp),
                    style = style.text(12, if (past) style.colors.onSurfaceVariant else style.colors.onCard),
                )
            }
        }
        if (events.size > maxEvents) {
            Text(text = "+${events.size - maxEvents}", style = style.text(11, style.colors.onCard))
        }
    }
}

/** 週日、週六用主題指定的顏色，跟一般紙本月曆一樣。 */
@Composable
private fun weekendColor(day: DayOfWeek): ColorProvider? {
    val colors = LocalWidgetStyle.current.colors
    return when (day) {
        DayOfWeek.SUNDAY -> colors.sunday
        DayOfWeek.SATURDAY -> colors.saturday
        else -> null
    }
}

/** 開啟 Google 日曆（或系統預設日曆 App）的某一天。 */
private fun openDayIntent(date: LocalDate): Intent {
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(millis.toString())
        .build()
    return Intent(Intent.ACTION_VIEW, uri)
}
