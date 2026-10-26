package com.skestc.calendarwidget.ui

/**
 * 各家手機的省電機制會擋掉背景更新（午夜換日、行程變動、時間軸），
 * 依 Build.MANUFACTURER 給對應的設定步驟。
 */
object DeviceTips {

    fun forManufacturer(manufacturer: String): List<String> {
        val brand = manufacturer.lowercase()
        return when {
            brand in setOf("xiaomi", "redmi", "poco") -> listOf(
                "應用程式資訊 → 開啟「自啟動」",
                "應用程式資訊 → 省電策略 → 選「無限制」",
                "最近使用的 App 畫面中，把本 App 往下拉鎖定，避免被一鍵清除",
            )
            brand == "samsung" -> listOf(
                "應用程式資訊 → 電池 → 選「不受限制」",
                "設定 → 電池 → 背景使用限制：確認本 App 不在「睡眠中」或「深度睡眠中」的應用程式",
            )
            brand in setOf("oppo", "realme", "oneplus") -> listOf(
                "應用程式資訊 → 電池使用 → 開啟「允許背景活動」與「允許自動啟動」",
            )
            brand in setOf("vivo", "iqoo") -> listOf(
                "i 管家 → 應用程式管理 → 自啟動管理：允許本 App",
                "設定 → 電池 → 背景耗電管理：允許本 App 在背景執行",
            )
            brand in setOf("huawei", "honor") -> listOf(
                "設定 → 電池 → 應用程式啟動管理：本 App 改成「手動管理」並全部開啟",
            )
            brand == "asus" -> listOf(
                "手機管家 → 自動啟動管理員：允許本 App",
            )
            else -> listOf(
                "應用程式資訊 → 電池 → 選「不受限制」（或關閉電池最佳化）",
            )
        }
    }
}
