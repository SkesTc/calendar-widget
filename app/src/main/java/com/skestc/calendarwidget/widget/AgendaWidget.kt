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
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.skestc.calendarwidget.ui.SearchActivity
import com.skestc.calendarwidget.ui.WidgetMenuActivity
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
import androidx.glance.appwidget.action.actionRunCallback
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
import com.skestc.calendarwidget.data.MonthLayout
import com.skestc.calendarwidget.data.WeekBuilder
import com.skestc.calendarwidget.data.WeekDay
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
        /** 「每日」版面：每天一列（前 7 天一定列出，之後只列有行程或假日的日子）。 */
        val dayRows: List<WeekDay> = emptyList(),
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
            dayRows = if (settings.agendaLayout == AgendaLayout.DAYS) {
                dayRowsOf(events, today, settings.agendaDays, labeler)
            } else {
                emptyList()
            },
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
                        AgendaLayout.DAYS -> AgendaDays(state)
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
    val open = appWidgetIdOrNull()?.let { actionStartIntent(WidgetMenuActivity.settingsIntent(context, it)) }
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

/** 測試或預覽時沒有真正的 widget id，回傳 null（按鈕照畫，只是沒有動作）。 */
@Composable
internal fun appWidgetIdOrNull(): Int? {
    val context = LocalContext.current
    val glanceId = LocalGlanceId.current
    return runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
}

/** 「⋯」：太窄放不下搜尋與設定兩顆按鈕時，合併成一顆，點了跳出小選單。 */
@Composable
internal fun MoreButton(size: Dp = 36.dp) {
    val style = LocalWidgetStyle.current
    val context = LocalContext.current
    val open = appWidgetIdOrNull()?.let { actionStartIntent(WidgetMenuActivity.intentFor(context, it)) }
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(style.colors.card)
            .cornerRadius(size / 2)
            .then(if (open != null) GlanceModifier.clickable(open) else GlanceModifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_more_horiz),
            contentDescription = glanceString(R.string.more_actions),
            colorFilter = ColorFilter.tint(style.colors.onCard),
            modifier = GlanceModifier.size(size * 0.6f),
        )
    }
}

/** 重新整理：重新讀取日曆並更新所有小工具。 */
@Composable
internal fun RefreshButton(size: Dp = 36.dp) {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(style.colors.card)
            .cornerRadius(size / 2)
            .clickable(actionRunCallback<RefreshAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = glanceString(R.string.refresh),
            colorFilter = ColorFilter.tint(style.colors.onCard),
            modifier = GlanceModifier.size(size * 0.6f),
        )
    }
}

/**
 * 「＋」以外的按鈕，依空間放：搜尋、重新整理、設定各一顆；
 * 或只留搜尋、其他收進「⋯」；或全部收進「⋯」。後面接著放「＋」的話自己加間距。
 */
@Composable
internal fun ActionButtons(buttons: MonthLayout.HeaderButtons, size: Dp, gap: Dp) {
    when (buttons) {
        MonthLayout.HeaderButtons.ALL -> {
            SearchButton(size)
            Spacer(GlanceModifier.width(gap))
            RefreshButton(size)
            Spacer(GlanceModifier.width(gap))
            SettingsButton(size)
        }
        MonthLayout.HeaderButtons.SEARCH_AND_MENU -> {
            SearchButton(size)
            Spacer(GlanceModifier.width(gap))
            MoreButton(size)
        }
        MonthLayout.HeaderButtons.MENU -> MoreButton(size)
    }
}

/** 放大鏡：打開 App 的行程搜尋。 */
@Composable
internal fun SearchButton(size: Dp = 36.dp) {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(style.colors.card)
            .cornerRadius(size / 2)
            .clickable(actionStartActivity<SearchActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_search),
            contentDescription = glanceString(R.string.search_title),
            colorFilter = ColorFilter.tint(style.colors.onCard),
            modifier = GlanceModifier.size(size * 0.55f),
        )
    }
}

/** 標題列右側的按鈕：搜尋、齒輪（設定）、「＋」（新增行程）；寬度不到 4 格時按鈕縮小一點。 */
@Composable
internal fun HeaderActions() {
    val width = LocalSize.current.width
    // 2×2 只放得下「＋」；搜尋與設定改從 App 或長按小工具進入。
    if (width < 180.dp) {
        AddEventButton(32.dp)
        return
    }
    // 標題左邊還有大號日期與星期（約 140dp）：寬的時候按鈕全部分開放，
    // 窄一點只留搜尋、其他收進「⋯」，再窄就只有「⋯」，避免按鈕被擠出畫面。
    val buttons = when {
        width >= 340.dp -> MonthLayout.HeaderButtons.ALL
        width >= 280.dp -> MonthLayout.HeaderButtons.SEARCH_AND_MENU
        else -> MonthLayout.HeaderButtons.MENU
    }
    val size = if (width >= 280.dp) 36.dp else 32.dp
    val gap = if (width >= 280.dp) 6.dp else 4.dp
    ActionButtons(buttons, size, gap)
    Spacer(GlanceModifier.width(gap))
    AddEventButton(size)
}

@Composable
internal fun AddEventButton(size: Dp = 36.dp) {
    val style = LocalWidgetStyle.current
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(style.colors.primary)
            .cornerRadius(size / 2)
            // 依設定開啟指定 App 的新增行程畫面（或每次詢問）。
            .clickable(openInCalendar(EventEditor.insertIntent(), editor = true)),
        contentAlignment = Alignment.Center,
    ) {
        // 用向量圖示而不是「+」文字：字型的上下留白會讓文字在圓圈裡偏離中心。
        Image(
            provider = ImageProvider(R.drawable.ic_add),
            contentDescription = glanceString(R.string.add_event),
            colorFilter = ColorFilter.tint(style.colors.onPrimary),
            modifier = GlanceModifier.size(size * 0.6f),
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

@Composable
private fun AgendaDays(state: AgendaState.Loaded) {
    if (state.dayRows.isEmpty()) {
        EmptyState(state.days)
        return
    }
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(state.dayRows, itemId = { it.date.toEpochDay() }) { day ->
            WeekDayRow(
                day = day,
                isToday = day.date == state.today,
                now = state.now,
                lines = DAY_ROW_MAX_EVENTS,
                modifier = GlanceModifier.padding(vertical = 4.dp),
                // 清單可以捲動：長標題換行完整顯示。
                wrapTitles = true,
            )
        }
    }
}

private const val DAY_ROW_MAX_EVENTS = 20

/** 「每日」版面的資料：前 7 天每天都列（空的日子顯示農曆），之後只留有行程或假日的日子。 */
internal fun dayRowsOf(events: List<CalendarEvent>, today: LocalDate, days: Int, labeler: DayLabeler?): List<WeekDay> =
    WeekBuilder().build(events, today, days, DAY_ROW_MAX_EVENTS, labeler)
        .filterIndexed { index, day -> index < 7 || day.events.isNotEmpty() || day.holiday != null }

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
