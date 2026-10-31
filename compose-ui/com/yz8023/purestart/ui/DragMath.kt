@file:JvmName("DragMath")
package com.yz8023.purestart.ui

import kotlin.math.roundToInt

/**
 * 底栏拖拽的纯数学（JVM 单元测试覆盖，见 DragMathTest）。
 *
 * 全部以「槽位坐标」为单位：0f = 首页，1f = 应用，2f = 拓展，3f = 设置，
 * 与像素宽度、RTL 布局方向无关；不依赖 Compose，可直接在 JVM 上验证。
 */

/** 触摸 x → 槽位坐标（胶囊中心对准手指，修复左缘对齐造成的半槽偏移）。 */
fun touchToPosition(x: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return 0f
    val logicalX = if (rtl) width - x else x
    return (logicalX * 4f / width - 0.5f).coerceIn(0f, 3f)
}

/** 拖动像素增量 → 槽位坐标增量（RTL 反向，首尾钳位）。 */
fun dragBy(position: Float, delta: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return position.coerceIn(0f, 3f)
    val logicalDelta = if (rtl) -delta else delta
    return (position + logicalDelta * 4f / width).coerceIn(0f, 3f)
}

/** 最近的整页槽位。 */
fun nearestTab(position: Float): Int = position.roundToInt().coerceIn(0, 3)

/**
 * 速度换算：像素/秒 → 槽位/秒（正向为正，即往「设置」页方向）。
 * RTL 下手指向右滑是后退，方向取反。
 */
fun slotsPerSecond(velocityPx: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return 0f
    val logical = if (rtl) -velocityPx else velocityPx
    return logical * 4f / width
}

/**
 * 松手落点：默认吸到最近槽位；若是快速甩动（超过 3.5 槽/秒），
 * 再顺速度方向多走一槽，保证「轻甩一下也能翻页」。
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
