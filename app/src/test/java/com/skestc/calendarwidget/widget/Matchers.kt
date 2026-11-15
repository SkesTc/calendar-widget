package com.skestc.calendarwidget.widget

import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo

/** 大號數字可能是文字，也可能是畫成點陣圖的 ExpressiveText（以 contentDescription 帶文字）。 */
internal fun hasNumber(text: String): GlanceNodeMatcher<MappedNode> =
    hasTextEqualTo(text) or hasContentDescriptionEqualTo(text)
