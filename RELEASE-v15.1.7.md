# PureStart v15.1.7

**（versionCode 116，versionName 15.1.7）**

两个反馈都定位到同一处：底栏不是「参数不对」，而是**几何模型照抄错了**。这一版把
`yz8023/UI` 的 `ui/components/LiquidBottomTabs.kt` 逐层重做了一遍。

---

## 1. 底栏拖动方向反了 —— 原因与修复

参考仓库的底栏只有**一条**方向约定：

```kotlin
// LiquidBottomTabs.kt（参考仓库原文）
onDrag = { _, dragAmount ->
    updateValue(targetValue + dragAmount.x / tabWidth)   // 手指右移 → tab 变大
}
translationX = dampedDragAnimation.value * tabWidthPx     // tab 变大 → 胶囊更靠右
```

即：**手指往哪走，胶囊就往哪走**（1:1，弹簧跟随）。它的「槽位」是绝对坐标
（0 = 最左那一格），不是「翻页增量」。

本项目前后两版都错在这里：

| 版本 | 做法 | 结果 |
|---|---|---|
| 15.1.2~15.1.5 | `travelled / tabWidth` 当槽位增量，符号没统一 | 拖动时胶囊朝反方向跑 |
| 15.1.6 | 为「和页面一致」改成 *手指左移 = 槽位 +1* | 胶囊仍按 `槽位 × 槽宽` 从左往右算 → **手指左移、胶囊右移**，还是反的 |

根因：把「页面滚动的相对位移」硬套到「胶囊的绝对坐标」上。

**v15.1.7 只保留参考实现这一套**（`DragMath.kt` 重写）：

```
tabDelta(手指位移)  = 手指像素 / 槽宽        // 右为正
dragTab(锚点, 位移) = 锚点 + tabDelta        // 拖动中
capsuleOffset(tab)  = 内缩 + tab × 槽宽      // 右为正
landingTab(位移, 速度) = 四舍五入(tab + 速度 × 50ms)
```

- 删除 `slotDelta / settleDockPage / slotsPerSecond`（就是反向的来源）；
- 锚点是**按下瞬间胶囊的位置**，不是手指位置（与参考实现 `updateValue(targetValue + …)` 一致），
  所以按下时胶囊不会瞬移，也不会整体偏半槽；
- 落点的前瞻量由速度换算成 tab，方向永远与手指一致，不会再出现「位移一个方向、落点另一个方向」；
- 弹簧参数改为参考实现的 `DampedRatio(1f) / stiffness ≈ 1000`（「动效阻尼」滑杆下限抬到 0.80），
  吸附干脆、不过冲；
- `DragMathTest` 全部改写为同向断言（8 例），其中 `legacyOppositeConventionIsGone`
  专门锁住「手指右移 → 胶囊像素必须变大」。

## 2. 选中胶囊把图标盖住 / 糊住 —— 照参考实现的三层结构

参考实现的胶囊按压行为是关键，之前完全抄反了：

```kotlin
onDrawSurface = {
    drawRect(白/黑.copy(alpha = .10f), alpha = 1f - progress)  // 底色只有 10%，按压时更淡
    drawRect(黑.copy(alpha = .03f * progress))
}
highlight = { Highlight(width = 1.dp, alpha = dampedDragAnimation.pressProgress) }  // 只有按压才有边
```

也就是**越按越透**。而本项目此前是「0.12~0.18 白色填充 + 0.35~0.85 硬白描边 + 阴影 + 4dp 模糊」——
三样叠加，图标自然被糊成一片，还带一圈白边。

这版对齐：

- 胶囊底色 10%，按压按 `1 − progress` 变淡；高光只在按压时出现；不再投影；
- 容器高光从 `alpha = .70` 的硬白边改成参考实现的 `Highlight.Default.copy(alpha = .75f)`；
- 补上参考实现的另一半：**胶囊正下方那一格 tab 淡出（alpha 0）**
  （`LocalLiquidBottomTabOriginalAlpha` 同款），其余图标**始终可见**——
  所以「图标看得见」和「选中态是玻璃」不再互相打架；
- 底栏高度 / 内缩 / 槽宽算法对齐参考实现（64dp 高、左右 4dp 内缩、`(W − 8dp) / 4`），
  胶囊 56dp、按压放大 78/56 ≈ 1.39 倍，可越出底栏边界。

## 3. 普通模式与玻璃模式「完全不变」 —— 底栏也补上实心分支

以前只有**卡片**走了普通模式的实心分支，`dockContainer` / `dockLens` 没有，
所以切普通模式时卡片变了、底栏没变（用户看到的就是「dock 完全不变」）。

现在三处都有 `if (appearance.plain)`：

| 元素 | 玻璃模式 | 普通模式（对照 VisualMode.Normal / Md3） |
|---|---|---|
| 卡片 | 磨砂 + 折射，`cardTint` 决定底色 | 实心 `surface` + 1dp 阴影 + 极淡内描边 |
| 底栏容器 | `drawBackdrop` 磨砂折射 + 柔和高光 | 实心 `surface` + 阴影 + 极淡内描边 |
| 胶囊 | 玻璃胶囊（10% 底色、按压更透） | `primary` 淡层（12%/20%）+ 淡描边，**不采样背景** |

静态测试新增三条断言（容器 / 胶囊 / 卡片各自必须有 `plain` 分支，且普通模式胶囊里不得出现
`drawBackdrop`），防止以后再次「只改一半」。

---

## 产物

| 文件 | 大小 | SHA-256 |
|---|---|---|
| `PureStart-v15.1.7-release.apk` | 1,937,286 B | `d1368cac933050175d9fb9bbd63bd610dc4e71666b71598271be5dc7a9d2659b` |
| `PureStart-v15.1.7-source.zip` | 250,180 B | `b3f58097c4c71ea952abf9c123e9020036695c58164ce9a79d9ad3d26f5dd679` |

- 签名证书与 v15.1.0+ 相同（SHA-256 `6c825440…477ab3ee`），可直接覆盖安装；
- 权限面不变：`MANAGE_EXTERNAL_STORAGE` + `READ_EXTERNAL_STORAGE(maxSdk 32)`。

## 验收清单（需真机）

1. 在底栏按住胶囊左右拖：**手指往左，胶囊就往左**，标签同步高亮，松手落在最近一格；
2. 拖动/按压时，胶囊那一格的图标淡出、其余图标清晰可见，胶囊内没有糊成一团的图标；
3. 打开「普通模式」：卡片、底栏容器、选中胶囊三处同时变成实心观感；
4. 页面左右滑动翻页，胶囊跟随方向与手指一致。

## 已知未修

- 设置页「获取更新」仍无 `onClick`；
- `HookStateProvider` exported 无鉴权（历史问题，与本次 UI 改动无关）。
