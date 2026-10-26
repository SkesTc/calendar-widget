package com.skestc.calendarwidget.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.skestc.calendarwidget.R

/**
 * widget 上「＋」要開哪個 App 的新增行程畫面（所有 widget 共用）：
 * 系統預設、每次詢問，或指定某個 App。指定的 App 被移除時自動退回系統預設。
 */
object EventEditor {
    const val ASK = "__ask__"
    const val GOOGLE_CALENDAR = "com.google.android.calendar"

    private const val PREFS = "event_editor"
    private const val KEY_CHOICE = "choice"
    private const val EVENT_DIR_TYPE = "vnd.android.cursor.dir/event"

    data class App(val packageName: String, val label: String)

    /** null：系統預設；[ASK]：每次詢問；其他：App 的套件名稱。 */
    fun choice(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHOICE, null)

    fun setChoice(context: Context, choice: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (choice == null) remove(KEY_CHOICE) else putString(KEY_CHOICE, choice)
        }.apply()
    }

    fun insertIntent(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)

    /** 裝置上能新增行程的 App（Google 日曆、三星日曆、Outlook…），依名稱排序。 */
    fun apps(context: Context): List<App> {
        val pm = context.packageManager
        val queries = listOf(
            insertIntent(),
            Intent(Intent.ACTION_INSERT).setType(EVENT_DIR_TYPE),
        )
        return queries
            .flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_DEFAULT_ONLY) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { (pkg, _) -> pkg != context.packageName }
            .distinctBy { it.first }
            .map { (pkg, label) -> App(pkg, label) }
            .sortedBy { it.label.lowercase() }
    }

    /** 依使用者的選擇，依序嘗試開啟的 Intent（前一個找不到 App 時用下一個）。 */
    fun launchIntents(context: Context, target: Intent): List<Intent> = launchIntents(
        choice(context),
        target,
        chooserTitle = context.getString(R.string.editor_chooser_title),
    )

    internal fun launchIntents(choice: String?, target: Intent, chooserTitle: String): List<Intent> = when (choice) {
        null -> listOf(target)
        ASK -> listOf(Intent.createChooser(target, chooserTitle), target)
        else -> listOf(Intent(target).setPackage(choice), target)
    }
}
