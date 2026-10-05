# 原生通知气泡 Hook 说明

本文档记录 ONextBox 当前测试版中“原生通知气泡”开关的实现方式、Hook 点和测试方法。

## 功能目标

在部分 Oplus/ColorOS/OxygenOS 机型上，Android 原生通知气泡能力虽然框架仍然存在，但会被 OEM 设置页、通知偏好、通知渠道或通知提取流程禁用。

本功能通过 LSPosed Hook 尝试强制放行原生会话气泡。

当前版本只处理已经带有 `BubbleMetadata` 的通知，不会凭空给普通通知生成气泡元数据。因此测试时应使用 Telegram/NagramX 等本身支持 Android 气泡的会话通知。

## Hook 总览

| 位置 | Hook 目标 | Hook 时机 | 条件 | 修改结果 | 作用 |
|---|---|---:|---|---|---|
| system_server | `PreferencesHelper#bubblesEnabled(UserHandle)` | after | 开关开启 | `result = true` | 强制系统全局气泡开关为开启 |
| system_server | `PreferencesHelper#getBubblePreference(String, int)` | after | 开关开启且结果为 `0` | `result = 1` | 防止应用级气泡偏好为“禁止” |
| system_server | `BubbleExtractor#canPresentAsBubble(NotificationRecord)` | after | 开关开启、通知带 `BubbleMetadata`、不是 FGS/UIJ | `result = true` | 绕过气泡展示资格判断 |
| system_server | `BubbleExtractor#process(NotificationRecord)` | after | 开关开启、通知带 `BubbleMetadata`、不是 FGS/UIJ | `setAllowBubble(true)`，`flags |= FLAG_BUBBLE` | 在通知提取流程结束后强制标记可气泡 |
| system_server | `NotificationRecord#setAllowBubble(boolean)` | before | 开关开启、通知带 `BubbleMetadata`、不是 FGS/UIJ | 参数强制改为 `true` | 阻止系统/OEM 把 `mAllowBubble` 写成 `false` |
| system_server | `NotificationRecord#canBubble()` | after | 开关开启、通知带 `BubbleMetadata`、不是 FGS/UIJ | `result = true` | 查询通知是否可气泡时强制返回允许 |
| system_server | `NotificationRecord#isConversation()` | after | 开关开启、通知带 `BubbleMetadata`、不是 FGS/UIJ | `result = true` | 绕过部分“必须是会话通知”的限制 |
| framework | `NotificationChannel#canBubble()` | after | 开关开启 | `result = true` | 绕过通知渠道气泡限制 |
| framework | `NotificationChannel#getAllowBubbles()` | after | 开关开启且结果为 `0` | `result = 1` | 防止渠道返回“禁止气泡” |
| Settings | `SettingsActivityPlugin$ConfigureNotificationSettings#onCreate(...)` | before | 开关开启 | `param.result = null` | 阻止 Oplus Settings 跳转到自家通知中心，尝试保留 AOSP 原生通知设置页 |
| App 配置 | `LspConfig` | 读写配置 | 用户打开开关 | 写入 `Settings.Global`、system property、flag 文件 | 让 system_server、Settings、SystemUI 都能读到开关状态 |

## 关键实现文件

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/mi/onextbox/lsp/FrameworkHooker.kt` | system_server/framework 侧通知气泡 Hook |
| `app/src/main/java/com/mi/onextbox/lsp/SettingsHooker.kt` | Settings 侧解除 OEM 跳转，并在系统设置注入 ONextBox 开关页 |
| `app/src/main/java/com/mi/onextbox/lsp/LspConfig.kt` | 原生通知气泡开关的持久化、同步和 Xposed 端读取 |
| `app/src/main/java/com/mi/onextbox/MainActivity.kt` | App 内开关状态绑定 |
| `app/src/main/java/com/mi/onextbox/ui/coui/CouiUi.kt` | COUI 页面与功能开关 |

## 开关同步位置

开关名：

```text
oost_native_notification_bubbles
```

同步写入：

```text
Settings.Global: oost_native_notification_bubbles
system property: persist.sys.oost.native_notification_bubbles
system property: oost.native_notification_bubbles
flag file: /data/local/oost_native_notification_bubbles.flag
legacy flag: /data/local/tmp/oost_native_notification_bubbles.flag
```

Xposed 端读取顺序大致为：

1. `persist.sys.oost.native_notification_bubbles`
2. `oost.native_notification_bubbles`
3. `Settings.Global oost_native_notification_bubbles`
4. `/data/local/oost_native_notification_bubbles.flag`
5. `/data/local/tmp/oost_native_notification_bubbles.flag`
6. App 私有 `SharedPreferences`

## 当前限制

1. 不会为普通通知创建 `BubbleMetadata`。
2. 不会强制把常驻服务通知、前台服务通知、用户发起 Job 通知变成气泡。
3. 如果 App 完全没有发会话快捷方式或气泡元数据，仅靠本 Hook 不一定能显示气泡。
4. 如果 SystemUI 气泡 UI 入口被 OEM 深度删除或禁用，framework 侧 `mAllowBubble=true` 仍可能不足以显示浮窗。

## 测试步骤

1. 安装新 APK。
2. 在 LSPosed 中确认作用域包含：
   - Android/System Framework
   - `com.android.systemui`
   - `com.android.settings`
3. 在 ONextBox 功能页开启“原生通知气泡”。
4. 重启手机。
5. 用 Telegram/NagramX 等真实会话通知测试。
6. 查看通知记录：

```bat
adb shell dumpsys notification --noredact | findstr /i "fork.risin42.nagramx mAllowBubble isBubble BubbleMetadata mConversationId mShortcutInfo effectiveNotificationChannel"
```

期望至少看到：

```text
mAllowBubble=true
BubbleMetadata=...
mShortcutInfo=...
```

如果仍然是：

```text
mAllowBubble=false
BubbleMetadata=null
```

说明当前通知本身不是有效气泡通知，或 Hook 没有进入 system_server。

## 建议排查命令

确认全局气泡开关：

```bat
adb shell settings get secure notification_bubbles
adb shell settings get global oost_native_notification_bubbles
```

确认模块同步值：

```bat
adb shell getprop persist.sys.oost.native_notification_bubbles
adb shell getprop oost.native_notification_bubbles
adb shell su -c "cat /data/local/oost_native_notification_bubbles.flag"
```

确认通知渠道：

```bat
adb shell dumpsys notification --noredact | findstr /i "fork.risin42.nagramx mAllowBubble isBubble mAllowBubbles canBubble effectiveNotificationChannel"
```

查看 LSPosed 日志关键词：

```text
ONextBox-LSP
Framework native notification bubbles hooks installed
```
