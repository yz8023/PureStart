# 致谢 · 源码项目来源

本素材库的代码与视觉语言建立在多个优秀的开源项目之上。衷心感谢所有原作者们
的无私分享。下面按功能点列出**直接参考**的项目来源。

## StudyTrack 液态玻璃主题与壁纸体验

| 项目 | 链接 | 说明 |
|---|---|---|
| **StudyTrack（学迹）· L1angK1NG 与贡献者** | https://github.com/L1angK1NG/StudyTrack | 浅色液态玻璃视觉、可调模糊/折射/染色/边框/色差参数，卡片、按钮、指标与壁纸个性化体验参考；本项目保留自己的液态玻璃底栏 |

> 截至本次适配时，上游仓库未提供明确的 `LICENSE` 文件。为尊重作者与避免未经许可复制，
> 本仓库只根据公开界面与功能描述进行独立实现，不直接复制 StudyTrack 源码；若上游后续补充许可证，
> 应继续按其条款更新署名与合规说明。

## 液态玻璃视觉语言（根基）

| 项目 | 链接 | 说明 |
|---|---|---|
| **kd64i/dyparse** | https://github.com/kd64i/dyparse | 液态玻璃 + Miuix 风格的视觉语言来源：配色、卡片圆角、设置页结构、玻璃面板质感均参考于此 |
| **Miuix · chibatching** | https://github.com/chibatching/Miuix | MIUI 风格的 Compose 组件库，控件语义与布局结构参考 |

## 玻璃渲染引擎

| 项目 | 链接 | 说明 |
|---|---|---|
| **backdrop-android · kyant0** | https://github.com/kyant0/backdrop-android | 真 backdrop 玻璃库（`io.github.kyant0:backdrop-android`）：采样、模糊、折射、高光的底层实现 |
| **shapes · kyant0** | https://github.com/kyant0/shapes | 自定义 Shape（`Capsule`）支持库 |

## 动效设计系统

| 项目 | 链接 | 说明 |
|---|---|---|
| **feitangyuan/motion-web** | https://github.com/feitangyuan/motion-web | 动效设计系统：时长 / 缓动 / 弹簧 token 的来源（`motion-tokens.md`） |
| **Framer Motion** | https://www.framer.com/motion/ | 弹簧参数 `(stiffness, damping)` 记法与预设的本源 |

## 应用图标切换

| 项目 / 文档 | 链接 | 说明 |
|---|---|---|
| Android 官方 · activity-alias | https://developer.android.com/guide/topics/manifest/activity-alias-element | alias 组件声明机制 |
| Android 官方 · PackageManager | https://developer.android.com/reference/android/content/pm/PackageManager | `setComponentEnabledSetting` 组件启用/禁用 |
| 社区 · Sitepoint 教程 | https://www.sitepoint.com/changing-your-android-apps-icon-dynamically/ | 运行时换图标的经典实现思路 |

## 应用欢迎引导界面

| 项目 | 链接 | 说明 |
|---|---|---|
| **CYQawa/YunX（云析）** | https://github.com/CYQawa/YunX | 首次启动引导页结构（品牌区 / 功能列表 / 免费卡 / 免责卡 / 开源入口 / 底部主操作），AGPL-3.0 |

## 主题色配置与切换

| 项目 | 链接 | 说明 |
|---|---|---|
| **CYQawa/YunX（云析）** | https://github.com/CYQawa/YunX | 三态外观模式（跟随系统 / 浅色 / 深色）、动态色彩开关（Android 12+）、10 色预选板 + HSV 自定义调色盘、HCT/TonalSpot 种子色生成完整颜色方案，AGPL-3.0 |
| **material-color-utilities** | https://github.com/material-foundation/material-color-utilities | `SchemeTonalSpot` + `Hct` 算法，从单一种子色生成全套 Material 3 颜色令牌（含于 `com.google.android.material:material`） |

## 桌面图标动态切换

| 项目 | 链接 | 说明 |
|---|---|---|
| **CYQawa/YunX（云析）** | https://github.com/CYQawa/YunX | activity-alias 两套图标（经典 / 新图标）切换、变体值 SharedPreferences 持久化、Compose 设置页卡片集成，AGPL-3.0 |

## O神主题与布局（F14）

| 项目 | 链接 | 说明 |
|---|---|---|
| **suqi8/OShin（O神）** | https://github.com/suqi8/OShin | 界面设计参考：Miuix 分组卡片、系统蓝、翻页主壳、悬浮胶囊底栏的公开界面参数。本仓库为**独立实现的视觉映射，未复制其 AGPL-3.0 源码**；若直接使用其本体代码须遵守 AGPL-3.0 与其 README 开源说明 |
| **Miuix · compose-miuix-ui** | https://github.com/compose-miuix-ui/miuix | OShin 界面底座，默认色板与分组列表规范出处（Apache-2.0） |

## AI 协作开发流程规范

| 来源 | 链接 | 说明 |
|---|---|---|
| **《AI 开发的大概流程》· SpicyTater（飞书文档）** | https://iaadlou6fm1.feishu.cn/wiki/GLf6w38ITiH64EkkB9pceuxynRd | 需求描述、渐进式强度（L0–L4）、接口调用与联动、双会话审计、Bug 先定位后修、Skills 沉淀等关键点来源；`docs/AI开发流程与需求描述规范.md`、`prompts/07-AI协作流程.md` 与 `AGENTS.md` 第 6 节为结合本模板教训的**提炼改写，非原文复制** |

## 其他资源

- **Material 3 / Jetpack Compose**（Google）：https://developer.android.com/jetpack/compose
  —— 主题、组件、动画的官方实现与文档。
- **Material Icons**（Apache-2.0）：内联图标矢量路径来自 androidx material icons 源码。

## 开源许可证说明

| 项目 | 许可证 |
|---|---|
| backdrop-android / shapes | Apache-2.0 |
| Miuix | Apache-2.0 |
| Android (AOSP) | Apache-2.0 |
| Material Icons | Apache-2.0 |

本素材库自己的代码（`素材库/*/代码/` 与示例应用）以 **MIT** 许可证开源，
可自由用于个人与商业项目。引用上游代码时请保留对应项目的许可证声明并署名。

再次感谢所有开源贡献者。
