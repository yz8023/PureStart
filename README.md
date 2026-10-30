# PureStart (ModKit Edition)

LSPosed/libxposed 去广告模块（纯 Java + Android View，无 Gradle）：悬浮底栏四页签设置界面、
小蚕惠生活净化（忠实移植 [GeniusQA/XiaoCan](https://github.com/GeniusQA/XiaoCan)）、
17 个国民级 App 的通用去广告引擎、磨砂玻璃（View 近似）界面。

## 构建

```bash
# 前置：JDK 17；Android SDK（platforms/android-35 + build-tools/34.0.0）
export ANDROID_HOME=/path/to/sdk
export JAVA_HOME=/path/to/jdk17

# 签名密钥只从环境变量读（见下节）
KEYSTORE_PATH=debug.keystore KEYSTORE_PASS=android KEY_ALIAS=androiddebugkey KEY_PASS=android \
  bash build.sh
# 产物：build/PureStart-vX.Y.Z-release.apk
```

- 版本号唯一来源是 `AndroidManifest.xml`；module.prop 与输出文件名由 `build.sh` 派生，
  代码内版本**运行时**经 PackageManager 读取（无 BuildVersion 生成文件）。
- 构建前自动运行 `scripts/check.py` 漂移门禁（见下）。

## 签名（为什么不可覆盖、为什么不给默认值）

- **`KEYSTORE_PATH` 必填**：未设置或文件不存在时构建直接失败退出，**绝不自动生成新密钥**。
  Android 按签名证书决定能否覆盖升级——换签名 = 老用户无法覆盖安装，只能卸载重装（丢配置）。
- `KEYSTORE_PASS` / `KEY_ALIAS` / `KEY_PASS` 缺省 `android` / `androiddebugkey` / `android`
  （标准 debug 三件套）。
- 仓库内 `debug.keystore` 是历代连续发布用的密钥（证书 `6c825440…`），上面的示例命令
  即等价调用；正式密钥把它换成自己的路径即可，其余流程不变。

## scripts/check.py 用法

```bash
python3 scripts/check.py    # 退出码：通过 0、发现漂移 1
```

六项检查（构建前自动运行；提交前建议手动跑一次）：

1. 目标清单三处一致：`Targets.LIST` / `xposed/scope.list` / `SettingsActivity.APP_DESC`；
2. 版本号单一来源：源码出现字面版本号即报错，module.prop 必须与 Manifest 一致；
3. API 版本声明一致：Main.java ↔ module.prop `targetApiVersion`；
4. Provider authority 一致：Manifest ↔ `Main.MODULE_PACKAGE + ".hookstate"`；
5. UI/Hook 类隔离：UI 进程文件不得引用 Hook 类（防设置页 NoClassDefFoundError）；
6. 签名密钥在位。

## 权限取舍

| 权限 | 状态 | 理由 |
|---|---|---|
| `<queries>` 17 目标包 | 使用 | 精确可见性，替代全量枚举 |
| ~~`QUERY_ALL_PACKAGES`~~ | 已移除 | 设置页只逐包查询 17 个目标，无需全量 |
| ~~`INTERNET`~~ | 已移除 | 模块自身无网络请求（反馈按钮走系统浏览器） |
| `HookStateProvider` exported | 保留 | 目标进程读取开关的跨进程通道（FPA/LSPatch/LSPosed 通用）；无鉴权，理论上可被他 App 读开关/伪造自检上报，敏感度低，属有意取舍 |
| `forceQueryable` | 保留 | 保证目标进程在各类框架下都能 resolve 到本模块 |

## 已知决定与待办

- **包名已于 v15.1.0 迁移至 `com.yz8023.purestart`**（原脚手架占位 `com.example.l`）。
  迁移同步改了 Manifest package、Provider authority、`xposed/module.prop` id、
  `java_init.list`、IconAlias/广播 action 与源码目录；authority 一致性由 check.py 第 4 项把关。
  **断代影响**：旧包名版本无法覆盖升级到新包名（Android 按包名+签名判定），需先卸载旧版
  （或两版并存）；旧版曾隐藏桌面图标的用户，恢复命令为
  `adb shell pm enable com.example.l/.IconAlias`（或 `adb shell am broadcast -a com.example.l.action.SHOW_ICON`）。
- 磨砂玻璃界面是 **View 近似**（命名与差异声明遵循 [yz8023/ui](https://github.com/yz8023/ui)
  `prompts/03` 路线 B）：卡片=窗口内背景位图裁剪、底栏=限频截图降分辨率模糊、
  弹窗 Android 12+ 用系统窗口背景模糊；无 Compose backdrop 折射。
- `DexFinder` 是反射特征匹配的第一层实现，未对标 DexKit 全量 dex 检索（注释已如实标注）。

## 目录速览

见 [ModKit-开发指南.md](ModKit-开发指南.md)（添加 Hook 的步骤、版本规矩、签名规矩、
磨砂维护红线、门禁说明）。
