@file:JvmName("DragMath")
package com.yz8023.purestart.ui

import kotlin.math.roundToInt

/**
 * 底栏拖拽的纯数学（JVM 单元测试覆盖，见 DragMathTest）。
 *
 * 全部以「槽位坐标」为单位：0f = 首页，1f = 应用，2f = 拓展，3f = 设置，
 * 与像素宽度、RTL 布局方向无关；不依赖 Compose，可直接在 JVM 上验证。
 */

/**
 * 唯一的拖拽方向约定（v15.1.6 统一）：
 *
 *   手指左移（dx < 0）＝ 往「设置」方向走 ＝ 槽位 +1 ＝ 页面往左翻
 *   手指右移（dx > 0）＝ 往「首页」方向走   ＝ 槽位 -1
 *
 * 这与 HorizontalPager 完全一致（Pager 内部就是 offset - pageSize * currentPage，
 * 页面内容向左走才是下一页）。之前 15.1.2~15.1.5 底栏用的是「手指右移 = 槽位 +1」，
 * 与页面滑动方向相反，所以拖动时胶囊和页面朝相反方向跑。
 * RTL 布局下手指的物理方向仍然对应同一个视觉方向，因此只需取反一次。
 */

/** 手指位移（像素）→ 槽位位移（正数 = 往「设置」方向）。 */
fun slotDelta(dragPx: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return 0f
    val logical = if (rtl) dragPx else -dragPx
    return logical * 4f / width
}

/** 触摸 x → 槽位坐标（点按用于直接跳页；胶囊中心对准手指）。 */
fun touchToPosition(x: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return 0f
    val logicalX = if (rtl) width - x else x
    return (logicalX * 4f / width - 0.5f).coerceIn(0f, 3f)
}

/** 拖动像素增量 → 槽位坐标增量（与 [slotDelta] 同一约定，首尾钳位）。 */
fun dragBy(position: Float, delta: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return position.coerceIn(0f, 3f)
    return (position + slotDelta(delta, width, rtl)).coerceIn(0f, 3f)
}

/** 最近的整页槽位。 */
fun nearestTab(position: Float): Int = position.roundToInt().coerceIn(0, 3)

/**
 * 速度换算：像素/秒 → 槽位/秒（正数 = 往「设置」方向）。
 * 与 [slotDelta] 用同一套方向，避免「跟手方向」和「甩动翻页方向」互相拆台。
 */
fun slotsPerSecond(velocityPx: Float, width: Float, rtl: Boolean): Float =
    if (width <= 0f) 0f else slotDelta(velocityPx, width, rtl)

/**
 * 松手落点：默认吸到最近槽位；快速甩动（超过 3.5 槽/秒）再顺速度方向多走一槽。
 * [slotsPerSecond] 为正表示往「设置」方向，因此这里对应 +1。
 */
fun settleDockPage(position: Float, slotsPerSecond: Float): Int {
    val step = when {
        slotsPerSecond > 3.5f -> 1
        slotsPerSecond < -3.5f -> -1
        else -> 0
    }
    return (nearestTab(position) + step).coerceIn(0, 3)
}

// ---------------------------------------------------------------------------
// 页面横向滑动（与底栏拖拽共用同一套槽位坐标，见 DragMathTest）
// ---------------------------------------------------------------------------

/** 页面层位移（像素，向左为负）→ 精确页位：-offset / 页宽。 */
fun pageFromOffset(offset: Float, pageWidth: Float): Float {
    if (pageWidth <= 0f) return 0f
    return (-offset / pageWidth).coerceIn(0f, 3f)
}

/** 单次手势内的拖动进度（单位：页，-1..1），用于页面跟手平移。 */
fun pageDelta(drag: Float, pageWidth: Float): Float {
    if (pageWidth <= 0f) return 0f
    return (drag / pageWidth).coerceIn(-1f, 1f)
}

/**
 * 页面松手落点：位移超过 1/5 页，或速度超过 750px/s 才翻页，否则回弹；
 * 单次手势最多翻一页，避免一甩跳过好几页。
 */
fun settlePage(start: Int, drag: Float, velocity: Float, pageWidth: Float): Int {
    if (pageWidth <= 0f) return start.coerceIn(0, 3)
    val progress = if (kotlin.math.abs(velocity) > 750f) -velocity / pageWidth else -drag / pageWidth
    val limited = progress.coerceIn(-1f, 1f)
    return when {
        limited > 1f / 5f -> (start + 1).coerceIn(0, 3)
        limited < -1f / 5f -> (start - 1).coerceIn(0, 3)
        else -> start.coerceIn(0, 3)
    }
}
