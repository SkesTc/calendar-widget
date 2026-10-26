package com.skestc.calendarwidget.widget

import android.content.ContentUris
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
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.EventStatus
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.Timeline
import com.skestc.calendarwidget.data.TimelineBuilder
import com.skestc.calendarwidget.data.TimelineItem
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.ExpressiveText
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.cardBackground
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayTextStyle
import java.util.Date
import java.util.Locale

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface TimelineState {
    data object NoPermission : TimelineState
    data class Loaded(val today: LocalDate, val note: String?, val timeline: Timeline) : TimelineState
}

/**
 * 時間軸 widget：今天的行程依時間排成一列，標出已結束、進行中、下一個，
 * 以及「現在」的位置和一小時以上的空檔。行程開始或結束時會自動更新。
 */
class TimelineWidget : GlanceAppWidget() {

    companion object {
        private val REGULAR = DpSize(250.dp, 180.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(REGULAR))

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
                    TimelineContent(state)
                }
            }
        }
    }

    private suspend fun load(
        context: Context,
        repository: CalendarRepository,
        settings: WidgetSettings,
    ): TimelineState {
        if (!repository.hasPermission()) return TimelineState.NoPermission
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        // 前後各多抓一天，涵蓋以 UTC 午夜計的全天事件與跨夜行程。
        val start = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repository.instances(start, end, settings.hiddenCalendarIds)

        val builder = TimelineBuilder(zone)
        val timeline = builder.build(events, today, now)
        builder.nextBoundary(timeline, now)?.let { DayChangeScheduler.scheduleBoundary(context, it) }

        val holidays = timeline.allDay.filter { it.holiday == HolidayKind.DAY_OFF }.map { it.title }
        val lunar = if (settings.showLunar) DayLabeler(IcuLunarSource()).labelOf(today).long else null
        val note = (listOfNotNull(lunar) + holidays).joinToString(" · ").ifEmpty { null }
        return TimelineState.Loaded(today, note, timeline)
    }
}

@Composable
internal fun TimelineContent(state: TimelineState) {
    val style = LocalWidgetStyle.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(style)
            .padding(16.dp),
    ) {
        when (state) {
            TimelineState.NoPermission -> PermissionPrompt()
            is TimelineState.Loaded -> {
                TimelineHeader(state.today, state.note)
                Spacer(GlanceModifier.height(6.dp))
                val allDay = state.timeline.allDay.filter { it.holiday != HolidayKind.DAY_OFF }
                if (allDay.isNotEmpty()) AllDayStrip(allDay)
                if (state.timeline.items.isEmpty()) {
                    TimelineEmpty(hasAllDay = allDay.isNotEmpty())
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(state.timeline.items) { item ->
                            when (item) {
                                is TimelineItem.Event -> TimelineEventRow(item.event, item.status)
                                TimelineItem.NowMarker -> NowMarkerRow()
                                is TimelineItem.Gap -> GapRow(item.minutes)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(today: LocalDate, note: String?) {
    val style = LocalWidgetStyle.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ExpressiveText(text = today.dayOfMonth.toString(), size = 34, color = style.colors.primary)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = glanceString(
                    R.string.today_prefix,
                    today.dayOfWeek.getDisplayName(DayTextStyle.FULL, Locale.getDefault()),
                ),
                style = style.text(16, style.colors.onSurface, FontWeight.Bold),
            )
            if (note != null) {
                Text(text = note, maxLines = 1, style = style.text(12, style.colors.onSurfaceVariant))
            }
        }
        AddEventButton()
    }
}

/** 今天的全天事件（含紀念日、補班），一行列出。 */
@Composable
private fun AllDayStrip(events: List<CalendarEvent>) {
    val style = LocalWidgetStyle.current
    Text(
        text = glanceString(
            R.string.timeline_all_day,
            events.joinToString(glanceString(R.string.list_separator)) { it.title },
        ),
        maxLines = 1,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .cardBackground(style)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = style.text(12, style.colors.onCard, FontWeight.Medium),
    )
}

private val TIME_COLUMN = 46.dp
private val RAIL_COLUMN = 18.dp

@Composable
private fun TimelineEventRow(event: CalendarEvent, status: EventStatus) {
    val style = LocalWidgetStyle.current
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    val past = status == EventStatus.PAST
    val current = status == EventStatus.NOW

    val openEvent = Intent(
        Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId),
    ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp).clickable(openInCalendar(openEvent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.format(Date(event.begin)),
            modifier = GlanceModifier.width(TIME_COLUMN),
            style = style.text(
                size = 12,
                color = if (past) style.colors.outline else style.colors.onSurface,
                weight = if (current) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        // 時間軸上的圓點：進行中的放大並用主色，已結束的用淡色。
        Box(modifier = GlanceModifier.width(RAIL_COLUMN), contentAlignment = Alignment.Center) {
            val dot = if (current) 12.dp else 8.dp
            Box(
                modifier = GlanceModifier
                    .size(dot)
                    .background(
                        when {
                            current -> style.colors.primary
                            past -> style.colors.outline
                            else -> ColorProvider(Color(event.color))
                        },
                    )
                    .cornerRadius(dot / 2),
            ) {}
        }
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .then(
                    if (past) {
                        GlanceModifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        GlanceModifier
                            .cardBackground(style)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    },
                ),
        ) {
            val textColor = if (past) style.colors.outline else style.colors.onCard
            Text(
                text = event.title,
                maxLines = 1,
                style = style.text(14, textColor, if (current) FontWeight.Bold else FontWeight.Medium),
            )
            val until = timeFormat.format(Date(event.end))
            val detail = if (current) {
                glanceString(R.string.in_progress_until, until)
            } else {
                "${timeFormat.format(Date(event.begin))} – $until"
            }
            Text(
                text = listOfNotNull(detail, event.location).joinToString(" · "),
                maxLines = 1,
                style = style.text(12, textColor),
            )
        }
    }
}

@Composable
private fun NowMarkerRow() {
    val style = LocalWidgetStyle.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = glanceString(R.string.now),
            modifier = GlanceModifier.width(TIME_COLUMN),
            style = style.text(12, style.colors.primary, FontWeight.Bold),
        )
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(2.dp)
                .background(style.colors.primary)
                .cornerRadius(1.dp),
        ) {}
    }
}

@Composable
private fun GapRow(minutes: Long) {
    val style = LocalWidgetStyle.current
    Text(
        text = glanceString(R.string.free_time, formatDuration(LocalContext.current, minutes)),
        modifier = GlanceModifier.padding(start = TIME_COLUMN + RAIL_COLUMN + 8.dp, top = 2.dp, bottom = 6.dp),
        style = style.text(12, style.colors.onSurfaceVariant),
    )
}

@Composable
private fun TimelineEmpty(hasAllDay: Boolean) {
    val style = LocalWidgetStyle.current
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = glanceString(if (hasAllDay) R.string.timeline_no_more else R.string.timeline_empty),
            style = style.text(13, style.colors.onSurfaceVariant),
        )
    }
}
