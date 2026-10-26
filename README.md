# 行事曆小工具（Calendar Widget）

一個 Android 桌面行事曆小工具，可以顯示 Google 日曆的行程，重點放在外觀設計。

## 目前進度：P0 雛形

- ✅ 透過 Android Calendar Provider（`CalendarContract`）讀取手機上已同步的 Google 日曆，**不需要 OAuth**
- ✅ 「行程清單」widget（Jetpack Glance）
  - 大字日期標題，右上角 `+` 可以新增事件
  - 事件依「今天／明天／日期」分組，色條沿用 Google 日曆裡設定的顏色
  - 點事件會開啟 Google 日曆的事件頁面
  - 響應式排版：2×2 只顯示下一個行程，3×2 以上顯示完整清單
  - Material You 動態取色（Android 12+），自動支援深色模式
- ✅ 自動更新：日曆資料有變動時（WorkManager 的 Content URI 觸發器）、時區或時間改變時、每 30 分鐘一次
- ✅ App 首頁：權限授權、列出已同步的日曆、一鍵把 widget 加到桌面

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
├── MainActivity.kt              App 首頁（權限、日曆清單、加入 widget）
├── data/
│   ├── CalendarModels.kt        CalendarInfo / CalendarEvent
│   ├── CalendarRepository.kt    查詢 Calendars 與 Instances 表
│   └── AgendaFormatter.kt       分組與日期／時間格式
├── widget/
│   ├── AgendaWidget.kt          Glance widget UI
│   ├── AgendaWidgetReceiver.kt
│   ├── CalendarChangeWorker.kt  日曆有變動就更新
│   └── TimeChangeReceiver.kt    時區或時間改變就更新
└── ui/theme/Theme.kt            Material You 主題
```

## 建置

用 Android Studio 開啟，或執行：

```bash
./gradlew assembleDebug
```

每次 push 和每個 PR，GitHub Actions 都會自動建置，APK 可以在 Actions 的 Artifacts 下載。

## 使用前提

手機的「設定 → 帳戶 → Google → 帳戶同步」要開啟「Google 日曆」，事件才會出現在系統的 Calendar Provider 裡。

## 開發路線圖

- **P1 核心**：月曆 widget、選擇要顯示哪些日曆、每個 widget 各自設定、午夜準時換日
- **P2 美化**：主題包（玻璃擬態、AMOLED、莫蘭迪、手帳風）、透明度／圓角／字體設定、設定頁即時預覽
- **P3 進階**：時間軸 widget、農曆與節氣、台灣國定假日、Google Tasks
- **P4 上架**：各家桌面（One UI、MIUI、Pixel）相容性測試、Play 上架
