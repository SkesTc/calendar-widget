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

### P2 美化 ✅
- **5 種主題**：Material You（跟著桌布取色）、AMOLED 純黑、莫蘭迪、手帳（米色紙張＋襯線字）、毛玻璃（半透明漸層＋亮邊）
- **外觀設定**（每個 widget 各自設定）：背景不透明度 0～100%、圓角（直角／12／24／32）、文字大小（小／中／大／特大）、字型（跟隨主題／無襯線／襯線／等寬）
- **即時預覽**：設定頁上方會墊一張示意桌布，即時顯示 widget 的樣子，透明度和毛玻璃效果都看得到
- **對比度測試**：每個主題在淺色、深色模式下都通過 WCAG 對比度檢查（內文 4.5:1）

> 限制：widget 不支援即時模糊，所以毛玻璃是用半透明漸層模擬的。Material You 主題的背景調成半透明時，換桌布後要等 widget 下次更新（最多 30 分鐘）才會跟上新顏色。

### P3 進階 ✅（Google Tasks 待確認）
- **今日時間軸 widget**：今天的行程依時間排成一列；已結束的變淡、進行中的放大並標示「進行中 · 到 15:00」、標出「現在」位置與一小時以上的空檔。行程開始或結束時自動更新（非喚醒鬧鐘，不耗電）
- **農曆、節氣、傳統節日**：月曆格子顯示農曆小字（初一顯示月份）、二十四節氣、春節／元宵／端午／七夕／中元／中秋／重陽／臘八／除夕；行程清單與時間軸的標題也會顯示農曆日期。每個 widget 可以關閉
  - 農曆用 Android 內建的 ICU `ChineseCalendar` 換算
  - 節氣用太陽視黃經計算（Meeus 低精度公式，以東八區判定日期）
- **台灣國定假日與補班**：讀取 Google 日曆的「台灣的節慶假日」（需要先在 Google 日曆訂閱）。放假日用紅字，補班日標「補班」，紀念日只標名稱。每年政府公布的放假日與補班日由 Google 更新，不必改 App

### P4 上架準備 ✅（實機測試待進行）
- **符合 Play 規定**：`targetSdk 36`、R8 縮減與資源縮減、上傳金鑰簽署（從環境變數讀取，不放進 repo）
- **CI**：單元測試、Android Lint、未簽署的 release 建置（確認 R8 正常）；推送 `v*` tag 時用 repo secrets 產生已簽署的 AAB
- **桌面相容性**
  - 圓角預設「跟隨系統」（讀 Android 12+ 的 `system_app_widget_background_radius`），和 Pixel、One UI 等桌面上其他小工具一致
  - 小工具清單裡的預覽畫面（`previewLayout`），跟著桌布動態取色
  - App 首頁「小工具沒有自動更新？」依手機品牌（小米、Samsung、OPPO、vivo、華為…）顯示省電設定步驟
- **上架文件**（[docs/](docs/)）：[發佈流程](docs/release.md)、[實機測試清單](docs/device-test-checklist.md)、[商店資訊與資料安全性](docs/play-listing.md)、[隱私權政策](docs/privacy-policy.md)、[圖示與主題圖片](docs/play-assets/)

### 更多 widget 樣式 ✅
- **下一個行程**（2×1 起）：只顯示進行中或下一個行程（「進行中 · 到 15:00」「明天 09:00」），適合放在時鐘或搜尋列旁；行程開始或結束時自動換下一個。拉高時多顯示地點
- **一週**（4×2）：從今天起 7 天並排，每天的行程用事件顏色的淡色塊列出，放不下的顯示「+2」；假日紅字、可顯示農曆，依高度自動調整每天顯示幾筆

### 自動化 UI 測試 ✅
- 用 Glance 官方的 `glance-appwidget-testing` 搭配 Robolectric，在 CI 裡直接畫出 5 種 widget 並檢查畫面上的文字：
  - 行程清單：日期分組、農曆、假日併入標題、2×2 只顯示下一個、空狀態、未授權
  - 月曆：標題與切換按鈕、「今」按鈕、假日名稱與「補班」、農曆小字（縮小時隱藏）
  - 時間軸：「現在」與空檔、進行中、全天列（不含放假日）、空狀態
  - 下一個行程：進行中、拉高時顯示地點、「明天 · 全天」、沒有行程
  - 一週：依高度顯示筆數與「+N」、假日與農曆小字
- 農曆換算改用 Robolectric 內的 `android.icu` 驗證已知日期（2026 春節、除夕、清明、2025 閏六月）

### 英文介面 ✅
- 所有介面文字移到字串資源：`values/`（英文，預設）、`values-b+zh+Hant/`（繁體中文）與 `values-zh/`（同內容，給簡體中文語系當備援）。
  繁體一定要放在 `b+zh+Hant`：Android 7+ 會把 `values-zh` 當成簡體，台灣手機反而會顯示英文。由 `scripts/gen_strings.py` 從同一張表產生，確保兩種語言的 key 一致（Lint 會擋缺少的翻譯）
- 手機是中文時跟原本一樣；其他語言顯示英文，且預設關閉農曆（設定裡仍可開啟）
- 小工具清單的預覽圖文字也跟著語系切換
- [英文版 Play 商店資訊](docs/play-listing.md#english-store-listing-en-us)

> 修改介面文字：編輯 `scripts/gen_strings.py` 後執行 `python3 scripts/gen_strings.py app/src/main/res`。

### 自訂主題色 ✅
- 第 6 個主題「自訂」：只選主色和背景色（12 個主色、10 個背景色票，加上色相／飽和度／亮度微調）
- 其他顏色由 `CustomTheme.derive` 自動推算：文字用深色或淺色中對比較高的、主色太接近背景時自動加深或調亮、卡片用主色淡淡地疊在背景上
- 單元測試掃過 46,656 種主色 × 背景組合，全部符合 WCAG 對比度（內文 4.5:1、強調色 3:1）

### 月曆點日期看行程 ✅
- 月曆 widget 拉到約 4×5 以上時，下方會列出選取日期的行程（預設今天，最多 3 筆，其餘顯示「+N」）
- 點日期切換選取，選取的日期用卡片色圓底標示；點行程開啟該事件，點「開啟 ›」在日曆中開啟那一天
- 比較矮時維持原本的行為：點日期直接開啟 Google 日曆；切換月份時清除選取

### 平板／摺疊機大版面 ✅
- 月曆 widget 拉寬到 480dp 以上（平板、摺疊機展開）時，選取日期的行程改放在月曆右邊，最多 6 筆，一樣可以點日期切換
- 修正：Glance 的 `SizeMode.Responsive` 只會用宣告過的尺寸畫版面，原本月曆只宣告了 180×180、250×250，
  所以實機上農曆小字與下方行程永遠不會出現（一週 widget 也最多只顯示 3 筆）。
  月曆改用 `SizeMode.Exact`（依實際大小畫，也避免多份大型版面一次送出超過 Binder 上限）；一週補上每個門檻的尺寸；並有測試防止再發生

### 一週 widget 改版 ✅（依實機回饋）
- 直長（約 4×4 以上）改成**每天一列**：左邊日期、右邊整行寬度的行程（時間＋標題），每天顯示幾行依實際高度計算，不再留下大片空白
- 扁長（4×2、4×3）維持 7 欄，每欄的色塊數也依實際高度計算
- 自動縮短學校／機關行事曆常見的年度前綴：「115學年度第一學期期中考」→「期中考」、「115年度校慶」→「校慶」，窄欄位不再整排都是「115年…」
- 改用 `SizeMode.Exact`，依 widget 的實際大小排版

### 隱藏長期行程 ✅（依實機回饋）
- 首頁「長期行程」→「管理」：列出跨 3 天以上的行程（一個月前到三個月後），可以個別隱藏
- 隱藏後所有 widget 都不再顯示；重複的行程會一起隱藏；Google 日曆裡的行程不受影響
- 已隱藏的行程即使超出查詢範圍也會留在清單裡，隨時可以取消隱藏
- 每個 widget 的設定頁下方也有「隱藏長期行程…」的入口

### 實機回饋第二輪 ✅
- **重複的行程也能隱藏**：「長期行程」除了跨 3 天以上，也列出在範圍內重複 3 次以上的行程（例如每天的早自習），顯示「重複 N 次」
- **依帳號隱藏日曆**：小工具設定的日曆清單依帳號分組，帳號那一列可以一次顯示／隱藏該帳號的所有日曆（部分顯示時顯示「—」）
- **新增行程後立刻更新**：從 widget 開啟日曆（`+`、點行程、點日期）改經過透明的 `CalendarLaunchActivity`，回到桌面時馬上更新所有 widget；背景的內容變更觸發也縮短到 1～10 秒
- **今天已結束的行程淡化顯示**：行程清單、一週、月曆的當天行程不再把已結束的拿掉，而是色條轉灰、文字改次要色；行程結束時自動重畫

### UI 改版：M3 Expressive ✅（參考 GitHub 專案）
- 參考 [nsh07/Tomato](https://github.com/nsh07/Tomato)（Glance widget 的版面層次）與 [PixelPlayerHQ/PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer)（M3 Expressive 風格）；只參考設計，沒有複製程式碼
- 大號日期改用圓潤粗體數字（Google Sans Flex 子集，ROND 100、等寬數字），以點陣圖繪製後依主題染色；選襯線／等寬字型時維持原字型
- 同一天連續的行程卡片連成一組：外側大圓角、內側小圓角（分段清單）
- 外框內距統一 16dp，日期標題加粗；Android 12 以前也有圓角外框
- 升級 Jetpack Glance 1.2.0

字型授權：`app/src/main/assets/fonts/` 內的 Google Sans Flex 子集採 SIL Open Font License 1.1，全文見同目錄的 `OFL.txt`。

### 實機回饋第三輪 ✅
- **行程清單至少顯示一個月**：天數選項改成 1／2／3 個月，舊的 3／7／14 天設定自動升到 1 個月（最多列 150 筆）
- **點行程清單左上角的日期**：開啟 Google 日曆的今天；沒裝 Google 日曆時用系統預設的日曆 App
- **「＋」可選新增行程的 App**：小工具設定裡選「系統預設」「每次詢問」或指定 App（Google 日曆、三星日曆…），所有小工具共用；指定的 App 被移除時自動退回系統預設
- **月曆下方的當天行程區塊**：依當天行程數量自動長高（最多約一半高度），行程多時列得更多；約 4×4 以上就會出現
- **有行程的日子更醒目**：圓點放大（5 → 7dp）、間距加寬，日期數字改粗體

### 設定入口 ✅
- 行程清單、月曆、時間軸的「＋」旁邊多一顆齒輪，點了直接打開這個小工具的設定（也可以長按小工具 →「小工具設定」）
- App 首頁新增「「＋」新增行程使用」卡片，顯示目前選擇，按「更改」挑選 App（所有小工具共用）

### 月曆加速、行程清單時間軸版面 ✅
- **月曆切換更快**：載入過的月份留在記憶體，並在背景先載好前後各一個月；農曆換算也會記住。點日期只換下方行程，不再重新查詢日曆
- **行程清單「時間軸」版面**：小工具設定 →「版面」選「時間軸」，每天一組，左側時間欄＋圓點，今天的行程之間有「現在」橫線，進行中的行程放大標示

## 技術棧

| 項目 | 選用 |
|---|---|
| 語言 | Kotlin 2.0 |
| Widget | Jetpack Glance 1.1 |
| App UI | Jetpack Compose ＋ Material 3 |
| 背景更新 | WorkManager |
| 最低版本 | Android 8.0（API 26），targetSdk 36 |

## 專案結構

```
app/src/main/java/com/skestc/calendarwidget/
├── MainActivity.kt                App 首頁（權限、日曆清單、加入 widget）
├── config/WidgetConfigActivity.kt 每個 widget 的設定頁
├── config/WidgetPreview.kt        設定頁的即時預覽
├── settings/WidgetSettings.kt     per-widget 設定（存在 Glance 狀態）
├── theme/
│   ├── ThemeSpecs.kt              5 套主題配色＋WCAG 對比度計算（純 Kotlin）
│   └── WidgetStyle.kt             把主題與外觀設定套用到 Glance widget
├── data/
│   ├── CalendarModels.kt          CalendarInfo / CalendarEvent
│   ├── CalendarRepository.kt      查詢 Calendars 與 Instances 表
│   ├── AgendaFormatter.kt         清單分組與日期／時間格式
│   ├── MonthBuilder.kt            把事件攤到月曆格子上
│   ├── TimelineBuilder.kt         時間軸：狀態、現在位置、空檔
│   ├── WeekBuilder.kt             一週 widget 的每日行程；「下一個行程」的挑選
│   └── Holidays.kt                辨識節慶假日日曆與放假／補班／紀念日
├── lunar/
│   ├── Lunar.kt                   農曆名稱、傳統節日、格子小字的優先順序
│   ├── IcuLunarSource.kt          用 ICU ChineseCalendar 換算農曆
│   └── SolarTerms.kt              二十四節氣（天文計算）
├── widget/
│   ├── AgendaWidget.kt            行程清單 widget
│   ├── MonthWidget.kt             月曆 widget
│   ├── TimelineWidget.kt          今日時間軸 widget
│   ├── NextEventWidget.kt         下一個行程 widget
│   ├── WeekWidget.kt              一週 widget
│   ├── ChangeMonthAction.kt       切換月份
│   ├── SelectDayAction.kt         月曆上點選日期
│   ├── WidgetData.kt              依 key 重新載入資料
│   ├── WidgetUpdater.kt           更新全部 widget、管理背景排程
│   ├── CalendarChangeWorker.kt    日曆有變動就更新
│   ├── DayChangeScheduler.kt      午夜換日、時間軸的行程邊界
│   └── TimeChangeReceiver.kt      時區／時間／開機
└── ui/
    ├── DeviceTips.kt / UpdateHelp.kt  各品牌的背景執行設定說明
    └── theme/Theme.kt             Material You 主題
```

## 建置

用 Android Studio 開啟，或執行：

```bash
./gradlew testDebugUnitTest   # 單元測試＋widget UI 測試（Robolectric）
./gradlew assembleDebug       # 建置 APK
```

每次 push 和每個 PR，GitHub Actions 都會自動建置，APK 可以在 Actions 的 Artifacts 下載。

## 使用前提

- 手機的「設定 → 帳戶 → Google → 帳戶同步」要開啟「Google 日曆」，事件才會出現在系統的 Calendar Provider 裡。
- 要顯示國定假日與補班日：在電腦版 Google 日曆「設定 → 新增日曆 → 瀏覽感興趣的日曆 → 節慶假日」勾選「台灣的節慶假日」。

## 開發路線圖

- **Google Tasks**：需要 Google 登入（OAuth）與 Google Cloud 專案設定
- **上架**：依 [實機測試清單](docs/device-test-checklist.md) 測試、封閉測試、正式上架
