package com.skestc.calendarwidget.ui

/**
 * 各家手機的省電機制會擋掉背景更新（午夜換日、行程變動、時間軸），
 * 依 Build.MANUFACTURER 對應到品牌；各品牌的設定步驟在 strings.xml（tips_*）。
 */
enum class PhoneBrand { XIAOMI, SAMSUNG, OPPO, VIVO, HUAWEI, ASUS, OTHER }

object DeviceTips {

    fun brandOf(manufacturer: String): PhoneBrand = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> PhoneBrand.XIAOMI
        "samsung" -> PhoneBrand.SAMSUNG
        "oppo", "realme", "oneplus" -> PhoneBrand.OPPO
        "vivo", "iqoo" -> PhoneBrand.VIVO
        "huawei", "honor" -> PhoneBrand.HUAWEI
        "asus" -> PhoneBrand.ASUS
        else -> PhoneBrand.OTHER
    }
}
