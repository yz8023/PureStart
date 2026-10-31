# PureStart v15.1.4（113）测试版

针对你反馈的两个问题的修复版：**系统壁纸调用失败**、**卡片内仍有白色底色方块**。配置键、包名、签名、Hook 逻辑未变，可覆盖 v15.1.0+。

> 仍未在真机 / LSPosed 上验收，继续按 **pre-release** 发布。

## 1. 系统壁纸调用失败

**根因（Android 系统限制，不是实现写错）**：`WallpaperManager.getDrawable()` 从 Android 13 起就不能给普通应用用了——API 33 会返回系统默认壁纸或抛 `SecurityException`，**API 34 起必定抛 `SecurityException`**，Google 在 [issuetracker 237124750](https://issuetracker.google.com/issues/237124750) 明确标记 **Won't Fix (Intended Behavior)**：壁纸只对特权系统应用开放（壁纸可作指纹识别用途）。所以「申请个权限就能读」这条路在 13+ 已经堵死。

**修复（改用官方推荐的窗口级做法）**：

- 新增透明窗口主题 `AppTheme.Wallpaper`（`windowIsTranslucent` + `windowShowWallpaper`），并在 `SettingsActivity` 里给窗口加 `FLAG_SHOW_WALLPAPER`——由**系统**把桌面壁纸合成到窗口下层。壁纸不需要被应用读取，也不需要任何权限；Compose 的 RenderEffect 依然能折射到它，玻璃效果不打折。
- 选择「系统壁纸」时会自动 `recreate()` 一次，窗口透明标志立即生效。
- API ≤ 32（Android 12 及以下）仍然优先直接读取壁纸位图（能取到就画在背景层，折射更实）；取不到就同样退回窗口合成，不会白屏。
- 设置页新增**读取状态**一行，明确告诉你这次是「取到位图」还是「系统已禁止读取、已改用系统合成」，不再出现「点了没反应也不知道为什么」。
- 背景层在系统壁纸模式下保持透明，不会把壁纸叠两遍。

> 注意：窗口合成模式下壁纸由系统绘制，因此**壁纸的压暗滑杆只对「自定义图片」生效**；系统壁纸想更暗可以在系统里换一张深色壁纸。

## 2. 卡片内仍有白色底方块

上一版只改了「关闭磨砂」的降级分支，改错了地方。真正的原因是**玻璃分支里 `onDrawSurface` 在模糊之后又盖了一层不透明 surface**：

```
drawBackdrop(..., onDrawSurface = { drawRect(surface.copy(alpha = alpha)) })
// 默认 alpha = tint(.08) + opacity(.55) * .04 ≈ .10，不透明度拉满时 .45
```

4dp 模糊已经把背景抹成接近纯色的雾面，再压一层实色 surface，看起来就是一块白板。现在：

- 卡片的 `onDrawSurface` 从 `.10~.45` 压到 **`.08 * .35 ≈ .03~.16`**（几乎不着色，只留一点提亮）；
- 底栏容器单独保留 `.06~.40` 的实体感，避免底栏飘在页面上；
- 降级分支沿用上一版的极淡雾面（5%~22%）+ 1dp 描边，不再有实心白底。

## 新增回归测试

`tests/check_glass_surfaces.py`：静态断言这两类问题不会复发——卡片不得在模糊后覆盖高 alpha 的整块 surface；Android 13+ 不得再调用 `getDrawable()`，必须走 `FLAG_SHOW_WALLPAPER` + 透明窗口；并且背景下功能**不得引入任何权限**（当前 `uses-permission` 为空）。

## 产物

- `PureStart-v15.1.4-release.apk`（versionCode 113，versionName 15.1.4）
- `PureStart-v15.1.4-source.zip`（源码包，不含签名密钥）

- APK SHA-256：`fd2508ebdf5c944cbcbfcb81a8c04b9c534a3524184ae866dbfde00828f25fd9`
- 证书 SHA-256：`6c82544006f036be304a14d7334f102dd186d97998718764f88e7f3e477ab3ee`（与 v15.1.0+ 相同，可直接覆盖安装）

## 验证

`bash gradlew clean :app:testReleaseUnitTest :app:assembleRelease`（JDK 21 / SDK 36 / build-tools 34 / `KEYSTORE_PATH=debug.keystore`）通过；`scripts/check.py`、`tests/check_ui_contract.py`、`tests/check_glass_surfaces.py`、`tests/check_apk.py`、两个 JVM 桩测试全部通过；已确认 `style/AppTheme.Wallpaper` 进入 APK 资源表。

## 已知限制

- 窗口透明 + `FLAG_SHOW_WALLPAPER` 在个别 ROM（部分国产系统对透明窗口有额外限制）上可能只显示纯色底，此时建议改用「自定义图片」。
- 动态壁纸（Live Wallpaper）在窗口合成模式下多数系统可以透出，但仍取决于 ROM。
- 真机观感（卡片通透度、壁纸压暗、底栏可读性）尚未验收，欢迎反馈具体机型与系统版本。
