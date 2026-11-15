package com.skestc.calendarwidget.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTipsTest {

    @Test
    fun brandsAreDetectedCaseInsensitively() {
        assertEquals(PhoneBrand.XIAOMI, DeviceTips.brandOf("Xiaomi"))
        assertEquals(PhoneBrand.XIAOMI, DeviceTips.brandOf("POCO"))
        assertEquals(PhoneBrand.XIAOMI, DeviceTips.brandOf("Redmi"))
        assertEquals(PhoneBrand.SAMSUNG, DeviceTips.brandOf("samsung"))
        assertEquals(PhoneBrand.OPPO, DeviceTips.brandOf("OnePlus"))
        assertEquals(PhoneBrand.VIVO, DeviceTips.brandOf("iQOO"))
        assertEquals(PhoneBrand.HUAWEI, DeviceTips.brandOf("HONOR"))
    }

    @Test
    fun unknownBrandFallsBackToGenericTips() {
        assertEquals(PhoneBrand.OTHER, DeviceTips.brandOf("Google"))
    }
}
