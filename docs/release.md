# 發佈到 Google Play

## 1. 建立上傳金鑰（只做一次）

```bash
keytool -genkeypair -v \
  -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

- 這是 **上傳金鑰**。開啟 Play App Signing（新 App 預設開啟）後，真正簽署 App 的金鑰由 Google 保管；上傳金鑰遺失時可以向 Google 申請更換。
- `upload.jks` 和密碼 **不要** 放進 repo（`.gitignore` 已排除 `*.jks`），請另外備份。

## 2. 設定 GitHub Secrets

到 repo 的 Settings → Secrets and variables → Actions，新增：

| Secret | 內容 |
|---|---|
| `CW_KEYSTORE_BASE64` | `base64 -w0 upload.jks` 的輸出（macOS：`base64 -i upload.jks`） |
| `CW_KEYSTORE_PASSWORD` | keystore 密碼 |
| `CW_KEY_ALIAS` | `upload` |
| `CW_KEY_PASSWORD` | 金鑰密碼 |

## 3. 產生要上傳的 AAB

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions 的 `release` job 會產生已簽署的 `app-release.aab` 和 `mapping.txt`（在該次執行的 Artifacts）。

- `versionCode` 用 Actions 的執行編號，每次都會變大，符合 Play 的要求。
- `versionName` 在 `app/build.gradle.kts`，發新版前記得改。

本機建置：

```bash
export CW_KEYSTORE_PATH=/path/to/upload.jks CW_KEYSTORE_PASSWORD=… CW_KEY_ALIAS=upload CW_KEY_PASSWORD=…
./gradlew bundleRelease
```

## 4. Play Console

1. **建立應用程式**：名稱、預設語言（中文（台灣））、免費。
2. **商店資訊**：照 [play-listing.md](play-listing.md) 填；圖示與主題圖片在 [play-assets/](play-assets/)。
   - 手機螢幕截圖至少 2 張：建議在實機上拍「行程清單＋月曆」「時間軸」「設定頁的主題預覽」「毛玻璃主題」。
3. **應用程式內容**
   - 隱私權政策：需要一個公開網址，內容見 [privacy-policy.md](privacy-policy.md)。
     （這個 repo 是私人的，GitHub Pages 需要付費方案；可以改放 Google 協作平台或公開的 Gist。）
   - 資料安全性：照 [play-listing.md](play-listing.md#資料安全性表單) 回答。
   - 廣告：沒有廣告。
   - 應用程式存取權：不需要登入。
   - 內容分級問卷：工具類、沒有使用者互動內容。
   - 目標對象：13 歲以上即可。
4. **上傳 mapping.txt**：在該版本的「App Bundle 探索工具」上傳，當機報告才看得到原始的類別與行號。

## 5. 測試與上架

- **新建立的個人開發者帳號**：Google 規定要先完成「封閉測試」——至少 12 位測試人員持續加入 14 天，才能申請正式版。可以先邀請同事、家人用 Google 帳號加入。
  （規定可能變動，請以 Play Console 上顯示的為準。）
- 封閉測試期間依 [device-test-checklist.md](device-test-checklist.md) 在不同品牌手機上測試。
- 目標 API：Google Play 要求新 App 與更新以近一年內的 Android 版本為目標；目前設定 `targetSdk = 36`（Android 16）。每年 8 月底前確認是否要再升級。
