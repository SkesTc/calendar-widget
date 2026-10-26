# 行事曆小工具（Calendar Widget）

一個 Android 桌面行事曆小工具，可以顯示 Google 日曆的行程，重點放在外觀設計。

## 目前進度

### P0 雛形 ✅
- 透過 Android Calendar Provider（`CalendarContract`）讀取手機上已同步的 Google 日曆，**不需要 OAuth**
- 「行程清單」widget：大字日期、依日期分組、色條沿用日曆顏色、點事件開啟 Google 日曆、Material You 動態取色
- App 首頁：權限授權、列出已同步的日曆、一鍵把 widget 加到桌面

### P1 核心 ✅
- **月曆 widget**：有事件的日子會打上日曆顏色的小圓點（最多 3 個），可切換上／下個月、一鍵回到本月，點日期開啟那一天；週日、週六用不同顏色
- **每個 widget 各自設定**：新增時（或長按 →「編輯小工具」）可以選要顯示哪些日曆、行程清單要顯示 3／7／14／30 天
- **自動更新**
  - 日曆資料有變動：WorkManager Content URI 觸發器
  - 午夜換日：非精確鬧鐘，不需要額外權限，也不會把手機叫醒
  - 時區、時間、語言改變，以及開機、App 更新後
- 修正：widget 正在顯示時資料變動不會重新查詢（改成依設定和 refresh 計數器重新載入）

## 技術棧

| 項目 | 選用 |
|---|---|
| 語言 | Kotlin 2.0 |
| Widget | Jetpack Glance 1.1 |
| App UI | Jetpack Compose ＋ Material 3 |
| 背景更新 | WorkManager |
| 最低版本 | Android 8.0（API 26），targetSdk 35 |

## 專案結構

```
app/src/main/java/com/skestc/calendarwidget/
├── MainActivity.kt                App 首頁（權限、日曆清單、加入 widget）
├── config/WidgetConfigActivity.kt 每個 widget 的設定頁
├── settings/WidgetSettings.kt     per-widget 設定（存在 Glance 狀態）
├── data/
│   ├── CalendarModels.kt          CalendarInfo / CalendarEvent
│   ├── CalendarRepository.kt      查詢 Calendars 與 Instances 表
│   ├── AgendaFormatter.kt         清單分組與日期／時間格式
│   └── MonthBuilder.kt            把事件攤到月曆格子上
├── widget/
│   ├── AgendaWidget.kt            行程清單 widget
│   ├── MonthWidget.kt             月曆 widget
│   ├── ChangeMonthAction.kt       切換月份
│   ├── WidgetData.kt              依 key 重新載入資料
│   ├── WidgetUpdater.kt           更新全部 widget、管理背景排程
│   ├── CalendarChangeWorker.kt    日曆有變動就更新
│   ├── DayChangeScheduler.kt      午夜換日
│   └── TimeChangeReceiver.kt      時區／時間／開機
└── ui/theme/Theme.kt              Material You 主題
```

## 建置

用 Android Studio 開啟，或執行：

```bash
./gradlew testDebugUnitTest   # 單元測試
./gradlew assembleDebug       # 建置 APK
```

每次 push 和每個 PR，GitHub Actions 都會自動建置，APK 可以在 Actions 的 Artifacts 下載。

## 使用前提

手機的「設定 → 帳戶 → Google → 帳戶同步」要開啟「Google 日曆」，事件才會出現在系統的 Calendar Provider 裡。

## 開發路線圖

- **P2 美化**：主題包（玻璃擬態、AMOLED、莫蘭迪、手帳風）、透明度／圓角／字體設定、設定頁即時預覽
- **P3 進階**：時間軸 widget、農曆與節氣、台灣國定假日、Google Tasks
- **P4 上架**：各家桌面（One UI、MIUI、Pixel）相容性測試、Play 上架
