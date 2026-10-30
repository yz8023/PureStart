# PureStart (ModKit Edition)

LSPosed/libxposed 去广告模块（纯 Java + Android View，无 Gradle）：悬浮底栏四页签设置界面、
小蚕惠生活净化（忠实移植 [GeniusQA/XiaoCan](https://github.com/GeniusQA/XiaoCan)）、
17 个国民级 App 的通用去广告引擎、磨砂玻璃（View 近似）界面。

## 构建

```bash
# 前置：JDK 17；Android SDK（platforms/android-35 + build-tools/34.0.0）
export ANDROID_HOME=/path/to/sdk
export JAVA_HOME=/path/to/jdk17
bash build.sh
# 产物：build/PureStart-vX.Y.Z-release.apk
```

- 版本号唯一来源是 `AndroidManifest.xml`；module.prop、代码内版本、输出文件名均由
  `build.sh` 派生（`BuildVersion.java` 自动生成）。
- 构建前自动运行 `scripts/check_consistency.py`：目标清单三处一致
  （`Main.TARGETS` / `xposed/scope.list` / `SettingsActivity.APP_DESC`）、版本一致、密钥在位。

## 签名

- 仓库自带 `debug.keystore`（历代证书一致，可直接覆盖升级）。
- 正式发布可用 `PURESTART_KEYSTORE=/path/to/keystore bash build.sh` 指定密钥。
- **密钥缺失时构建失败退出**，绝不静默生成新密钥（换签名 = 用户无法覆盖升级）。

## 权限取舍

| 权限 | 状态 | 理由 |
|---|---|---|
| `<queries>` 17 目标包 | 使用 | 精确可见性，替代全量枚举 |
| ~~`QUERY_ALL_PACKAGES`~~ | 已移除 | 设置页只逐包查询 17 个目标，无需全量 |
| ~~`INTERNET`~~ | 已移除 | 模块自身无网络请求（反馈按钮走系统浏览器） |
| `HookStateProvider` exported | 保留 | 目标进程读取开关的跨进程通道（FPA/LSPatch/LSPosed 通用）；无鉴权，理论上可被他 App 读开关/伪造自检上报，敏感度低，属有意取舍 |
| `forceQueryable` | 保留 | 保证目标进程在各类框架下都能 resolve 到本模块 |

## 已知决定与待办

- **包名保持 `com.example.l`**：脚手架占位包名未迁移是**有意决定**——更换包名会让现有用户
  无法覆盖升级、LSPosed 模块识别断代。待做大版本断代时再议。
- 磨砂玻璃界面是 **View 近似**（命名与差异声明遵循 [yz8023/ui](https://github.com/yz8023/ui)
  `prompts/03` 路线 B）：卡片=窗口内背景位图裁剪、底栏=限频截图降分辨率模糊、
  弹窗 Android 12+ 用系统窗口背景模糊；无 Compose backdrop 折射。
- `DexFinder` 是反射特征匹配的第一层实现，未对标 DexKit 全量 dex 检索（注释已如实标注）。

## 目录速览

见 [开发指南.md](开发指南.md)（添加 Hook 的步骤、版本规矩、磨砂维护红线）。
