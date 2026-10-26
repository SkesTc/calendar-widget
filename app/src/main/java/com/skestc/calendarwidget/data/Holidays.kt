package com.skestc.calendarwidget.data

enum class HolidayKind {
    /** 放假日：月曆用紅字。 */
    DAY_OFF,

    /** 補行上班日：只標字、不用紅字。 */
    MAKEUP_WORKDAY,

    /** 紀念日／節日但不放假（例如母親節）：只標字。 */
    OBSERVANCE,
}

/**
 * 辨識 Google 日曆的「節慶假日」日曆（例如「台灣的節慶假日」）。
 * 在 Google 日曆 → 設定 → 新增日曆 → 瀏覽感興趣的日曆 → 節慶假日 訂閱後，就會跟其他日曆一起同步到手機。
 */
object Holidays {
    private const val GOOGLE_HOLIDAY_SUFFIX = "#holiday@group.v.calendar.google.com"

    /** [ownerAccount] / [name] 是 Calendars 表的 OWNER_ACCOUNT 與 NAME，通常就是日曆 ID。 */
    fun isHolidayCalendar(ownerAccount: String?, name: String?): Boolean =
        listOfNotNull(ownerAccount, name).any { it.endsWith(GOOGLE_HOLIDAY_SUFFIX, ignoreCase = true) }

    /**
     * Google 節慶假日日曆在說明欄第一行標示類型（「紀念日」/「Observance」），
     * 補班日則寫在標題（「補行上班」）。其他都視為放假日。
     */
    fun classify(title: String, description: String?): HolidayKind {
        if ("補行上班" in title || "補班" in title || "makeup workday" in title.lowercase()) {
            return HolidayKind.MAKEUP_WORKDAY
        }
        val firstLine = description?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        if (firstLine.startsWith("紀念日") || firstLine.startsWith("Observance", ignoreCase = true)) {
            return HolidayKind.OBSERVANCE
        }
        return HolidayKind.DAY_OFF
    }
}
