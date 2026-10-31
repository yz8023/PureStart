> 注意：本指南主要描述重构前的 Java/View UI，界面相关文件与构建方式已过期；当前以 README.md 和 COMPOSE_MIGRATION.md 为准。Hook 部分修改前请核对现行源码。

# PureStart (ModKit Edition) — 开发指南

> 适用：LSPosed / FPA（fun.fpa）/ LSPatch 全兼容（libxposed API 100+，纯 Java，无 androidx、无 Gradle）。
> 本指南与代码互相校验（2026-10-31，v15.0.8 起）；发现文档与代码不符请以代码为准并回改本文档。

---

## 〇、仓库里有什么

```
PureStart/
├── AndroidManifest.xml        # ★ 版本号唯一来源 + 权限/queries 声明
├── build.sh                   # 一键构建（版本号/文件名全部派生自 Manifest；签名仅环境变量）
├── scripts/check.py           # 漂移门禁 6 项（见「五、门禁」）
├── debug.keystore             # 连续发布签名密钥（证书 6c825440…；构建经 KEYSTORE_PATH 引用）
├── libs/api.jar               # libxposed API（仅编译期）
├── res/                       # 图标 + 设置页资源
├── src/com/yz8023/purestart/
│   ├── Main.java              # ★ 模块入口：目标分发 + 开关闸门（fail-safe）+ 自检上报
│   ├── Targets.java           # ★ 目标清单单一事实来源（LIST：包名 + 描述）
│   ├── XiaoCanHook.java       # 小蚕惠生活净化（含壳延迟安装；主类未命中有 ERROR 留痕）
│   ├── UniversalAdHook.java   # 通用去广告引擎（Activity/addView/Dialog + DexFinder 跳过）
│   ├── DexFinder.java         # 反射特征匹配（方法/字段第一层匹配；非 DexKit 级）
│   ├── HookGate.java          # 开关闸门接口
│   ├── HookStateProvider.java # 跨进程开关通道 + Hook 自检回传通道（exported）
│   ├── IconControlReceiver.java # 桌面图标隐藏/恢复（adb 广播，防锁死兜底）
│   ├── Prefs.java             # ★ SharedPreferences 全键收口（玻璃参数/Hook 开关/自检数据）
│   ├── SettingsActivity.java  # 设置页装配 + 底栏宿主 + GlassCardView.Host（≤800 行）
│   ├── LiquidBar.java         # 液态玻璃底栏（弹簧/手势/胶囊渲染；P1-2 自 Activity 拆出）
│   ├── GlassEngine.java       # 磨砂捕获引擎（位图链/box 模糊/BackdropView；P1-2 拆出）
│   ├── HomePage.java          # 「首页」页组装（P1-2 拆出）
│   ├── AppsPage.java          # 「应用」页组装（P1-2 拆出）
│   ├── ExtensionPage.java     # 「拓展」页组装（P1-2 拆出）
│   ├── SettingsCards.java     # 「设置」页组装（P1-2 拆出）
│   ├── GlassCardView.java     # 磨砂卡片（背景位图裁剪 + 染色 + 棱光 + 描边）
│   ├── LiquidGlass.java       # 磨砂工具箱（弹窗退化公式 + API 31+ 系统窗口模糊）
│   └── ThemeDialog / AppDetailDialog / HoldShieldView
└── xposed/
    ├── java_init.list         # 入口类声明
    ├── module.prop            # 版本行由 build.sh 从 Manifest 写回（勿手改版本）
    └── scope.list             # ★ 作用域（与 Targets.LIST / APP_DESC 三处同步，脚本把关）
```

## 一、给新 App 加 Hook（只改两处 + 声明一处）

1. `Targets.java` 的 `LIST` 加一行 `{ "包名", "应用名 · 功能描述" }`；
2. `SettingsActivity.APP_DESC` 加同一包名描述行（设置页展示用）；
3. `xposed/scope.list` 加同一包名（LSPosed 作用域）。

三处必须同步——`scripts/check.py` 在每次构建前校验，缺一处直接构建失败。

Hook 逻辑本身：通用净化走 `UniversalAdHook`（自动生效，无需改代码）；
App 专属逻辑新建 `XxxHook.java`，在 `Main.onPackageLoaded` 分发，
并在安装点调用 `reporter.ok/miss/fail`（自检页依赖这些上报，不要静默 catch；
纯防御 catch 必须注释「为什么可忽略」，安装路径 catch 至少 `Log.e`）。

## 二、版本与发布

- 版本号**只改 `AndroidManifest.xml`**（versionName/versionCode）。
  `build.sh` 自动派生：module.prop 版本行、输出文件名
  `build/PureStart-vX.Y.Z-release.apk`。代码内版本**运行时**经 PackageManager 读取
  （`Main.moduleVersion()` / 设置页 `versionName()`），禁止任何硬编码字面版本号。
- 签名：**只从环境变量读**——`KEYSTORE_PATH`（必填，密钥文件路径）、
  `KEYSTORE_PASS` / `KEY_ALIAS` / `KEY_PASS`（缺省 `android` / `androiddebugkey` / `android`）。
  **`KEYSTORE_PATH` 缺失或文件不存在时构建直接失败**——绝不自动生成新密钥
  （换签名 = 用户无法覆盖升级）。仓库内 `debug.keystore` 即历代连续发布密钥，
  等价调用：`KEYSTORE_PATH=debug.keystore KEYSTORE_PASS=android KEY_ALIAS=androiddebugkey KEY_PASS=android bash build.sh`。

## 三、构建

```bash
export ANDROID_HOME=/path/to/sdk          # 需 platforms/android-35 + build-tools/34.0.0
export JAVA_HOME=/path/to/jdk17           # javac -source/-target 17
KEYSTORE_PATH=/path/to/ks bash build.sh       # 其余变量见「二」，缺省 android 三件套
```

构建前置齐全时产物在 `build/PureStart-vX.Y.Z-release.apk`；
构建过程自带漂移门禁（见「五」）。

## 四、磨砂玻璃（View 近似）——维护红线

命名与实现遵循 yz8023/ui `prompts/03` 路线 B：

- 交付物只叫「**磨砂玻璃（View 近似）**」，不得称液态玻璃/宣称对齐模板；
- 参数取值借鉴模板（`Prefs` 收口，默认值与范围与模板 SettingsScreen.kt 一致），但
  **行为映射是自创近似**（棱光厚度/背景放大），设置页差异声明必须保留；
- 近似优先用系统能力：弹窗 API 31+ 用 `Window.setBackgroundBlurRadius`（真磨砂）；
- 验收三条件：滑动不掉帧、深浅色可读、可点区域不受影响。

## 五、门禁：scripts/check.py

```bash
python3 scripts/check.py    # 退出码：通过 0、发现漂移 1
```

六项检查（build.sh 构建前自动运行，提交前请手动跑一次）：

1. 目标清单三处一致（`Targets.LIST` / `xposed/scope.list` / `SettingsActivity.APP_DESC`）；
2. 版本号单一来源（源码出现字面版本号即报错；module.prop 与 Manifest 一致）；
3. API 版本声明一致（Main.java ↔ module.prop targetApiVersion）；
4. Provider authority 一致（Manifest ↔ `Main.MODULE_PACKAGE + ".hookstate"`，为包名迁移把关）；
5. UI/Hook 类隔离（UI 进程文件不得引用 Hook 类，防 NoClassDefFoundError 崩溃）；
6. 签名密钥在位。

## 六、权限与安全取舍（README 有完整说明）

- 已移除 `INTERNET`（无网络请求）与 `QUERY_ALL_PACKAGES`（改 `<queries>` 17 目标包）；
- `HookStateProvider` 保持 exported（目标进程读取开关必需），无鉴权，
  理论上可被他 App 读取开关/伪造自检上报——接受此取舍，因敏感度低且是跨进程最通用通道。
- 包名已迁移为正式包名 `com.yz8023.purestart`（v15.1.0 断代；原 `com.example.l` 为脚手架占位）。
  同步面：Manifest package / Provider authority / `module.prop` id / `java_init.list` /
  IconAlias ComponentName / 图标恢复广播 action / 源码目录 `src/com/yz8023/purestart/`，
  全部由 check.py 第 4 项（authority 一致性）与构建流程把关。
  旧包名版本不可覆盖升级（Android 按包名判定），旧版隐藏图标恢复用
  `adb shell pm enable com.example.l/.IconAlias`。
