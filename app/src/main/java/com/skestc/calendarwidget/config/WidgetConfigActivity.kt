package com.skestc.calendarwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.skestc.calendarwidget.settings.Appearance
import com.skestc.calendarwidget.settings.FontChoice
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.DynamicSpec
import com.skestc.calendarwidget.theme.ThemeSpecs
import com.skestc.calendarwidget.theme.WidgetThemeId
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.AgendaWidget
import com.skestc.calendarwidget.widget.MonthWidget
import com.skestc.calendarwidget.widget.MonthWidgetReceiver
import com.skestc.calendarwidget.widget.NextEventWidget
import com.skestc.calendarwidget.widget.NextEventWidgetReceiver
import com.skestc.calendarwidget.widget.WeekWidget
import com.skestc.calendarwidget.widget.WeekWidgetReceiver
import com.skestc.calendarwidget.widget.TimelineWidget
import com.skestc.calendarwidget.widget.TimelineWidgetReceiver
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 新增 widget 時（以及長按 widget →「編輯小工具」時）開啟的設定頁。
 * 每個 widget 的設定分開儲存，例如工作用和家庭用各放一個、各用不同主題。
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
        val kind = when (providerClass) {
            MonthWidgetReceiver::class.java.name -> WidgetKind.MONTH
            TimelineWidgetReceiver::class.java.name -> WidgetKind.TIMELINE
            NextEventWidgetReceiver::class.java.name -> WidgetKind.NEXT
            WeekWidgetReceiver::class.java.name -> WidgetKind.WEEK
            else -> WidgetKind.AGENDA
        }
        val widget: GlanceAppWidget = when (kind) {
            WidgetKind.AGENDA -> AgendaWidget()
            WidgetKind.MONTH -> MonthWidget()
            WidgetKind.TIMELINE -> TimelineWidget()
            WidgetKind.NEXT -> NextEventWidget()
            WidgetKind.WEEK -> WeekWidget()
        }

        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                ConfigScreen(
                    kind = kind,
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
                prefs[WidgetSettings.KEY_THEME] = settings.appearance.theme.name
                prefs[WidgetSettings.KEY_BG_OPACITY] = settings.appearance.backgroundOpacity
                prefs[WidgetSettings.KEY_CORNER] = settings.appearance.cornerDp
                prefs[WidgetSettings.KEY_TEXT_SCALE] = settings.appearance.textScalePercent
                prefs[WidgetSettings.KEY_FONT] = settings.appearance.font.name
                prefs[WidgetSettings.KEY_SHOW_LUNAR] = settings.showLunar
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
    kind: WidgetKind,
    loadSettings: suspend (GlanceId) -> WidgetSettings,
    glanceId: () -> GlanceId,
    onSave: (GlanceId, WidgetSettings) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    val id = remember { glanceId() }

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var settings by remember { mutableStateOf(WidgetSettings()) }

    LaunchedEffect(id) {
        settings = loadSettings(id)
        calendars = repository.calendars()
    }

    fun updateAppearance(change: (Appearance) -> Appearance) {
        settings = settings.copy(appearance = change(settings.appearance))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("小工具設定") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WidgetPreview(
                appearance = settings.appearance,
                kind = kind,
                darkTheme = isSystemInDarkTheme(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.size(4.dp)) }

                item {
                    Section("主題") {
                        ThemePicker(
                            selected = settings.appearance.theme,
                            onSelect = { theme -> updateAppearance { it.copy(theme = theme) } },
                        )
                    }
                }

                if (settings.appearance.theme.supportsOpacity) {
                    item {
                        Section("背景不透明度 ${settings.appearance.backgroundOpacity}%") {
                            Slider(
                                value = settings.appearance.backgroundOpacity.toFloat(),
                                onValueChange = { v ->
                                    updateAppearance { it.copy(backgroundOpacity = v.roundToInt()) }
                                },
                                valueRange = 0f..100f,
                                steps = 9,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                item {
                    Section("圓角") {
                        Segmented(
                            options = Appearance.CORNER_OPTIONS,
                            selected = settings.appearance.cornerDp,
                            onSelect = { v -> updateAppearance { it.copy(cornerDp = v) } },
                        )
                    }
                }

                item {
                    Section("文字大小") {
                        Segmented(
                            options = Appearance.TEXT_SCALE_OPTIONS,
                            selected = settings.appearance.textScalePercent,
                            onSelect = { v -> updateAppearance { it.copy(textScalePercent = v) } },
                        )
                    }
                }

                item {
                    Section("字型") {
                        Segmented(
                            options = FontChoice.entries.map { it to it.label },
                            selected = settings.appearance.font,
                            onSelect = { v -> updateAppearance { it.copy(font = v) } },
                        )
                    }
                }

                // 「下一個行程」沒有日期格子，不顯示農曆。
                if (kind != WidgetKind.NEXT) item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settings = settings.copy(showLunar = !settings.showLunar) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("顯示農曆、節氣與傳統節日", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "國定假日與補班日來自 Google 日曆的「台灣的節慶假日」",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.showLunar,
                            onCheckedChange = { settings = settings.copy(showLunar = it) },
                        )
                    }
                }

                if (kind == WidgetKind.AGENDA) {
                    item {
                        Section("顯示天數") {
                            Segmented(
                                options = WidgetSettings.AGENDA_DAY_OPTIONS.map { it to "$it 天" },
                                selected = settings.agendaDays,
                                onSelect = { v -> settings = settings.copy(agendaDays = v) },
                            )
                        }
                    }
                }

                item {
                    Section("顯示的日曆") {
                        if (!repository.hasPermission()) {
                            Text(
                                "尚未授權讀取日曆，請先開啟 App 完成授權。",
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                items(calendars, key = { it.id }) { calendar ->
                    val hidden = settings.hiddenCalendarIds
                    CalendarCheckRow(
                        calendar = calendar,
                        checked = calendar.id !in hidden,
                        onToggle = {
                            settings = settings.copy(
                                hiddenCalendarIds = if (calendar.id in hidden) hidden - calendar.id else hidden + calendar.id,
                            )
                        },
                    )
                }
            }

            Button(
                onClick = { onSave(id, settings) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        content()
    }
}

/** 每個主題一顆色票：背景色＋主色圓點，下面是名稱。 */
@Composable
private fun ThemePicker(selected: WidgetThemeId, onSelect: (WidgetThemeId) -> Unit) {
    val context = LocalContext.current
    val dynamic = remember { DynamicSpec.resolve(context) }
    val dark = isSystemInDarkTheme()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(WidgetThemeId.entries) { theme ->
            val spec = ThemeSpecs.fixed(theme) ?: dynamic
            val bg = Color(if (dark) spec.background.night else spec.background.day)
            val primary = Color(if (dark) spec.primary.night else spec.primary.day)
            val isSelected = theme == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(theme) },
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8E8E93))
                        .background(bg)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(primary))
                }
                Text(
                    theme.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun CalendarCheckRow(calendar: CalendarInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp),
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
