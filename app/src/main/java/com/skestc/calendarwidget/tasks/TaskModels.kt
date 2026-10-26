package com.skestc.calendarwidget.tasks

import java.time.LocalDate

/** Google Tasks 的一筆待辦（只保留 widget 需要的欄位）。 */
data class TaskItem(
    val id: String,
    val listId: String,
    val title: String,
    /** Google Tasks 的到期日只有日期，沒有時間；沒設定時為 null。 */
    val due: LocalDate?,
)

object TaskFilters {

    /** 到期日在 [last] 之前（含）的待辦，依到期日排序；沒有到期日的不顯示在日期型 widget 上。 */
    fun dueThrough(tasks: List<TaskItem>, last: LocalDate): List<TaskItem> =
        tasks.filter { it.due != null && it.due <= last }.sortedWith(compareBy({ it.due }, { it.title }))

    fun isOverdue(task: TaskItem, today: LocalDate): Boolean = task.due != null && task.due < today

    /** 待辦要顯示在哪一天：逾期的併到今天。 */
    fun displayDate(task: TaskItem, today: LocalDate): LocalDate? = task.due?.let { maxOf(it, today) }
}
