@file:JvmName("DragMath")
package com.yz8023.purestart.ui

import kotlin.math.roundToInt

/**
 * 底栏几何（纯 JVM 数学，见 DragMathTest；不依赖 Compose）。
 *
 * ## 唯一一套坐标约定（v15.1.7 起，照 yz8023/UI 的 `LiquidBottomTabs` 重写）
 *
 * 参考实现只有**一条**约定，而且它的胶囊运动与手指物理方向完全一致：
 *
 * ```kotlin
 * // LiquidBottomTabs.kt（参考仓库，原文）
 * onDrag = { _, dragAmount ->
 *     updateValue(targetValue + dragAmount.x / tabWidth)   // 手指右移 → 槽位变大
 * }
 * translationX = dampedDragAnimation.value * tabWidthPx     // 槽位变大 → 胶囊更靠右
 * ```
 *
 * 也就是说：**手指往哪走，胶囊就往哪走**（1:1，只是按弹簧跟随）。
 * 它的「槽位」是绝对坐标（0 = 最左那个 tab），不是「翻页增量」。
 *
 * ## 之前的两次错误（都可解释用户看到的现象）
 *
 * - v15.1.2~15.1.5：`travelled / tabWidth` 当成槽位增量、却没确认正负，
 *   于是拖动时胶囊朝反方向跑；
 * - v15.1.6：为了「和页面滑动一致」，把手指位移换算成「左移 = 槽位 +1」，
 *   但胶囊仍然按 `槽位 * tabWidth` 从左边算起 —— 手指左移而胶囊右移，
 *   方向还是相反。根因是把「页面滚动方向」这套相对量硬套到「胶囊绝对坐标」上。
 *
 * v15.1.7 起只保留参考实现这一套：手指像素位移 → tab 位移（右正）→ 胶囊像素位移（右正）。
 *
 * ## v15.1.9：相对位移 → **绝对定位**
 *
 * v15.1.7/15.1.8 用的是相对模型 `dragTab(锚点, 手指位移)`：胶囊在左边、手指按在右边时，
 * 胶囊**仍停在原地**，只按手指的相对位移左右晃；必须先把位移「攒」到跨格才挪过去，
 * 手指和胶囊完全对不上（用户看到的就是「在原地跟着手指晃」）。
 *
 * 现在唯一的生产模型是**绝对定位**：[tabAtCenter] 直接把「手指横坐标」换算成
 * 「胶囊中心正好落在手指上」的 tab 坐标 —— 按下即到位，之后每帧 1:1 跟随。
 * 相对模型只剩 [tabDelta] 一处，且仅用于 [landingTab] 的速度前瞻。
 */

/** 每个 tab 的宽度：总宽减去两侧内缩后四等分（参考实现：`(W - 8dp) / tabsCount`）。 */
fun tabWidth(totalWidth: Float, sideInset: Float): Float =
    if (totalWidth <= 0f) 0f else ((totalWidth - sideInset * 2f) / 4f).coerceAtLeast(0f)

/**
 * 手指像素位移 → tab 位移。**右移为正**（与手指同向，与参考实现一致）。
 * 一整个 tab 宽度的位移 = 1.0 个 tab。
 */
fun tabDelta(dragPx: Float, tabWidth: Float): Float =
    if (tabWidth <= 0f) 0f else dragPx / tabWidth

/** 手指位置（像素，从底栏左缘算）→ 该点所在的 tab 坐标（绝对，0..3）。 */
fun tabAtPosition(x: Float, tabWidth: Float, sideInset: Float = 0f): Float {
    if (tabWidth <= 0f) return 0f
    return ((x - sideInset) / tabWidth).coerceIn(0f, 4f)
}

/**
 * **绝对定位**（v15.1.9 起唯一的生产模型）：手指横坐标 → 「胶囊中心正好落在手指上」的 tab 坐标。
 *
 * 胶囊左缘 = 内缩 + tab × 槽宽 ⇒ 中心 = 内缩 + tab × 槽宽 + 槽宽/2，
 * 令「中心 = 手指」解得 `tab = (x - 内缩) / 槽宽 - 0.5`。
 *
 * 减 0.5 是关键：不减的话，手指按在某一格的左半部分时胶囊会整体偏右半格；
 * 减掉之后 [capsuleOffset] 出来的胶囊中心与手指像素**严格重合**（见 DragMathTest）。
 */
@JvmOverloads
fun tabAtCenter(x: Float, tabWidth: Float, sideInset: Float = 0f): Float {
    if (tabWidth <= 0f) return 0f
    return ((x - sideInset) / tabWidth - 0.5f).coerceIn(0f, 3f)
}

/**
 * 松手落点：把「当下 tab 坐标 + 速度前瞻」四舍五入到最近槽位。
 *
 * 前瞻量 = 速度 × [FlingLookahead]（秒），换算成 tab；等价于「按当前速度再滑 50ms」。
 * 这样轻扫（位移小但速度快）也能翻一页，而方向永远由手指方向决定，
 * 不会再出现「位移一个方向、落点另一个方向」的情况。
 */
const val FlingLookahead: Float = 0.05f

fun landingTab(tab: Float, velocityPx: Float, tabWidth: Float): Int {
    val predicted = tab + tabDelta(velocityPx * FlingLookahead, tabWidth)
    return predicted.roundToInt().coerceIn(0, 3)
}

/** 最近的整槽位（只用于高亮判断，不做落点）。 */
fun nearestTab(tab: Float): Int = tab.roundToInt().coerceIn(0, 3)

/**
 * 轻点落点：手指按下的横坐标属于哪一格（按「胶囊中心对齐手指」换算后取整）。
 *
 * 胶囊在按下瞬间就已经跳到手指位置（见 [tabAtCenter]），这个结果只用于松手后的
 * 半格内吸附与翻页。
 */
@JvmOverloads
fun tapTab(x: Float, tabWidth: Float, sideInset: Float = 0f): Int =
    nearestTab(tabAtCenter(x, tabWidth, sideInset))

/**
 * 图标放大系数的最大增量（「放大液态处理」）。
 * 胶囊正下方那一格放大到 1.28 倍，向两侧线性衰减到 1.00。
 */
const val IconMaxScaleDelta: Float = 0.28f

/**
 * 某一格与胶囊的亲近度：胶囊正下方 = 1，间隔 1.5 格及以上 = 0（拖动中连续变化）。
 *
 * 衰减窗口取 1.5 格（而不是 1 格）：胶囊停在两格之间时，左右两格同时微微放大，
 * 滑过去的过程是连续的，不会出现「这一格突然弹大、那一格突然缩回」的跳变。
 */
const val IconFalloffSlots: Float = 1.5f

fun proximity(shownTab: Float, index: Int): Float =
    (1f - kotlin.math.abs(shownTab - index.toFloat()) / IconFalloffSlots).coerceIn(0f, 1f)

/**
 * 图标放大系数：1.00（远）… 1.28（胶囊正下方）。
 *
 * 这是「滑动时图标被遮挡/模糊」的替代方案：图标画在胶囊**之上**且不透明，
 * 靠近胶囊时放大，而不是被胶囊盖住后淡出。
 */
@JvmOverloads
fun proximityScale(shownTab: Float, index: Int, maxDelta: Float = IconMaxScaleDelta): Float =
    1f + maxDelta.coerceAtLeast(0f) * proximity(shownTab, index)

/**
 * 胶囊左缘像素坐标：底栏内缩 + tab × 槽宽，右正。
 * 钳位在 [sideInset, sideInset + 3 × tabWidth]，胶囊整体不会跑出底栏。
 */
fun capsuleOffset(tab: Float, tabWidth: Float, sideInset: Float): Float {
    if (tabWidth <= 0f) return sideInset
    val t = tab.coerceIn(0f, 3f)
    return sideInset + t * tabWidth
}
