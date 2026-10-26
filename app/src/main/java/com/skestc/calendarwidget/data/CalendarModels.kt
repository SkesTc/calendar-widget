package com.skestc.calendarwidget.data

/** 一個日曆帳號底下的單一日曆（例如 Google 帳號的「個人」「家庭」）。 */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val visible: Boolean,
    /** Google「節慶假日」日曆。 */
    val isHoliday: Boolean = false,
)

/**
 * 一個事件「實例」。重複事件會被展開成多筆，每筆有自己的 [begin]/[end]。
 * 全天事件的 [begin]/[end] 是 UTC 午夜，顯示時必須用 UTC 換算日期。
 */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?,
    val calendarName: String,
    val calendarId: Long = 0,
    /** 來自節慶假日日曆時才有值。 */
    val holiday: HolidayKind? = null,
)
