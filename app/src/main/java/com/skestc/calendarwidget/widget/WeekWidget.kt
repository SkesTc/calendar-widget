package com.skestc.calendarwidget.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.WeekBuilder
import com.skestc.calendarwidget.data.WeekDay
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface WeekState {
    data object NoPermission : WeekState
    data class Loaded(val today: LocalDate, val days: List<WeekDay>) : WeekState
}

/** 一週 widget（4×2）：從今天起 7 天並排，每天列出行程色塊；假日紅字、可顯示農曆。 */
class WeekWidget : GlanceAppWidget() {

    companion object {
        private val SHORT = DpSize(250.dp, 110.dp)
        private val TALL = DpSize(250.dp, 180.dp)

        /** 先多載一些，畫面依高度決定實際顯示幾筆。 */
        private const val MAX_PER_DAY = 4
    }

    override val sizeMode = SizeMode.Responsive(setOf(SHORT, TALL))

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
                    WeekContent(state)
                }
            }
        }
    }

    private suspend fun load(repository: CalendarRepository, settings: WidgetSettings): WeekState {
        if (!repository.hasPermission()) return WeekState.NoPermission
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 前後各多抓一天，涵蓋以 UTC 午夜計的全天事件。
        val start = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repository.instances(start, end, settings.hiddenCalendarIds)
        val labeler = if (settings.showLunar) DayLabeler(IcuLunarSource()) else null
        return WeekState.Loaded(today, WeekBuilder(zone).build(events, today, 7, MAX_PER_DAY, labeler))
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
                val height = LocalSize.current.height
                val visible = when {
                    height < 140.dp -> 2
                    height < 200.dp -> 3
                    else -> 4
                }
                val showLabel = height >= 140.dp
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    for (day in state.days) {
                        WeekDayColumn(
                            day = day,
                            isToday = day.date == state.today,
                            visible = visible,
                            showLabel = showLabel,
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayColumn(
    day: WeekDay,
    isToday: Boolean,
    visible: Int,
    showLabel: Boolean,
    modifier: GlanceModifier,
) {
    val style = LocalWidgetStyle.current
    val dayColor = when {
        day.holiday == HolidayKind.DAY_OFF -> style.colors.sunday
        day.holiday == HolidayKind.MAKEUP_WORKDAY -> style.colors.onSurface
        day.date.dayOfWeek == DayOfWeek.SUNDAY -> style.colors.sunday
        day.date.dayOfWeek == DayOfWeek.SATURDAY -> style.colors.saturday
        else -> style.colors.onSurface
    }

    Column(
        modifier = modifier.padding(horizontal = 1.dp).clickable(actionStartIntent(openDay(day.date))),
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
        val label = if (day.holiday == HolidayKind.MAKEUP_WORKDAY) "補班" else day.label
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
        for (event in shown) EventChip(event)
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
private fun EventChip(event: CalendarEvent) {
    val style = LocalWidgetStyle.current
    Text(
        text = event.title,
        maxLines = 1,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .background(ColorProvider(Color(event.color).copy(alpha = 0.30f)))
            .cornerRadius(4.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        style = style.text(9, style.colors.onSurface, FontWeight.Medium),
    )
}

private fun openDay(date: LocalDate): Intent {
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return Intent(
        Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build(),
    )
}
