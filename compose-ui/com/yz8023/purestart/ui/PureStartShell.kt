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
    // 页面内容自己再录一层：底栏玻璃要折射「背景 + 页面内容」两者。
    val pageBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val sceneBackdrop = currentSceneBackdrop()
    val labelsBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val dockBackdrop = if (sceneBackdrop != null) rememberCombinedBackdrop(sceneBackdrop, pageBackdrop)
                       else pageBackdrop
    val lensBackdrop = rememberCombinedBackdrop(dockBackdrop, labelsBackdrop)

    // 页面内容底部留白 = 底栏高度 + 上下间距 + 导航栏，避免内容被胶囊压住。
    val contentBottom = 70.dp + 12.dp * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassSceneRoot {
        // ① 页面层：纯内容，背景已由 GlassSceneRoot 画好，所以页面本身保持透明可滑动。
        HorizontalPager(
            state = pagerState,
            // 录进 pageBackdrop：底栏玻璃采样「背景 + 当前页内容」。
            modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop),
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
                // 阻尼滑杆越小越「弹」：v15.1.5 把下限从 .25 抬到 .45，并限制过冲，
                // 松手回位不再甩过头（原来会明显冲过头再弹回来）。
                else spring(dampingRatio = (0.55f + damping * 0.45f).coerceIn(.45f, 1f), stiffness = 420f),
                label = "tabIndicator")

            // ① 玻璃底板：只负责磨砂/折射背景 + 承载标签与点击区。
            Box(Modifier.fillMaxSize().dockContainer(dockBackdrop)
                .pointerInput(rtl) {
                    // 底栏拖拽（v15.1.5 重写）。
                    //
                    // 旧版把「手指从按下点的位移」当成胶囊位移（basePosition + travelled），
                    // 于是按下位置在槽位中心与边缘之间时，胶囊会整体偏半槽，
                    // 松手就跳到相邻的错误页。前几版更糟：按下瞬间就把胶囊瞬移到手指位置。
                    //
                    // 新版语义与系统 Pager 一致：
                    //   胶囊目标位 = 当前页 + slotDelta(手指位移)（手指左移 = 下一页）
                    // 手指左移（负 dx）→ 槽位 +1；右移 → 槽位 -1；按下不动就不动。
                    val width = size.width.toFloat()
                    val tabWidth = width / 4f
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        val startPage = pagerState.currentPage.toFloat()
                        var travelled = 0f
                        var dragging = false
                        var position = startPage
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            travelled += change.positionChange().x
                            if (!dragging && kotlin.math.abs(travelled) > slop) dragging = true
                            if (dragging) {
                                // 统一方向约定（见 DragMath.slotDelta）：手指左移 = 槽位 +1，
                                // 与页面滑动、胶囊滑动方向一致。
                                position = (startPage + slotDelta(travelled, width, rtl)).coerceIn(0f, 3f)
                                dockDragPosition = position
                                change.consume()
                            }
                        }
                        if (dragging) {
                            // 用「胶囊当前位置」落点：位移超过半槽或甩动够快才翻页。
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
                // ③ 点击区（图标在上）：画完图标后，胶囊再以浮层身份盖上来，
                // 这样选中态是「图标泡在液体玻璃里」，而不是胶囊压住图标。
                Row(Modifier.fillMaxSize()) {
                    DockItems(pagerState.currentPage, badge, interactions, true) { index ->
                        scope.launch { pagerState.animateScrollToPage(index, animationSpec = settleSpec) }
                    }
                }
            }

            // ④ 玻璃胶囊：必须在图标层之后绘制（否则 .20 白底 + 4dp 模糊会把图标糊住），
            // 且与底板同级、不受底板裁剪，按压放大（模板 78/56）时可越出底栏。
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
