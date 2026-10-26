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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R

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
                (if (expanded) "▾ " else "▸ ") + stringResource(R.string.update_help_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (expanded) {
                Text(stringResource(R.string.update_help_body), style = MaterialTheme.typography.bodySmall)
                val tips = when (DeviceTips.brandOf(Build.MANUFACTURER)) {
                    PhoneBrand.XIAOMI -> R.array.tips_xiaomi
                    PhoneBrand.SAMSUNG -> R.array.tips_samsung
                    PhoneBrand.OPPO -> R.array.tips_oppo
                    PhoneBrand.VIVO -> R.array.tips_vivo
                    PhoneBrand.HUAWEI -> R.array.tips_huawei
                    PhoneBrand.ASUS -> R.array.tips_asus
                    PhoneBrand.OTHER -> R.array.tips_generic
                }
                stringArrayResource(tips).forEachIndexed { i, tip ->
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
                    Text(stringResource(R.string.open_app_info))
                }
            }
        }
    }
}
