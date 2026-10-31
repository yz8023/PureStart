# PureStart v15.1.6（115）测试版

按你的要求**完整对照了 [yz8023/UI](https://github.com/yz8023/UI) 的实现**，重做系统壁纸与普通模式，并修掉底栏拖动方向相反的问题。

## 1. 系统壁纸：照参考仓库重做（上一版方向就错了）

我读了 `素材库/网络壁纸与离线缓存/代码/SystemWallpaper.kt` 与其 `实现方案.md`，参考仓库能读到壁纸的真正原因是我上一版漏掉的关键：

> **Android 14+ `getDrawable()` 必定抛 SecurityException，除非应用持有「所有文件访问」（`MANAGE_EXTERNAL_STORAGE`）。**

参考仓库的做法是：清单里声明 `MANAGE_EXTERNAL_STORAGE`，用户选择「系统壁纸」时主动把用户带到 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 系统设置页授权，授权后重读即可拿到真实壁纸；只在代码里读那一行、不做这套引导，在 Android 14+ 必然失败。

上一版我改成「透明窗口 + `FLAG_SHOW_WALLPAPER`」让系统去画壁纸，这条路在多数 ROM 上根本透不出来——等于没实现。现在已经**完全对齐参考仓库**：

- 清单声明 `MANAGE_EXTERNAL_STORAGE`（+ `READ_EXTERNAL_STORAGE` 仅 `maxSdkVersion=32`，覆盖 Android 12 及以下）；
- 读取走**双通道**：先 `WallpaperManager.drawable`，失败再 `getWallpaperFile(FLAG_SYSTEM)` 直接解码（个别 ROM 只认其中一条）；
- 选「系统壁纸」时先尝试读取；读不到就**自动跳授权页**，返回后立即重读；设置页常驻一个「开启『所有文件访问』」按钮可再进；
- **读不到就明说**：显示具体原因（未授权 / 平台拒绝 / 可能是动态壁纸），绝不假装成功；
- 回到前台（`ON_RESUME`）自动重读，换了桌面壁纸不用重启；已移除上一版那套窗口透明主题。

## 2. 普通模式：按参考仓库做成「实心卡片」

参考仓库的 `VisualMode.Normal` 是这样的（`Glass.kt`）：

```kotlin
// Normal / MD3 modes: plain Material surface, no backdrop sampling at all.
val solidTint = baseTint.copy(alpha = 1f)
return this.shadow(elevation, shape, clip = false).clip(shape).background(solidTint)
```

也就是**实心、不透明、不采样背景、不模糊不折射**。而我们的“普通”之前只是“玻璃关掉后的半透明降级”，所以浅色主题下永远有一层发白的半透明块——这就是你反复看到“白框框”的根源。

现在设置页新增 **「普通模式（实心卡片，不做玻璃）」** 开关：打开后卡片是干净的实心 Material 表面（浅色白、深色深），1dp 极淡描边，不做任何背景采样。玻璃模式下的观感仍由「卡片底色」滑杆控制（0 = 完全透明）。

## 3. 底栏拖动方向与滑块相反

方向约定在历史版本里自相矛盾：底栏用「手指右移 = 槽位 +1」，而页面用「手指左移 = 下一页」，两边反向。现在统一成一条约定（`DragMath.slotDelta`，位移和速度共用）：

```
手指左移 = 往「设置」方向 = 槽位 +1 = 页面往左翻
手指右移 = 往「首页」方向 = 槽位 -1
```

RTL 布局下物理方向映射到同一视觉方向，只取反一次。单元测试新增 `dragDirectionMatchesPager` 覆盖该约定。

## 产物

- `PureStart-v15.1.6-release.apk`（versionCode 115，versionName 15.1.6）
- `PureStart-v15.1.6-source.zip`（源码包，不含签名密钥）

- APK SHA-256：`0156e287ce2e26341a5e7124aedfade0e4fc1cd01721439a80ef8854f73c130c`
- 证书 SHA-256：`6c82544006f036be304a14d7334f102dd186d97998718764f88e7f3e477ab3ee`（与 v15.1.0+ 相同，可直接覆盖安装）

## 验证

`bash gradlew clean :app:testReleaseUnitTest :app:assembleRelease` 通过；9 个 JVM 单元测试全部通过（含方向一致性新用例）；`scripts/check.py`、`tests/check_ui_contract.py`、`tests/check_glass_surfaces.py`、`tests/check_apk.py`、`tests/test_settings_repository.py`、`tests/test_backup_repository.py` 全部通过；已确认 APK 内实际声明了 `MANAGE_EXTERNAL_STORAGE`。

## 关于权限（请知悉）

`MANAGE_EXTERNAL_STORAGE`（所有文件访问）是一项**敏感权限**，Google Play 对它有严格审核限制。本模块不在商店分发、且只有你在主动选择「系统壁纸」时才会被引导授权，不授权则功能自动提示并可改用「自定义图片」或「极光」。如果你介意这个权限，可以只用自定义图片——那一路完全不需要任何权限。

## 已知限制

- 未在真机验收：授权页跳转、各家 ROM 的壁纸读取、普通模式观感与底栏手感仍需实测。
- 动态壁纸（Live Wallpaper）在系统壁纸模式下部分 ROM 仍可能返回静态帧或默认图。
- 极光/自定义图片不需要任何权限，可作为系统壁纸不可用时的替代。
