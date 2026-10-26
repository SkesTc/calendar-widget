package com.skestc.calendarwidget.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.tasks.GoogleTasksAuth
import com.skestc.calendarwidget.tasks.TasksRepository
import com.skestc.calendarwidget.tasks.TasksSyncWorker
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.Date

/** 首頁的 Google Tasks 卡片：連結／取消連結、同步狀態與錯誤說明。 */
@Composable
fun TasksCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { TasksRepository(context) }
    val scope = rememberCoroutineScope()
    // 改變時重新讀取 repository 的狀態。
    var version by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    val configError = stringResource(R.string.tasks_error_config)

    fun handle(outcome: GoogleTasksAuth.Outcome, launchConsent: (GoogleTasksAuth.Outcome.NeedsConsent) -> Unit) {
        when (outcome) {
            is GoogleTasksAuth.Outcome.Authorized -> {
                repository.setConnected(true)
                TasksSyncWorker.syncNow(context)
                TasksSyncWorker.schedulePeriodic(context)
                problem = null
            }
            is GoogleTasksAuth.Outcome.NeedsConsent -> launchConsent(outcome)
            is GoogleTasksAuth.Outcome.Failed -> problem = if (GoogleTasksAuth.isConfigurationError(outcome)) {
                configError
            } else {
                context.getString(R.string.tasks_error_generic, outcome.statusCode)
            }
        }
        version++
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        handle(GoogleTasksAuth.fromConsentResult(context, result.data)) { /* 同意後不會再要求一次 */ }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.titleSmall)
            // 讀 version 讓連結狀態改變時重新組成。
            val connected = version >= 0 && repository.isConnected
            if (!connected) {
                Text(stringResource(R.string.tasks_description), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.tasks_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    scope.launch {
                        handle(GoogleTasksAuth.authorize(context)) { consent ->
                            consentLauncher.launch(IntentSenderRequest.Builder(consent.pendingIntent.intentSender).build())
                        }
                    }
                }) {
                    Text(stringResource(R.string.tasks_connect))
                }
            } else {
                val status = when {
                    repository.lastError == "authorization" -> stringResource(R.string.tasks_error_auth)
                    repository.lastError != null -> stringResource(R.string.tasks_error_sync)
                    repository.lastSync == 0L -> stringResource(R.string.tasks_connected_syncing)
                    else -> stringResource(
                        R.string.tasks_connected_synced,
                        DateFormat.getTimeFormat(context).format(Date(repository.lastSync)),
                    )
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        TasksSyncWorker.syncNow(context)
                        version++
                    }) {
                        Text(stringResource(R.string.tasks_sync_now))
                    }
                    OutlinedButton(onClick = {
                        repository.setConnected(false)
                        TasksSyncWorker.cancel(context)
                        scope.launch { WidgetUpdater.refreshAll(context) }
                        version++
                    }) {
                        Text(stringResource(R.string.tasks_disconnect))
                    }
                }
            }
            problem?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
