package com.skestc.calendarwidget.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 首頁的「小工具沒有自動更新？」說明，點開後顯示這支手機品牌的設定步驟。 */
@Composable
fun UpdateHelpCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                (if (expanded) "▾ " else "▸ ") + "小工具沒有自動更新？",
                style = MaterialTheme.typography.titleSmall,
            )
            if (expanded) {
                Text(
                    "部分手機的省電功能會擋住背景更新，導致換日、行程變動後小工具沒跟著變。" +
                        "請依下列步驟允許本 App 在背景執行：",
                    style = MaterialTheme.typography.bodySmall,
                )
                DeviceTips.forManufacturer(Build.MANUFACTURER).forEachIndexed { i, tip ->
                    Text("${i + 1}. $tip", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) {
                    Text("開啟應用程式資訊")
                }
            }
        }
    }
}
