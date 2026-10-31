# v15.2 第二阶段：UI 与代码量压缩（对照《PureStart_Review_and_Plan.md》步骤 2）

> 交付范围：**第二阶段（UI 优化 / 代码量）**。第一阶段（架构）见 `REFACTOR-v15.2-phase1.md`，
> 第三阶段（APK 体积）为下一轮。
> 全部改动通过仓库门禁；**仍未在 Android 设备 / LSPosed 上验证**（本环境无 Android SDK，
> 无法构建 APK，详见文末「验证状态」）。

## 一、先给结论：这一阶段压掉了什么

| 重复/冗余 | 之前 | 现在 |
| --- | --- | --- |
| 页面偏好刷新样板（`mutableIntStateOf` + `DisposableEffect` + `addObserver`） | 5 处（首页/应用/设置/规则编辑/壳） | 1 个函数 `rememberSettingsRevision` |
| `SharedPreferences` 监听器（每个开关、每个滑杆各一个 + 页面级"双保险"） | 15+ 个且互相重复触发 | 1 个（仓库内部） |
| 界面层直接持有 `SharedPreferences` | 3 个文件 `Prefs.sp(...)` | 0（全部经 `SettingsRepository`） |
| `Build.VERSION.SDK_INT < 31` 材质降级判定 | 3 处（卡片/容器/胶囊各写一遍） | 1 处（`GlassTier`） |
| 桌面图标状态机 + 「切换失败」提示 | 2 份（首页、设置页） | 1 份 `rememberLauncherIconState` |
| 隐藏图标恢复文案 + 确认弹窗 | 2 份长文案 | 1 份 `HIDE_ICON_HINT` / `HideIconDialog` |
| 外观档位 / 语言档位文案列表 | 各 2 份（正文 + 弹窗） | 各 1 份常量 |
| 自检上报解析（`ok\|miss\|fails\|version\|ts`） | 写在 Composable 里，不可测 | 纯函数 `SelfCheckReport.parse`（有 JVM 测试） |
| 永不使用却一直运行的无限动画（`glassBackground` 里的 `phase`） | 1 处（每帧白烧） | 0 |
| 未被任何代码引用的依赖 `kyant-shapes` | 1 个库 | 0 |

**代码量（`app/src/main/java/**/*.kt`，去掉注释与空行后的真实逻辑行）：1715 → 1660（−55 行）**，
注释量基本持平（343 → 349 行）。文件数 12 → 15，总行数略增是因为新增了带文档的复用文件与
空行分段；**重复逻辑的减少比总行数更有意义**，这也是本阶段刻意没去"凑行数"的原因。

## 二、具体改动

### 1. 状态只有一条链（新增 `ui/SettingState.kt`）

```kotlin
val revision = rememberSettingsRevision(repository)   // 偏好变化（仓库统一通知，主线程）
val resume = rememberResumeRevision()                 // 从系统设置页/桌面返回
val icon = rememberLauncherIconState()                // 桌面图标别名状态 + 切换
```

- 旧实现里，同一次偏好变更会被通知 2~3 次（页面 Observer + 页面自己的 SharedPreferences 监听 +
  控件自己的 `booleanFlow` 监听）。第二阶段的 `PreferenceSwitch/Slider` 仍然"写入即回读"，
  但订阅的是仓库那一条回调（`valueFlow`），所以监听器总数从 15+ 降到 1。
- 设置页原来那份 `// 自己再监听一遍偏好：不依赖 repository 的通知也能刷新界面` 的"双保险"
  已删除——它掩盖的是一个真实的 bug 面（重复刷新、关闭后残留回调），而不是提供保障。
- `remember` 的键直接用版本号，不再需要任何手动失效代码。

### 2. 设置控件复用（新增 `ui/PreferenceControls.kt`）

`PreferenceSwitch` / `PreferenceSlider` 从 `SettingsScreen.kt` 移出，并删掉旧签名里
**从未被读过**的 `revision` 参数（纯噪声），读写改为 `repository.boolean/putBoolean`
（不再各自 `Prefs.sp(context)`）。

### 3. 玻璃材质收敛（`GlassTheme.kt`）

旧实现里 `glassPanel` / `dockContainer` / `dockLens` 各自重复判断
"普通模式？关了磨砂？API < 31？"，同一句 SDK 判定出现 3 次，**任何一处漏改就会出现
"卡片是实心、底栏还是玻璃"的割裂**。现在：

```kotlin
private enum class GlassTier { Plain, Fallback, Backdrop }
private val GlassAppearance.tier get() = when {
    plain -> GlassTier.Plain
    !enabled || Build.VERSION.SDK_INT < 31 -> GlassTier.Fallback
    else -> GlassTier.Backdrop
}
```

三个表面都变成 `when (appearance.tier) { ... }`，实心表面统一由 `solidSurface(shape, elevation, …)`
提供（卡片 1dp / 底栏 3dp 只差阴影高度），雾面统一由 `cardSheen(...)` 提供。
**渲染参数一个都没动**（rim/tintAlpha/折射/高光/阴影数值逐项保留），所以观感应与 v15.1.7 一致。

同时删掉 `glassBackground()` 里一个从未被使用的无限动画：它每帧算 `phase` 然后丢弃，
返回的 `Brush` 里没有任何时间项——纯掉帧来源。

另外 `PureStartTheme` 改成接收 `repository`，外观参数由仓库统一读取，界面层再无 `Prefs.sp(...)`。

### 4. 自检上报解析抽成纯函数（新增 `repository/SelfCheckReport.kt`）

`ok|miss|fails|version|timestamp` 的解析原本埋在 Composable 的 `when` 里，无法测试，而且把
"没上报"和"上报了但 0 项 Hook"混成同一个结论。现在 `SelfCheckReport.parse()` 负责解析与兜底
（脏数据绝不抛异常），`summary()` 负责结论文案，界面只做渲染 + 时间格式化。
新增 `tests/test_self_check_report.py` 覆盖：null/空白、正常、0 项、字段缺失、脏数据、多余字段、超长失败列表。

### 5. 依赖精简

`kyant-shapes` 全仓库零引用（只用到 `kyant-backdrop` 的 `drawBackdrop`/`lens`/`vibrancy`），
已从 `app/build.gradle.kts` 与 `libs.versions.toml` 移除。

### 6. 底栏几何：让被测函数真正上生产

胶囊横向位置原本是 `sideInsetDp + tabDp * shownTab` 内联计算，`DragMath.capsuleOffset`
只被单元测试调用（典型的"测的是 A、跑的是 B"）。现在生产代码改为
`capsuleOffset(shownTab, tabPx, sideInsetPx).toDp()`，公式与 `DragMathTest` 覆盖的是同一份。

## 三、门禁变化

- `tests/check_glass_surfaces.py`：改为按 `GlassTier` 三档结构断言（普通模式实心、降级雾面、
  玻璃档才允许 `drawBackdrop`、SDK 判定必须只有一处），并新增"胶囊位置必须由 `capsuleOffset` 导出"。
- `tests/check_ui_contract.py`：新增"页面必须使用 `rememberSettingsRevision`、不得自己注册 Observer"、
  "隐藏图标文案与设置控件只能有一份定义"。
- 新增 `tests/check_ui_state.py`：界面层不得出现 `registerOnSharedPreferenceChangeListener`、
  不得出现 `Prefs.sp(`、`SettingsRepository.Observer` 只允许在 `SettingState.kt`、
  不得自带 `LifecycleEventObserver`、不得复制恢复文案。
- 新增 `tests/test_self_check_report.py`：见上。
- `scripts/verify.sh` 已包含上述全部。

## 四、明确**没有**做的事（原因）

1. **没有删 `kyant-backdrop`、没有把玻璃换成 Compose 原生 `Modifier.blur`。**
   评审报告建议"用 `Modifier.blur` 替代自定义绘制"，但本项目要的是**折射（lens）+ 色差 + 按压变透**
   这一套参考实现（yz8023/UI 的 `LiquidBottomTabs` / `Glass.kt`），Compose 原生的
   `Modifier.blur` 只有模糊，没有透镜折射与 `onDrawSurface` 语义；换掉等于删功能。
   唯一的 `Canvas`（极光四团径向光斑）也无法用 `blur` 表达。
2. **没有把底栏手势换成 `PagerDefaults.flingBehavior` / `snapFlingBehavior`。**
   现在的拖拽 + 速度前瞻（`landingTab`）是 v15.1.7 为了修"胶囊反向/糊住图标"专门对齐参考实现的，
   有单测锁定方向约定。换手势实现必须真机手感验收，本环境做不到，盲改风险大于收益。
3. **没有做折叠屏/平板响应式适配。**
   需要真机（或至少 Compose Preview 截图）验收，否则只是把布局改坏。
4. **没有按评审报告的"压缩到 1200 行"目标硬删代码。** 真实基线是 UI 侧 1715 行逻辑代码
   （不是报告说的 1933 行含注释总量），其中 `WallpaperRepository`（184 行）、`DragMath`（81 行）、
   `PureStartShell` 的手势段（约 120 行）都是被测试与 bug 历史锁定的必要复杂度。
   本阶段压掉的是**重复**，不是功能。

## 五、验证状态

本环境（JDK 11 + Python，无 Android SDK）实际跑通：

```
python3 scripts/check.py                      通过（7 项）
python3 tests/check_ui_contract.py            通过（含新增的 state 断言）
python3 tests/check_glass_surfaces.py         通过（改为 GlassTier 结构断言）
python3 tests/check_ui_state.py               通过（新增）
python3 tests/check_backend_unchanged.py      通过（Hook 后端仍与基线逐字节一致）
python3 tests/test_settings_repository.py     通过
python3 tests/test_backup_repository.py       通过
python3 tests/test_self_check_report.py       通过（新增）
```

Compose 代码仅做了**解析级**检查（`kotlinc` 在没有 Compose/Android 依赖时报的
unresolved reference 已逐条确认为依赖缺失，非语法错误）——这**不能**替代编译与真机验收。

**必须在有 SDK 的机器上补做的验收**：
1. `bash scripts/verify.sh` → `bash build.sh`（编译是唯一能证明这次 `when(GlassTier)` 重构无误的办法）；
2. 真机逐项对照 v15.1.7：玻璃模式 / 普通模式 / 关磨砂降级 / API 30 设备四组，
   看卡片、底栏容器、胶囊三处是否**同档**（这正是本次收敛要防的割裂）；
3. 设置页开关与滑杆：点击后是否即时跟手、四个页面是否同步、退出重进是否保持；
4. 桌面图标：首页菜单与设置页两处入口是否一致、隐藏后 adb 广播能否恢复；
5. Hook 自检：安装一次目标应用，确认上报解析（新增纯函数）与旧文案一致。

## 六、下一阶段（第三阶段：体积）

以实测 APK 为准：`ic_launcher_foreground.png`（21KB）转矢量、`res/drawable` 里未被引用的图标、
R8 规则从 `-keep com.yz8023.purestart.**`（整包保留）收窄到反射入口白名单、
`resources`/语言裁剪、是否值得上 AAB。**前提是先能构建并量出 baseline 体积**，
否则所有"压缩到 xx%"都是估算——这一点和第一阶段文档里的说明一致。
