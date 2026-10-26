package com.skestc.calendarwidget.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.skestc.calendarwidget.MainActivity
import com.skestc.calendarwidget.data.AgendaFormatter
import com.skestc.calendarwidget.data.AgendaRow
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

private sealed interface AgendaState {
    data object NoPermission : AgendaState
    data class Loaded(val today: LocalDate, val rows: List<AgendaRow>) : AgendaState
}

/** 行程清單 widget：大字日期 + 依日期分組的事件清單，顏色跟隨 Material You。 */
class AgendaWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 220.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = CalendarRepository(context)
        val state = if (repository.hasPermission()) {
            val formatter = AgendaFormatter(context)
            val today = LocalDate.now()
            AgendaState.Loaded(today, formatter.rows(repository.upcomingEvents(), today))
        } else {
            AgendaState.NoPermission
        }

        provideContent {
            GlanceTheme {
                AgendaContent(state)
            }
        }
    }
}

@Composable
private fun AgendaContent(state: AgendaState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when (state) {
            AgendaState.NoPermission -> PermissionPrompt()
            is AgendaState.Loaded -> {
                DateHeader(state.today)
                Spacer(GlanceModifier.height(8.dp))
                if (LocalSize.current.width < 180.dp) {
                    NextEventOnly(state.rows)
                } else {
                    AgendaList(state.rows)
                }
            }
        }
    }
}

@Composable
private fun DateHeader(today: LocalDate) {
    val locale = Locale.getDefault()
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = today.dayOfMonth.toString(),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = today.dayOfWeek.getDisplayName(DayTextStyle.FULL, locale),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = DateTimeFormatter.ofPattern("yyyy/MM", locale).format(today),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        AddEventButton()
    }
}

@Composable
private fun AddEventButton() {
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(18.dp)
            .clickable(actionStartIntent(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun AgendaList(rows: List<AgendaRow>) {
    if (rows.isEmpty()) {
        EmptyState()
        return
    }
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows, itemId = { it.id }) { row ->
            when (row) {
                is AgendaRow.Header -> DayHeaderRow(row.label)
                is AgendaRow.Event -> EventRow(row.event, row.timeLabel)
            }
        }
    }
}

@Composable
private fun NextEventOnly(rows: List<AgendaRow>) {
    val next = rows.firstNotNullOfOrNull { it as? AgendaRow.Event }
    if (next == null) EmptyState() else EventRow(next.event, next.timeLabel)
}

@Composable
private fun DayHeaderRow(label: String) {
    Text(
        text = label,
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 4.dp),
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun EventRow(event: CalendarEvent, timeLabel: String) {
    val openEvent = Intent(
        Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId),
    ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(12.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(actionStartIntent(openEvent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 色條沿用 Google 日曆裡設定的事件／日曆顏色。
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(32.dp)
                .background(ColorProvider(Color(event.color)))
                .cornerRadius(2.dp),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = listOfNotNull(timeLabel, event.location).joinToString(" · "),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "接下來 7 天沒有行程 🎉",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun PermissionPrompt() {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "點此授權讀取日曆",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
