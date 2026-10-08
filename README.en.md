<div align="center">

<img src="docs/assets/onextbox-logo.svg" width="128" height="128" alt="ONextBox Logo" />

# ONextBox

**[简体中文](README.md) · [繁體中文](README.zh-TW.md) · English**

### 🧩 More possibilities for ColorOS.

ONextBox is a ColorOS optimization and extension toolbox powered by Root and Modern Xposed, giving you more freedom to customize your system and a smoother everyday experience.

![Version](https://img.shields.io/badge/Version-17.0-877598?style=flat-square)
![ColorOS](https://img.shields.io/badge/ColorOS-17-AB9BBA?style=flat-square)
![Modern Xposed](https://img.shields.io/badge/Modern_Xposed-API_102-6B8EAD?style=flat-square)
![Architecture](https://img.shields.io/badge/ABI-arm64--v8a-738B80?style=flat-square)
[![License](https://img.shields.io/badge/License-GPL--3.0-877598?style=flat-square)](LICENSE)

**[📥 Download](https://github.com/MiToverG422/ONextBox/releases) · [🐛 Report an issue](https://github.com/MiToverG422/ONextBox/issues) · [💬 Telegram](https://t.me/ONextBox)**

**[🚀 Releases](https://t.me/ONextBox/2) · [🧪 CI builds](https://t.me/ONextBox/4)**

[✨ Features](#features) · [📱 Compatibility](#compatibility) · [🔨 Building](#building) · [📚 References & credits](#credits)

</div>

---

> [!WARNING]
> Development and compatibility work are ongoing. System-level modifications may affect stability, and behavior may vary across devices, system versions, and regional builds. Back up important data first and enable only what you need; avoid turning on every option at once.

## 🌿 About the project

ONextBox is a personal project **led and maintained by MiToverG422**. The author leads the project's direction, feature decisions, interface design, compatibility work, and on-device validation. ChatGPT / Codex are supporting tools for some coding, problem analysis, and iteration; they do not replace the author's judgment or effort. Final development decisions and project maintenance remain the author's responsibility.

**If you prefer not to use projects developed with AI assistance, you are free not to use this one. Mutual respect is all we ask.** The project is transparent about its use of these tools and does not present them as a guarantee of quality.

We also hope the project is judged by its actual user experience, code quality, and how problems are addressed. AI assistance does not automatically make a project poorly made or erase the author's effort; nor does it automatically make a project reliable. Specific bug reports and suggestions are welcome, rather than dismissing the entire project solely because it uses AI assistance.

Feedback with reproduction steps, as well as code, translation, and design contributions, is welcome. Let the experience and ongoing improvements speak for the project. 💜

**Official releases are free, with no paid licensing, additional charges, or paid activation.** Use the official project links and beware of paid versions impersonating the project.

<a id="features"></a>

## ✨ More than a toggle

### 🎨 Two interfaces, one toolbox

| Interface style | Experience |
| --- | --- |
| **COUI 17.0** | A ColorOS-inspired interface with rounded cards, connected lists, top-bar blur, and refined interaction animations |
| **Material 3 Expressive** | Material 3 Expressive components and theming, offering a different layout and interaction experience |

> 💡 The activation flow has its own visual style. Changing the app's theme mode does not change the colors of the activation screens. “Activation” means first-time setup, not paid licensing.

<a id="compatibility"></a>

## 📱 Compatibility & requirements

| Item | Details |
| --- | --- |
| 🎯 Primary target | **ColorOS 17** |
| 🌏 Primary region | **CN China — Mainland China builds 🇨🇳** |
| 🕰️ Older systems | Some options retain implementations for ColorOS 16 and other versions; full compatibility with older systems is not guaranteed |
| 🌍 Device & regional differences | Users of different devices and regional builds are welcome to try the app and share feedback. Support depends on the device's system version, in-app guidance, and actual testing |
| 🔑 Permissions | A working Root environment is required; hooking features also require a framework supporting **Modern Xposed API 102** |
| 🧱 CPU architecture | Current builds target **arm64-v8a** |
| 📦 Minimum installation version | `minSdk 28` (Android 9) is only the minimum Android version for installing the APK, not a guarantee of feature support |

The primary device currently used for testing is the **OPPO Find X9 Ultra**. Results on a single device do not guarantee compatibility with other devices or OTA versions.

> [!NOTE]
> ONextBox is an independent third-party project. It is not an official product of OPPO, OnePlus, realme, or Google, and is not endorsed by these brands.

## 🛡️ Usage boundaries & privacy

ONextBox reads the device, system, Root, and framework status, along with diagnostic information with sensitive data removed, as needed for compatibility checks and feature operation. Settings are stored locally, and runtime logs are kept in the app's private directory without automatic uploads.

- 🌐 Checking for updates, loading GitHub avatars online, and similar actions access third-party services. These services may receive ordinary network metadata such as your IP address.
- 🚫 No advertising or analytics SDKs are integrated, and the developer operates no custom account or telemetry servers.
- 🧪 Region, permission, voice assistant, and eSIM options depend heavily on the environment. They are not guaranteed to change server-side eligibility, regional policies, or carrier restrictions.
- 🤝 Only manage devices, SIMs / eSIMs, and accounts you own or are explicitly authorized to manage. Do not use the project for fraud, impersonation, or other unlawful activities.

Full details are available in the app under “About → Agreements & references,” or in the repository's [privacy notice](app/src/main/res/raw-zh-rCN/onboarding_agreement_privacy.txt) and [agreement resources](app/src/main/res/raw-zh-rCN) (Simplified Chinese).

## 🐛 Feedback & contributions

When reporting a problem through [Issues](https://github.com/MiToverG422/ONextBox/issues), please include:

- 📱 Device model, full system version, and regional build.
- 📦 ONextBox version / build number, interface style, and framework version.
- 🧭 Reproduction steps, enabled options, expected behavior, and actual behavior.
- 🎬 Relevant screenshots, screen recordings, or logs with sensitive information removed.

**Before posting, remove phone numbers, accounts, QR codes, activation codes, EID / ICCID, tokens, notification content, and other sensitive information.**

Pull requests are welcome. For system feature changes, describe the applicable versions, target processes, and validation method. For UI changes, please consider both interface styles, light and dark modes, and different languages. 🌱

<a id="building"></a>

## 🔨 Building from source

### 🧑‍💻 Development environment

| Environment | Current configuration |
| --- | --- |
| JDK | **21** |
| Android SDK | **37** (compileSdk), targetSdk 36 |
| Gradle Wrapper | **9.3.1** |
| Android Gradle Plugin | **9.1.0** |
| Kotlin | **2.4.10** |
| UI | Jetpack Compose, COUI, Material 3 Expressive |
| Module API | libxposed **102.0.0** |

Refer to the [version catalog](gradle/libs.versions.toml), [app build configuration](app/build.gradle.kts), and [Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties) for the authoritative configuration.

Open the project in Android Studio, install the required SDK, and complete Gradle sync. The first build requires internet access to download dependencies.

**Windows / PowerShell:**

```powershell
# Everyday debugging
.\gradlew.bat :app:assembleDebug

# Optimized local build for a more representative performance experience
.\gradlew.bat :app:assembleDebugSlim

# Release build
.\gradlew.bat :app:assembleRelease
```

**Linux / macOS:**

```bash
chmod +x gradlew
./gradlew :app:assembleDebugSlim
```

| Build type | Purpose | Default APK output |
| --- | --- | --- |
| `debug` | Local debugging and troubleshooting | `app/build/outputs/apk/debug/app-debug.apk` |
| `debugSlim` | Enables code shrinking and resource shrinking, with the debuggable flag disabled; intended for local on-device experience testing | `app/build/outputs/apk/debugSlim/app-debugSlim.apk` |
| `release` | Optimized builds and release validation | `app/build/outputs/apk/release/app-release.apk` |

Release builds require `ONEXTBOX_RELEASE_STORE_FILE`, `ONEXTBOX_RELEASE_STORE_PASSWORD`, `ONEXTBOX_RELEASE_KEY_ALIAS` and `ONEXTBOX_RELEASE_KEY_PASSWORD` in the Git-ignored `signing.properties` file or equivalent Gradle properties, builds without signing credentials fail instead of silently using a debug certificate

Before committing, run `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`, use `./gradlew` on Linux / macOS, manual Actions builds run the same checks

<a id="credits"></a>

## 📚 References, sources & credits

Thank you to the authors and contributors of the following UI frameworks, feature reference projects, and libraries. 💜

### 💜 Inspiration & beginnings

Special thanks to [LuckyTool](https://github.com/luckyzyx/LuckyTool) and [OShin](https://github.com/suqi8/OShin), and to their authors and contributors. They sparked my exploration of ColorOS customization and extensions, and inspired many ideas for ONextBox's features and user experience. From early curiosity to hands-on development, these projects showed me more possibilities for the system experience and encouraged me to gradually build a toolbox of my own. Thank you for sharing your work and putting so much into it. 🌱

### 🎨 UI & interaction frameworks

| Project | Author · License |
| --- | --- |
| **ONextBox COUI 17.0** | [MiToverG422](https://github.com/MiToverG422) · GPL-3.0 |
| [COUI](https://github.com/suqi8/coui) | suqi8 and contributors · Apache-2.0 |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | YuKongA and contributors · Apache-2.0 |
| [Jetpack Compose / Material 3](https://developer.android.com/jetpack/compose) | Google and AndroidX contributors · Apache-2.0 |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Kyant0 and contributors · Apache-2.0 |
| [Capsule](https://github.com/Kyant0/Capsule) | Kyant0 and contributors · Apache-2.0 |

### 🧩 Feature reference projects

| Project | Author · License |
| --- | --- |
| [OShin](https://github.com/suqi8/OShin) | suqi8 and contributors · AGPL-3.0 |
| [LuckyTool](https://github.com/luckyzyx/LuckyTool) | luckyzyx and contributors · GPL-3.0 |
| [InxLocker](https://github.com/Chimioo/InxLocker) | Chimioo and contributors · GPL-3.0 |

### 📦 Libraries & runtime components

| Project | Author · License |
| --- | --- |
| [AndroidX](https://developer.android.com/jetpack/androidx) | Google and contributors · Apache-2.0 |
| [Kotlin](https://github.com/JetBrains/kotlin) | JetBrains and contributors · Apache-2.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | JetBrains and contributors · Apache-2.0 |
| [libxposed API](https://github.com/libxposed/api) | libxposed contributors · Apache-2.0 |
| [libxposed service](https://github.com/libxposed/service) | libxposed contributors · Apache-2.0 |
| [libsu](https://github.com/topjohnwu/libsu) | topjohnwu and contributors · Apache-2.0 |
| [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | LSPosed contributors · Apache-2.0 |
| [MaterialKolor](https://github.com/jordond/MaterialKolor) | jordond and contributors · MIT; Material Color Utilities portions are Apache-2.0 |
| [Material Icons](https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary) | Google and contributors · Apache-2.0 |

See the [version catalog](gradle/libs.versions.toml) and [build configuration](app/build.gradle.kts) for dependency versions. Copyright notices, NOTICE files, and licenses supplied with each component must still be retained; these tables do not alter their licenses.

## 📜 Open-source license

ONextBox's project code is released under **GNU GPL v3**. See [LICENSE](LICENSE) for the full terms. Third-party code and resources retain their own licenses and copyright notices; this README explains their sources and does not relicense them.

If you believe any code, assets, or other content in the project infringes your rights, please contact the author through [GitHub Issues](https://github.com/MiToverG422/ONextBox/issues) or [Telegram](https://t.me/ONextBox), identifying the content and providing information about your rights to it. Once verified, we will promptly remove or replace the content, or make the necessary corrections. Thank you for your understanding and for bringing it to our attention. 🤝

When redistributing, follow the applicable licenses, retain required attribution, copyright notices, license texts, and modification notices, and provide corresponding source code as required by those licenses. **Credits and repository links are not a substitute for license obligations.**

Providing official releases for free is the project's distribution policy, not an additional “no commercial use” restriction on the GPL. The project is provided under its license without warranties of compatibility, stability, or merchantability.

---

<div align="center">

**🧩 A little more freedom for your system. A little more care for your experience.**

If ONextBox helps you, consider leaving a ⭐ or sharing thoughtful, clear feedback to help it improve.

### ⭐ Star History

<a href="https://www.star-history.com/?repos=MiToverG422%2FONextBox&amp;type=date&amp;legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=MiToverG422/ONextBox&amp;type=date&amp;theme=dark&amp;legend=top-left" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=MiToverG422/ONextBox&amp;type=date&amp;legend=top-left" />
    <img alt="ONextBox Star History" src="https://api.star-history.com/chart?repos=MiToverG422/ONextBox&amp;type=date&amp;legend=top-left" width="720" />
  </picture>
</a>

</div>
