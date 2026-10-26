package com.skestc.calendarwidget.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.HiddenEvents

/** 首頁的「長期行程」卡片：說明＋目前隱藏幾個＋進入管理畫面。 */
@Composable
fun LongEventsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hiddenCount by remember { mutableIntStateOf(HiddenEvents(context).ids().size) }
    // 從管理畫面回到首頁（onResume）時重新讀取隱藏的數量。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hiddenCount = HiddenEvents(context).ids().size
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.long_events_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.long_events_card_body), style = MaterialTheme.typography.bodySmall)
            if (hiddenCount > 0) {
                Text(
                    stringResource(R.string.long_events_hidden_count, hiddenCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { context.startActivity(Intent(context, LongEventsActivity::class.java)) }) {
                Text(stringResource(R.string.long_events_manage))
            }
        }
    }
}
