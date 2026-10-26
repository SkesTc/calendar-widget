package com.skestc.calendarwidget.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.data.DayCell
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.MonthData
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface MonthState {
    data object NoPermission : MonthState
    data class Loaded(val data: MonthData, val monthOffset: Int) : MonthState
}

/** 月曆 widget：有事件的日子會打上日曆顏色的小圓點，可以切換月份；配色依外觀設定。 */
class MonthWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT = DpSize(180.dp, 180.dp)
        private val REGULAR = DpSize(250.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, REGULAR))

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
        return MonthState.Loaded(builder.build(month, today, events, labeler), settings.monthOffset)
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
                // 格子夠高才放農曆／假日小字（約每格 40dp 以上）。
                val showLabels = !compact && size.height >= 260.dp
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
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                            )
                        }
                    }
                }
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
            NavButton("今", delta = 0)
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
            style = style.text(if (label == "今") 13 else 18, style.colors.onCard, FontWeight.Bold),
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
        modifier = modifier.clickable(actionStartIntent(openDayIntent(day.date))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(circle)
                .then(
                    if (isToday) {
                        GlanceModifier.background(style.colors.primary).cornerRadius(circle / 2)
                    } else {
                        GlanceModifier
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
        HolidayKind.MAKEUP_WORKDAY -> "補班"
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
