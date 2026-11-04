package com.skestc.calendarwidget.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.skestc.calendarwidget.R

private val ACCENT_PRESETS = listOf(
    0xFF1A73E8, 0xFF0B8043, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF8E24AA,
    0xFF3F51B5, 0xFF039BE5, 0xFF33B679, 0xFFD50000, 0xFF795548, 0xFF616161,
)

private val BACKGROUND_PRESETS = listOf(
    0xFFFFFFFF, 0xFFFBF6EA, 0xFFF1F3F4, 0xFFE8F0FE, 0xFFFCE8E6, 0xFFE6F4EA,
    0xFF202124, 0xFF1B2A41, 0xFF2D2A26, 0xFF000000,
)

/**
 * 自訂主題的顏色：切換「主色／背景」，點色票快速選，或用色相、飽和度、亮度微調。
 * 文字色與對比度由 CustomTheme.derive 自動處理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorEditor(primary: Long, background: Long, onChange: (primary: Long, background: Long) -> Unit) {
    var editingAccent by remember { mutableStateOf(true) }
    val current = if (editingAccent) primary else background
    fun update(color: Long) = if (editingAccent) onChange(color, background) else onChange(primary, color)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(
                selected = editingAccent,
                onClick = { editingAccent = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Swatch(primary, 16) },
                label = { Text(stringResource(R.string.custom_target_accent)) },
            )
            SegmentedButton(
                selected = !editingAccent,
                onClick = { editingAccent = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Swatch(background, 16) },
                label = { Text(stringResource(R.string.custom_target_background)) },
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(if (editingAccent) ACCENT_PRESETS else BACKGROUND_PRESETS) { preset ->
                Swatch(
                    color = preset,
                    sizeDp = 36,
                    selected = (preset and 0xFFFFFF) == (current and 0xFFFFFF),
                    modifier = Modifier.clickable { update(preset) },
                )
            }
        }

        // HSL 微調。用目前顏色換算出滑桿位置；拖動時再換回 ARGB。
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(current.toInt(), it) }
        fun withHsl(h: Float = hsl[0], s: Float = hsl[1], l: Float = hsl[2]): Long =
            ColorUtils.HSLToColor(floatArrayOf(h, s, l)).toLong() and 0xFFFFFFFFL
        HslSlider(stringResource(R.string.custom_hue), hsl[0], 0f..360f) { update(withHsl(h = it)) }
        HslSlider(stringResource(R.string.custom_saturation), hsl[1], 0f..1f) { update(withHsl(s = it)) }
        HslSlider(stringResource(R.string.custom_lightness), hsl[2], 0f..1f) { update(withHsl(l = it)) }

        Text(
            stringResource(R.string.custom_contrast_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun HslSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Swatch(color: Long, sizeDp: Int, selected: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    )
}
