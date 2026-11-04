package com.skestc.calendarwidget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleShortenerTest {

    @Test
    fun stripsYearAndTermPrefixes() {
        assertEquals("校慶", TitleShortener.compact("115年度校慶"))
        assertEquals("期中考", TitleShortener.compact("115學年度第一學期期中考"))
        assertEquals("期末考", TitleShortener.compact("115學年度 第2學期 期末考"))
        assertEquals("開學", TitleShortener.compact("115學年下學期開學"))
        assertEquals("教師研習", TitleShortener.compact("115年 教師研習"))
        assertEquals("家長日", TitleShortener.compact("2026年：家長日"))
        assertEquals("10月教師會議", TitleShortener.compact("115年10月教師會議"))
        assertEquals("運動會", TitleShortener.compact("一一五學年度－運動會"))
    }

    @Test
    fun leavesOtherTitlesAlone() {
        assertEquals("光復節", TitleShortener.compact("光復節"))
        assertEquals("小美生日", TitleShortener.compact("小美生日"))
        assertEquals("115 號公車", TitleShortener.compact("115 號公車"))
        assertEquals("會議 2026年回顧", TitleShortener.compact("會議 2026年回顧"))
    }

    @Test
    fun keepsTheOriginalWhenNothingWouldRemain() {
        assertEquals("115學年度", TitleShortener.compact("115學年度"))
        assertEquals("115年", TitleShortener.compact("  115年  "))
    }
}
