# v15.2 第三阶段：体积、R8 与资源（对应《PureStart_Review_and_Plan.md》步骤 3）

> 范围：步骤 3 / Day 8-10。这一阶段的主题是「把能确定的东西钉死，把不能确定的东西量出来」。
> **本文所有体积数字都标注了来源**：`确定` = 工作区里数出来的字节数；`构建中间产物` = 本次
> 真实跑 gradle 得到的中间文件；`估计` = 没有构建成功、按经验给出的区间，**不得当作实测值引用**。

---

## 0. 一句话结论

| 项目 | 结果 |
| --- | --- |
| 启动图标 | PNG（21,313 B）→ VectorDrawable（2,985 B），**−18,328 B（确定）**，像素级 IoU = 1.0000 |
| 冗余图标 | 删掉 9 个零引用矢量（release 本来会被 `shrinkResources` 丢掉，收益主要是干净 + debug 包 + 构建输入变少） |
| R8 | `-keep class com.yz8023.purestart.** { *; }` → 只保留 5 个真正按名字加载的入口（**混淆面从「整包不可混淆」变成「整包可混淆」**） |
| 打包 | 剔除 `*.kotlin_module` / `*.version` / `*.kotlin_builtins` / `kotlin/**` / 根 `*.txt` / `DebugProbesKt.bin`；语言资源只留 `zh`、`en` |
| 门禁 | 新增 3 个（`check_release_config.py`、`check_comments_balanced.py`、`report_apk_size.py`），前两个已接入 `verify.sh` |
| 顺手修掉的真 bug | `app/build.gradle.kts` 里 KDoc 的 `app/src/main/**` 触发了 Kotlin **嵌套块注释**，整个 `android { }` 块被注释掉——**第一阶段提交后仓库根本构建不了**（见 §3） |
| APK 实测 | **没有**。本机（2 vCPU / 2 GB 无 swap）构建跑到 R8 之后被打断，未产出 APK。测量步骤见 §5 |

---

## 1. 启动图标：PNG → VectorDrawable

### 1.1 为什么不用手工描路径
旧图标是 256×256 RGBA、21 KB 的位图：蓝色圆角方（squircle）+ 白色字母 M + 对角渐变。
手写 `pathData` 要么形状不像，要么文件比 PNG 还大。所以写了 **生成器**，而不是手抄路径：

`scripts/make_launcher_vector.py`（新增，依赖 opencv/numpy/PIL）
1. 读 PNG 的 alpha 通道做轮廓提取，`approxPolyDP` 分别压缩「形状」与「字形」两条轮廓；
2. 用最小二乘拟合对角渐变端点色（得到 `#FF3070EB → #FF1549A8`）；
3. 输出 `vector` XML：视口 256×256、`android:width/height = 108dp`、两条 path（形状带
   `<aapt:attr name="android:fillColor">` 渐变，字形纯白）；
4. **`--check` 自检**：把生成的 XML 里 pathData 重新栅格化，和原 PNG 做 IoU 比对，低于 0.99 直接失败。

### 1.2 三次迭代（都是实测数字，不是估算）
| 迭代 | eps（形状/字形） | 文件大小 | IoU | 结论 |
| --- | --- | --- | --- | --- |
| 1 | 0.9 / 0.7 | 1,641 B | 0.9989 / 0.9952 | 37 px 的圆角在 xxxhdpi（432 px）下**肉眼可见折线**，退货 |
| 2 | 0.35 / 0.30 | 3,893 B | 1.0000 / 1.0000 | 形状对了，但 `%.1f` 浮点坐标把文件撑大 30% |
| 3 | 0.35 / 0.30 + 整数化 | **2,985 B** | 1.0000 / 1.0000 | 定稿 |

视觉复核：把 pathData 在 432 px（xxxhdpi 前景实际渲染尺寸）下重绘，`/home/user/launcher_preview_432.png`
留存，曲线连续无折角。

> 纪律：这个 XML **不要手改**。要改样子就改 PNG 再跑生成器，否则 `--check` 的 IoU 保证就失效了。

### 1.3 顺带删掉的 9 个零引用图标
`ic_set_bell / ic_set_drop / ic_set_home / ic_set_lang / ic_set_palette / ic_set_refresh / ic_tab_home_fill / ic_tab_extension_fill / ic_tab_settings_fill`

引用链核对方式：`R.drawable.*` 与 `@drawable/…`、`@mipmap/…` 全仓库 grep，这 9 个只出现在
`res/drawable/` 自己身上（底部 tab 的选中态已经改成 tint + 动画权重，不再需要 `_fill` 变体）。

**诚实说明**：因为 release 开了 `isShrinkResources = true`，这些资源本来就不会进 release APK，
所以这一项对**发布体积**基本没有贡献；收益是 debug 包变小、资源合并输入变少、以及
「`res/drawable` 下的每个文件都真的有用」这条规则可以被门禁守住（见 §4）。

---

## 2. R8：从「整包保留」到「只保留入口」

### 2.1 依据（先找证据，再改规则）
`Class.forName(...)` / `getDeclaredMethod(...)` 在全仓库的目标逐个看了一遍：全部是**框架类**
（`android.*`、`de.robv.android.xposed.*`）或**目标 App 的类**（小蚕、网易云等），
**没有任何一处按名字反射自己的类**。所以整包 keep 是不必要的。

唯一的例外是 `Main`：它不在我们进程里被加载，而是被 `xposed/java_init.list` 以字符串指出、
由 libxposed 在**目标进程**里按名字加载；而 `libs/api.jar` 是 `compileOnly`，R8 无法从字节码推断
这个引用——不 keep 就会被重命名，然后模块静默不生效。这类“看不见的入口”必须写在规则里。

### 2.2 新规则（`app/proguard-rules.pro`）
- `-keep` 仅：`Main`（java_init.list 入口）、`SettingsActivity`、`HookStateProvider`、`IconControlReceiver`（清单组件）、`BuildInfo`；
- `-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisible/InvisibleAnnotations,AnnotationDefault,SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile`：Compose/协程都要读注解与签名，行号保留才能让 LSPosed 日志里的崩溃栈可读；
- `-dontwarn io.github.libxposed.api.**`：`compileOnly` 依赖在 R8 眼里是“缺类”，不 `-dontwarn` 直接失败；
- `-dontoptimize`：低内存构建机上优化阶段最费时费内存（作者的 `gradle.properties` 已经按 820 MB 堆调过），
  这是**可调项**——换到内存充足的机器上删掉这一行、对比 APK 体积后再决定。

### 2.3 与门禁的联动
`check_apk.py` 原来按类名断言 `Prefs`/`Targets`/`DexFinder`/`UniversalAdHook`/`XiaoCanHook`/`HookGate`
都在 dex 里——**收窄 keep 之后这些断言必然失败**（它们会被重命名）。已改成两级断言：

1. 必须按原名存在的入口：`Main`、`SettingsActivity`、`HookStateProvider`、`IconControlReceiver`、`BuildInfo`；
2. 功能标记字符串（混淆不改字符串字面量）：`hook_`、`sub_splash_`、`set_liquid_blur`、`plain_mode`、
   `com.realtech.xiaocan`、`com.netease.cloudmusic`、`com.yz8023.purestart.hookstate`。

这样即使内部类全被重命名，也能证明「R8 没把功能削掉」。

---

## 3. 顺手修掉的一个真 bug（这阶段最重要的产出）

**症状**：`./gradlew :app:assembleRelease` 直接失败：
```
Android Gradle Plugin: project ':app' does not specify `compileSdk` in build.gradle.kts
```
但文件里明明写着 `compileSdk = 36`。

**根因**：第一阶段我给 `app/build.gradle.kts` 写的 KDoc 里有这句话——
`` `sourceSets` … 全部落在 AGP 默认布局 `app/src/main/**` 下 ``。
`main/**` 里的**斜杠紧跟星号**，在 Kotlin 里会开启一个**嵌套块注释**（Kotlin 的块注释可嵌套，
Java 不可）。KDoc 结尾的 `*/` 只关掉了内层，外层注释一路吞到文件末尾——
**整个 `android { }`、`dependencies { }`、`packageRelease` 签名检查全部变成了注释**。
编译器不报错（它们本来就是注释），AGP 只能报“没有声明 compileSdk”，错误信息指向文件却不说位置。

也就是说：**第一阶段的提交（`1c3d4ed`）之后这个仓库是构建不出来的**，而当时的门禁全绿——
因为没有一个门禁真的去“编译构建脚本”。这是三阶段工作里唯一一个「门禁齐全但产物是坏的」的例子。

**修复**：把注释里的 `main/**` 改成 `app/src/main`，并把这条经验写进注释本身。

**防复发**：新增 `tests/check_comments_balanced.py`
- Kotlin（`.kt/.kts`）：块注释里再出现「斜杠+星号」⇒ 直接失败并给出行号；文件结束必须回到 0 层；
- Java（`.java`）：块注释不嵌套，只查是否闭合；
- 字符串、字符字面量、Kotlin 原始字符串 `"""` 都不参与判断（否则 `"/META-INF/*.x"` 这类正常写法会误报）。

这条门禁第一次跑就抓到了我自己刚写进修复注释里的两处同样的序列（说明它确实有效）。

---

## 4. 新增门禁

| 文件 | 作用 | 接入 |
| --- | --- | --- |
| `tests/check_release_config.py` | ① 禁止退回整包 keep，且 5 个入口 keep 必须在；② 源码里一旦出现按名字反射自己的类就失败（缩窄 keep 的前提被破坏）；③ `isMinifyEnabled/isShrinkResources` 必须为 true，packaging 必须剔除构建期元数据，必须有 `resourceConfigurations`；④ `res/` 下不得有位图，`res/drawable` 下不得有零引用文件；⑤ 启动图标必须是完整自适应图标（vector + background/foreground + 清单引用） | `verify.sh` |
| `tests/check_comments_balanced.py` | 见 §3 | `verify.sh` |
| `tests/report_apk_size.py` | 不需要 ANDROID_HOME 的 APK/AAB 体积拆解：按 dex / resources.arsc / res / META-INF / lib / assets 分类、未压缩 Top15、“不该出现”清单（kotlin_module、DebugProbesKt、语言数 > 2）、两个 APK 的差值对比 | 手动（构建后跑） |

`check_release_config.py` 的自检输出：
```
PASS: release config keeps only real entry points, drops bitmap resources, and prunes build metadata
```

---

## 5. 体积账：哪些是确定的，哪些还没测

### 5.1 确定的部分（工作区字节数）
- 图标位图 → 矢量：**−18,328 B 原始字节**（21,313 → 2,985）。
  注意这是**原始字节**：PNG 本身已压缩，XML 在 APK 里还会再压，所以 **APK 内的实际节省明显小于 18 KB**，
  经验区间 5–10 KB，需要实测。
- 9 个零引用矢量：0（release 本来就被裁掉）。
- `DebugProbesKt.bin`、各 `*.kotlin_module`：单个几 KB、几十个文件，合计**估计 10–40 KB**，未实测。

### 5.2 本次构建能证实的部分（构建中间产物，不是 APK）
在本机（2 vCPU / 2 GB / 无 swap）装好 JDK17 + SDK 35/36 后跑 `:app:assembleRelease`：

| 阶段 | 结果 |
| --- | --- |
| 清单合并、资源合并、Kotlin 编译（Compose UI + repository） | ✅ 通过（在修好 §3 的注释 bug 之后） |
| R8（`minifyReleaseWithR8`） | ✅ 通过，产出 `classes.dex` = **3,896,804 B（未压缩）** |
| 资源裁剪（`shrinkResources`） | 开始执行 |
| 打包 / 签名 / 产出 APK | ❌ **未完成**：R8 之后内存与 CPU 被打满（JVM RSS 1.5 GB 撞 2 GB 上限、无 swap），沙箱在 20 分钟后整体卡死，只能中止 |

**结论：R8 接受了新的 proguard 规则（没有缺类/缺属性的报错），但「体积到底降了多少」仍然没有实测。**

### 5.3 计划文档里 `2–3 MB → 1.2 MB` 这个目标的现实性
按依赖树（Compose BOM + material3 + `kyant-backdrop`）判断，1.2 MB 基本不可达：
仅 Compose UI 的 dex 就在 1.5 MB 量级。**建议把目标改成「相对 v15.1.7 下降 ≥ 15%，且不超过 3 MB」**，
用 `report_apk_size.py 新.apk 旧.apk` 的差值来验收，而不是拍一个绝对值。

---

## 6. 复现测量（在有内存的机器上，三条命令）

```bash
# 1) release 构建（脚本内部会先跑 scripts/check.py，再 gradlew assembleRelease）
KEYSTORE_PATH=debug.keystore bash build.sh

# 2) 拆解 + 与上一版对比
python3 tests/report_apk_size.py app/build/outputs/apk/release/app-release.apk  旧版.apk

# 3) APK 结构门禁（需要 ANDROID_HOME）
ANDROID_HOME=~/Android/Sdk python3 tests/check_apk.py app/build/outputs/apk/release/app-release.apk
```

`report_apk_size.py` 会直接告诉你三件事：哪一类占多少、`META-INF` 里还有没有 `*.kotlin_module`
（有就是 packaging 配置退化了）、语言资源是不是真的只剩 2 种（超过 2 种就是 `resourceConfigurations` 失效）。
如需按 entry 逐条对比：`unzip -l 新.apk | sort -k1 -n` 与旧包同一命令的输出 diff 即可。

---

## 7. 未做 / 延后（附理由，避免以后当成遗漏）

| 事项 | 状态 | 理由 |
| --- | --- | --- |
| 出 AAB 让商店按设备下发 | 延后 | 这是 LSPosed 模块，分发方式是 APK 直装（`META-INF/xposed` 在 AAB 拆分交付下反而麻烦），收益为 0 |
| 按 ABI 拆 native 库 | 不适用 | 依赖树里没有 native 库（`lib/` 目录在 APK 里会是空的） |
| 进一步裁语言（只留 `zh`） | 待设备确认 | 现在留 `zh`+`en`：本仓库文案只有中文，但 AndroidX 组件自带的行内文案在 `en` 下更完整；先保证不出现空白界面 |
| 去掉 `-dontoptimize` | 待测 | 需要一台内存 ≥ 4 GB 的机器对比“优化开/关”的 APK 体积与构建时间 |
| 图片资源改 WebP / 位图清理 | 无对象 | `res/` 下已经没有位图（`check_release_config.py` 会守住这条） |
| 真机验证（玻璃四档、Dock 拖拽、桌面图标开关、自检） | 未做 | 沙箱无设备/模拟器；三阶段所有 UI 改动都只到静态门禁 + JVM 契约层 |

---

## 8. 计划文档勾选状态（步骤 3）

| 计划条目 | 状态 | 说明 |
| --- | --- | --- |
| 删除冗余 PNG / 矢量 | ✅ | 位图清零（PNG→矢量），9 个零引用矢量删除，`check_release_config.py` 守住 |
| 收紧 `proguard-rules.pro` | ✅ | 整包 keep → 5 个入口；`check_apk.py` 改为「入口 + 行为标记」两级断言 |
| 跑 `gradlew assembleRelease` | ⚠️ 部分 | 依赖安装、清单/资源合并、Kotlin 编译、R8 均通过；打包阶段因 2 GB 内存中止 |
| 对比 APK 体积 | ❌ 未完成 | 没产出 APK；工具（`report_apk_size.py`）与命令已就位，见 §6 |
| APK ≤ 1.2 MB | ❌ 不建议作为目标 | 见 §5.3，建议改为“相对 v15.1.7 下降 ≥ 15%” |
