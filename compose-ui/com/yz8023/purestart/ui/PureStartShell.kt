package com.yz8023.purestart.ui

import android.content.SharedPreferences
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yz8023.purestart.Prefs
import com.yz8023.purestart.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主壳：四个横向页面 + 一条玻璃底栏。
 *
 * v15.1.2 的两处修复：
 *  1. 页面可直接左右滑动翻页（原版只有底栏能拖）；
 *  2. 底栏拖拽的胶囊不再被底栏容器「框住」：胶囊作为独立浮层画在容器之上，
 *     按压时按模板放大到 78/56 ≈ 1.39 倍，可以真正溢出底栏边界。
 *
 * 页面位置与胶囊位置共用同一份状态：[PagerState]。页面滑动时胶囊实时跟随，
 * 底栏拖拽松手后也直接驱动 Pager，两套手势不会再互相打架。
 */
@Composable
fun PureStartShell(repository: SettingsRepository) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs.sp(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val apps by produceState<List<AppRecord>?>(null, context) {
        value = withContext(Dispatchers.IO) { loadApps(context.applicationContext) }
    }
    val badge = remember(apps, revision) {
        if (!Prefs.appBadgeEnabled(context)) 0
        else apps?.count { it.installed && repository.isHookEnabled(it.pkg) } ?: 0
    }
    val damping = remember(revision) { Prefs.damping(context) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val settleSpec = spring<Float>(dampingRatio = (0.75f + damping * 0.25f).coerceIn(0.6f, 1f), stiffness = 420f)

    var dockDragPosition by remember { mutableStateOf<Float?>(null) }
    val interactions = remember { List(4) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState().value }
    val pressedIndex = pressed.indexOfFirst { it }
    val dockPressing = dockDragPosition != null || pressedIndex >= 0
    val pressProgress by animateFloatAsState(if (dockPressing) 1f else 0f,
        spring(dampingRatio = .65f, stiffness = 350f), label = "dockPress")
    val pageBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val labelsBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val lensBackdrop = rememberCombinedBackdrop(pageBackdrop, labelsBackdrop)

    // 页面内容底部留白 = 底栏高度 + 上下间距 + 导航栏，避免内容被胶囊压住。
    val contentBottom = 70.dp + 12.dp * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        // ① 页面层：可横向滑动，同时作为玻璃采样的背景。
        Box(Modifier.matchParentSize().layerBackdrop(pageBackdrop)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                pageSpacing = 0.dp,
                key = { it }
            ) { page ->
                // 一次只组合当前页：翻页时不会重复加载应用列表。
                when (page) {
                    0 -> HomeScreen(repository, bottomInset = contentBottom)
                    1 -> AppsScreen(repository, bottomInset = contentBottom)
                    2 -> ExtensionScreen(bottomInset = contentBottom)
                    else -> SettingsScreen(repository, bottomInset = contentBottom)
                }
            }
        }

        // 底栏区域。注意：dockContainer 的 drawBackdrop 会把子内容按胶囊形状裁剪，
        // 所以玻璃胶囊必须放在容器「外面」（同级、后绘制），否则永远被底栏框住。
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(70.dp)) {
            val tabWidth = maxWidth / 4
            // 胶囊位置：Pager 的真实滚动位置（0..3 浮点）；拖动底栏时临时覆盖。
            val livePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val targetPosition = dockDragPosition ?: livePosition
            val offset by animateDpAsState(tabWidth * targetPosition,
                if (dockDragPosition != null) snap()
                else spring(dampingRatio = (0.4f + damping * 0.8f).coerceIn(.25f, 1.25f), stiffness = 360f),
                label = "tabIndicator")

            // ① 玻璃底板：只负责磨砂/折射背景 + 承载标签与点击区。
            Box(Modifier.fillMaxSize().dockContainer(pageBackdrop)
                .pointerInput(rtl) {
                    // 底栏拖拽：越过触摸阈值才算拖动（纯点击仍交给 Tab 的 clickable），
                    // 拖动时胶囊跟手，松手按位移/速度吸附到目标页。
                    val width = size.width.toFloat()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        // 按槽位记录按下点：拖动从「按下的那个槽」开始算，
                        // 手指落在胶囊外侧时胶囊也不会瞬移到手指下。
                        val basePosition = touchToPosition(down.position.x, width, rtl)
                        var position = basePosition
                        var dragging = false
                        var travelled = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val dx = change.positionChange().x
                            travelled += dx
                            if (!dragging && kotlin.math.abs(travelled) > slop) dragging = true
                            if (dragging) {
                                // 从累计位移重新计算（不累加，避免阈值前的位移丢失）。
                                val slots = slotsPerSecond(travelled, width, rtl)
                                position = (basePosition + slots).coerceIn(0f, 3f)
                                dockDragPosition = position
                                change.consume()
                            }
                        }
                        if (dragging) {
                            val speed = slotsPerSecond(velocity.calculateVelocity().x, width, rtl)
                            val target = settleDockPage(position, speed)
                            dockDragPosition = null
                            if (pagerState.currentPage != target) {
                                scope.launch { pagerState.animateScrollToPage(target, animationSpec = settleSpec) }
                            }
                        } else {
                            dockDragPosition = null
                        }
                    }
                }) {
                // ② 透明副本：只给胶囊做折射采样，让标签文字透进玻璃里。
                Row(Modifier.fillMaxSize().alpha(0f).layerBackdrop(labelsBackdrop).clearAndSetSemantics { }) {
                    DockItems(pagerState.currentPage, badge, interactions, false) { }
                }
                // ③ 点击区：铺满底栏，点击直接翻到对应页。
                Row(Modifier.fillMaxSize()) {
                    DockItems(pagerState.currentPage, badge, interactions, true) { index ->
                        scope.launch { pagerState.animateScrollToPage(index, animationSpec = settleSpec) }
                    }
                }
            }

            // ④ 玻璃胶囊：与底板同级、后绘制，按压放大（模板 78/56）时可越出底栏。
            Box(Modifier.offset(x = offset).padding(5.dp).width(tabWidth - 10.dp).fillMaxHeight()
                .graphicsLayer {
                    val scale = 1f + .39f * pressProgress
                    scaleX = scale
                    scaleY = scale
                }
                .dockLens(lensBackdrop, pressProgress))
        }
    }
}

@Composable
private fun RowScope.DockItems(tab: Int, badge: Int, interactions: List<MutableInteractionSource>,
                              interactive: Boolean, onSelect: (Int) -> Unit) {
    val titles = listOf("首页", "应用", "拓展", "设置")
    val icons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_apps, R.drawable.ic_tab_extension, R.drawable.ic_tab_settings)
    titles.forEachIndexed { index, title ->
        val click = if (interactive) Modifier.clickable(interactionSource = interactions[index], indication = null,
            role = Role.Tab) { onSelect(index) } else Modifier
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(30.dp)).then(click),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                Icon(painterResource(icons[index]), contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (index == 1 && badge > 0) Text(badge.toString(), modifier = Modifier.align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-5).dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)).padding(horizontal = 3.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
