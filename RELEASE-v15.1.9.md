# PureStart v15.1.9

**（versionCode 118，versionName 15.1.9）**

这一版两件事：**底栏胶囊改成绝对定位（按下就跳到手指位置）**，以及 **APK 体积砍掉 31.5%**。

---

## 1. 胶囊「在原地跟着手指晃」→ 按下即到位

v15.1.8 用的是**相对模型**：`dragTab(锚点, 手指位移)`，锚点是「按下瞬间胶囊的位置」。
胶囊停在最左边、手指按在最右边时，胶囊**不会动到手指那里**，只按手指的相对位移左右晃；
必须先把位移「攒」够半格/一格才挪过去 —— 手感就是「对不上、在原地晃」。

v15.1.9 换成**绝对定位**（`DragMath.tabAtCenter`）：

```kotlin
// 胶囊左缘 = 内缩 + tab × 槽宽  ⇒  中心 = 内缩 + tab × 槽宽 + 槽宽/2
// 令「中心 = 手指」解得：tab = (x - 内缩) / 槽宽 - 0.5
fun tabAtCenter(x: Float, tabWidth: Float, sideInset: Float): Float
```

- **按下那一帧**：`tabAtCenter(按下点 x)` 直接把胶囊放到手指下面（不再从原位置动画过去）；
- **拖动过程中**：每帧都取手指的**绝对横坐标**重算，胶囊中心与手指像素严格重合，不累加、不漂移；
- **松手**：拖动走 `snapSpec`（速度前瞻吸附），轻点走 `glideSpec` —— 但因为胶囊已经在手指下面，
  缓速动画只发生在**半格之内**，不会再出现「从左边慢慢滑到右边」的长距离滑行；
- 相对模型 `dragTab()` 已从代码里删除（`tests/check_glass_surfaces.py` 门禁新增
  「`fun dragTab(` 不得存在」的断言，防止再改回去）。

`DragMathTest` 新增 `capsuleCentersOnFinger`：对一整排手指位置逐一断言
「胶囊中心 == 手指像素」，并额外断言「手指在右半边 → 直接就是右边那一格，与胶囊原位置无关」。

## 2. APK 体积：1,786,809 B → 1,224,839 B（−31.5%）

| 项 | v15.1.8 | v15.1.9 | 变化 |
|---|---|---|---|
| APK | 1,786,809 B | **1,224,839 B** | **−561,970 B（−31.5%）** |
| dex（解压） | 3,804.4 KB | **2,147.8 KB** | −43.5% |
| dex（压缩） | 1,609.5 KB | **1,092.0 KB** | −32.2% |
| dex 类数 | 34,792 | **12,026** | −65.4% |
| META-INF | 50.5 KB | **0.8 KB** | −98% |
| 条目数 | 39 | **30** | |

三处改动：

1. **打开 R8 优化阶段**（`proguard-rules.pro` 删掉 `-dontoptimize`，加 `-allowaccessmodification`、
   `-repackageclasses 'o'`）。`-dontoptimize` 当年是为了避开低内存构建机 OOM，但它同时关掉了
   内联 / 类合并 / 常量传播 / 死方法消除 —— 这些才是 dex 体积的大头（dex 占整包 ~90%）。
   只做「收缩 + 混淆」时，R8 只能删掉完全没人引用的类，实测只能省 2 KB 量级。
2. **剔除第三方 LICENSE / NOTICE**：AGP 的 `"/*.txt"` 只匹配根目录，匹配不到
   `META-INF/androidx/**/LICENSE.txt`（5 个文件 50 KB）。现在用 `**/LICENSE.txt`、
   `**/NOTICE.txt`、`/META-INF/androidx/**` 精确剔除；
   **`META-INF/xposed/**`（模块元数据）依然保留**，`tests/check_apk.py` 会校验。
3. 资源裁剪随优化阶段一起生效：`res/` 条目 17 → 13。

> ⚠️ 若你的构建机是 1C/1G：优化阶段可能 OOM。可临时加回 `-dontoptimize`（功能完全不受影响，
> 产物回到 1.78 MB），或提高 `org.gradle.jvmargs=-Xmx`。已按 -Xmx820m 在 2 GB / 2 vCPU 上验证通过。

## 3. 校验

```
scripts/check.py                 通过
tests/check_ui_contract.py       PASS（新增 tabAtCenter 断言）
tests/check_glass_surfaces.py    PASS（新增「dragTab 必须不存在」断言）
tests/check_ui_state.py          PASS
tests/check_comments_balanced.py PASS
tests/check_release_config.py    PASS
tests/check_apk.py               PASS（模块元数据 / 入口类 / 签名结构）
:app:testReleaseUnitTest         13 tests, 0 failed
apksigner verify                 Verifies（v2，证书与 v15.1.7/15.1.8 相同）
```

## 4. 仍未做的

- **真机 / LSPosed 运行验收**。R8 打开优化阶段后类会被内联/合并，虽然入口类
  （`Main`、清单组件、`BuildInfo`）都有 keep 规则、且门禁确认仓库里没有「按名字反射自己的类」，
  但仍建议第一次装完在 LSPosed 日志里确认模块正常加载、Hook 生效。
  若要排查崩溃栈，用构建产出的 `app/build/outputs/mapping/release/mapping.txt` 还原类名。
- 手感仍需上机确认：按下是否「一按就到位」、拖动是否严格 1:1、松手吸附的长短。
