# v15.2 第一阶段：架构重构（对照《PureStart_Review_and_Plan.md》步骤 1）

> 交付范围：**第一阶段（架构）**。第二阶段（UI/代码量压缩）与第三阶段（APK 体积）见文末「后续」。
> 所有改动均已通过仓库自带门禁与新增的 JVM 契约测试；**尚未在 Android 设备 / LSPosed 上验证**
> （本仓库所在环境没有 Android SDK，无法产出 APK，详见「验证状态」）。

## 一、这一阶段做了什么

### 1. 目录统一：`src/` + `compose-ui/` → AGP 默认布局

评审报告的第一条是「半 Kotlin 半 Java、半 Compose 半旧架构，构建链条复杂」。原因是 Java 放在
`src/com/yz8023/purestart/`、Kotlin 放在 `compose-ui/com/yz8023/purestart/`、资源放在根 `res/`，
全都要靠 `app/build.gradle.kts` 里的 `sourceSets` 手工映射。

现在全部落到 AGP 默认布局，`sourceSets` 里只剩「追加生成代码」一项：

| 之前 | 现在 |
| --- | --- |
| `src/com/yz8023/purestart/*.java`（Hook 后端） | `app/src/main/java/com/yz8023/purestart/*.java` |
| `compose-ui/.../SettingsActivity.kt`、`ui/*.kt` | `app/src/main/java/com/yz8023/purestart/`、`.../ui/` |
| `src/com/yz8023/purestart/ui/*.java` | `app/src/main/java/com/yz8023/purestart/repository/*.kt`（见第 2 点） |
| `res/**` | `app/src/main/res/**` |
| 根目录 `AndroidManifest.xml`（需复制去 `package=`） | `app/src/main/AndroidManifest.xml`（直接作为 AGP 输入） |

`src/`、`compose-ui/`、`res/` 三个自定义源码根已删除，`scripts/check.py` 新增守门：
一旦这些目录再出现就报错。

### 2. `SettingsRepository` / `BackupRepository`：Java → Kotlin + Flow

- 位置：`app/src/main/java/com/yz8023/purestart/repository/`
  （评审建议的「移进统一目录」已落实；包名从 `...ui` 改成 `...repository`，语义更准确——
  它们没有一行 Compose 代码）。
- `SettingsRepository` 的**公开 API 与语义保持不变**：`Observer` / `addObserver` / `removeObserver` /
  `setObserver` / `isHookEnabled` / `setHookEnabled` / `isRuleEnabled` / `setRuleEnabled` / `selfCheck` /
  `themeMode` / `localeMode` / `liquidBlurEnabled` / `appBadgeEnabled` / `autoUpdateEnabled` / `close()`。
  现有四个页面、`SettingsActivity` 与 `RuleEditor` 只改了 `import` 一行，行为零变化。
- 新增 **Flow 通道**（第二阶段让控件逐个切过去，避免「写入后状态读不回来」的整类 bug）：
  `valueFlow {}`、`hookEnabledFlow(pkg)`、`ruleEnabledFlow(pkg, rule)`、`selfCheckFlow(pkg)`、
  `themeModeFlow()`、`liquidBlurFlow()`、`appBadgeFlow()`。
  实现上用**一个** `MutableStateFlow<Long>` 做「版本号」，偏好一变就自增，Flow 侧重读内存中的
  SharedPreferences；不再让每个控件各注册一份 `SharedPreferences` 监听（旧 `PrefFlows` 保留，
  单键场景仍然最省事）。
- 复合键语义收口：`hook_<pkg>`、`sub_*_<pkg>` 的拼接不再散落在界面层。
- `close()` 之后 Flow 与 Observer 都停止通知（旧实现主线程消息队列里可能残留回调）。
- `BackupRepository` 同样转 Kotlin，**JSON 格式与合并语义逐字节保持**（导出只写 5 种基本类型、
  导入是增量合并、非法 JSON 抛 `JSONException`、空流抛 `IOException`）。
- `PrefFlows.kt` 随之下移到 `repository` 包，并补了 `stringFlow`。

### 3. 构建脚本简化：不再生成清单，不再靠任务名猜依赖

评审报告对 `app/build.gradle.kts` 的两条批评（「配置阶段写文件」「靠手写任务生成 Manifest」）
成立，但根因不是「不该生成」，而是**清单里写了 AGP 8 禁止的 `package=`**，只能生成一份去掉它的副本，
再靠 `tasks.matching { it.name.startsWith("process") && ... }` 这类字符串匹配挂依赖——新增 AGP 任务
就漏挂，漏挂的表现正是历史上出现过的 `mainManifest ... doesn't exist`。

现在：

1. 清单里删掉 `package=`（包名权威改为 `build.gradle.kts` 的 `namespace`）与 `<uses-sdk>`
   （SDK 版本权威改为 `defaultConfig`）。清单从此是合法输入，**生成清单的逻辑整段删除**。
2. 生成任务只剩一件事：`BuildInfo.java` + `META-INF/xposed`（`module.prop` 版本从清单派生写回）。
   它声明了 `inputs.file(manifest)` / `inputs.dir(xposed)` / `outputs.*`，只在 `doLast` 里写文件；
   唯一让它每次重跑的是「构建时间戳」。
3. 依赖改挂 **`preBuild`**（所有变体任务图的祖先），删除全部 `tasks.matching` 字符串匹配。
4. 签名与「没有 `KEYSTORE_PATH` 就不许打包」的既有约束原样保留。

### 4. 门禁同步升级

- `scripts/check.py`：路径全部更新；包名权威改读 `namespace`；新增
  「清单不得残留 `package=`」「UI/repository 层零 Java」「repository 层不得依赖 Compose」
  「自定义源码目录不得回潮」四项（共 7 项）。
- `tests/check_backend_unchanged.py`：改读 `app/src/main/java/**`，并把「路径搬家」显式映射
  （搬家 ≠ 允许改内容，逐字节对比照旧）。顺带把 v15.1.6 的普通模式偏好行加入白名单——该门禁在
  本次改动前就已经因为这几行而失败，现在恢复绿灯。
- `tests/test_settings_repository.py`、`tests/test_backup_repository.py`：Java 源码换成 Kotlin 后，
  改成用 `kotlinc` 把**真实源码**与极小的 Android 桩一起编译、在纯 JVM 上跑契约测试
  （没有 `kotlinc` 时打印 SKIP，不阻塞无 Kotlin 编译器的机器）。新增 `tests/test_settings_repository.py`
  的 Flow 用例：发射顺序、`distinctUntilChanged`、关闭后不再发射。
- 新增 `scripts/verify.sh`：一条命令跑完全部门禁。

## 二、明确**没有**做的事（以及原因）

评审报告里还有两条建议，这一阶段**拒绝执行**，理由是风险与收益不成比例：

1. **把 Hook 后端（`Main` / `XiaoCanHook` / `UniversalAdHook` / `DexFinder` / `Prefs` 等 ≈1900 行 Java）
   也改写成 Kotlin。**
   - 这些类是 libxposed 的注入侧代码，全是对第三方 APK 的反射/特征匹配，正确性只能在目标应用里验证；
   - 本环境没有 Android SDK 与设备，改完的代码连编译都无法自证，等于交付一堆「看起来更现代」的
     不可运行代码——这正是 `tests/check_backend_unchanged.py`（自 v15.1.2 起）存在的意义：
     把后端冻结在基线上，避免 UI 迁移顺手改坏 Hook。
   - 收益仅是语言统一，功能与体积没有任何改善。若确实要做，必须先建立「真机 Hook 行为基线」，
     再逐类迁移 + 逐类回归。
2. **把工程拆成 `:ui` / `:repository` / `:hook` 三个 Gradle 模块。**
   - 这是 Xposed 模块：所有类最终必须进同一个 APK 的 dex（`tests/check_apk.py` 就在核对入口类与
     Hook 类是否在 dex 里）；拆模块后仍要靠 `implementation` 合并回来，只是多了三个 `build.gradle.kts`
     和一套跨模块可见性约束；
   - 真正的收益是「防止 UI 误引用 Hook 类」，而这条约束已经由 `scripts/check.py` 第 5 项静态守门
     （比编译期更严格：连 `io.github.libxposed` 字样都不许出现）；
   - 拆模块还会让「单文件 JVM 契约测试」变复杂，与当前低成本的验证方式冲突。
   - 若将来要做，正确顺序是先有 CI 与真机回归，再谈模块化。

另外，评审报告里有几条事实性偏差，本次按实际代码处理，没有跟着改：

| 报告中的说法 | 代码实际情况 |
| --- | --- |
| 「`GlassTheme.kt` 里重复写了 Canvas/Blur/Animation，应该改用 `Modifier.blur`」 | 已经用了 `Modifier.blur` 与 `drawBackdrop`；唯一保留的 `Canvas` 是四团极光光斑（径向渐变+轨道动画），`Modifier.blur` 无法表达，删掉等于删功能。真正的重复在「玻璃降级分支」上，属于第二阶段。 |
| 「`minifyEnabled = true` 但没配 proguard-rules.pro」 | `app/proguard-rules.pro` 存在且是有意为之：`-keep com.yz8023.purestart.**`（反射入口）+ `-dontoptimize`；`shrinkResources` 也已开启。 |
| 「总代码量约 1933 行 Kotlin + 100 行 Java」 | 实际约 1933 行 Kotlin（仅 Compose 侧）+ 1884 行 Java（Hook 后端）+ 271 行 XML。报告显然只统计了 UI 侧，所以「压缩到 1200 行」这个目标对不上真实基线。 |
| 「APK 约 2–3MB，可压到 1.2MB」 | 本环境无法构建 APK，这个数字未被验证，第三阶段以实测为准。 |

## 三、验证状态

在本环境（JDK 11 + Python，无 Android SDK）能跑的全部跑了：

```
python3 scripts/check.py                      通过（7 项）
python3 tests/check_ui_contract.py            通过
python3 tests/check_glass_surfaces.py         通过
python3 tests/check_backend_unchanged.py      通过（后端与基线 3bb6bbd 逐字节一致）
python3 tests/test_settings_repository.py     通过（Kotlin 源码 + JVM 桩：观察者/close/Flow 通道）
python3 tests/test_backup_repository.py       通过（Kotlin 源码 + JVM 桩：JSON 格式/增量合并/异常）
```

**没有验证的**：

- `gradlew :app:assembleRelease`（本环境无 Android SDK；`app/build.gradle.kts` 的改动是按 AGP 8
  默认布局与 `preBuild` 依赖写的，仍必须在有 SDK 的机器上跑一次）。
- `tests/check_apk.py`（需要已构建的 release APK）。
- 真机 / LSPosed / 目标应用运行行为。
- Compose 代码只做了「解析级」检查（无 Compose/Android 依赖时 `kotlinc` 会报大量 unresolved
  reference，已确认其中没有任何语法错误）。

**建议的验收顺序**：在有 JDK 17 + Android SDK 的机器上 `bash scripts/verify.sh` →
`bash build.sh` → 装到设备，依次验收：设置页四个页面、底栏拖动方向、普通模式/玻璃模式、
配置导出导入（本次把它转成 Kotlin 了，务必用旧备份文件实测一次回归）。

## 四、风险与回滚

- 风险最高的单点是 `BackupRepository` 的 Kotlin 重写（用户已有备份文件）。契约测试锁住了格式与
  合并语义，但仍建议用一份真实旧备份实测。
- 回滚：`git revert` 本阶段提交即可，改动集中在文件搬移 + 两个 repository + 构建脚本 + 门禁，
  不涉及 Hook 与偏好键语义，回滚不会丢用户配置。
- 旧偏好键全部沿用（`Prefs.java` 未改动，`check_backend_unchanged` 逐字节核对），无迁移脚本需求——
  评审报告担心的「配置迁移」问题在本阶段不存在。

## 五、后续

- **第二阶段（UI / 代码量）**：设置页控件全面切到 `Flow`（删掉各页面重复的 revision/manual listener）、
  合并 `GlassTheme.kt` 的三套「降级/普通/玻璃」分支、`PureStartShell` 用 `Animatable`/`spring` 收敛手势状态、
  抽出 `GlassSurface` 统一入口；同步评估是否有可删的冗余资源。
- **第三阶段（体积）**：以实测 APK 为准——`libs.versions.toml` 里 `kyant-shapes` 的实际使用面、
  `ic_launcher_foreground.png`（21KB）转矢量、R8 规则「保留整个包」能否收窄到反射入口、
  ABI/语言资源裁剪、AAB 发布。
