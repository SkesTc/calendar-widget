package com.skestc.calendarwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.skestc.calendarwidget.data.CalendarInfo
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.AgendaWidget
import com.skestc.calendarwidget.widget.MonthWidget
import com.skestc.calendarwidget.widget.MonthWidgetReceiver
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch

/**
 * 新增 widget 時（以及長按 widget →「編輯小工具」時）開啟的設定頁。
 * 每個 widget 的設定分開儲存，例如工作用和家庭用各放一個。
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 使用者按返回鍵就當作取消新增。
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val providerClass = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className
        val isMonth = providerClass == MonthWidgetReceiver::class.java.name
        val widget: GlanceAppWidget = if (isMonth) MonthWidget() else AgendaWidget()

        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                ConfigScreen(
                    showAgendaOptions = !isMonth,
                    loadSettings = { glanceId -> loadSettings(glanceId) },
                    glanceId = { GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId) },
                    onSave = { glanceId, settings -> save(widget, glanceId, settings) },
                )
            }
        }
    }

    private suspend fun loadSettings(glanceId: GlanceId): WidgetSettings =
        WidgetSettings.from(getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId))

    private fun save(widget: GlanceAppWidget, glanceId: GlanceId, settings: WidgetSettings) {
        lifecycleScope.launch {
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WidgetSettings.KEY_HIDDEN_CALENDARS] =
                    settings.hiddenCalendarIds.map { it.toString() }.toSet()
                prefs[WidgetSettings.KEY_AGENDA_DAYS] = settings.agendaDays
                prefs[WidgetSettings.KEY_REFRESH] = (prefs[WidgetSettings.KEY_REFRESH] ?: 0L) + 1
            }
            widget.update(this@WidgetConfigActivity, glanceId)
            WidgetUpdater.syncSchedules(this@WidgetConfigActivity)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    showAgendaOptions: Boolean,
    loadSettings: suspend (GlanceId) -> WidgetSettings,
    glanceId: () -> GlanceId,
    onSave: (GlanceId, WidgetSettings) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    val id = remember { glanceId() }

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var hidden by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var days by remember { mutableStateOf(WidgetSettings.DEFAULT_AGENDA_DAYS) }
    var original by remember { mutableStateOf(WidgetSettings()) }

    LaunchedEffect(id) {
        original = loadSettings(id)
        hidden = original.hiddenCalendarIds
        days = original.agendaDays
        calendars = repository.calendars()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("小工具設定") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showAgendaOptions) {
                Text("顯示天數", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in WidgetSettings.AGENDA_DAY_OPTIONS) {
                        FilterChip(
                            selected = days == option,
                            onClick = { days = option },
                            label = { Text("$option 天") },
                        )
                    }
                }
            }

            Text("顯示的日曆", style = MaterialTheme.typography.titleMedium)
            if (!repository.hasPermission()) {
                Text("尚未授權讀取日曆，請先開啟 App 完成授權。")
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(calendars, key = { it.id }) { calendar ->
                    CalendarCheckRow(
                        calendar = calendar,
                        checked = calendar.id !in hidden,
                        onToggle = {
                            hidden = if (calendar.id in hidden) hidden - calendar.id else hidden + calendar.id
                        },
                    )
                }
            }

            Button(
                onClick = { onSave(id, original.copy(hiddenCalendarIds = hidden, agendaDays = days)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun CalendarCheckRow(calendar: CalendarInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(calendar.color)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                calendar.accountName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
