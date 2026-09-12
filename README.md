# 蓝牙闹钟

到点先检测蓝牙耳机是否连接，连上就通过蓝牙播放你设定的音乐闹铃。

和普通闹钟应用的区别在于它把「播放路由」当成一等公民处理：Android 系统会把闹钟音频强制锁在扬声器上，想从蓝牙耳机出声必须绕开这套机制，这个项目就是专门解决这件事的。

## 功能

- **自定义铃声** —— 从本地选任意音频文件作为闹铃
- **到点检测蓝牙连接状态**，按下面规则决定输出：

  | 「仅蓝牙播放」 | 连了蓝牙耳机 | 没连蓝牙耳机 |
  |---|---|---|
  | 关（默认） | 蓝牙耳机播放 | 扬声器播放 |
  | 开 | 蓝牙耳机播放 | **本次完全静默**，不在扬声器外放 |

- 每周重复（按星期几自由勾选）、标签
- 稍后提醒（5/10/15/20/30 分钟可调）
- 音量渐强、震动开关
- 响铃界面在**锁屏之上全屏显示**

## 保活设计

闹钟能不能准时响，取决于系统愿不愿意让你响。这里分了五层，每层挡不同的失效场景：

| 机制 | 挡住的问题 |
|---|---|
| `AlarmManager.setAlarmClock()` | Doze、应用待机导致的响铃推迟。这是系统唯一把应用当「用户可见闹钟」对待的接口，不受任何省电策略影响 |
| 看门狗每 15 分钟自检 | 排期被系统或厂商 ROM 悄悄丢掉 |
| 开机 / 应用更新 / 时间时区变更后重注册 | 重启、更新、改时区后闹钟消失 |
| 电池优化白名单 + 厂商自启动引导 | 国产 ROM 的后台清理 |
| 常驻前台服务（默认关闭） | 以上都挡不住时的兜底 |

另外整个应用启用了 **direct boot**：闹钟状态存在 device-protected 存储里，相关组件声明 `directBootAware`。所以**重启后即使你还没解锁屏幕，闹钟就已经恢复了** —— 否则半夜重启、早上不碰手机就会整晚不响。

看门狗除了补排「早该响却没响」的闹钟，还会拿系统的 `nextAlarmClock` 做交叉验证：应用被「强行停止」时系统会清空它的全部闹钟登记，而本地记录的排期时间还在未来，单看本地状态发现不了，只有这种交叉验证能兜住。

## 安装

从 [Releases](../../releases) 下载 APK 直接安装（需要允许「安装未知来源应用」）。

> Release 里的 APK 是 **debug 构建**，用调试密钥签名。功能完整、可以直接装，但不适合上架应用商店，也无法在后续换成正式签名后覆盖安装。需要正式签名的话见下方「发布正式版本」。

**装好后请先打开「保活与权限」页，把里面几项逐个开全** —— 尤其是厂商自启动白名单，这是国产 ROM 上最容易漏掉、也最容易导致闹钟失效的一项。

## 构建

需要 JDK 17 和 Android SDK（`compileSdk 36`）。

```bash
./gradlew assembleDebug      # 调试包
./gradlew testDebugUnitTest  # 单元测试
./gradlew lintDebug          # 静态检查
```

仓库里没有 `local.properties`（含本机 SDK 绝对路径，按 Android 惯例不入库）。Android Studio 会自动生成；命令行构建则需设置 `ANDROID_HOME` 环境变量，或自己建一个：

```properties
sdk.dir=/path/to/Android/Sdk
```

## 权限说明

| 权限 | 用途 |
|---|---|
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | 精确闹钟。前者面向 Android 13+（闹钟类应用安装即授予），后者面向 12/12L |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复闹钟 |
| `WAKE_LOCK` | 响铃期间保持唤醒，避免响到一半睡回去 |
| `FOREGROUND_SERVICE` + `_MEDIA_PLAYBACK` | 响铃前台服务 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 可选的常驻保活服务 |
| `POST_NOTIFICATIONS` | 响铃通知与常驻通知 |
| `USE_FULL_SCREEN_INTENT` | 锁屏时弹出响铃界面 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 申请电池优化白名单 |
| `MODIFY_AUDIO_SETTINGS` | 调整媒体音量 |
| `VIBRATE` | 震动提醒 |
| `BLUETOOTH_CONNECT` | **可选**，仅用于展示蓝牙设备名。检测连接状态走 `AudioManager`，不需要这个权限 |

应用不申请任何存储权限 —— 选音乐走系统文件选择器（`ACTION_OPEN_DOCUMENT`），只接管选中文件本身的持久化读权限。

## 技术笔记

几个踩过的坑，记下来免得以后重新踩：

**为什么用 `USAGE_MEDIA` 而不是 `USAGE_ALARM`**
Android 会把 `USAGE_ALARM` 强制路由到扬声器 —— 这是系统刻意的设计，闹钟不允许走蓝牙。所以想从蓝牙耳机出声，只能把音频声明成媒体流（`USAGE_MEDIA`），再用 `setPreferredDevice()` 指定输出设备。副作用是响铃期间会正常参与音频焦点竞争。

**为什么 `targetSdk` 停在 34**
Android 15/16 针对 `targetSdk 35+` 收紧了两件对本应用致命的事：`BOOT_COMPLETED` 拉起前台服务的限制，以及 `mediaPlayback` 前台服务的额外前置条件。闹钟类应用留在 34 上可靠性明显更好。要上架 Google Play（要求 35+）时需要重新处理这块。

**为什么检测蓝牙不需要权限**
`AudioManager.getDevices(GET_DEVICES_OUTPUTS)` 会返回当前所有可用输出设备，含 `AudioDeviceInfo`，可以直接交给 `MediaPlayer.setPreferredDevice()`。相比走 `BluetoothAdapter` 的 A2DP profile proxy，这条路既不用权限，拿到的也是能直接用于路由的对象。

**看门狗不无条件重排**
早期版本每 15 分钟无条件重排所有闹钟，结果会把用户刚设的「稍后提醒」覆盖掉 —— 10 分钟的 snooze 约 2/3 概率被吃掉。现在只补排「确实漏响」「从未排上」「登记丢失」三种情况，正常等待中的一律不动。

**单次闹钟的稍后提醒**
单次闹钟响过之后会被自动停用。此后再点「稍后提醒」必须先把 `enabled` 重新置真，否则到点时接收器会因为 `enabled == false` 直接跳过，闹钟静默不响。

## 已知限制

- **「仅蓝牙播放」模式下震动也被一起静默**。也就是说开关打开、蓝牙又没连时，手机彻底没反应；单次闹钟这一响会被直接消耗掉。这是当前行为，不是 bug，但确实容易睡过头。
- 响铃中途蓝牙断开，音频会切回扬声器，不做重新判定。
- 应用被「强行停止」后，在用户重新打开应用之前不会响 —— 这是 Android 的限制，任何应用都绕不过。开机自启、看门狗等机制在强制停止状态下全部失效。
- 部分厂商 ROM（小米/华为/OPPO/vivo 等）需要手动加入自启动白名单，应用只能引导跳转，无法代为设置。

## 测试

排期计算（重复日掩码、跨周绕回、单次闹钟滚动、夏令时）有单元测试覆盖：

```bash
./gradlew testDebugUnitTest
```

## 项目结构

```
app/src/main/kotlin/com/demo/btalarm/
├── alarm/          闹钟数据模型、持久化、排期与各类接收器
│   ├── Alarm.kt            数据模型 + 下次触发时刻计算
│   ├── AlarmRepository.kt  device-protected 存储
│   ├── AlarmScheduler.kt   setAlarmClock 注册 + 自愈逻辑
│   ├── AlarmReceiver.kt    到点触发
│   ├── BootReceiver.kt     开机/更新/改时间后重注册
│   └── WatchdogReceiver.kt 15 分钟自检
├── ring/           响铃
│   ├── AudioRoute.kt       蓝牙输出设备检测
│   ├── AlarmPlayer.kt      MediaPlayer 封装 + 音量渐强
│   ├── AlarmRingService.kt 响铃前台服务
│   └── RingActivity.kt     锁屏响铃界面
├── keepalive/      可选常驻保活服务
├── system/         权限检查、通知渠道
└── ui/             Compose 界面
```

## 许可证

[MIT](LICENSE) © 2026 Lmzzzxxx
