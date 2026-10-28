package com.skestc.calendarwidget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.skestc.calendarwidget.data.CalendarInfo
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.NextEvent
import com.skestc.calendarwidget.data.NextEventPicker
import com.skestc.calendarwidget.lunar.DayLabeler
import com.skestc.calendarwidget.lunar.IcuLunarSource
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.ui.EventEditorRow
import com.skestc.calendarwidget.ui.GroupDivider
import com.skestc.calendarwidget.ui.LongEventsRow
import com.skestc.calendarwidget.ui.SchoolWeeksRow
import com.skestc.calendarwidget.ui.SearchActivity
import com.skestc.calendarwidget.ui.SectionTitle
import com.skestc.calendarwidget.ui.SettingsGroup
import com.skestc.calendarwidget.ui.UpdateHelpRow
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.AgendaWidgetReceiver
import com.skestc.calendarwidget.widget.MonthWidgetReceiver
import com.skestc.calendarwidget.widget.NextEventWidgetReceiver
import com.skestc.calendarwidget.widget.TimelineWidgetReceiver
import com.skestc.calendarwidget.widget.WeekWidgetReceiver
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                HomeScreen()
            }
        }
    }
}

/** 首頁上方「今天」卡片的資料。 */
private data class TodaySummary(val count: Int, val next: NextEvent?, val lunar: String?)

/** 首頁：整頁可以捲動。今天概況與搜尋 → 加入小工具 → 設定 → 已同步的日曆。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    var granted by remember { mutableStateOf(repository.hasPermission()) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var today by remember { mutableStateOf<TodaySummary?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    LaunchedEffect(granted) {
        if (granted) {
            calendars = repository.calendars()
            today = loadToday(repository)
            WidgetUpdater.syncSchedules(context)
            WidgetUpdater.refreshAll(context)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!granted) {
            PermissionScreen(
                modifier = Modifier.padding(padding),
                onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "today") { TodayCard(today) }

            if (calendars.isNotEmpty() && calendars.none { it.isHoliday }) {
                item(key = "holiday") { HolidayHint() }
            }

            item(key = "widgets-title") { SectionTitle(stringResource(R.string.add_widgets_title)) }
            item(key = "widgets") { WidgetGallery() }

            item(key = "settings-title") { SectionTitle(stringResource(R.string.section_settings)) }
            item(key = "settings") {
                SettingsGroup {
                    EventEditorRow()
                    GroupDivider()
                    LongEventsRow()
                    GroupDivider()
                    SchoolWeeksRow()
                    GroupDivider()
                    UpdateHelpRow()
                }
            }

            item(key = "calendars-title") {
                SectionTitle(stringResource(R.string.synced_calendars_count, calendars.size))
            }
            if (calendars.isEmpty()) {
                item(key = "no-calendars") {
                    Text(
                        stringResource(R.string.no_calendars),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            // 依帳號分組，每個帳號一張卡片。
            for ((account, accountCalendars) in calendars.groupBy { it.accountName }) {
                item(key = "account:$account") { AccountCard(account, accountCalendars) }
            }
        }
    }
}

private suspend fun loadToday(repository: CalendarRepository): TodaySummary {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.now(zone)
    val now = System.currentTimeMillis()
    // 前後多抓一天，涵蓋以 UTC 午夜計的全天事件。
    val start = date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    val events = MonthBuilder(zone).eventsOn(repository.instances(start, end), date)
    val lunar = if (WidgetSettings.defaultShowLunar()) DayLabeler(IcuLunarSource()).labelOf(date).long else null
    return TodaySummary(events.size, NextEventPicker.pick(events, now, zone), lunar)
}

/** 今天的大號日期、農曆、行程數與下一個行程，下面是搜尋列。 */
@Composable
private fun TodayCard(summary: TodaySummary?) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val date = LocalDate.now()
    val rounded = remember {
        runCatching { FontFamily(Font(path = "fonts/google_sans_flex_rounded_bold.ttf", assetManager = context.assets)) }.getOrNull()
    }
    val monthText = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale).format(date)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.dayOfMonth.toString(),
                    fontFamily = rounded,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        listOfNotNull(monthText, summary?.lunar).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (summary != null) {
                Text(
                    if (summary.count == 0) {
                        stringResource(R.string.home_today_none)
                    } else {
                        stringResource(R.string.home_today_count, summary.count)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                summary.next?.let { next ->
                    val time = if (next.event.allDay) {
                        stringResource(R.string.all_day)
                    } else {
                        DateFormat.getTimeFormat(context).format(Date(next.event.begin))
                    }
                    Text(
                        stringResource(R.string.home_next, "$time  ${next.event.title}"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // 像搜尋框的按鈕：點了進搜尋畫面。
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable { context.startActivity(Intent(context, SearchActivity::class.java)) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.search_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class WidgetOption(
    val label: Int,
    val description: Int,
    val receiver: Class<out GlanceAppWidgetReceiver>,
)

private val widgetOptions = listOf(
    WidgetOption(R.string.pin_agenda, R.string.widget_desc_agenda, AgendaWidgetReceiver::class.java),
    WidgetOption(R.string.pin_month, R.string.widget_desc_month, MonthWidgetReceiver::class.java),
    WidgetOption(R.string.pin_week, R.string.widget_desc_week, WeekWidgetReceiver::class.java),
    WidgetOption(R.string.pin_timeline, R.string.widget_desc_timeline, TimelineWidgetReceiver::class.java),
    WidgetOption(R.string.pin_next, R.string.widget_desc_next, NextEventWidgetReceiver::class.java),
)

/** 兩欄的小工具卡片，點了請桌面加入。 */
@Composable
private fun WidgetGallery() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in widgetOptions.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (option in row) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { scope.launch { requestPin(context, option.receiver) } },
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_event),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(option.label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(option.description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2,
                                maxLines = 2,
                            )
                        }
                    }
                }
                // 最後一列只有一張時補空位，卡片寬度才會一致。
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 一個帳號的日曆：帳號名稱，下面每個日曆一列（顏色圓點＋名稱）。 */
@Composable
private fun AccountCard(account: String, calendars: List<CalendarInfo>) {
    SettingsGroup {
        Text(
            account,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        )
        for (calendar in calendars) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(calendar.color)),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    calendar.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!calendar.visible) {
                    Text(
                        stringResource(R.string.calendar_not_synced),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 還沒授權時：置中的圖示、說明與授權按鈕。 */
@Composable
private fun PermissionScreen(modifier: Modifier, onGrant: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_event),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_permission)) }
    }
}

/** 還沒訂閱「台灣的節慶假日」時，提示怎麼加，國定假日與補班日才會出現在 widget 上。 */
@Composable
private fun HolidayHint() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.holiday_hint_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                stringResource(R.string.holiday_hint_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** 請桌面加入指定的 widget（Android 8+ 支援的桌面才會跳出確認視窗）。 */
private suspend fun requestPin(context: Context, receiver: Class<out GlanceAppWidgetReceiver>) {
    when (receiver) {
        AgendaWidgetReceiver::class.java -> GlanceAppWidgetManager(context).requestPinGlanceAppWidget(AgendaWidgetReceiver::class.java)
        MonthWidgetReceiver::class.java -> GlanceAppWidgetManager(context).requestPinGlanceAppWidget(MonthWidgetReceiver::class.java)
        TimelineWidgetReceiver::class.java -> GlanceAppWidgetManager(context).requestPinGlanceAppWidget(TimelineWidgetReceiver::class.java)
        NextEventWidgetReceiver::class.java -> GlanceAppWidgetManager(context).requestPinGlanceAppWidget(NextEventWidgetReceiver::class.java)
        WeekWidgetReceiver::class.java -> GlanceAppWidgetManager(context).requestPinGlanceAppWidget(WeekWidgetReceiver::class.java)
    }
}
