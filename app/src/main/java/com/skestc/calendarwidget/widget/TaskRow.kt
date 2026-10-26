package com.skestc.calendarwidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.tasks.TaskItem
import com.skestc.calendarwidget.theme.LocalWidgetStyle

/** Google Tasks 待辦的一列：勾選框＋標題；逾期的用假日色並標「已逾期」。 */
@Composable
fun TaskRow(task: TaskItem, overdue: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val style = LocalWidgetStyle.current
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTaskAction>(
                actionParametersOf(CompleteTaskAction.TaskIdKey to task.id),
            ),
            colors = CheckboxDefaults.colors(
                checkedColor = style.colors.primary,
                uncheckedColor = style.colors.onSurfaceVariant,
            ),
        )
        Text(
            text = if (overdue) glanceString(R.string.task_overdue_title, task.title) else task.title,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = style.text(14, if (overdue) style.colors.sunday else style.colors.onSurface),
        )
    }
}
