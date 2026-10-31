# PureStart v15.1.2（111）测试版

本次是**交互修复版**，只改界面手势、玻璃绘制与一处 Hook 性能问题，配置格式、包名、签名和 Hook 逻辑均未改变，可直接覆盖安装 v15.1.0 / v15.1.1。

> 仍未经 Android / LSPosed 真机验收；建议发布时勾选 GitHub 的 **pre-release**，不要描述为已验证的稳定版。

## 本次修复

### 1. 底栏拖动换页：玻璃胶囊被底栏「框住」

- **根因**：玻璃胶囊是 `dockContainer` 的**子节点**，而 `drawBackdrop` 在测量阶段会把子内容放进一个「按胶囊形状裁剪」的 graphicsLayer（`clip = true`），胶囊再怎么放大都会被底栏轮廓裁掉。
- **修复**：胶囊移到容器**之外**、与底板同级并**后绘制**，成为真正的浮层；底栏只负责磨砂底板、标签与点击区。
- **溢出效果**：按压时胶囊按参考模板 `pressedScale = 78/56 ≈ 1.39` 放大（原代码 1.30 且被裁剪，所以看不出效果），高光、内阴影、投影随按压增强，胶囊确实「浮」在底栏上并溢出边界。

### 2. 页面可以直接左右滑动

- **根因**：页面是 `when (tab)` 直接切换的，没有任何横向手势；只有底栏能拖。
- **修复**：改用 `HorizontalPager`（Compose Foundation，无新增依赖），四页可直接左右滑、带回弹与惯性。
- **单一状态源**：页面位置与胶囊位置现在共用 `PagerState`。手指滑页面时胶囊实时跟手；拖底栏松手后也直接驱动 Pager。原来「底栏位置」和「页面」是两套互不相干的状态，容易不同步。
- **开关**：设置 → 个性化 → **页面左右滑动翻页**（默认开），关闭后可避免与页面内的横向控件冲突。

### 3. 底栏拖拽手感

- 加入触摸阈值：纯点击仍然准确的落到对应 Tab（原来按下即定位，点击会先「跳一下」）。
- 使用 `VelocityTracker`，快速甩动也能翻页（相邻页）。
- 拖动从「按下的那个槽位」起算，手指落在胶囊外侧时胶囊不会瞬移。
- 左右方向与 RTL 布局都正确取反，位置始终钳制在 0..3 页。

### 4. 代码审查修掉的问题

- **`UniversalAdHook` 热路径**：`ViewGroup.addView` 的拦截里**每个 View 都读两次开关闸门**；闸门查询本身已有 2s 缓存，但仍会反复加锁/比对。现改为 500ms 节流缓存，界面滚动不再被 Hook 放大开销。
- **`SettingsActivity` 空指针风险**：`onDestroy` 里 `repository.close()` 在 `lateinit` 尚未赋值时（`onCreate` 早期异常）会抛 `UninitializedPropertyAccessException`，已改为可空安全释放。
- **`tests/check_apk.py` 硬编码 `build-tools/35.0.0`**：构建机只装 34.0.0 时误报失败，改为自动选取已安装的最高版本 `apksigner`。
- **`tests/check_backend_unchanged.py` 基线过严**：新增一条界面偏好键（页面滑动开关）会被判成「后端被改」。改为 `Prefs.java` 按**白名单新增行**核对，其余 Hook 键与读写方法仍受保护。
- **`tests/check_ui_contract.py`**：增加断言，防止胶囊被重新放回 `dockContainer` 内部（该回归一旦发生，问题 1 就会复发）。
- **单元测试补齐**：`DragMathTest` 新增底栏吸附、甩动、RTL 速度换算等用例（原测试在 v15.1.1 源码里实际未被执行）。
- **`clean` 后构建失败**：`app/build.gradle.kts` 在配置阶段写 `AndroidManifest.xml` / `BuildInfo.java`，`clean` 会把这些文件删掉，导致下一次构建 `mainManifest ... doesn't exist`。现改为 `prepareGeneratedSources` 任务并显式声明输出与依赖。
- **仓库与源码包不一致**：Compose 迁移后，旧 View UI 类（`SettingsActivity.java`、`LiquidBar.java`、`GlassEngine.java` 等 12 个文件）仍被 Git 跟踪，直接克隆仓库构建会出现 `SettingsActivity` 重复声明。已从仓库删除。

## 产物

- `PureStart-v15.1.2-release.apk`（versionCode 111，versionName 15.1.2，约 1.9MB）
- `PureStart-v15.1.2-source.zip`（源码包，不含签名密钥）

- APK SHA-256：`7e1a645bd5870568e5c8165d35f4df6049b5e8b75262b7d53efc176f51c26434`
- 证书 SHA-256：`6c82544006f036be304a14d7334f102dd186d97998718764f88e7f3e477ab3ee`（与 v15.1.0 / v15.1.1 相同，可覆盖安装）

## 验证

在本仓库的构建环境（JDK 21 编译、`compileSdk 36` / `build-tools 34.0.0`、Gradle 8.13、`KEYSTORE_PATH=debug.keystore`）执行：

- `bash gradlew :app:testReleaseUnitTest`：通过（`DragMathTest`、`UniversalReportTest`、`XiaoCanGateTest`）。
- `bash gradlew :app:assembleRelease`（R8 + 资源裁剪）：通过。
- `python3 scripts/check.py`：通过（目标清单三处一致 / 版本单一来源 / API 声明 / authority / UI-Hook 隔离 / 密钥在位）。
- `python3 tests/check_ui_contract.py`：通过（含「胶囊必须在容器外绘制」「页面必须可滑动」断言）。
- `python3 tests/check_apk.py app/build/outputs/apk/release/app-release.apk`：通过（Xposed 元数据、入口类、签名结构）。
- `python3 tests/test_settings_repository.py`、`tests/test_backup_repository.py`：通过（JVM 桩）。

## 已知限制与风险

- **未做真机验收**：手势、玻璃折射、LSPosed 注入仍需在设备上确认；本版所有「已修复」结论来自静态结构核对、构建产物检查与单元测试。
- **体积变大**：v15.1.0 的 release 是 `build.sh`（aapt2 + javac + d8，无 Compose 运行时）产物；本仓库默认 Gradle 构建包含 Compose/AndroidX，APK 约 1.9MB（与 v15.0.5/15.0.6 的 Compose 版体积一致）。如需极小体积请用 `bash build.sh` 对应的构建链。
- **签名**：继续使用仓库原有 `debug.keystore`（证书 SHA-256 `6c8254...ab3ee`）。证书必须与已安装版本一致才能覆盖安装；请先备份配置，不要为试装卸载正式版。
- 低版本 Android（< 12）没有 RenderEffect，玻璃为半透明降级，不能呈现实时折射；胶囊仍会放大溢出（降级路径按按压进度加深投影与描边）。
- Hook 能力限制见 `HOOK_AUDIT.md`：自检的「已安装」仅代表 Hook 注册成功，不代表广告一定被拦截。
