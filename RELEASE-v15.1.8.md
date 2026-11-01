# PureStart v15.1.8

**（versionCode 117，versionName 15.1.8）**

修两处底栏手感问题：**滑块不跟手 / 轻点不缓速**，以及**滑动时图标被胶囊遮挡发糊**。
两者根因不同，但都落在底栏的三层绘制顺序与手势判定上。

---

## 1. 滑块不跟手、轻点不缓速

旧实现（`PureStartShell.kt` 手势层）是三处叠加：

| 原因 | 旧行为 | v15.1.8 |
|---|---|---|
| 触摸阈值死区 | 手指移动 > `touchSlop`(≈8dp) 才开始更新胶囊，之前一动不动；越过阈值时又一次性补跳 | 阈值**只**用来判断「这次算拖动还是轻点」，位移一出现胶囊立刻跟着走 |
| 轻点动画曲线 | 轻点复用松手吸附的 `snapSpec`（stiffness 700~1000），肉眼几乎是瞬移 | 新增 `glideSpec`（stiffness 260 / damping 0.92）——缓速滑过去 |
| 落点判定 | 只靠图标 `clickable` 的命中区，胶囊动画与手指位置无关 | 新增 `DragMath.tapTab(x)`：按**手指按下点的横坐标**算出目标格，点到哪一格就滑到哪一格 |

配套细节：抬手时先 `capsuleTab.snapTo(手指当前位置)` 再动画到落点，`dragTabPos` 在 snap
之后才清空，所以「松手/抬手」这一帧不会回跳。

```kotlin
val target = if (dragging) landingTab(current, tracker.calculateVelocity().x, tabPx)
             else tapTab(down.position.x, tabPx, sideInsetPx)   // 轻点：点哪里去哪里
```

## 2. 滑动时几个功能 SVG 被遮挡 / 模糊

旧实现的绘制顺序是 **底板 → 图标层 → 玻璃胶囊**，且胶囊按压时放大 **39%**
（56dp → 78dp，横向 139%），直接盖住相邻 2~3 格；胶囊自身还叠 `blur(4dp)` + 底色染色 +
`depthEffect` 折射，被盖住的 SVG 看起来又糊又暗。原代码的补救办法是把胶囊正下方那一格
`alpha` 淡出到 0（`fadedIndex`）——等于让那个 SVG 直接消失。

v15.1.8 把顺序反过来，并把它写成回归门禁（`tests/check_ui_contract.py`）：

```
① 底板  dockContainer   —— 磨砂 / 折射 / 手势区
② 胶囊  dockLens        —— 只当「液态底」，画在图标之下
③ 图标  DockItems       —— 最上层，永远清晰
```

- **不再淡出、不再被覆盖**：`fadedIndex` / `alpha = fade` 全部删除；
- **放大液态**：每格按与胶囊的距离算放大系数（`DragMath.proximityScale`）——
  胶囊正下方 **1.28×**，相邻格约 1.09×，1.5 格外回到 1.00×；拖动过程中连续变化，
  图标随胶囊滑过而长大；
- **胶囊放大收敛到 6%**（`.39f` → `.06f`），不会再越过邻格压住别的图标；
- 标签文字只做很轻的跟随放大（1.00 → 1.06），避免文字糊成一团；
- 删掉「alpha = 0 的图标采样副本」那一层（原来为了让胶囊折射图标，现在图标在胶囊之上，
  这层既多余又白烧一帧 GPU）；
- 顺带修正：胶囊竖向居中（56dp 在 64dp 底栏里上下各 4dp，之前贴上沿、偏上 4dp）。

## 3. 门禁与测试

`tests/check_ui_contract.py` / `tests/check_glass_surfaces.py` 原先锁的是旧设计
（「必须淡出」「胶囊必须在图标之后绘制」），正是它们把糊图标的实现钉住的；本版改成新契约的
反向断言（不得淡出、必须放大、顺序 底板 < 胶囊 < 图标）。`DragMathTest` 新增两个用例：
`tapLandsOnPointedSlot`、`iconScaleGrowsNearCapsule`（含「任何输入都不会缩小 / 不会越界放大」的扫描断言）。

```
python3 scripts/check.py                 通过
python3 tests/check_ui_contract.py       PASS（含新顺序契约）
python3 tests/check_glass_surfaces.py    PASS（含「不得淡出 / 必须放大」断言）
python3 tests/check_ui_state.py          PASS
python3 tests/check_comments_balanced.py PASS
python3 tests/check_release_config.py    PASS
python3 tests/check_apk.py               PASS
:app:testReleaseUnitTest                 13 tests, 0 failed
apksigner verify                         Verifies（v2）
```

## 4. 仍未做的

真机 / LSPosed 运行验收。以下手感只能上机确认：拖动是否严格 1:1 跟手；轻点的「缓速」时长
（当前约 400~500ms 定格）；1.28× 放大在你的屏幕上是否偏大／偏小
（改 `DragMath.IconMaxScaleDelta` 一个常量即可，测试与门禁会一起跟随）。
