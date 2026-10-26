package com.skestc.calendarwidget.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.HiddenEvents
import com.skestc.calendarwidget.data.LongEvent
import com.skestc.calendarwidget.data.LongEvents
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「長期行程」：列出跨 3 天以上的行程，讓使用者個別在所有 widget 上隱藏。
 * 只影響 widget 的顯示，Google 日曆裡的行程不會改變。
 */
class LongEventsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                LongEventsScreen(onBack = ::finish)
            }
        }
    }
}

/** 清單中的一列：目前查得到的長期行程，或之前隱藏、已超出查詢範圍的行程（只有標題）。 */
private data class Entry(val eventId: Long, val title: String, val event: LongEvent?, val hidden: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LongEventsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hiddenStore = remember { HiddenEvents(context) }
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<LongEvent>?>(null) }
    var hidden by remember { mutableStateOf(hiddenStore.all()) }

    LaunchedEffect(Unit) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = CalendarRepository(context).instances(start, end, includeHiddenEvents = true)
        found = LongEvents(zone).find(events)
    }

    val entries = found?.let { list ->
        val listed = list.map { Entry(it.eventId, it.title, it, it.eventId in hidden) }
        val outOfRange = hidden.filterKeys { id -> list.none { it.eventId == id } }
            .map { (id, title) -> Entry(id, title, null, true) }
        listed + outOfRange
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.long_events_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.long_events_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (entries != null && entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.long_events_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(entries.orEmpty(), key = { it.eventId }) { entry ->
                LongEventRow(entry) { hide ->
                    hiddenStore.setHidden(entry.eventId, entry.title, hide)
                    hidden = hiddenStore.all()
                    scope.launch { WidgetUpdater.refreshAll(context) }
                }
            }
        }
    }
}

@Composable
private fun LongEventRow(entry: Entry, onHiddenChange: (Boolean) -> Unit) {
    val locale = Locale.getDefault()
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHiddenChange(!entry.hidden) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(entry.event?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val event = entry.event
            Text(
                if (event != null) {
                    stringResource(
                        R.string.long_event_range,
                        dateFormat.format(event.first),
                        dateFormat.format(event.last),
                        event.days,
                    )
                } else {
                    stringResource(R.string.long_event_out_of_range)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = entry.hidden, onCheckedChange = onHiddenChange)
            Text(stringResource(R.string.long_event_hide), style = MaterialTheme.typography.labelSmall)
        }
    }
}
