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

/** 拖动中的胶囊 tab 坐标：以按下点为锚 + 手指位移（右移 → 右移）。 */
fun dragTab(anchorTab: Float, dragPx: Float, tabWidth: Float): Float =
    (anchorTab + tabDelta(dragPx, tabWidth)).coerceIn(0f, 3f)

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
 * 胶囊左缘像素坐标：底栏内缩 + tab × 槽宽，右正。
 * 钳位在 [sideInset, sideInset + 3 × tabWidth]，胶囊整体不会跑出底栏。
 */
fun capsuleOffset(tab: Float, tabWidth: Float, sideInset: Float): Float {
    if (tabWidth <= 0f) return sideInset
    val t = tab.coerceIn(0f, 3f)
    return sideInset + t * tabWidth
}
