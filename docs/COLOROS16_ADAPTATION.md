# ColorOS 16 适配清单

适配目标为 ColorOS 16 / Android 16（API 36），接口核对基于 PMA110 的 ColorOS 16.0.10.501 (CN01)。系统未提供或无法核准目标的功能保持关闭，国际桌面搜索栏绘制暂未适配。

运行时入口、配置值、页面、搜索和已启用清单使用同一批已核准功能。C17 保留原分发；C16 单独处理改名、参数、字段与插件差异。数值、文本和列表配置也受版本边界约束，保存的 C17 选择不会被改写。

已接入系统框架、SystemUI/QS、Settings、桌面、截图/录屏、电话、Athena、电池、权限与应用锁、手机管家、互传、主题试用、支付、游戏部分工具、OTA/系统文件更新、流量、浏览器流体云及推送监控等备份中可核准的目标。参数编辑器保留对应滑条、选择器和应用列表。

验证范围：已逐域核对 DEX 中的方法定义、字段、继承链及资源，并完成构建和自动化检查。ColorOS 16 的实机效果仍待验证，ColorOS 17 上的运行结果不能替代此项验收。

| 状态 | 功能 | 页面 | 作用域 |
| --- | --- | --- | --- |
| 已接入 | 解除国行 GMS 限制（实验性） | AndroidSystem | system |
| 已接入 | 全局165Hz极速高刷(ColorOS 16) | AndroidSystem | system |
| 已接入 | 屏蔽系统 Root 状态检测 | AndroidSystem | system |
| 已接入 | 解除截图录屏限制 | AndroidSystem | system |
| 已接入 | 解锁全景息屏全天显示 | Aod | com.android.systemui, com.oplus.aod |
| 已接入 | 自定义息屏亮度 | Aod | com.android.systemui |
| 已接入 | 屏蔽 AOD 单击唤醒 | Aod | com.android.systemui |
| 已接入 | 记录应用异常 | AppErrors | system |
| 已接入 | 应用商店去臃肿化 | AppMarket | com.heytap.market |
| 已接入 | 解除国家/地区切换限制 | AppMarket | com.heytap.market |
| 已接入 | 国际版智能助手（实验性）、国际版关机组合键（实验性） | Assistant | com.android.settings, com.android.systemui, system |
| 已接入 | C17 划卡后台保护 | Athena | system, com.oplus.athena |
| 已接入 | 使用系统内置电池优化白名单 | Battery | system |
| 已接入 | 阻止电池限制插件加载 | Battery | com.oplus.battery |
| 已接入 | 显示电池循环次数 | Battery | com.oplus.battery |
| 已接入 | 显示电池健康状态、充满容量、额定容量、典型容量 | Battery | com.oplus.battery |
| 已接入 | 显示实际容量（计算） | Battery | com.oplus.battery |
| 已接入 | 允许系统应用使用隐藏 API | CorePatch | system |
| 已接入 | 禁用 APK 签名验证 | CorePatch | system |
| 已接入 | 禁用唯一签名匹配 | CorePatch | system |
| 已接入 | 绕过最低 SDK 版本限制 | CorePatch | system |
| 已接入 | 绕过 resources.arsc 对齐限制 | CorePatch | system |
| 已接入 | 绕过共享用户签名验证 | CorePatch | system |
| 已接入 | 禁用软件包管理器签名验证 | CorePatch | system |
| 已接入 | 禁用安装包验证代理 | CorePatch | system |
| 已接入 | 允许降级安装应用 | CorePatch | system |
| 已接入 | 安装时始终使用已装 APP 的签名 | CorePatch | system |
| 已接入 | 彩色通知圆点 | DesktopIcons | com.android.launcher |
| 已接入 | 隐藏应用分身角标 | DesktopIcons | com.android.launcher |
| 已接入 | 隐藏应用快捷方式角标 | DesktopIcons | com.android.launcher |
| 已接入 | 隐藏 App 安装/更新圆点 | DesktopIcons | com.android.launcher |
| 已接入 | 隐藏应用工作空间角标 | DesktopIcons | com.android.launcher |
| 已接入 | 通知圆点阴影 | DesktopIcons | com.android.launcher |
| 已接入 | 移除文件夹输入限制 | DesktopIcons | com.android.launcher |
| 已接入 | 图标与文字间距、间距偏移 | DesktopIcons | com.android.launcher |
| 已接入 | 禁用二级菜单 | DesktopIcons | com.android.launcher |
| 已接入 | 启用桌面 Dock 栏 | DesktopLayout | com.android.launcher |
| 已接入 | 桌面 Dock 栏模糊 | DesktopLayout | com.android.launcher |
| 已接入 | 自定义桌面布局、列数、行数 | DesktopLayout | com.android.launcher |
| 已接入 | 解锁桌面布局 | DesktopLayout | com.android.launcher |
| 已接入 | 任务栏搜索框 | DesktopLayout | com.android.launcher |
| 已接入 | 小组件无字 | DesktopLayout | com.android.launcher |
| 已接入 | 负一屏自定义工具 | DesktopPages | com.android.launcher, com.coloros.assistantscreen |
| 已接入 | 禁用自动切换到上个任务 | DesktopRecent | com.android.launcher |
| 已接入 | 隐藏最近任务清除按钮 | DesktopRecent | com.android.launcher |
| 已接入 | 旧版最近任务清除按钮 | DesktopRecent | com.android.launcher |
| 已接入 | 长按应用图标打开应用详情 | DesktopRecent | com.android.launcher |
| 已接入 | 解锁最近任务内存显示 | DesktopRecent | com.android.launcher |
| 已接入 | 最近任务卡片圆角、圆角大小 | DesktopRecent | com.android.launcher |
| 已接入 | 简化上滑顶部区域、浮窗触发区域宽度 | DesktopRecent | com.android.launcher |
| 已接入 | 从最近任务列表中隐藏应用 | DesktopRecent, DesktopHiddenApps | com.android.launcher |
| 已接入 | 使用原生文件选择器 | FileManager | system |
| 已接入 | 屏蔽真我性能引擎动画 | GameAssistant | com.oplus.games |
| 已接入 | 屏蔽 Root 检测 | GameAssistant | com.oplus.games |
| 已接入 | 屏蔽工具推荐与福利推广 | GameAssistant | com.oplus.games |
| 已接入 | 移除侧栏数量限制 | GameAssistant | com.oplus.games |
| 已接入 | 启用安装重定向 | Installer | system |
| 已接入 | 接管系统安装器请求 | Installer | system |
| 已接入 | 移除默认应用限制 | Installer | system |
| 已接入 | 拦截会话安装 | Installer | system |
| 已接入 | 拦截卸载请求 | Installer | system |
| 已接入 | 强制显示全部运营商网络选项 | MobileNetwork | com.android.phone |
| 已接入 | 隐藏山海通信网络引擎入口 | MobileNetwork | com.android.phone |
| 已接入 | 隐藏大流量卡入口 | MobileNetwork | com.android.phone |
| 已接入 | 一键隐藏手机号码 | MobileNetwork | com.android.phone |
| 已接入 | 隐藏逍遥游入口 | MobileNetwork | com.android.phone |
| 已接入 | 隐藏智慧云加速入口 | MobileNetwork | com.android.phone |
| 已接入 | 伪装国家与运营商（实验性）、国家／地区、运营商、运营商列表 | MobileNetwork | com.android.phone |
| 已接入 | 移除应用上层通知 | NotificationRemoval | system |
| 已接入 | 移除 VPN 活动通知 | NotificationRemoval | system |
| 已接入 | 移除充电完成通知 | NotificationRemoval | com.android.systemui |
| 已接入 | 移除开发者选项通知 | NotificationRemoval | com.android.systemui |
| 已接入 | 移除免打扰模式通知 | NotificationRemoval | com.android.systemui |
| 已接入 | 移除手电筒已开启通知 | NotificationRemoval | com.android.systemui |
| 已接入 | 移除 GT 模式通知 | NotificationRemoval | com.android.systemui |
| 已接入 | 移除通知勿扰提醒 | NotificationRemoval | com.android.systemui |
| 已接入 | 移除应用耗电异常警告 | NotificationRemoval | system, com.oplus.battery |
| 已接入 | 移除高性能模式通知 | NotificationRemoval | system, com.oplus.battery |
| 已接入 | 解除所有人 10 分钟限制 | OShare | com.coloros.oshare |
| 已接入 | 移除所有弹窗倒计时 | PhoneManager | com.coloros.phonemanager |
| 已接入 | 关闭风险应用流体云提醒 | PhoneManager | com.coloros.phonemanager |
| 已接入 | 快递不跳小程序 | QuickAppServices | com.android.launcher, com.nearme.instant.platform, com.coloros.assistantscreen |
| 已接入 | 锁屏录屏 | ScreenRecording | com.android.systemui, com.oplus.screenrecorder |
| 已接入 | AOD 截图 | Screenshot | com.oplus.screenshot |
| 已接入 | 截图隐藏小窗 | Screenshot | com.oplus.screenshot |
| 已接入 | 关闭支付风险流体云提醒 | SecurePay | com.coloros.securepay |
| 已接入 | 应用锁延时锁定（实验性） | SecurityPermission | com.oplus.safecenter, system |
| 已接入 | 禁用恶意风险应用拦截 | SecurityPermission | system |
| 已接入 | 始终允许启动应用 | SecurityPermission | com.oplus.securitypermission, system |
| 已接入 | 自动解除授权限制 | SecurityPermission | com.oplus.securitypermission |
| 已接入 | 国际版权限管理页面 | SecurityPermission | com.android.permissioncontroller, com.android.settings |
| 已接入 | 原生权限申请弹窗 | SecurityPermission | com.android.permissioncontroller |
| 已接入 | 使用旧版跳转弹窗 | SecurityPermission | com.oplus.securitypermission |
| 已接入 | 禁止 LTPO/LTPS 动态降频 | SettingsAppearance | system |
| 已接入 | 隐藏顶部账号入口 | SettingsAppearance | com.android.settings |
| 已接入 | 设置图示、图示样式 | SettingsAppearance | com.android.settings |
| 已接入 | 隐藏顶部更新提醒 | SettingsAppearance | com.android.settings |
| 已接入 | 设置标题栏收起 | SettingsAppearance | com.android.settings |
| 已接入 | 应用详情信息 | SettingsApps | com.android.settings |
| 已接入 | 恢复打开应用按钮 | SettingsApps | com.android.settings |
| 已接入 | 强制显示应用独立音量页面 | SettingsRealme | com.android.settings |
| 已接入 | 强制显示扩展亮度 | SettingsRealme | com.android.settings |
| 已接入 | 强制显示真我 DC 调光 | SettingsRealme | com.android.settings |
| 已接入 | 强制显示定向声音 | SettingsRealme | com.android.settings |
| 已接入 | 强制显示双模音乐分享 | SettingsRealme | com.android.settings |
| 已接入 | 显示实验室 | SettingsRealme | com.android.settings |
| 已接入 | 强制开启多个人脸录入 | SettingsRealme | com.android.settings |
| 已接入 | 强制显示进程管理 | SettingsRealme | com.android.settings |
| 已接入 | 强制显示应用自启动 | SettingsRegion | com.android.settings |
| 已接入 | 恢复国行辅助功能选项 | SettingsRegion | com.android.settings |
| 已接入 | 强制显示 Google | SettingsRegion | com.android.settings |
| 已接入 | 启用国际版设置 | SettingsRegion | com.android.settings |
| 已接入 | 恢复智能解锁 | SettingsRegion | com.android.settings |
| 已接入 | 国际版钱包入口 | SettingsRegion | com.android.settings |
| 已接入 | 屏蔽系统文件更新 | SoftwareUpdate | com.oplus.ota, com.oplus.sau |
| 已接入 | 屏蔽系统更新 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 禁止后台自动下载 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 禁止夜间自动安装 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 屏蔽软件更新 BL 检测 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 屏蔽自动更新推荐弹窗 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 屏蔽软件更新 Root 检测 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 屏蔽新版本更新弹窗 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 显示更新包详情 | SoftwareUpdate | com.oplus.ota |
| 已接入 | 始终显示本地安装 | SoftwareUpdate | com.oplus.ota |
| 已接入 | OPPO Push 监控器 | SystemMessages | com.heytap.mcs |
| 已接入 | 隐藏编辑开关 | SystemUiControlCenter | com.android.systemui |
| 已接入 | 隐藏控制中心更多入口 | SystemUiControlCenter | com.android.systemui |
| 已接入 | 隐藏控制中心设置按钮 | SystemUiControlCenter | com.android.systemui |
| 已接入 | 隐藏顶部运营商 | SystemUiControlCenter | com.android.systemui |
| 已接入 | 自定义控制中心圆角 | SystemUiControlCenter, SystemUiCornerSettings | com.android.systemui |
| 已接入 | 进度条百分比显示 | SystemUiControlCenter, SystemUiPercentageSettings | com.android.systemui |
| 已接入 | Tonal Spot 动态取色修复 | SystemUiDynamicColor | com.android.systemui |
| 已接入 | 网页跟随默认浏览器 | SystemUiFluidCloud | com.coloros.colordirectservice, com.oplus.metis |
| 已接入 | 播放上岛黑名单 | SystemUiFluidCloud, SystemUiFluidCloudMediaBlacklist | com.android.systemui |
| 已接入 | 独立排序双卡信号 | SystemUiIconOrder | com.android.systemui |
| 已接入 | 解锁后不隐藏指纹图标、解锁指纹动画 | SystemUiLockScreen | com.android.systemui |
| 已接入 | 原生通知气泡 | SystemUiNative | system |
| 已接入 | 原生通知图标 | SystemUiNative | com.android.systemui, system |
| 已接入 | 原生剪贴板 | SystemUiNative | com.android.systemui |
| 已接入 | 原生关机菜单 | SystemUiNative | com.android.systemui |
| 已接入 | 屏蔽旋转建议按钮 | SystemUiNavigationBar | com.android.systemui |
| 已接入 | 自定义小横条、小横条自动隐藏、沉浸式小横条 | SystemUiNavigationBar | com.android.systemui |
| 已接入 | 隐藏通知中心清理按钮 | SystemUiNotificationCenter | com.android.systemui |
| 已接入 | 国际版通知分类样式 | SystemUiNotificationCenter | com.android.systemui |
| 已接入 | 缩小小窗顶部控制区 | SystemUiSmallWindow | system |
| 已接入 | 多任务隐藏小窗应用 | SystemUiSmallWindow | system |
| 已接入 | 悬浮小窗贴边挂机 | SystemUiSmallWindow | system |
| 已接入 | 横屏应用小窗保持屏幕比例 | SystemUiSmallWindow | system |
| 已接入 | 增大小窗最大尺寸 | SystemUiSmallWindow | system |
| 已接入 | 小窗贴边媒体静音 | SystemUiSmallWindow | system |
| 已接入 | 加大上下安全边距 | SystemUiSmallWindow | com.android.systemui, system |
| 已接入 | 去除多小窗数量限制 | SystemUiSmallWindow | system |
| 已接入 | 去除小窗帧率限制 | SystemUiSmallWindow | system |
| 已接入 | 小窗贴边显示为白色竖条 | SystemUiSmallWindow | com.android.systemui, system |
| 已接入 | 自定义运营商名称 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 仅显示数据卡信号、仅独立样式生效 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 保留图标外电量 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 移除返回顶部白名单 | SystemUiStatusBar | system |
| 已接入 | 隐藏网络活动指示标 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 未连接蓝牙时隐藏蓝牙图标 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 隐藏移动信号漫游指示标 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 国际版网络显示规范 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 仿原生大号网络类型图标包、4G 显示为 LTE | SystemUiStatusBar | com.android.systemui |
| 已接入 | 大号网络类型图标 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 状态栏功耗指示器（实验性） | SystemUiStatusBar | com.android.systemui |
| 已接入 | 状态栏左右边距 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 状态栏上下边距 | SystemUiStatusBar | com.android.systemui |
| 已接入 | 状态栏图标排序 | SystemUiStatusBar, SystemUiIconOrder | com.android.systemui |
| 已接入 | 禁止调节时变为细条 | SystemUiVolume | com.android.systemui |
| 已接入 | 屏蔽听力安全提醒 | SystemUiVolume | com.android.systemui |
| 已接入 | 百分比显示 | SystemUiVolume, SystemUiSideVolumePercentageSettings | com.android.systemui |
| 已接入 | 解锁部分 VIP 功能 | ThemeStore | com.heytap.themestore |
| 已接入 | 禁止联网名单云下发 | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 解除 Google 应用联网限制 | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 解除“软件更新”联网保护 | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 解除默认流量限额 | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 允许“仅在漫游时限制” | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 禁止部分指定应用隐藏设置 | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 禁止部分预装应用过滤 | TrafficManagement | com.oplus.trafficmonitor |
| 已接入 | 3D 壁纸灵敏度（实验性）、倾斜灵敏度 | Wallpapers | com.oplus.wallpapers, com.heytap.colorfulengine |
| 无对应能力，跳过 | 绕过黑名单 | CorePatch | system |
| 无对应能力，跳过 | 强制全局极速高刷 | SettingsAppearance | com.android.settings, system |
| 无对应能力，跳过 | 解除视频色彩增强温控限制 | SettingsAppearance | system |
| 无对应能力，跳过 | 解除画质超清增强温控限制 | SettingsAppearance | system |
| 无对应能力，跳过 | 解锁官方极限刷新率 | SettingsAppearance | com.android.settings |
| 无对应能力，跳过 | 跳过特殊权限风险提醒 | SettingsPermissions | com.android.settings |
| 无对应能力，跳过 | 恢复国行关于本机选项 | SettingsRegion | com.android.settings |
| 无对应能力，跳过 | side_key | SideKey | system |
| 无对应能力，跳过 | 强制设置莫奈取色规范 | SystemUiDynamicColor | com.android.systemui |
| 无对应能力，跳过 | 去除锁屏密码高光描边 | SystemUiLockScreen | com.android.systemui |
| 无对应能力，跳过 | 滑动输入锁屏数字密码（实验性） | SystemUiLockScreen | com.android.systemui |
| 无对应能力，跳过 | 原生双卡堆叠信号 | SystemUiNative | com.android.systemui |
| 无对应能力，跳过 | 旧版通知清除按钮 | SystemUiNotificationCenter | com.android.systemui |
| 无对应能力，跳过 | 跟随系统字体、使用粗体字体 | SystemUiStatusBar | com.android.systemui |
| 无对应能力，跳过 | 移除时分秒显示限制 | SystemUiStatusBar | com.android.systemui |
| 无对应能力，跳过 | 显示一加红“1”入口 | Wallpapers | com.oplus.wallpapers |
| 目标未核准，关闭 | 手势条长按替换（ColorOS 16）、手势条长按替换（ColorOS 17） | Assistant | com.android.settings, com.android.launcher |
| 目标未核准，关闭 | 国际版自定义语音助手 | Assistant | com.android.settings, com.android.launcher |
| 目标未核准，关闭 | 国际版智能助手（实验性） | Assistant | com.android.settings, com.android.launcher |
| 目标未核准，关闭 | 长按菜单显示小组件 | DesktopIcons | com.android.launcher |
| 目标未核准，关闭 | 强制开启桌面模糊动效 | DesktopLayout | com.android.launcher |
| 目标未核准，关闭 | 仿 IOS 资源库（实验性） | DesktopPages | com.android.launcher |
| 目标未核准，关闭 | 隐藏安全访问提示 | FileManager | com.coloros.filemanager |
| 目标未核准，关闭 | AI 修图解锁（实验性） | Gallery | com.coloros.gallery3d |
| 目标未核准，关闭 | 修改游戏助手皮肤 | GameAssistant | com.oplus.games |
| 目标未核准，关闭 | 游戏助手开发者模式 | GameAssistant | com.oplus.games |
| 目标未核准，关闭 | 界面皮肤 | GameAssistant | com.oplus.games |
| 目标未核准，关闭 | 移除音乐卡片白名单 | GameAssistant | com.oplus.games |
| 目标未核准，关闭 | ColorOS 15 样式 | SettingsAboutDevice | com.android.settings |
| 目标未核准，关闭 | 统一卡片材质 | SystemUiFluidCloud | com.android.systemui |
| 目标未核准，关闭 | 原生返回气泡 | SystemUiNative | com.android.systemui |
| 目标未核准，关闭 | 原生实时通知胶囊 | SystemUiNative | com.android.systemui |
| 目标未核准，关闭 | 原生音量面板 | SystemUiNative | com.android.systemui |
| 目标未核准，关闭 | 去广告 | ThemeStore | com.heytap.themestore |
| 缺少备份，跳过 | 强制开启Hey Google唤醒 | Assistant | com.google.android.googlequicksearchbox |
| 缺少备份，跳过 | 国际版智能助手（实验性） | Assistant | com.google.android.googlequicksearchbox |
| 缺少备份，跳过 | 运营商验证码输入 | Esim | com.oplus.euicc |
| 缺少备份，跳过 | 绕过 eSIM 数量限制 | Esim | com.oplus.euicc |
| 缺少备份，跳过 | 解除境内下载限制 | Esim | com.oplus.euicc |
| 缺少备份，跳过 | 解除境内下载限制 | Esim | com.oplus.euicc |
| 缺少备份，跳过 | eSIM 诊断信息 | EsimDiagnostics | com.oplus.euicc |
| 缺少备份，跳过 | 复制验证码 | GoogleMessages | com.google.android.apps.messaging |
| 缺少备份，跳过 | 启用 Gemini 聊天 | GoogleMessages | com.google.android.apps.messaging |
| 缺少备份，跳过 | 修复初始化底栏抬高问题 | Keyboard | com.oplus.keyboard |
| 缺少备份，跳过 | 补全旗帜 Emoji | Keyboard | com.oplus.keyboard |
| 缺少备份，跳过 | 隐藏左下角收起键盘按钮 | Keyboard | com.oplus.keyboard |
| 缺少备份，跳过 | 解除质感模糊应用限制 | Keyboard | com.oplus.keyboard |
| 缺少备份，跳过 | 强制显示心率检测 | SettingsRealme | com.android.settings |
| 缺少备份，跳过 | 强制显示入眠胶囊 | SettingsRealme | com.android.settings |

C16 中缺少备份的主要包为 eSIM、Google App、Google Messages 和键盘。没有开放相册 AI 修图、未核准的游戏皮肤/开发者/音乐入口、桌面分类页/强制模糊/部件替换及助手圈搜等路径。清单中的“已接入”表示已完成离线接口核准与接线，不表示全部已通过 C16 实机验收。
