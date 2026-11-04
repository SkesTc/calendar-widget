package com.skestc.calendarwidget.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.EventView
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.ViewDay
import com.skestc.calendarwidget.data.ViewMode
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.settings.EventEditor
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.CalendarLaunchActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * App 內的行程檢視：從 widget 點日期或行程時開啟，取代跳到 Google 日曆。
 * 可切換當天、當週、當月、近四週；只顯示那個 widget 勾選的日曆，不含已隱藏的長期行程。
 */
class EventViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, LocalDate.now().toEpochDay()))
        val hidden = intent.getLongArrayExtra(EXTRA_HIDDEN_CALENDARS)?.toSet().orEmpty()
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1).takeIf { it >= 0 }
        setContent {
            CalendarWidgetTheme {
                EventViewerScreen(date, hidden, eventId, onBack = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_DATE = "date"
        private const val EXTRA_HIDDEN_CALENDARS = "hidden_calendars"
        private const val EXTRA_EVENT_ID = "event_id"

        fun intent(context: Context, date: LocalDate, hiddenCalendarIds: Set<Long>, eventId: Long? = null): Intent =
            Intent(context, EventViewerActivity::class.java)
                .putExtra(EXTRA_DATE, date.toEpochDay())
                .putExtra(EXTRA_HIDDEN_CALENDARS, hiddenCalendarIds.toLongArray())
                .putExtra(EXTRA_EVENT_ID, eventId ?: -1L)
                // 每個日期、每組日曆各自一個 Intent。
                .setData(Uri.parse("calendarwidget://view/${date.toEpochDay()}/${eventId ?: -1}/${hiddenCalendarIds.sorted().hashCode()}"))
    }
}

/** 上次選的範圍（當天／當週…），下次打開沿用。 */
private object ViewerPrefs {
    private const val PREFS = "event_viewer"
    private const val KEY_MODE = "mode"

    fun mode(context: Context): ViewMode =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
            ?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.DAY

    fun setMode(context: Context, mode: ViewMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventViewerScreen(start: LocalDate, hidden: Set<Long>, highlight: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    val builder = remember { MonthBuilder() }
    var anchor by remember { mutableStateOf(start) }
    var mode by remember { mutableStateOf(ViewerPrefs.mode(context)) }
    var days by remember { mutableStateOf<List<ViewDay>?>(null) }
    val range = EventView.range(mode, anchor, builder.firstDayOfWeek)

    // 編輯完行程回來時重新讀取。
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(range, resumes) {
        val zone = ZoneId.systemDefault()
        // 前後多抓一天，涵蓋以 UTC 午夜計的全天事件。
        val from = range.start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = range.endInclusive.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        days = EventView.days(repository.instances(from, to, hidden), range, mode, builder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rangeTitle(mode, range)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { anchor = LocalDate.now() }) { Text(stringResource(R.string.today_word)) }
                    IconButton(onClick = {
                        // 在目前看的那一天新增（當週等範圍用第一天）。
                        val date = if (mode == ViewMode.DAY) anchor else range.start
                        context.startActivity(
                            CalendarLaunchActivity.intentFor(context, EventEditor.insertIntent(date), editor = true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_event))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                val options = listOf(
                    ViewMode.DAY to R.string.view_day,
                    ViewMode.WEEK to R.string.view_week,
                    ViewMode.MONTH to R.string.view_month,
                    ViewMode.FOUR_WEEKS to R.string.view_four_weeks,
                )
                options.forEachIndexed { i, (option, label) ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = {
                            mode = option
                            ViewerPrefs.setMode(context, option)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        label = { Text(stringResource(label), maxLines = 1) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { anchor = EventView.shift(mode, anchor, -1) }) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.view_previous))
                }
                Text(
                    rangeSubtitle(mode, range),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = { anchor = EventView.shift(mode, anchor, 1) }) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.view_next))
                }
            }
            days?.let { EventViewerContent(it, highlight) }
        }
    }
}

/** 標題：當天「11月20日 星期五」、當週與近四週「11/16 – 11/22」、當月「2026年11月」。 */
@Composable
private fun rangeTitle(mode: ViewMode, range: ClosedRange<LocalDate>): String {
    val locale = Locale.getDefault()
    fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    return when (mode) {
        ViewMode.DAY -> pattern("MMMdEEEE").format(range.start)
        ViewMode.MONTH -> pattern("yMMMM").format(range.start)
        else -> pattern("Md").let { "${it.format(range.start)} – ${it.format(range.endInclusive)}" }
    }
}

/** 標題下方的說明：哪一年、或「今天」。 */
@Composable
private fun rangeSubtitle(mode: ViewMode, range: ClosedRange<LocalDate>): String {
    val today = LocalDate.now()
    return when {
        mode == ViewMode.DAY && range.start == today -> stringResource(R.string.today_word)
        today in range.start..range.endInclusive -> stringResource(R.string.view_includes_today, range.start.year)
        else -> range.start.year.toString()
    }
}

/** 一天一組：日期標題（含農曆與假日），下面是行程；點行程直接開啟編輯。 */
@Composable
internal fun EventViewerContent(days: List<ViewDay>, highlight: Long?) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val labeler = remember { if (WidgetSettings.defaultShowLunar()) DayLabeler(IcuLunarSource()) else null }
    val listState = rememberLazyListState()
    // 打開時捲到點的那個行程（或今天）。
    LaunchedEffect(days) {
        val index = days.indexOfFirst { day -> day.events.any { it.eventId == highlight } }
            .takeIf { it >= 0 } ?: days.indexOfFirst { it.date >= today }.takeIf { it >= 0 }
        if (index != null && index > 0) listState.scrollToItem(index)
    }
    if (days.isEmpty()) {
        Text(
            stringResource(R.string.view_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    val now = System.currentTimeMillis()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(days, key = { it.date.toEpochDay() }) { day ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DayTitle(day, isToday = day.date == today, lunar = labeler?.labelOf(day.date)?.long)
                if (day.events.isEmpty()) {
                    Text(
                        stringResource(R.string.month_day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                for (event in day.events) {
                    ViewerEventRow(
                        event = event,
                        past = !event.allDay && event.end <= now,
                        highlighted = event.eventId == highlight,
                    ) { openEventEditor(context, event) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayTitle(day: ViewDay, isToday: Boolean, lunar: String?) {
    val locale = Locale.getDefault()
    val format = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdEEEE"), locale) }
    val dayOff = day.holidays.any { it.holiday == HolidayKind.DAY_OFF }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            format.format(day.date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                dayOff -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.today_word),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (lunar != null) {
            Spacer(Modifier.width(8.dp))
            Text(lunar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (day.holidays.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (holiday in day.holidays) {
                Text(
                    if (holiday.holiday == HolidayKind.MAKEUP_WORKDAY) stringResource(R.string.makeup_workday) else holiday.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (holiday.holiday == HolidayKind.DAY_OFF) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewerEventRow(event: CalendarEvent, past: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val alpha = if (past) 0.55f else 1f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier,
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.color).copy(alpha = alpha)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        if (event.allDay) {
                            stringResource(R.string.all_day)
                        } else {
                            "${timeFormat.format(Date(event.begin))} – ${timeFormat.format(Date(event.end))}"
                        },
                        event.location?.takeIf { it.isNotBlank() },
                        event.calendarName.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}
