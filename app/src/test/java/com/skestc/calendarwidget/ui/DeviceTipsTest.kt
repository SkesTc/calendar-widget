package com.skestc.calendarwidget.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTipsTest {

    @Test
    fun brandSpecificTips() {
        assertTrue(DeviceTips.forManufacturer("Xiaomi").any { "自啟動" in it })
        assertTrue(DeviceTips.forManufacturer("POCO").any { "自啟動" in it })
        assertTrue(DeviceTips.forManufacturer("samsung").any { "不受限制" in it })
        assertTrue(DeviceTips.forManufacturer("OnePlus").any { "自動啟動" in it })
    }

    @Test
    fun unknownBrandGetsGenericTip() {
        val tips = DeviceTips.forManufacturer("Google")
        assertTrue(tips.size == 1 && "電池" in tips.single())
    }
}
