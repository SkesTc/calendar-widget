package com.skestc.calendarwidget.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.action.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent

/**
 * 從 widget 開啟日曆（新增、檢視行程）的透明中繼頁：
 * 使用者在日曆 App 新增或修改行程、回到桌面時立刻更新所有 widget。
 * 只靠背景的內容變更通知時，系統省電機制可能讓更新延遲好幾分鐘。
 */
class CalendarLaunchActivity : ComponentActivity() {

    private val calendar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 畫面旋轉等重建時，日曆已經開過了，只要等它回來。
        if (savedInstanceState != null) return
        val target = targetOf(intent)
        if (target == null) {
            finish()
            return
        }
        try {
            calendar.launch(target)
        } catch (e: ActivityNotFoundException) {
            finish()
        }
    }

    private fun refreshAndFinish() {
        val appContext = applicationContext
        // 中繼頁馬上關掉，更新在背景完成（不跟著這個 Activity 一起被取消）。
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            WidgetUpdater.refreshAll(appContext)
        }
        finish()
    }

    companion object {
        private const val EXTRA_TARGET = "target"

        fun intentFor(context: Context, target: Intent): Intent =
            Intent(context, CalendarLaunchActivity::class.java)
                .putExtra(EXTRA_TARGET, target)
                // PendingIntent 比對不看 extras；用 data 區分不同的目標，避免點到別的行程。
                .setData(Uri.parse("calendarwidget://open/" + target.toUri(Intent.URI_INTENT_SCHEME).hashCode()))

        private fun targetOf(intent: Intent): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_TARGET, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_TARGET)
            }
    }
}

/** widget 上開啟日曆的點擊動作：經過 [CalendarLaunchActivity]，回來時自動更新。 */
@Composable
fun openInCalendar(target: Intent): Action =
    actionStartIntent(CalendarLaunchActivity.intentFor(LocalContext.current, target))
