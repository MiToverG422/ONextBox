<div align="center">

<img src="docs/assets/onextbox-logo.svg" width="128" height="128" alt="ONextBox Logo" />

# ONextBox

**[简体中文](README.md) · 繁體中文 · [English](README.en.md)**

### 🧩 讓 ColorOS，多一點可能。

ONextBox 是一款基於 Root 與 Modern Xposed 的 ColorOS 系統最佳化與擴充工具箱，讓系統自訂更自由，讓日常體驗更順手。

![Version](https://img.shields.io/badge/Version-17.0-877598?style=flat-square)
![ColorOS](https://img.shields.io/badge/ColorOS-17-AB9BBA?style=flat-square)
![Modern Xposed](https://img.shields.io/badge/Modern_Xposed-API_102-6B8EAD?style=flat-square)
![Architecture](https://img.shields.io/badge/ABI-arm64--v8a-738B80?style=flat-square)
[![License](https://img.shields.io/badge/License-GPL--3.0-877598?style=flat-square)](LICENSE)
[![Downloads](https://img.shields.io/github/downloads/MiToverG422/ONextBox/total?style=flat-square&label=Downloads&color=738B80)](https://github.com/MiToverG422/ONextBox/releases)

**[📥 下載](https://github.com/MiToverG422/ONextBox/releases) · [🐛 問題回報](https://github.com/MiToverG422/ONextBox/issues) · [💬 Telegram](https://t.me/ONextBox)**

**[🚀 正式發布](https://t.me/ONextBox/2) · [🧪 測試建置 / CI](https://t.me/ONextBox/4)**

[✨ 功能](#features) · [📱 相容性](#compatibility) · [🔨 建置](#building) · [📚 引用與致謝](#credits)

</div>

---

> [!WARNING]
> 專案仍在持續開發與適配中。系統層級修改可能影響穩定性，不同機型、系統版本和地區版本的表現也可能不同。請先備份重要資料，再依需求開啟功能，不建議一次開啟所有選項。

## 🌿 關於專案

ONextBox 是由 **MiToverG422 主導開發與維護** 的個人專案。專案方向、功能取捨、介面設計、適配與實機驗證均由作者主導；ChatGPT / Codex 只是輔助工具，用於部分程式碼撰寫、問題分析與迭代，不取代作者的判斷與投入。最終的開發決策與專案維護由作者負責。

**如果你不接受 AI 輔助開發的專案，可以選擇不使用，彼此尊重即可。** 專案會如實說明工具的使用，不迴避，也不把它當作品質保證。

也希望大家更多關注實際體驗、程式碼品質和問題是否得到解決。使用 AI 輔助，不代表作品天生粗糙，更不代表作者沒有付出；同樣，使用 AI 也不代表作品自動可靠。歡迎指出具體問題、提出改進建議，而不是僅憑一個「AI 輔助」標籤否定整個專案。

歡迎附有重現步驟的回報，也歡迎程式碼、翻譯與設計貢獻。讓作品靠體驗和改進說話。💜

**官方版本免費提供，不存在付費授權、二次收費或付費啟用。** 請認明專案連結，提防冒充官方的收費版本。

<a id="features"></a>

## ✨ 不只一個開關

### 🎨 兩種介面，一份工具箱

| 介面風格 | 體驗方向 |
| --- | --- |
| **COUI 17.0** | 以 ColorOS 風格為方向，提供圓潤卡片、拼接列表、頂欄模糊與細緻的互動動畫 |
| **Material 3 Expressive** | 使用 Material 3 Expressive 元件與主題，呈現另一套版面配置和互動體驗 |

> 💡 啟用流程使用獨立的視覺樣式，選擇 App 的主題模式不會改變啟用頁面的顏色。「啟用」是首次設定流程，不是付費授權。

<a id="compatibility"></a>

## 📱 相容性與執行需求

| 項目 | 說明 |
| --- | --- |
| 🎯 主要適配目標 | **ColorOS 17** |
| 🌏 主要適配地區 | **CN China 中國大陸版 🇨🇳** |
| 🕰️ 舊版系統 | 部分選項保留 ColorOS 16 等版本的實作，不承諾舊版系統完整相容 |
| 🌍 機型與地區差異 | 歡迎不同機型與地區版本的使用者體驗並回報，具體支援情況以裝置系統版本、App 內提示與實際測試為準 |
| 🔑 權限 | 需要可用的 Root 環境；Hook 功能還需要支援 **Modern Xposed API 102** 的框架 |
| 🧱 CPU 架構 | 目前建置以 **arm64-v8a** 為目標 |
| 📦 安裝下限 | `minSdk 35`（Android 15），僅表示 APK 的安裝下限，不等於功能支援範圍 |

目前主要實機測試裝置為 **OPPO Find X9 Ultra**。單一裝置上的測試結果不能作為其他裝置或 OTA 版本的相容保證。

> [!NOTE]
> ONextBox 是第三方獨立專案，不是 OPPO、OnePlus、realme 或 Google 的官方產品，也不代表上述品牌為專案背書。

## 🛡️ 使用界線與隱私

ONextBox 會為相容性檢查和功能執行讀取必要的裝置、系統、Root、框架狀態及已移除敏感資訊的診斷資料。設定保存在本機，執行記錄存放於應用程式私有目錄，不會自動上傳。

- 🌐 檢查更新、載入線上 GitHub 頭像等操作會存取第三方服務，第三方可能取得 IP 位址等一般網路中繼資料。
- 🚫 未整合廣告或統計 SDK，也沒有開發者自行架設的帳號或遙測伺服器。
- 🧪 地區、權限、語音助理與 eSIM 相關選項高度依賴使用環境，不保證能夠改變伺服器端資格、地區政策或電信業者限制。
- 🤝 僅管理自己擁有或已獲明確授權的裝置、SIM / eSIM 與帳戶；請勿用於詐欺、身分冒用或其他違法行為。

完整說明可在 App 的「關於 → 協議與引用內容」查看，也可閱讀儲存庫內的 [隱私說明](app/src/main/res/raw-zh-rCN/onboarding_agreement_privacy.txt) 與 [協議資源](app/src/main/res/raw-zh-rCN)（簡體中文）。

## 🐛 回報與參與

發現問題時，請到 [Issues](https://github.com/MiToverG422/ONextBox/issues) 提供以下資訊：

- 📱 裝置型號、完整系統版本與地區版本。
- 📦 ONextBox 版本 / 建置編號、使用的介面風格，以及框架版本。
- 🧭 重現步驟、開啟了哪些選項、預期結果和實際結果。
- 🎬 必要的螢幕截圖、螢幕錄影或已移除敏感資訊的記錄。

**提交前請移除電話號碼、帳戶、QR 碼、啟用碼、EID / ICCID、權杖以及通知內文等敏感資訊。**

歡迎提交 Pull Request。系統功能變更請說明適用版本、目標程序與驗證方式；UI 修改請盡量兼顧兩套介面、淺深色模式和不同語言。🌱

<a id="building"></a>

## 🔨 從原始碼建置

### 🧑‍💻 開發環境

| 環境 | 目前設定 |
| --- | --- |
| JDK | **21** |
| Android SDK | **37**（compileSdk），targetSdk 為 36 |
| Gradle Wrapper | **9.3.1** |
| Android Gradle Plugin | **9.1.0** |
| Kotlin | **2.4.10** |
| 介面 | Jetpack Compose、COUI、Material 3 Expressive |
| 模組 API | libxposed **102.0.0** |

以 [版本目錄](gradle/libs.versions.toml)、[App 建置設定](app/build.gradle.kts) 和 [Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties) 為準。

在 Android Studio 中開啟專案，安裝所需 SDK 並完成 Gradle 同步。首次建置需要連線下載相依套件。

**Windows / PowerShell：**

```powershell
# 日常偵錯
.\gradlew.bat :app:assembleDebug

# 更接近日常使用效能的本機最佳化建置
.\gradlew.bat :app:assembleDebugSlim

# Release 建置
.\gradlew.bat :app:assembleRelease
```

**Linux / macOS：**

```bash
chmod +x gradlew
./gradlew :app:assembleDebugSlim
```

| 建置類型 | 用途 | 預設 APK 輸出 |
| --- | --- | --- |
| `debug` | 本機偵錯與問題排查 | `app/build/outputs/apk/debug/app-debug.apk` |
| `debugSlim` | 啟用程式碼壓縮、資源縮減，關閉可偵錯旗標；用於本機實機體驗測試 | `app/build/outputs/apk/debugSlim/app-debugSlim.apk` |
| `release` | 最佳化建置與發布驗證 | `app/build/outputs/apk/release/app-release.apk` |

正式建置需在 Git 忽略的 `signing.properties` 設定 `ONEXTBOX_RELEASE_STORE_FILE`、`ONEXTBOX_RELEASE_STORE_PASSWORD`、`ONEXTBOX_RELEASE_KEY_ALIAS` 和 `ONEXTBOX_RELEASE_KEY_PASSWORD`，亦可透過同名 Gradle 屬性傳入，缺少簽名設定時建置會失敗，不會自動使用 Debug 憑證

提交前執行 `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`，Linux / macOS 使用 `./gradlew`，手動 Actions 建置同樣會執行這些檢查

<a id="credits"></a>

## 📚 引用、來源與致謝

感謝以下 UI 框架、功能參考專案與基礎函式庫的作者及貢獻者。💜

### 💜 啟蒙與靈感

特別感謝 [LuckyTool](https://github.com/luckyzyx/LuckyTool) 與 [OShin](https://github.com/suqi8/OShin)，以及兩者的作者和貢獻者。它們是我探索 ColorOS 系統自訂與擴充的啟蒙，也為 ONextBox 的功能構思與體驗設計帶來了許多靈感。從最初的興趣到動手實踐，這些專案讓我看見了系統體驗的更多可能，也鼓勵我逐步做出自己的工具箱。感謝你們的分享與投入。🌱

### 🎨 UI 框架與互動框架

| 專案 | 作者 · 授權條款 |
| --- | --- |
| **ONextBox COUI 17.0** | [MiToverG422](https://github.com/MiToverG422) · GPL-3.0 |
| [COUI](https://github.com/suqi8/coui) | suqi8 及貢獻者 · Apache-2.0 |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | YuKongA 及貢獻者 · Apache-2.0 |
| [Jetpack Compose / Material 3](https://developer.android.com/jetpack/compose) | Google、AndroidX 貢獻者 · Apache-2.0 |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Kyant0 及貢獻者 · Apache-2.0 |
| [Capsule](https://github.com/Kyant0/Capsule) | Kyant0 及貢獻者 · Apache-2.0 |

### 🧩 部分功能參考

| 專案 | 作者 · 授權條款 |
| --- | --- |
| [OShin](https://github.com/suqi8/OShin) | suqi8 及貢獻者 · AGPL-3.0 |
| [LuckyTool](https://github.com/luckyzyx/LuckyTool) | luckyzyx 及貢獻者 · GPL-3.0 |
| [InxLocker](https://github.com/Chimioo/InxLocker) | Chimioo 及貢獻者 · GPL-3.0 |

### 📦 基礎函式庫與執行元件

| 專案 | 作者 · 授權條款 |
| --- | --- |
| [AndroidX](https://developer.android.com/jetpack/androidx) | Google 及貢獻者 · Apache-2.0 |
| [Kotlin](https://github.com/JetBrains/kotlin) | JetBrains 及貢獻者 · Apache-2.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | JetBrains 及貢獻者 · Apache-2.0 |
| [libxposed API](https://github.com/libxposed/api) | libxposed 貢獻者 · Apache-2.0 |
| [libxposed service](https://github.com/libxposed/service) | libxposed 貢獻者 · Apache-2.0 |
| [libsu](https://github.com/topjohnwu/libsu) | topjohnwu 及貢獻者 · Apache-2.0 |
| [DexKit](https://github.com/LuckyPray/DexKit) | LuckyPray · LGPL-3.0 |
| [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | LSPosed 貢獻者 · Apache-2.0 |
| [MaterialKolor](https://github.com/jordond/MaterialKolor) | jordond 及貢獻者 · MIT；Material Color Utilities 部分為 Apache-2.0 |
| [Material Icons](https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary) | Google 及貢獻者 · Apache-2.0 |

相依套件版本見 [版本目錄](gradle/libs.versions.toml) 和 [建置設定](app/build.gradle.kts)。各元件附帶的著作權聲明、NOTICE 和授權條款仍應保留，本表不改變其授權。

## 📜 開源授權

ONextBox 專案程式碼依據 **GNU GPL v3** 發布，完整條款見 [LICENSE](LICENSE)。第三方程式碼與資源保留各自的授權和著作權聲明；本 README 的用途是說明來源，並非重新授權這些內容。

如發現專案中的程式碼、素材或其他內容涉嫌侵犯您的合法權益，請透過 [GitHub Issues](https://github.com/MiToverG422/ONextBox/issues) 或 [Telegram](https://t.me/ONextBox) 聯絡作者，並提供相關內容的位置及權利歸屬說明。核實後，我們將及時刪除、替換相關內容或作出必要修正。感謝您的理解與提醒。🤝

再次散布時，請遵循適用授權條款，保留所需的署名、著作權聲明、授權文字及修改說明，並依授權條款要求提供對應原始碼。**致謝和儲存庫連結不能取代授權義務。**

官方免費發布是專案的散布政策，不是對 GPL 額外加入「禁止商業使用」限制。專案依授權條款提供，不附帶相容性、穩定性或適銷性擔保。

---

<div align="center">

**🧩 為系統留一點自由，為體驗添一點細節。**

如果 ONextBox 對你有幫助，歡迎留下一顆 ⭐，或用一份認真、清楚的回報幫助它變得更好。

### ⭐ Star 成長趨勢

<a href="https://www.star-history.com/?repos=MiToverG422%2FONextBox&amp;type=date&amp;legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=MiToverG422/ONextBox&amp;type=date&amp;theme=dark&amp;legend=top-left" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=MiToverG422/ONextBox&amp;type=date&amp;legend=top-left" />
    <img alt="ONextBox Star 成長趨勢" src="https://api.star-history.com/chart?repos=MiToverG422/ONextBox&amp;type=date&amp;legend=top-left" width="720" />
  </picture>
</a>

</div>
