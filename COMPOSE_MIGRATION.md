# Compose 迁移状态与验收记录

## 已实现

- 正式 `.SettingsActivity` 已切换到 Compose；旧 View UI 页面和玻璃引擎源码已移除。保留原包名、launcher alias、图标恢复 receiver 与 Provider。
- 首页、作用域应用搜索和开关、细分规则、拓展、设置、桌面图标控制、自检和备份恢复已接入新入口。配置仍使用原 `settings` / `selfcheck` 文件和原键；随后按用户要求修复了部分 Hook 闸门与自检回传，详情见 `HOOK_AUDIT.md`。
- Compose backdrop、模糊、lens、染色、高光与动态背景已接入部分组件；底部栏采样页面内容并带弹簧动画与角标。主题和若干原有玻璃参数与偏好关联。
- AGP/Gradle 更新以解决 lint 检查器兼容问题，完整 `assembleRelease` 和 lint 通过。release 签名必须显式提供密钥。

## 已执行的检查

- `bash build.sh`：通过（JDK 17、SDK 36，使用仓库现有 debug.keystore）。
- `scripts/check.py`：通过；作用域、Targets、Manifest queries 三处一致。
- `tests/check_backend_unchanged.py`：仍保护未修改的 Hook/配置文件；Main、UniversalAdHook、XiaoCanHook 按用户要求进行了修复，不包含在不变性断言中。
- 设置适配与备份读写 JVM 模拟测试：通过。APK 检查：Xposed 元数据、入口类、签名结构通过。
- 用原仓库 debug.keystore 签名，APK 证书 SHA-256：`6c82544006f036be304a14d7334f102dd186d97998718764f88e7f3e477ab3ee`。**这不证明用户已安装版本使用同一证书。**

## 未完成的验证与已知限制

- 当前没有连接 Android 设备或 LSPosed，尚未验证安装、覆盖升级、启动、权限、作用域、目标进程 Hook、自检回传、导入导出和桌面图标恢复。
- 未进行视觉、无障碍和性能真机验收；低 Android 版本的效果可能降级。现有导航与弹窗实现尚未逐项验证是否达到参考项目交互水准，不能声称完全一致。
- JVM 桩与静态一致性检查不能证明所有功能行为零变化；备份恢复还需在真实 SAF 文件提供器上验收。
- debug.keystore 仅按仓库原有构建示例用于本次构建；请先备份用户数据并核对已安装 APK 的包名与证书，签名不一致不要卸载正式版来试装。

## 底栏与体积修复

- 底栏新增水平拖动选择（含首尾钳位与 RTL 坐标），保留直接点击；单元测试覆盖坐标边界，但尚未做真机手势验收。
- 底栏参考玻璃胶囊使用独立模糊、高光与动态背景；Android 12 以下采用半透明降级，不能显示同等实时折射。
- release 启用 R8 裁剪和资源压缩；所有 `com.yz8023.purestart` 类及成员受 keep 规则保护，但仍需在真实 LSPosed 环境验证依赖裁剪后的运行行为。
- 完整 release 的打包、lint 任务已执行；同时请求的后续 debug 测试因内存不足卡住并被终止，随后单独执行的 debug 单元测试通过。

## 首页长按与底栏视觉调整

- 首页盾牌按下时缩放，长按期间显示一秒进度环和旋转光弧，完成时回弹；原来的一秒全局开关及短按提示保留。
- 底栏保留完整的透明玻璃容器，移除不透明灰色实底；选中胶囊单独记录页面和标签的 backdrop，按压/拖动时弹簧放大，并使用 lens、高光和阴影。Android 12 以下或关闭玻璃效果时采用半透明降级，不能提供相同的实时折射。
- `lintVitalRelease`、R8、`assembleRelease` 分开执行以降低内存峰值，均通过；JVM 坐标测试、后端检查和 APK 元数据/签名检查通过。没有连接设备，实际视觉、触摸与 LSPosed 行为尚未真机验收。

## Dock、卡片与桌面图标修正

- 恢复可见的玻璃 Dock 外壳；GitHub 反馈、拓展、桌面图标、自检、更新恢复卡片分组。GitHub 图标打开项目页和 issue 页；原版“获取更新”是空操作，本版保留原语义而不是虚构更新实现。
- 玻璃细则使用右侧 VectorDrawable（SVG 路径）并随展开状态旋转。
- 参考 WeKit 独立 launcher alias 与 Xposed MODULE_SETTINGS 入口，并在首页右上角添加图标开关；保留原设置页确认与 adb 恢复入口，回到应用时重新读取 alias 状态。不同框架是否显示模块设置入口仍需设备验证。参考说明见 `third_party/WeKit-REFERENCE.md`。

## v15.1.2 交互修复（底栏溢出 / 页面滑动）

- **底栏胶囊被容器框住**：根因是胶囊作为 `dockContainer`（`drawBackdrop`）的子节点，
  而 backdrop 库在测量时把子内容放进 `clip = true` 的 graphicsLayer（见
  `DrawBackdropModifier.kt` 的 `layoutLayerBlock`），因此任何放大都会被胶囊轮廓裁掉。
  现在胶囊画在容器**之外**、与底板同级后绘制，并按模板 `78/56` 放大，按压时真正溢出底栏。
- **页面左右滑动**：页面由 `when (tab)` 改为 `HorizontalPager`，与底栏共用 `PagerState`
  作为唯一位置来源（`currentPage + currentPageOffsetFraction`）。底栏拖拽松手后调用
  `animateScrollToPage`，不再维护两套位置。
- **手势细节**：底栏拖动加触摸阈值（点击不再先跳位）、速度甩动翻页、从按下槽位起算、
  RTL 取反；页面底部内边距由底栏高度 + 导航栏计算下发，避免内容被胶囊遮挡。
- **可靠性**：`UniversalAdHook` 的 `addView` 热路径闸门改为 500ms 节流；`SettingsActivity`
  释放 repository 改为空安全；`tests/check_apk.py` 不再硬编码 build-tools 版本；
  `tests/check_backend_unchanged.py` 对新增界面偏好键改为白名单；
  `tests/check_ui_contract.py` 增加「胶囊必须在容器外」的回归断言。
- 仍未在真机 / LSPosed 上验收浮动与滑动效果。

## v15.1.3 观感修复（遮挡 / 白底 / 背景来源）

- **底栏遮挡图标**：胶囊的 `onDrawSurface` 白底（.20）+ 4dp 模糊原先画在图标层之上，
  等于给图标盖了一层奶白玻璃。现在胶囊在图标层**之后**绘制（图标泡在玻璃里），
  白底降到 .12（按压 +.06）；API < 31 的降级路径从 .68 白底降到 .22（深色 .10）。
- **卡片白底**：`glassPanel` 的降级分支曾是 `surface.copy(alpha = .88f)` 实心白，
  并且 `AppsScreen` / `RuleEditor` 还铺了 `#F5F8FC` 实色底。降级分支改为极淡雾面
  （白/黑 6%~14%）+ 1dp 描边，页面实色底移除，统一走背景层。
- **玻璃细则**：默认折叠（`rememberSaveable`）。
- **背景来源**：新增 `GlassSceneRoot` 作为唯一背景层（极光 `AuroraBlobs` / 系统壁纸 /
  相册自定义图片，对齐 yz8023/UI 的三档设计），背景只画一次并作为 backdrop 共享；
  底栏玻璃采样「背景 + 页面内容」两层。新增 `WallpaperRepository`（私有目录缓存、
  长边 2048、无权限读取系统壁纸）。
- 仍未在真机验收观感与帧率。

## v15.1.4 系统壁纸与玻璃表面修正

- **系统壁纸调用失败**：`WallpaperManager.getDrawable()` 在 API 33 已被限制、API 34 起必定
  `SecurityException`（Google 标记 Won't Fix）。改为透明窗口主题 `AppTheme.Wallpaper`
  （`windowIsTranslucent` + `windowShowWallpaper`）+ 窗口 `FLAG_SHOW_WALLPAPER`，
  由系统在窗口下层合成壁纸；API ≤ 32 仍优先直接读取位图。新增读取状态上报
  （`Prefs.KEY_WALLPAPER_ERROR`），设置页直接显示原因。
- **卡片白底**：上一版只改了降级分支。真正原因是玻璃分支的
  `onDrawSurface = drawRect(surface.copy(alpha = alpha))` 在模糊之后又盖了一层实色
  （默认 .10、不透明度拉满 .45）。现压到 `.03~.16`，底栏容器单独保留 `.06~.40`。
- 新增 `tests/check_glass_surfaces.py` 防止两类问题复发，并断言背景功能不引入任何权限。
- 仍未在真机验收（尤其透明窗口在国产 ROM 上的表现）。

## v15.1.5 开关/底栏/卡片白框修正

- **磨砂模糊开关点不动**：`SettingsRepository` 只有单个 observer 槽，四个页面
  `setObserver` 互相顶掉，设置页收不到变化；且 `PreferenceSwitch` 只在首次组合时读一次
  偏好。改为 `CopyOnWriteArrayList` 多观察者（add/removeObserver、切主线程回调）+
  新增 `PrefFlows.kt`（callbackFlow 订阅每个键，写入即回读）。
- **底栏跳错位置**：旧逻辑 `basePosition + travelled/tabWidth` 把手指位移加到按下槽位上，
  按下点偏边缘时整体偏半槽。改为 Pager 语义：`startPage - travelled/tabWidth`；
  松手弹簧下限 .25 → .45、刚度 360 → 420，减少过冲。
- **卡片白框**：填充去掉后只剩 1dp 白色高光描边与阴影光晕。描边压到 `border × .45`
  （≤18%）、卡片不再投阴影、填充改为白色渐变且由新参数「卡片底色」（0–40%，默认 10%）
  唯一控制，拉到 0 即完全透明。
- 切换背景不再无条件 recreate（只有进出系统壁纸才重建）。
- 新增行为测试：多观察者全部收到通知、纯 UI 键切换也会通知。

## v15.1.6 系统壁纸 / 普通模式 / 底栏方向（对照 yz8023/UI 重做）

- **系统壁纸**：上一版走「透明窗口 + FLAG_SHOW_WALLPAPER」，多数 ROM 不透出壁纸，等于没实现。
  对照参考仓库 `素材库/网络壁纸与离线缓存/代码/SystemWallpaper.kt` 与 `实现方案.md` 重做：
  清单声明 `MANAGE_EXTERNAL_STORAGE`（+ `READ_EXTERNAL_STORAGE` maxSdk 32），读取走
  `drawable` → `getWallpaperFile(FLAG_SYSTEM)` 双通道；选择系统壁纸时若读不到，
  自动跳 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 授权页，返回后重读；
  设置页常驻授权按钮并显示具体失败原因；`ON_RESUME` 自动重读。
  已删除上一版的 `AppTheme.Wallpaper` 透明窗口主题与 `FLAG_SHOW_WALLPAPER`。
- **普通模式**：参考仓库 `VisualMode.Normal` 是 `baseTint.copy(alpha = 1f)` 的**实心**卡片
  （不采样背景）。此前本项目把「普通」做成了玻璃关掉的半透明降级，导致浅色主题永远有发白块。
  新增 `KEY_PLAIN_MODE`（设置 → 外观 → 普通模式），打开后为实心 Material 卡片。
- **底栏方向**：历史上底栏用「手指右移 = 槽位 +1」，页面用「手指左移 = 下一页」，方向相反。
  统一为 `DragMath.slotDelta()`：手指左移 = 槽位 +1 = 下一页，位移与速度共用同一约定；
  `DragMathTest` 新增方向一致性用例。
- 仍未在真机验收。

## v15.1.7 底栏三层重做（照 `LiquidBottomTabs` 逐行对照）

用户反馈两件事：**普通模式与玻璃模式的 dock 完全没区别**、**胶囊方向与实际滑动相反且把图标盖住/糊住**。
逐行对照 `android/.../ui/components/LiquidBottomTabs.kt` 后，确认是**几何模型**而非数值问题：

- **方向**：参考实现只有一条约定 ——
  `onDrag { updateValue(targetValue + dragAmount.x / tabWidth) }` 与
  `translationX = value * tabWidthPx`，即「手指右移 = tab 变大 = 胶囊右移」，一槽宽 = 1 个 tab。
  v15.1.2~15.1.5 用了 `travelled / tabWidth` 却没定正负；v15.1.6 为了「和页面一致」
  改成「手指左移 = 槽位 +1」，但胶囊仍按 `槽位 × 槽宽` 从左边算起 —— 于是**手指左移、胶囊右移**。
  根因：把「页面滚动的相对位移」硬套到「胶囊的绝对坐标」上。
  现在只保留参考实现这一套：`DragMath.tabDelta / dragTab / capsuleOffset`（全程右正），
  删除 `slotDelta / settleDockPage / slotsPerSecond`，`DragMathTest` 全部改写为同向断言。
- **胶囊盖住/糊住图标**：参考实现的按压行为是
  `onDrawSurface = { drawRect(色.copy(alpha = .10f), alpha = 1f - progress) }` ——
  **底色只有 10%，而且按压时按 1−progress 变淡**；`Highlight(width = 1.dp, alpha = pressProgress)`
  也只在按压时出现。本项目此前是「0.12~0.18 白色填充 + 0.35~0.85 硬白描边 + shadow + 4dp 模糊」，
  那就是「选中即糊、还带一圈白边」的来源。现在底色/高光/阴影全部对齐参考实现，
  并且补上参考实现的另一半：**胶囊正下方那一格 tab 淡出（alpha 0）**
  （`LocalLiquidBottomTabOriginalAlpha`），其余图标始终可见。
- **普通模式看不出区别**：以前只有卡片（`glassPanel`）走了实心分支，底栏容器与胶囊没有。
  现在 `dockContainer` 与 `dockLens` 都有 `if (appearance.plain)` 分支：
  实心 `surface` 底栏 + 阴影 + 极淡内描边，胶囊用 `primary` 淡层，不采样背景、不模糊不折射 ——
  与玻璃模式的外观差异是明确的。静态测试新增三条断言锁住这三处。
- 版本 116 / 15.1.7；`DragMathTest` 8 例通过。仍未真机验收（方向手感需实机确认）。
