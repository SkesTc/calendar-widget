package com.skestc.calendarwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skestc.calendarwidget.data.CalendarEvent
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
import com.skestc.calendarwidget.ui.Onboarding
import com.skestc.calendarwidget.ui.OnboardingScreen
import com.skestc.calendarwidget.ui.SchoolWeeksRow
import com.skestc.calendarwidget.ui.SearchActivity
import com.skestc.calendarwidget.ui.SectionTitle
import com.skestc.calendarwidget.ui.SettingsGroup
import com.skestc.calendarwidget.ui.UpdateHelpRow
import com.skestc.calendarwidget.ui.openEventEditor
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.AgendaWidgetReceiver
import com.skestc.calendarwidget.widget.MonthWidgetReceiver
import com.skestc.calendarwidget.widget.NextEventWidgetReceiver
import com.skestc.calendarwidget.widget.TimelineWidgetReceiver
import com.skestc.calendarwidget.widget.WeekWidgetReceiver
import com.skestc.calendarwidget.widget.WidgetUpdater
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
private data class TodaySummary(
    val events: List<CalendarEvent>,
    val next: NextEvent?,
    val lunar: String?,
    val now: Long,
)

/** 首頁卡片最多列幾個今天的行程，其餘顯示「還有 N 個」。 */
private const val TODAY_LIMIT = 6

/** 首頁：整頁可以捲動。今天概況與搜尋 → 加入小工具 → 設定 → 已同步的日曆。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    var granted by remember { mutableStateOf(repository.hasPermission()) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var today by remember { mutableStateOf<TodaySummary?>(null) }
    // 已經授權過的人（舊版升級上來）不再看引導。
    var onboarded by remember { mutableStateOf(Onboarding.done(context) || repository.hasPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    LaunchedEffect(granted) {
        if (granted) {
            calendars = repository.calendars()
            WidgetUpdater.syncSchedules(context)
            WidgetUpdater.refreshAll(context)
        }
    }
    // 每次回到首頁（例如編輯完行程）都重新讀今天的行程。
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(granted, resumes) {
        if (granted) today = loadToday(repository)
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
        if (!onboarded) {
            OnboardingScreen(
                modifier = Modifier.padding(padding),
                granted = granted,
                hasHolidayCalendar = calendars.any { it.isHoliday },
                onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onFinish = {
                    Onboarding.setDone(context)
                    onboarded = true
                },
                widgetGallery = { WidgetGallery() },
            )
            return@Scaffold
        }
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
    return TodaySummary(events, NextEventPicker.pick(events, now, zone), lunar, now)
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
                    if (summary.events.isEmpty()) {
                        stringResource(R.string.home_today_none)
                    } else {
                        stringResource(R.string.home_today_count, summary.events.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (summary.events.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    for (event in summary.events.take(TODAY_LIMIT)) {
                        TodayEventRow(
                            event,
                            past = !event.allDay && event.end <= summary.now,
                            isNext = event == summary.next?.event,
                        )
                    }
                    val more = summary.events.size - TODAY_LIMIT
                    if (more > 0) {
                        Text(
                            stringResource(R.string.home_more, more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                        )
                    }
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

/** 卡片裡的一個今天行程：色點、時間、標題；點了直接打開編輯畫面。已結束的變淡，下一個加粗。 */
@Composable
private fun TodayEventRow(event: CalendarEvent, past: Boolean, isNext: Boolean) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val alpha = if (past) 0.55f else 1f
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { openEventEditor(context, event) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(event.color).copy(alpha = alpha)),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            if (event.allDay) stringResource(R.string.all_day) else timeFormat.format(Date(event.begin)),
            style = MaterialTheme.typography.labelLarge,
            color = onColor,
            modifier = Modifier.width(64.dp),
        )
        Text(
            event.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isNext) FontWeight.Bold else null,
            color = onColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
    var showManual by remember { mutableStateOf(false) }
    if (showManual) ManualAddDialog(onDismiss = { showManual = false })
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
                            .clickable { if (!requestPin(context, option.receiver)) showManual = true },
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
        // 有些桌面（例如小米、OPPO 沒開權限時）按了不會跳出視窗，也不會告訴 App。
        TextButton(onClick = { showManual = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.pin_no_response))
        }
    }
}

/** 桌面不支援從 App 加入時，教使用者從桌面手動加入。 */
@Composable
private fun ManualAddDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_manual_title)) },
        text = { Text(stringResource(R.string.pin_manual_steps)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text(stringResource(R.string.pin_go_home)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
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

/**
 * 請桌面加入指定的 widget，桌面會跳出確認視窗。
 * 桌面不支援（或拒絕）時回傳 false，由畫面改成說明手動加入的方法。
 */
private fun requestPin(context: Context, receiver: Class<out GlanceAppWidgetReceiver>): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return runCatching { manager.requestPinAppWidget(ComponentName(context, receiver), null, null) }.getOrDefault(false)
}
