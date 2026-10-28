package com.skestc.calendarwidget.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import com.skestc.calendarwidget.data.CalendarInfo
import com.skestc.calendarwidget.data.RecentSearches
import com.skestc.calendarwidget.data.SearchRange
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.EventSearch
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.SearchResults
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** 搜尋行程：輸入關鍵字，找標題、地點、說明（往前一年、往後兩年），點結果開啟該行程。 */
class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                SearchScreen(onBack = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var keyword by remember { mutableStateOf("") }
    // 查到的原始結果；篩選（時間、日曆）在畫面上即時套用，不必重新查詢。
    var found by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var range by remember { mutableStateOf(SearchRange.ALL) }
    var calendarId by remember { mutableStateOf<Long?>(null) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    val recentStore = remember { RecentSearches(context) }
    var recent by remember { mutableStateOf(recentStore.list()) }
    fun saveRecent(keywordToSave: String) {
        recentStore.add(keywordToSave)
        recent = recentStore.list()
    }
    var searching by remember { mutableStateOf(false) }
    // 編輯完回到搜尋畫面時重新查一次，結果才會反映剛才的修改。
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        calendars = repository.calendars().filter { it.visible }
    }
    // 停止輸入 300 毫秒後才查詢，邊打字邊更新但不會每個字都查一次。
    LaunchedEffect(keyword, resumes) {
        if (keyword.isBlank()) {
            found = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        val (start, end) = EventSearch.range(System.currentTimeMillis())
        found = repository.search(keyword, start, end)
        searching = false
    }
    val results = found?.let {
        val now = System.currentTimeMillis()
        EventSearch.split(EventSearch.filter(it, range, calendarId, now), now)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    saveRecent(keyword)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            FilterRow(
                range = range,
                onRange = { range = it },
                calendars = calendars,
                calendarId = calendarId,
                onCalendar = { calendarId = it },
            )
            val current = results
            when {
                !repository.hasPermission() -> Message(stringResource(R.string.config_no_permission))
                keyword.isBlank() -> RecentList(
                    recent = recent,
                    onPick = {
                        keyword = it
                        saveRecent(it)
                    },
                    onClear = {
                        recentStore.clear()
                        recent = emptyList()
                    },
                )
                current == null || (searching && current.isEmpty) -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                current.isEmpty -> Message(stringResource(R.string.search_none, keyword.trim()))
                else -> ResultList(current, keyword, onOpen = { saveRecent(keyword) })
            }
        }
    }
}

/** 時間範圍與日曆的篩選，可以左右捲動。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    range: SearchRange,
    onRange: (SearchRange) -> Unit,
    calendars: List<CalendarInfo>,
    calendarId: Long?,
    onCalendar: (Long?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((option, label) in listOf(
            SearchRange.ALL to R.string.search_range_all,
            SearchRange.UPCOMING to R.string.search_range_upcoming,
            SearchRange.THIS_WEEK to R.string.search_range_week,
            SearchRange.THIS_MONTH to R.string.search_range_month,
        )) {
            FilterChip(selected = range == option, onClick = { onRange(option) }, label = { Text(stringResource(label)) })
        }
        if (calendars.size > 1) {
            Box {
                FilterChip(
                    selected = calendarId != null,
                    onClick = { menuOpen = true },
                    label = {
                        Text(calendars.firstOrNull { it.id == calendarId }?.displayName ?: stringResource(R.string.search_all_calendars))
                    },
                    trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null) },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_all_calendars)) },
                        onClick = {
                            onCalendar(null)
                            menuOpen = false
                        },
                    )
                    for (calendar in calendars) {
                        DropdownMenuItem(
                            text = { Text(calendar.displayName) },
                            leadingIcon = {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(calendar.color)))
                            },
                            onClick = {
                                onCalendar(calendar.id)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 還沒輸入關鍵字時：說明與最近搜尋（點一下就搜）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentList(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    Column {
        Message(stringResource(R.string.search_intro))
        if (recent.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.search_recent_clear)) }
            }
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (item in recent) {
                    AssistChip(onClick = { onPick(item) }, label = { Text(item) })
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun ResultList(results: SearchResults, keyword: String, onOpen: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (results.upcoming.isNotEmpty()) {
            item(key = "h-up") { SectionHeader(stringResource(R.string.search_upcoming, results.upcoming.size)) }
            items(results.upcoming, key = { "u${it.eventId}-${it.begin}" }) { ResultRow(it, keyword, past = false, onOpen) }
        }
        if (results.past.isNotEmpty()) {
            item(key = "h-past") { SectionHeader(stringResource(R.string.search_past, results.past.size)) }
            items(results.past, key = { "p${it.eventId}-${it.begin}" }) { ResultRow(it, keyword, past = true, onOpen) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultRow(event: CalendarEvent, keyword: String, past: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMdEEE"), locale)
    }
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val first = remember(event) { MonthBuilder().dateSpan(event).first }
    val whenText = if (event.allDay) {
        "${dateFormat.format(first)} · ${stringResource(R.string.all_day)}"
    } else {
        "${dateFormat.format(first)} · ${timeFormat.format(Date(event.begin))} – ${timeFormat.format(Date(event.end))}"
    }
    val alpha = if (past) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 點過的關鍵字記進「最近搜尋」。
                onOpen()
                openEventEditor(context, event)
            }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(event.color).copy(alpha = alpha)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                highlighted(event.title, keyword, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(whenText, event.location).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 標題裡符合關鍵字的部分加粗並用主色。 */
private fun highlighted(text: String, keyword: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in EventSearch.matches(text, keyword)) {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), range.first, range.last + 1)
    }
}
