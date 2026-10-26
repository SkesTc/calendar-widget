package com.skestc.calendarwidget

import android.Manifest
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.skestc.calendarwidget.data.CalendarInfo
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.ui.UpdateHelpCard
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.AgendaWidgetReceiver
import com.skestc.calendarwidget.widget.MonthWidgetReceiver
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

    Scaffold(topBar = { TopAppBar(title = { Text("行事曆小工具") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!granted) {
                Text("需要讀取日曆的權限，才能顯示手機上已同步的 Google 日曆行程。")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("授權讀取日曆")
                }
                return@Column
            }

            Text("加入桌面小工具", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            GlanceAppWidgetManager(context)
                                .requestPinGlanceAppWidget(AgendaWidgetReceiver::class.java)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("行程清單", maxLines = 1)
                }
                Button(
                    onClick = {
                        scope.launch {
                            GlanceAppWidgetManager(context)
                                .requestPinGlanceAppWidget(MonthWidgetReceiver::class.java)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("月曆", maxLines = 1)
                }
                Button(
                    onClick = {
                        scope.launch {
                            GlanceAppWidgetManager(context)
                                .requestPinGlanceAppWidget(TimelineWidgetReceiver::class.java)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("時間軸", maxLines = 1)
                }
            }
            Text(
                "長按桌面上的小工具 →「編輯小工具」可以選擇要顯示哪些日曆。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (calendars.isNotEmpty() && calendars.none { it.isHoliday }) {
                HolidayHint()
            }
            UpdateHelpCard()

            Text("已同步的日曆", style = MaterialTheme.typography.titleMedium)
            if (calendars.isEmpty()) {
                Text(
                    "找不到日曆。請確認手機的「設定 → 帳戶 → Google」已開啟日曆同步。",
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
                    calendar.accountName + if (calendar.visible) "" else " · 已隱藏",
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
            Text("顯示國定假日與補班日", style = MaterialTheme.typography.titleSmall)
            Text(
                "在電腦版 Google 日曆：設定 → 新增日曆 → 瀏覽感興趣的日曆 → 節慶假日 → 勾選「台灣的節慶假日」。" +
                    "同步到手機後，月曆會把放假日標成紅字、補班日標上「補班」。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
