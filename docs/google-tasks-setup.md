# 設定 Google Tasks 連結

App 用 Google Identity Services 的 `AuthorizationClient` 取得 Google Tasks 的權限。程式裡 **不需要放 client ID**：
Google 會用「套件名稱＋簽署憑證的 SHA-1」去比對你在 Google Cloud Console 建立的 Android OAuth 用戶端。
沒有設定好時，按「連結 Google Tasks」會顯示「找不到對應的 OAuth 用戶端」。

## 1. 建立 Google Cloud 專案並啟用 API

1. 到 <https://console.cloud.google.com/> 建立專案（例如 `calendar-widget`）。
2. 「API 和服務 → 程式庫」搜尋 **Google Tasks API**，按「啟用」。

## 2. 設定 OAuth 同意畫面

「API 和服務 → OAuth 同意畫面」（新版介面叫「Google Auth Platform」）：

- 使用者類型：**外部**
- 應用程式名稱：行事曆小工具；支援電子郵件、開發人員聯絡資訊填你的信箱
- 範圍（Scopes）：新增 `https://www.googleapis.com/auth/tasks`
- 發布狀態先維持 **測試中**，在「測試使用者」加入要用的 Google 帳號（最多 100 個）

> `auth/tasks` 屬於 Google 的「敏感範圍」。測試中的 App 只有測試使用者能登入，而且授權大約 7 天會過期、需要重新連結。
> 要開放給所有人使用，必須「發布」並通過 Google 的驗證（需要隱私權政策網址、App 首頁、說明影片），審核可能要幾週。

## 3. 建立 Android OAuth 用戶端

「API 和服務 → 憑證 → 建立憑證 → OAuth 用戶端 ID」，應用程式類型選 **Android**。每一種簽署金鑰各建一個：

| 用途 | 套件名稱 | SHA-1 |
|---|---|---|
| CI 建置的 debug APK（`app/debug.keystore`，已放在 repo） | `com.skestc.calendarwidget` | `DD:00:C7:9F:6C:45:0E:2E:92:E6:E0:E6:2A:71:DC:95:0D:E7:C1:A9` |
| 從 Google Play 安裝的版本 | `com.skestc.calendarwidget` | Play Console →「測試與發布 → 應用程式完整性 → 應用程式簽署」裡的 **應用程式簽署金鑰** SHA-1 |
| 自己用上傳金鑰簽署、直接安裝的 release APK | `com.skestc.calendarwidget` | `keytool -list -v -keystore upload.jks -alias upload` 顯示的 SHA-1 |

debug 金鑰的 SHA-1 可以這樣確認：

```bash
keytool -list -v -keystore app/debug.keystore -storepass android -alias androiddebugkey | grep SHA1
```

## 4. 在 App 裡連結

1. 安裝 APK，打開 App。
2. 首頁「Google Tasks 待辦」→「連結 Google Tasks」→ 選擇帳號、允許存取。
3. 幾秒後就會同步。行程清單與時間軸 widget 會顯示有到期日的待辦（沒有到期日的待辦不會顯示），勾選即可完成。

之後每 30 分鐘（有網路時）自動同步一次。不想用時，按「取消連結」會清除手機上的待辦快取；
要完全撤銷權限，請到 <https://myaccount.google.com/permissions> 移除本 App。
