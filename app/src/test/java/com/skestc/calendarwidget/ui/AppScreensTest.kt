package com.skestc.calendarwidget.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.skestc.calendarwidget.MainActivity
import com.skestc.calendarwidget.data.RecentSearches
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 實際啟動 Activity：第一次開 App 的引導、搜尋畫面的最近搜尋與沒有結果。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW")
class AppScreensTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun firstLaunchShowsOnboarding() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithText("歡迎使用行事曆小工具").assertIsDisplayed()
            rule.onNodeWithText("第 1 步，共 3 步").assertIsDisplayed()
        }
    }

    @Test
    fun afterOnboardingWithoutPermissionShowsPermissionScreen() {
        Onboarding.setDone(app)
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithText("授權讀取日曆").assertIsDisplayed()
            rule.onNodeWithText("歡迎使用行事曆小工具").assertDoesNotExist()
        }
    }

    @Test
    fun searchShowsAndClearsRecentKeywords() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        RecentSearches(app).add("期中考")
        RecentSearches(app).add("班會")
        ActivityScenario.launch(SearchActivity::class.java).use {
            rule.onNodeWithText("最近搜尋").assertIsDisplayed()
            rule.onNodeWithText("班會").assertIsDisplayed()
            rule.onNodeWithText("期中考").assertIsDisplayed()

            rule.onNodeWithText("清除").performClick()
            rule.onNodeWithText("期中考").assertDoesNotExist()
            rule.onNodeWithText("最近搜尋").assertDoesNotExist()
        }
    }

    @Test
    fun pickingRecentKeywordSearchesIt() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        RecentSearches(app).add("期中考")
        ActivityScenario.launch(SearchActivity::class.java).use {
            rule.onNodeWithText("期中考").performClick()
            // 測試環境沒有日曆資料，查完會顯示找不到。
            rule.waitUntil(5_000) {
                rule.onAllNodesWithText("找不到符合「期中考」的行程").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun searchWithoutPermissionExplainsWhy() {
        ActivityScenario.launch(SearchActivity::class.java).use {
            rule.onNodeWithText("尚未授權讀取日曆，請先開啟 App 完成授權。").assertIsDisplayed()
            rule.onNodeWithText("最近搜尋").assertDoesNotExist()
        }
    }
}
