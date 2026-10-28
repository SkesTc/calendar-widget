package com.skestc.calendarwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.config.WidgetConfigActivity
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.RefreshAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * widget 太窄、放不下搜尋、重新整理、設定這些按鈕時，改成一顆「⋯」，點了開這個小選單。
 * 透明的 Activity，只顯示一個對話框。
 */
class WidgetMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setContent {
            CalendarWidgetTheme {
                AlertDialog(
                    onDismissRequest = ::finish,
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(android.R.string.cancel)) } },
                    text = {
                        Column {
                            MenuItem(R.drawable.ic_search, stringResource(R.string.search_title)) {
                                startActivity(Intent(this@WidgetMenuActivity, SearchActivity::class.java))
                                finish()
                            }
                            MenuItem(R.drawable.ic_refresh, stringResource(R.string.refresh)) {
                                // 在背景更新（不跟著這個畫面一起被取消），提示後馬上關掉選單。
                                val appContext = applicationContext
                                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                                    RefreshAction.refreshEverything(appContext)
                                }
                                Toast.makeText(appContext, R.string.refreshed, Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                MenuItem(R.drawable.ic_settings, stringResource(R.string.widget_settings)) {
                                    startActivity(settingsIntent(this@WidgetMenuActivity, appWidgetId))
                                    finish()
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        fun intentFor(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetMenuActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // 每個 widget 一個 PendingIntent。
                .setData(Uri.parse("calendarwidget://menu/$appWidgetId"))

        /** 打開某個 widget 自己的設定頁。 */
        fun settingsIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("calendarwidget://config/$appWidgetId"))
    }
}

@Composable
private fun MenuItem(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
