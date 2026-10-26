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
    private const val EVENT_ITEM_TYPE = "vnd.android.cursor.item/event"

    data class App(val packageName: String, val label: String)

    /** null：系統預設；[ASK]：每次詢問；其他：App 的套件名稱。 */
    fun choice(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHOICE, null)

    fun setChoice(context: Context, choice: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (choice == null) remove(KEY_CHOICE) else putString(KEY_CHOICE, choice)
        }.apply()
    }

    /** 首頁顯示目前的選擇：「系統預設」「每次詢問」或 App 名稱（App 已移除時視為系統預設）。 */
    fun choiceLabel(context: Context, apps: List<App> = apps(context)): String = when (val c = choice(context)) {
        null -> context.getString(R.string.editor_default)
        ASK -> context.getString(R.string.editor_ask)
        else -> apps.firstOrNull { it.packageName == c }?.label ?: context.getString(R.string.editor_default)
    }

    fun insertIntent(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)

    /** 裝置上能新增行程的 App（Google 日曆、三星日曆、Outlook…），依名稱排序。 */
    fun apps(context: Context): List<App> {
        val pm = context.packageManager
        val queries = listOf(
            insertIntent(),
            Intent(Intent.ACTION_INSERT).setType(EVENT_DIR_TYPE),
            Intent(Intent.ACTION_INSERT).setType(EVENT_ITEM_TYPE),
        )
        val found = queries
            .flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_DEFAULT_ONLY) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        // Google 日曆不一定會被上面的查詢找到（各版本宣告的類型不同），有安裝就一定列出來。
        val google = runCatching {
            GOOGLE_CALENDAR to pm.getApplicationLabel(pm.getApplicationInfo(GOOGLE_CALENDAR, 0)).toString()
        }.getOrNull()
        return (listOfNotNull(google) + found)
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

    /**
     * 預設：有 Google 日曆就用它（跟點日期一樣），否則交給系統。
     * 指定 App 時先用標準的新增 Intent，不接受的話再試「單筆事件」類型，最後才退回系統預設。
     */
    internal fun launchIntents(choice: String?, target: Intent, chooserTitle: String): List<Intent> = when (choice) {
        null -> forPackage(GOOGLE_CALENDAR, target) + target
        ASK -> listOf(Intent.createChooser(target, chooserTitle), target)
        else -> forPackage(choice, target) + target
    }

    private fun forPackage(pkg: String, target: Intent): List<Intent> = listOf(
        Intent(target).setPackage(pkg),
        Intent(Intent.ACTION_INSERT).setType(EVENT_ITEM_TYPE).setPackage(pkg),
    )
}
