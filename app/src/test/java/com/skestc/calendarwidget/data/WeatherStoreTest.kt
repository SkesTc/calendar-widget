package com.skestc.calendarwidget.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherStoreTest {

    private val store = WeatherStore(ApplicationProvider.getApplicationContext())

    @Test
    fun offNeverAsksForRefresh() {
        assertFalse(store.claimRefresh(now = 1_000_000))
    }

    @Test
    fun refreshRequestsAreThrottled() {
        store.mode = WeatherLocationMode.PLACE
        store.place = WeatherPlaces.byId("taichung")
        val t = 10 * 60 * 60 * 1000L
        // 沒有快取：第一次要求更新，15 分鐘內不再要求（抓不到時避免反覆重畫）。
        assertTrue(store.claimRefresh(now = t))
        assertFalse(store.claimRefresh(now = t + 60_000))
        assertFalse(store.claimRefresh(now = t + WeatherStore.REQUEST_INTERVAL_MS - 1))
        assertTrue(store.claimRefresh(now = t + WeatherStore.REQUEST_INTERVAL_MS + 1))
    }
}
