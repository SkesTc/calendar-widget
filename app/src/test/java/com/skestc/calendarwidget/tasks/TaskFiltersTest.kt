package com.skestc.calendarwidget.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskFiltersTest {
    private val today = LocalDate.of(2026, 10, 26)
    private fun task(id: String, due: LocalDate?) = TaskItem(id, "list", "t$id", due)

    @Test
    fun dueThroughKeepsDatedTasksUpToTheLastDaySorted() {
        val tasks = listOf(
            task("1", today.plusDays(2)),
            task("2", null),
            task("3", today.minusDays(3)),
            task("4", today.plusDays(7)),
            task("5", today),
        )
        assertEquals(listOf("3", "5", "1"), TaskFilters.dueThrough(tasks, today.plusDays(6)).map { it.id })
        assertEquals(listOf("3", "5"), TaskFilters.dueThrough(tasks, today).map { it.id })
    }

    @Test
    fun overdueTasksMoveToToday() {
        assertTrue(TaskFilters.isOverdue(task("1", today.minusDays(1)), today))
        assertFalse(TaskFilters.isOverdue(task("2", today), today))
        assertFalse(TaskFilters.isOverdue(task("3", null), today))
        assertEquals(today, TaskFilters.displayDate(task("1", today.minusDays(5)), today))
        assertEquals(today.plusDays(2), TaskFilters.displayDate(task("2", today.plusDays(2)), today))
        assertNull(TaskFilters.displayDate(task("3", null), today))
    }
}
