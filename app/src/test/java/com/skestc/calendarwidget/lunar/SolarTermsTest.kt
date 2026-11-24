package com.skestc.calendarwidget.lunar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 以天文台公布的二分二至時刻（換算東八區）驗證。 */
class SolarTermsTest {

    private fun assertTerm(name: String, date: LocalDate) {
        assertEquals("$date", name, SolarTerms.termOn(date))
        assertEquals("${date.minusDays(1)}", null, SolarTerms.termOn(date.minusDays(1)))
    }

    @Test
    fun equinoxesAndSolstices2024() {
        assertTerm("春分", LocalDate.of(2024, 3, 20))
        // 2024-06-20 20:51 UTC，東八區已經是 6/21。
        assertTerm("夏至", LocalDate.of(2024, 6, 21))
        assertTerm("秋分", LocalDate.of(2024, 9, 22))
        assertTerm("冬至", LocalDate.of(2024, 12, 21))
    }

    @Test
    fun equinoxesAndSolstices2025() {
        assertTerm("春分", LocalDate.of(2025, 3, 20))
        assertTerm("夏至", LocalDate.of(2025, 6, 21))
        // 2025-09-22 18:19 UTC → 東八區 9/23 02:19。
        assertTerm("秋分", LocalDate.of(2025, 9, 23))
        // 2025-12-21 15:03 UTC → 東八區 23:03，離午夜不到一小時。
        assertTerm("冬至", LocalDate.of(2025, 12, 21))
    }

    @Test
    fun equinoxesAndSolstices2026() {
        // 2026-03-20 14:46 UTC → 東八區 22:46。
        assertTerm("春分", LocalDate.of(2026, 3, 20))
        assertTerm("夏至", LocalDate.of(2026, 6, 21))
        assertTerm("秋分", LocalDate.of(2026, 9, 23))
        // 2026-12-21 20:50 UTC → 東八區 12/22 04:50。
        assertTerm("冬至", LocalDate.of(2026, 12, 22))
        assertTerm("立春", LocalDate.of(2026, 2, 4))
    }

    @Test
    fun everyYearHasAll24TermsInOrder() {
        for (year in 2020..2035) {
            val terms = SolarTerms.termsOf(year)
            assertEquals("$year", 24, terms.size)
            assertEquals("$year", "小寒", terms.values.first())
            assertEquals("$year", "冬至", terms.values.last())
            assertEquals("$year", SolarTerms.NAMES.toSet(), terms.values.toSet())
        }
    }
}
