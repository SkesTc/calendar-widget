package com.skestc.calendarwidget.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 用 Robolectric 跑，確保用的是 Android 內建的 org.json 行為。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TasksJsonTest {

    @Test
    fun parsesTaskLists() {
        val json = """{"kind":"tasks#taskLists","items":[{"id":"L1","title":"我的待辦"},{"id":"L2","title":"Work"}]}"""
        assertEquals(listOf("L1", "L2"), TasksJson.parseLists(json).map { it.id })
        assertEquals(emptyList<TasksJson.TaskList>(), TasksJson.parseLists("""{"kind":"tasks#taskLists"}"""))
    }

    @Test
    fun parsesOpenTasksAndSkipsTheRest() {
        val json = """
            {"items":[
              {"id":"a","title":"繳電費","status":"needsAction","due":"2026-10-26T00:00:00.000Z"},
              {"id":"b","title":"買牛奶","status":"needsAction"},
              {"id":"c","title":"已完成","status":"completed","due":"2026-10-20T00:00:00.000Z"},
              {"id":"d","title":"已刪除","status":"needsAction","deleted":true},
              {"id":"e","title":"   ","status":"needsAction"},
              {"id":"f","title":"隱藏","status":"needsAction","hidden":true}
            ],"nextPageToken":"page2"}
        """.trimIndent()
        val tasks = TasksJson.parseTasks(json, "L1")
        assertEquals(listOf("a", "b"), tasks.map { it.id })
        assertEquals(LocalDate.of(2026, 10, 26), tasks[0].due)
        assertNull(tasks[1].due)
        assertEquals("L1", tasks[0].listId)
        assertEquals("page2", TasksJson.nextPageToken(json))
        assertNull(TasksJson.nextPageToken("""{"items":[]}"""))
    }

    @Test
    fun cacheRoundTrip() {
        val tasks = listOf(
            TaskItem("a", "L1", "繳電費", LocalDate.of(2026, 10, 26)),
            TaskItem("b", "L2", "買牛奶", null),
        )
        assertEquals(tasks, TasksJson.fromCache(TasksJson.toCache(tasks)))
        assertEquals(emptyList<TaskItem>(), TasksJson.fromCache(null))
    }
}
