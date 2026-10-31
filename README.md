# PureStart · Compose 迁移版

LSPosed/libxposed 模块。设置入口使用 Kotlin + Compose，液态玻璃参考 [yz8023/ui](https://github.com/yz8023/ui) 的 backdrop / lens 方案，应用背景提供极光 / 系统壁纸（需「所有文件访问」授权，见 README 权限说明）/ 相册自定义图片三种来源（v15.1.6）；Hook 与原配置存储沿用 Java 实现。最低 Android 8.0（API 26）。不同系统版本和设备可能对模糊、折射等效果降级。

## 构建

需要 JDK 17、Android SDK platform 36、Gradle Wrapper 可联网下载依赖，以及原有签名密钥：

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export KEYSTORE_PATH=/path/to/existing.keystore
# 按实际密钥设置 KEYSTORE_PASS、KEY_ALIAS、KEY_PASS；示例 debug 密钥默认值见 build.sh/Gradle。
bash build.sh
```

产物：`app/build/outputs/apk/release/app-release.apk`（`clean` 后可直接构建；`BuildInfo` 与 `META-INF/xposed` 由 `prepareGeneratedSources` 任务产出，清单不再是生成物）。无 `KEYSTORE_PATH` 时构建拒绝打包；不会生成新 release 密钥。Android 以包名和签名证书判断能否覆盖安装，**务必先核对现有安装版本证书、备份配置**。源码压缩包不包含签名密钥。

Linux x86_64 可在阅读并接受 Android SDK 许可后执行 `ACCEPT_ANDROID_SDK_LICENSES=1 bash scripts/setup-compose-env.sh` 安装构建依赖，按脚本末尾提示设置环境变量。

## 验证

```sh
python3 scripts/check.py                     # 漂移门禁（含密钥在位）
python3 tests/check_ui_contract.py           # 玻璃/导航/入口静态契约（含底栏胶囊回归断言）
python3 tests/check_glass_surfaces.py        # 卡片通透度 + 系统壁纸取法回归断言
python3 tests/check_backend_unchanged.py     # 需要基线提交 3bb6bbd 的 Git 检出
python3 tests/test_settings_repository.py   # Kotlin 仓库契约（多观察者 / close / Flow 通道），需要 kotlinc
python3 tests/test_backup_repository.py     # Kotlin 仓库契约（JSON 格式 / 增量合并 / 异常），需要 kotlinc
python3 tests/check_apk.py              # 需 ANDROID_HOME，针对默认 release 产物
```

`tests/check_backend_unchanged.py` 需要包含基线提交 `3bb6bbd` 的 Git 检出；源码压缩包不含 Git 历史。

两个 `test_*_repository.py` 会用 `kotlinc` 把真实的 Kotlin 源码和极小的 Android 桩一起编译后在纯 JVM 上跑，
没有 `kotlinc` 时它们会打印 SKIP（可用 `KOTLINC=/path/to/kotlinc` 指定）。一次跑完全部门禁：`bash scripts/verify.sh`。

release 使用 R8 及资源裁剪，并保留模块包内所有类及成员；混淆配置和第三方库的裁剪结果仍需在 LSPosed 真机验证。

上述检查包括作用域/Manifest 一致性、Hook 文件保护、JVM 模拟配置读写、APK 元数据和签名结构；**不能代替 Android 真机、LSPosed 及目标应用的运行验收**。当前尚未在设备上验证本迁移版，不应视为已证明功能零差异的正式发布版。

## 项目结构

v15.2 第一阶段重构后，源码统一使用 AGP 默认布局（不再有 `src/` 与 `compose-ui/` 两个自定义源码根）：

- `app/src/main/java/com/yz8023/purestart/`：`Prefs`、`Targets`、Provider 与原 Hook 实现（Java，仍受基线守门保护）。
- `app/src/main/java/com/yz8023/purestart/ui/`：设置 Activity 与 Compose 页面、玻璃与导航（Kotlin）。
- `app/src/main/java/com/yz8023/purestart/repository/`：设置读写与配置备份适配层（Kotlin + Flow，不依赖 Compose，可在纯 JVM 上跑契约测试）。
- `app/src/main/AndroidManifest.xml`：版本号、组件和可见包清单的权威来源（包名权威是 `app/build.gradle.kts` 的 `namespace`）。
- `app/src/main/res/`：图标与设置页资源。
- `xposed/`：libxposed 入口与作用域元数据。
- `third_party/`：参考项目许可说明。

分层规则（`scripts/check.py` 第 5/6 项守门）：`ui` / `repository` 层不得引用 Hook 类，
`repository` 层不得依赖 Compose；UI 与 repository 层不再保留 Java 文件。
第一阶段改动清单见 `REFACTOR-v15.2-phase1.md`。

## 权限说明

模块默认不需要任何权限。仅当你主动选择「应用背景 → 系统壁纸」时，才会引导你开启
`MANAGE_EXTERNAL_STORAGE`（所有文件访问）——Android 14 起系统不再允许普通应用读取桌面壁纸
（`WallpaperManager.getDrawable` 官方限制，Google 标记 Won't Fix），这是唯一可行途径，
实现方式与 [yz8023/UI](https://github.com/yz8023/UI) 一致。不授权时功能会明确提示，
可改用「自定义图片」或「极光」背景，那两种方式完全不需要任何权限。

配置文件名与键、Provider authority 及静态作用域保留；按需求调整了 Hook 闸门及自检回传，`module.prop` 版本字段已同步 Manifest。迁移范围、构建结果与尚待验收事项见 `COMPOSE_MIGRATION.md`。
