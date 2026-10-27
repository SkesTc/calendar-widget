package com.skestc.calendarwidget

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.skestc.calendarwidget.data.CalendarInfo
import com.skestc.calendarwidget.data.CalendarRepository
import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.skestc.calendarwidget.ui.SearchActivity
import com.skestc.calendarwidget.ui.EventEditorCard
import com.skestc.calendarwidget.ui.LongEventsCard
import com.skestc.calendarwidget.ui.UpdateHelpCard
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.AgendaWidgetReceiver
import com.skestc.calendarwidget.widget.MonthWidgetReceiver
import com.skestc.calendarwidget.widget.NextEventWidgetReceiver
import com.skestc.calendarwidget.widget.WeekWidgetReceiver
import com.skestc.calendarwidget.widget.TimelineWidgetReceiver
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(repository.hasPermission()) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (granted) {
                        IconButton(onClick = { context.startActivity(Intent(context, SearchActivity::class.java)) }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!granted) {
                Text(stringResource(R.string.permission_rationale))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text(stringResource(R.string.grant_permission))
                }
                return@Column
            }

            Text(stringResource(R.string.add_widgets_title), style = MaterialTheme.typography.titleMedium)
            val pinOptions = listOf(
                R.string.pin_agenda to AgendaWidgetReceiver::class.java,
                R.string.pin_month to MonthWidgetReceiver::class.java,
                R.string.pin_timeline to TimelineWidgetReceiver::class.java,
                R.string.pin_next to NextEventWidgetReceiver::class.java,
                R.string.pin_week to WeekWidgetReceiver::class.java,
            )
            for (row in pinOptions.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, receiver) in row) {
                        OutlinedButton(
                            onClick = { scope.launch { requestPin(context, receiver) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(label), maxLines = 1)
                        }
                    }
                    // 最後一列不滿三個時補空位，按鈕寬度才會一致。
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (calendars.isNotEmpty() && calendars.none { it.isHoliday }) {
                HolidayHint()
            }
            EventEditorCard()
            LongEventsCard()
            UpdateHelpCard()

            Text(stringResource(R.string.synced_calendars), style = MaterialTheme.typography.titleMedium)
            if (calendars.isEmpty()) {
                Text(
                    stringResource(R.string.no_calendars),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calendars, key = { it.id }) { CalendarCard(it) }
            }
        }
    }
}

@Composable
private fun CalendarCard(calendar: CalendarInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(calendar.color)),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (calendar.visible) {
                        calendar.accountName
                    } else {
                        stringResource(R.string.calendar_hidden, calendar.accountName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 還沒訂閱「台灣的節慶假日」時，提示怎麼加，國定假日與補班日才會出現在 widget 上。 */
@Composable
private fun HolidayHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.holiday_hint_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.holiday_hint_body),
                style = MaterialTheme.typography.bodySmall,
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
