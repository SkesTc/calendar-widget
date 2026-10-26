package com.skestc.calendarwidget.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import android.appwidget.AppWidgetManager
import android.net.Uri
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.skestc.calendarwidget.config.WidgetConfigActivity
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
import com.skestc.calendarwidget.MainActivity
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.AgendaFormatter
import com.skestc.calendarwidget.data.AgendaRow
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.data.EventStatus
import com.skestc.calendarwidget.settings.AgendaLayout
import com.skestc.calendarwidget.settings.EventEditor
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.Desaturate
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.ExpressiveText
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.Segment
import com.skestc.calendarwidget.theme.cardBackground
import com.skestc.calendarwidget.theme.widgetBackground
import java.time.LocalDate
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface AgendaState {
    data object NoPermission : AgendaState
    data class Loaded(
        val today: LocalDate,
        val days: Int,
        val rows: List<AgendaRow>,
        /** 標題下方的農曆日期（例如「九月十七 · 霜降」），關閉農曆時為 null。 */
        val lunarToday: String?,
        val layout: AgendaLayout = AgendaLayout.LIST,
        /** 時間軸版面判斷進行中／已結束、畫「現在」橫線用。 */
        val now: Long = System.currentTimeMillis(),
    ) : AgendaState
}

/** 一次最多列出的行程數，避免清單太大而無法送到桌面。 */
private const val MAX_EVENTS = 150

/** 行程清單 widget：大字日期 + 依日期分組的事件清單，配色依每個 widget 的外觀設定。 */
class AgendaWidget : GlanceAppWidget() {

    /**
     * 清單涵蓋一個月以上、行程很多，用 Exact 只送實際大小的版面：
     * Responsive 會把每個宣告尺寸的整份清單一起送給桌面，容易超過 Binder 的 1MB 上限。
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
                    AgendaContent(state)
                }
            }
        }
    }

    private suspend fun load(
        context: Context,
        repository: CalendarRepository,
        settings: WidgetSettings,
    ): AgendaState {
        if (!repository.hasPermission()) return AgendaState.NoPermission
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val events = repository.upcomingEvents(
            days = settings.agendaDays,
            limit = MAX_EVENTS,
            hiddenCalendarIds = settings.hiddenCalendarIds,
            now = now,
            keepEndedToday = true,
        )
        // 下一個行程結束時重畫，讓它變成淡色。
        events.filter { !it.allDay && it.end > now }.minOfOrNull { it.end }
            ?.let { DayChangeScheduler.scheduleBoundary(context, it) }
        val labeler = if (settings.showLunar) DayLabeler(IcuLunarSource()) else null
        return AgendaState.Loaded(
            today = today,
            days = settings.agendaDays,
            rows = AgendaFormatter(context).rows(events, today, labeler),
            lunarToday = labeler?.labelOf(today)?.long,
            layout = settings.agendaLayout,
            now = now,
        )
    }
}

@Composable
internal fun AgendaContent(state: AgendaState) {
    val style = LocalWidgetStyle.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(style)
            .padding(16.dp),
    ) {
        when (state) {
            AgendaState.NoPermission -> PermissionPrompt()
            is AgendaState.Loaded -> {
                DateHeader(state.today, state.lunarToday)
                Spacer(GlanceModifier.height(8.dp))
                if (LocalSize.current.width < 180.dp) {
                    NextEventOnly(state.rows, state.days)
                } else {
                    when (state.layout) {
                        AgendaLayout.LIST -> AgendaList(state.rows, state.days)
                        AgendaLayout.TIMELINE -> AgendaTimeline(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(today: LocalDate, lunar: String?) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    // 點日期（左上角）開啟 Google 日曆的今天；沒裝 Google 日曆時用系統預設的日曆 App。
    val openToday = openInCalendar(openDayIntent(today), preferredPackage = EventEditor.GOOGLE_CALENDAR)
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveText(
            text = today.dayOfMonth.toString(),
            size = 40,
            color = style.colors.primary,
            modifier = GlanceModifier.clickable(openToday),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight().clickable(openToday)) {
            Text(
                text = today.dayOfWeek.getDisplayName(DayTextStyle.FULL, locale),
                style = style.text(16, style.colors.onSurface, FontWeight.Bold),
            )
            Text(
                text = listOfNotNull(DateTimeFormatter.ofPattern("yyyy/MM", locale).format(today), lunar)
                    .joinToString(" · "),
                maxLines = 1,
                style = style.text(12, style.colors.onSurfaceVariant),
            )
        }
        HeaderActions()
    }
}

/** 「＋」旁的齒輪：打開這個 widget 自己的設定頁（主題、日曆、顯示天數…）。 */
@Composable
internal fun SettingsButton(size: Dp = 36.dp) {
    val style = LocalWidgetStyle.current
    val context = LocalContext.current
    val glanceId = LocalGlanceId.current
    // 測試或預覽時沒有真正的 widget id，就只畫按鈕。
    val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
    val open = appWidgetId?.let {
        actionStartIntent(
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, it)
                // 每個 widget 一個 PendingIntent，避免開到別的 widget 的設定。
                .setData(Uri.parse("calendarwidget://config/$it")),
        )
    }
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(style.colors.card)
            .cornerRadius(size / 2)
            .then(if (open != null) GlanceModifier.clickable(open) else GlanceModifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_settings),
            contentDescription = glanceString(R.string.widget_settings),
            colorFilter = ColorFilter.tint(style.colors.onCard),
            modifier = GlanceModifier.size(size * 0.55f),
        )
    }
}

/** 標題列右側的按鈕：齒輪（設定）＋「＋」（新增行程）。 */
@Composable
internal fun HeaderActions() {
    SettingsButton()
    Spacer(GlanceModifier.width(6.dp))
    AddEventButton()
}

@Composable
internal fun AddEventButton() {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(style.colors.primary)
            .cornerRadius(18.dp)
            // 依設定開啟指定 App 的新增行程畫面（或每次詢問）。
            .clickable(openInCalendar(EventEditor.insertIntent(), editor = true)),
        contentAlignment = Alignment.Center,
    ) {
        // 用向量圖示而不是「+」文字：字型的上下留白會讓文字在圓圈裡偏離中心。
        Image(
            provider = ImageProvider(R.drawable.ic_add),
            contentDescription = glanceString(R.string.add_event),
            colorFilter = ColorFilter.tint(style.colors.onPrimary),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

@Composable
private fun AgendaList(rows: List<AgendaRow>, days: Int) {
    if (rows.isEmpty()) {
        EmptyState(days)
        return
    }
    val segments = segmentsOf(rows)
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows.indices.toList(), itemId = { rows[it].id }) { index ->
            when (val row = rows[index]) {
                is AgendaRow.Header -> DayHeaderRow(row)
                is AgendaRow.Event -> EventRow(row.event, row.timeLabel, row.past, segments[index])
                AgendaRow.NowMarker -> NowMarkerRow()
            }
        }
    }
}

/** 時間軸版面：每天一組，左側時間欄＋圓點，今天的行程之間有「現在」橫線（同「今日時間軸」）。 */
@Composable
private fun AgendaTimeline(state: AgendaState.Loaded) {
    if (state.rows.isEmpty()) {
        EmptyState(state.days)
        return
    }
    val rows = AgendaRow.withNowMarker(state.rows, state.today, state.now)
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows, itemId = { it.id }) { row ->
            when (row) {
                is AgendaRow.Header -> DayHeaderRow(row)
                is AgendaRow.Event -> TimelineEventRow(row.event, statusOf(row, state.now))
                AgendaRow.NowMarker -> NowMarkerRow()
            }
        }
    }
}

internal fun statusOf(row: AgendaRow.Event, now: Long): EventStatus = when {
    row.past -> EventStatus.PAST
    !row.event.allDay && row.event.begin <= now && now < row.event.end -> EventStatus.NOW
    else -> EventStatus.FUTURE
}

/** 同一天連續、未結束的行程連成一組卡片；已結束的行程沒有底色，會把組切開。 */
internal fun segmentsOf(rows: List<AgendaRow>): List<Segment> {
    fun card(i: Int) = (rows.getOrNull(i) as? AgendaRow.Event)?.past == false
    return rows.indices.map {
        if (card(it)) Segment.of(hasPrevious = card(it - 1), hasNext = card(it + 1)) else Segment.SINGLE
    }
}

@Composable
private fun NextEventOnly(rows: List<AgendaRow>, days: Int) {
    // 2×2 只放得下一筆：跳過今天已經結束的。
    val next = rows.firstNotNullOfOrNull { (it as? AgendaRow.Event)?.takeIf { row -> !row.past } }
    if (next == null) EmptyState(days) else EventRow(next.event, next.timeLabel, past = false)
}

@Composable
private fun DayHeaderRow(header: AgendaRow.Header) {
    val style = LocalWidgetStyle.current
    val color = if (header.dayOff) style.colors.sunday else style.colors.primary
    Text(
        text = listOfNotNull(header.label, header.note).joinToString(" · "),
        maxLines = 1,
        modifier = GlanceModifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        style = style.text(12, color, FontWeight.Bold),
    )
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    timeLabel: String,
    past: Boolean,
    segment: Segment = Segment.SINGLE,
) {
    val style = LocalWidgetStyle.current
    val openEvent = Intent(
        Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId),
    ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    // 已結束的行程：拿掉卡片底色、色條改成灰階、文字用次要色，看起來「褪色」但仍讀得到。
    val textColor = if (past) style.colors.onSurfaceVariant else style.colors.onCard
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = if (segment == Segment.TOP || segment == Segment.MIDDLE) 2.dp else 6.dp)
            .then(if (past) GlanceModifier else GlanceModifier.cardBackground(style, segment))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(openInCalendar(openEvent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 色條沿用 Google 日曆裡設定的事件／日曆顏色。
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(32.dp)
                .background(ColorProvider(Color(if (past) Desaturate.color(event.color) else event.color)))
                .cornerRadius(2.dp),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = 1,
                style = style.text(14, textColor, FontWeight.Medium),
            )
            Text(
                text = listOfNotNull(timeLabel, event.location).joinToString(" · "),
                maxLines = 1,
                style = style.text(12, textColor),
            )
        }
    }
}

@Composable
private fun EmptyState(days: Int) {
    val style = LocalWidgetStyle.current
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = glanceString(R.string.agenda_empty, days),
            style = style.text(13, style.colors.onSurfaceVariant),
        )
    }
}

@Composable
internal fun PermissionPrompt() {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glanceString(R.string.tap_to_grant),
            style = style.text(14, style.colors.primary, FontWeight.Medium),
        )
    }
}
