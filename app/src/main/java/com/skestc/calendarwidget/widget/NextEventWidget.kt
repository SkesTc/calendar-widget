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
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.NextEvent
import com.skestc.calendarwidget.data.NextEventPicker
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

private sealed interface NextEventState {
    data object NoPermission : NextEventState
    data class Loaded(val today: LocalDate, val next: NextEvent?) : NextEventState
}

/**
 * 「下一個行程」極簡 widget（2×1 起）：只顯示進行中或下一個行程，適合放在時鐘或搜尋列旁邊。
 * 行程開始或結束時自動換下一個。
 */
class NextEventWidget : GlanceAppWidget() {

    companion object {
        private val TINY = DpSize(110.dp, 40.dp)
        private val WIDE = DpSize(180.dp, 40.dp)
        private val TALL = DpSize(180.dp, 100.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TINY, WIDE, TALL))

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
                    NextEventContent(state)
                }
            }
        }
    }

    private suspend fun load(
        context: Context,
        repository: CalendarRepository,
        settings: WidgetSettings,
    ): NextEventState {
        if (!repository.hasPermission()) return NextEventState.NoPermission
        val now = System.currentTimeMillis()
        val events = repository.upcomingEvents(days = 7, limit = 50, hiddenCalendarIds = settings.hiddenCalendarIds)
        val next = NextEventPicker.pick(events, now)
        NextEventPicker.nextBoundary(next, now)?.let { DayChangeScheduler.scheduleBoundary(context, it) }
        return NextEventState.Loaded(LocalDate.now(), next)
    }
}

@Composable
private fun NextEventContent(state: NextEventState) {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(style)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (state) {
            NextEventState.NoPermission -> PermissionPrompt()
            is NextEventState.Loaded -> {
                val next = state.next
                if (next == null) NoUpcoming() else NextEventRow(next, state.today)
            }
        }
    }
}

@Composable
private fun NextEventRow(next: NextEvent, today: LocalDate) {
    val style = LocalWidgetStyle.current
    val context = LocalContext.current
    val size = LocalSize.current
    val event = next.event
    val tall = size.height >= 100.dp
    val openEvent = Intent(
        Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId),
    ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    Row(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartIntent(openEvent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .fillMaxHeight()
                .background(ColorProvider(Color(event.color)))
                .cornerRadius(2.dp),
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = whenLabel(context, next, today),
                maxLines = 1,
                style = style.text(11, if (next.inProgress) style.colors.primary else style.colors.onSurfaceVariant, FontWeight.Bold),
            )
            Text(
                text = event.title,
                maxLines = if (tall) 2 else 1,
                style = style.text(if (tall) 17 else 15, style.colors.onSurface, FontWeight.Bold),
            )
            val location = event.location
            if (tall && location != null) {
                Text(
                    text = location,
                    maxLines = 1,
                    style = style.text(12, style.colors.onSurfaceVariant),
                )
            }
        }
    }
}

/** 「進行中 · 到 15:00」「今天 14:00」「明天 · 全天」「10/28（三）09:00」。 */
private fun whenLabel(context: Context, next: NextEvent, today: LocalDate): String {
    val event = next.event
    val timeFormat = DateFormat.getTimeFormat(context)
    if (next.inProgress && !event.allDay) return "進行中 · 到 ${timeFormat.format(Date(event.end))}"
    val zone = if (event.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate()
    val day = when {
        date <= today -> "今天"
        date == today.plusDays(1) -> "明天"
        else -> DateTimeFormatter.ofPattern("M/d（EEEEE）", Locale.getDefault()).format(date)
    }
    return if (event.allDay) "$day · 全天" else "$day ${timeFormat.format(Date(event.begin))}"
}

@Composable
private fun NoUpcoming() {
    val style = LocalWidgetStyle.current
    val openCalendar = Intent(
        Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build(),
    )
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartIntent(openCalendar)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "接下來 7 天沒有行程",
            maxLines = 1,
            style = style.text(13, style.colors.onSurfaceVariant),
        )
    }
}
