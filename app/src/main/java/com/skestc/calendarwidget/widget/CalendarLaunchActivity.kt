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
import com.skestc.calendarwidget.settings.EventEditor
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
        val preferred = intent.getStringExtra(EXTRA_PACKAGE)
        val attempts = when {
            intent.getBooleanExtra(EXTRA_EDITOR, false) -> EventEditor.launchIntents(this, target)
            preferred != null -> listOf(Intent(target).setPackage(preferred), target)
            else -> listOf(target)
        }
        // 指定的 App 沒裝（或被移除）時，退回下一個（系統預設）。
        for (attempt in attempts) {
            try {
                calendar.launch(attempt)
                return
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
        finish()
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
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_EDITOR = "editor"

        /**
         * [preferredPackage]：優先用這個 App 開（例如 Google 日曆），沒裝時用系統預設。
         * [editor]：新增行程，依使用者在設定選的 App（[EventEditor]）開啟。
         */
        fun intentFor(context: Context, target: Intent, preferredPackage: String? = null, editor: Boolean = false): Intent {
            val key = target.toUri(Intent.URI_INTENT_SCHEME) + "|" + preferredPackage + "|" + editor
            return Intent(context, CalendarLaunchActivity::class.java)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_PACKAGE, preferredPackage)
                .putExtra(EXTRA_EDITOR, editor)
                // PendingIntent 比對不看 extras；用 data 區分不同的目標，避免點到別的行程。
                .setData(Uri.parse("calendarwidget://open/" + key.hashCode()))
        }

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
fun openInCalendar(target: Intent, preferredPackage: String? = null, editor: Boolean = false): Action =
    actionStartIntent(CalendarLaunchActivity.intentFor(LocalContext.current, target, preferredPackage, editor))
