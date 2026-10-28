package com.skestc.calendarwidget.ui

import android.text.format.DateFormat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.rememberCoroutineScope
import com.skestc.calendarwidget.data.SchoolWeeks
import com.skestc.calendarwidget.widget.RefreshAction
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.config.EventEditorPicker
import com.skestc.calendarwidget.config.rememberAppIcon
import com.skestc.calendarwidget.data.HiddenEvents
import com.skestc.calendarwidget.settings.EventEditor

/** 首頁的區塊標題（例如「加入桌面小工具」）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** 一組設定列：同一張大圓角卡片，列之間用細線分隔（Material 3 的清單分組）。 */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** 設定列：左邊圓形圖示，中間標題與說明，右邊箭頭或自訂內容。 */
@Composable
fun SettingsRow(
    icon: Int?,
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            when {
                leading != null -> leading()
                icon != null -> Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「＋」新增行程使用哪個 App：顯示目前的選擇與圖示，點了跳出選單（所有小工具共用）。 */
@Composable
fun EventEditorRow() {
    val context = LocalContext.current
    val apps = remember { EventEditor.apps(context) }
    var label by remember { mutableStateOf(EventEditor.choiceLabel(context, apps)) }
    var choice by remember { mutableStateOf(EventEditor.choice(context)) }
    var picking by remember { mutableStateOf(false) }
    // 預設是 Google 日曆；「每次詢問」沒有 App 圖示，用一般的圖示。
    val iconPackage = when (choice) {
        null -> EventEditor.GOOGLE_CALENDAR
        EventEditor.ASK -> null
        else -> choice
    }
    val appIcon = iconPackage?.let { rememberAppIcon(it) }
    val leading: (@Composable () -> Unit)? = if (appIcon != null) {
        { Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(40.dp)) }
    } else {
        null
    }

    SettingsRow(
        icon = R.drawable.ic_add,
        title = stringResource(R.string.section_editor),
        subtitle = label,
        leading = leading,
        onClick = { picking = true },
    )

    if (picking) {
        val close = {
            picking = false
            label = EventEditor.choiceLabel(context, apps)
            choice = EventEditor.choice(context)
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.section_editor)) },
            // 選項會立即儲存；App 很多時可以捲動。
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { EventEditorPicker() } },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.done)) } },
        )
    }
}

/** 「長期行程」：顯示目前隱藏幾個，點了進管理畫面；回到首頁時重新讀取數量。 */
@Composable
fun LongEventsRow() {
    val context = LocalContext.current
    var hiddenCount by remember { mutableIntStateOf(HiddenEvents(context).ids().size) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hiddenCount = HiddenEvents(context).ids().size
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsRow(
        icon = R.drawable.ic_visibility_off,
        title = stringResource(R.string.long_events_title),
        subtitle = if (hiddenCount > 0) {
            stringResource(R.string.long_events_hidden_count, hiddenCount)
        } else {
            stringResource(R.string.long_events_row_hint)
        },
        onClick = { context.startActivity(Intent(context, LongEventsActivity::class.java)) },
    )
}

/** 「小工具沒有自動更新？」：點了跳出這支手機品牌的省電設定步驟。 */
@Composable
fun UpdateHelpRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    SettingsRow(
        icon = R.drawable.ic_help,
        title = stringResource(R.string.update_help_title),
        subtitle = stringResource(R.string.update_help_row_hint),
        onClick = { open = true },
    )
    if (open) {
        val tips = when (DeviceTips.brandOf(Build.MANUFACTURER)) {
            PhoneBrand.XIAOMI -> R.array.tips_xiaomi
            PhoneBrand.SAMSUNG -> R.array.tips_samsung
            PhoneBrand.OPPO -> R.array.tips_oppo
            PhoneBrand.VIVO -> R.array.tips_vivo
            PhoneBrand.HUAWEI -> R.array.tips_huawei
            PhoneBrand.ASUS -> R.array.tips_asus
            PhoneBrand.OTHER -> R.array.tips_generic
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.update_help_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.update_help_body))
                    stringArrayResource(tips).forEachIndexed { i, tip -> Text("${i + 1}. $tip") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) { Text(stringResource(R.string.open_app_info)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

/**
 * 「學期週次」：選開學日後，月曆、一週、行程清單與時間軸都會顯示第幾週；可以關掉。
 * 所有小工具共用，改完立刻重新整理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolWeeksRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(SchoolWeeks.start(context)) }
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val dateText = start?.let {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMd"), Locale.getDefault()).format(it)
    }
    val week = SchoolWeeks.weekOf(today, start)
    SettingsRow(
        icon = R.drawable.ic_event,
        title = stringResource(R.string.school_weeks_title),
        subtitle = when {
            dateText == null -> stringResource(R.string.school_weeks_unset)
            week != null -> stringResource(R.string.school_weeks_summary, dateText, week)
            else -> stringResource(R.string.school_weeks_set_only, dateText)
        },
        onClick = { picking = true },
    )

    fun save(date: LocalDate?) {
        start = date
        SchoolWeeks.setStart(context, date)
        scope.launch { RefreshAction.refreshEverything(context.applicationContext) }
    }

    if (picking) {
        // DatePicker 用 UTC 毫秒表示日期。
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (start ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let { save(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    picking = false
                    save(null)
                }) { Text(stringResource(R.string.school_weeks_off)) }
            },
        ) {
            DatePicker(state = state, title = { Text(stringResource(R.string.school_weeks_pick), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

