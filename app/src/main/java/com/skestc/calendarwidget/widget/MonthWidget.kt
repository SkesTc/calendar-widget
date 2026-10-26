package com.skestc.calendarwidget.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.DayCell
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.MonthData
import com.skestc.calendarwidget.settings.WidgetSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

private sealed interface MonthState {
    data object NoPermission : MonthState
    data class Loaded(val data: MonthData, val monthOffset: Int) : MonthState
}

/** 月曆 widget：有事件的日子會打上日曆顏色的小圓點，可以切換月份。 */
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
                MonthContent(state)
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
        return MonthState.Loaded(builder.build(month, today, events), settings.monthOffset)
    }
}

@Composable
private fun MonthContent(state: MonthState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        when (state) {
            MonthState.NoPermission -> PermissionPrompt()
            is MonthState.Loaded -> {
                val compact = LocalSize.current.width < 250.dp
                MonthHeader(state.data.month, state.monthOffset)
                WeekdayRow(state.data.firstDayOfWeek, compact)
                for (week in state.data.weeks) {
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (day in week) {
                            DayCellView(
                                day = day,
                                today = state.data.today,
                                compact = compact,
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
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
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
    Box(
        modifier = GlanceModifier
            .size(32.dp)
            .background(GlanceTheme.colors.secondaryContainer)
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
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = if (label == "今") 13.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun WeekdayRow(firstDayOfWeek: DayOfWeek, compact: Boolean) {
    val locale = Locale.getDefault()
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        for (i in 0 until 7) {
            val day = firstDayOfWeek.plus(i.toLong())
            Text(
                text = day.getDisplayName(DayTextStyle.NARROW, locale),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = weekendColor(day) ?: GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun DayCellView(day: DayCell, today: LocalDate, compact: Boolean, modifier: GlanceModifier) {
    val isToday = day.date == today
    val fontSize: TextUnit = if (compact) 12.sp else 14.sp
    val circle = if (compact) 22.dp else 28.dp

    val textColor = when {
        isToday -> GlanceTheme.colors.onPrimary
        !day.inMonth -> GlanceTheme.colors.outline
        else -> weekendColor(day.date.dayOfWeek) ?: GlanceTheme.colors.onSurface
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
                        GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(circle / 2)
                    } else {
                        GlanceModifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        EventDots(day.eventColors, dimmed = !day.inMonth)
    }
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

/** 週日用 error 色、週六用 tertiary 色，跟一般紙本月曆一樣。 */
@Composable
private fun weekendColor(day: DayOfWeek) = when (day) {
    DayOfWeek.SUNDAY -> GlanceTheme.colors.error
    DayOfWeek.SATURDAY -> GlanceTheme.colors.tertiary
    else -> null
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
