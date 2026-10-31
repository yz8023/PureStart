package com.yz8023.purestart.ui

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
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
import com.yz8023.purestart.repository.SettingsRepository

/**
 * 主壳：四个横向页面 + 一条底栏。
 *
 * ## v15.1.7 重写（对照 yz8023/UI 的 `LiquidBottomTabs`）
 *
 * 参考实现的底栏只有三层，顺序和职责非常固定：
 *
 *  1. **容器**：`drawBackdrop(...)` 只负责磨砂/折射「底层内容」，
 *     highlight 用 `Highlight.Default`（柔和高光），surface 染色极淡
 *     （`surfaceContainer.copy(alpha = 0.10f + opacity * 0.55f)`）；
 *  2. **图标层**：普通图标在**胶囊下面**，胶囊画上去之后图标是「泡在玻璃里」的；
 *     真正被胶囊盖住的那一个 tab，参考实现把它做成**透明**（alpha 0）——
 *     所以图标永远不会「被玻璃糊住看不见」；
 *  3. **胶囊**：`translationX = tab * tabWidth`（右正），吸附时走弹簧；
 *     按压时只提高 highlight / innerShadow / lens 的强度，
 *     **surface 染色随按压变小**（`alpha = 1f - progress`），越按越透，绝不糊图标。
 *
 * 本文件把这套结构照搬过来，并按本项目的「普通模式」补一条分支：
 * 普通模式下 `glassEnabled = false`，容器与胶囊都退回**实心/极淡描边**，
 * 底栏外观与玻璃模式明显不同（这就是参考实现的 VisualMode 与 Glass 之分）。
 *
 * 方向：手指位移 → tab 位移 → 胶囊像素，全程「右正」，左移即 v 左移。
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
    // 参考实现：valueAnimation 用 DampedRatio(1f) + stiffness 1000，落点干脆不越界。
    // 「动效阻尼」滑杆只影响这里，下限抬到 .70，避免过冲/抖动。
    val snapSpec = remember(damping) {
        spring<Float>(
            dampingRatio = (0.80f + damping * 0.20f).coerceIn(0.80f, 1f),
            stiffness = 700f + 300f * damping,
            visibilityThreshold = 0.001f
        )
    }

    // 胶囊位置（tab 坐标，0..3）。唯一同向约定：手指右移 = tab 增大（见 DragMath.tabDelta）。
    val capsuleTab = remember { Animatable(pagerState.currentPage.toFloat()) }
    // pager 静止时把胶囊同步到真实页（点击 tab / 侧滑翻页 / 首次布局都走这里）。
    LaunchedEffect(pagerState.settledPage) {
        capsuleTab.animateTo(pagerState.settledPage.toFloat(), snapSpec)
    }
    var dragTabPos by remember { mutableStateOf<Float?>(null) }
    val interactions = remember { List(4) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState().value }
    val dockPressing = dragTabPos != null || pressed.any { it }
    // 按压强度：驱动胶囊高光/缩放（参考实现 pressProgress）
    val pressProgress by androidx.compose.animation.core.animateFloatAsState(
        if (dockPressing) 1f else 0f,
        spring(dampingRatio = .65f, stiffness = 350f), label = "dockPress")

    // 页面内容自己再录一层：底栏玻璃要折射「背景 + 页面内容」两者。
    val pageBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val sceneBackdrop = currentSceneBackdrop()
    val labelsBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val dockBackdrop = if (sceneBackdrop != null) rememberCombinedBackdrop(sceneBackdrop, pageBackdrop)
                       else pageBackdrop
    val lensBackdrop = rememberCombinedBackdrop(dockBackdrop, labelsBackdrop)

    // 页面内容底部留白 = 底栏高度 + 上下间距 + 导航栏，避免内容被胶囊压住。
    val contentBottom = 64.dp + 12.dp * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassSceneRoot {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop),
            beyondViewportPageCount = 0,
            pageSpacing = 0.dp,
            key = { it }
        ) { page ->
            when (page) {
                0 -> HomeScreen(repository, bottomInset = contentBottom)
                1 -> AppsScreen(repository, bottomInset = contentBottom)
                2 -> ExtensionScreen(bottomInset = contentBottom)
                else -> SettingsScreen(repository, bottomInset = contentBottom)
            }
        }

        // 底栏区域：高度、内缩都对齐参考实现（64dp 高、左右各 4dp 内缩）。
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(64.dp)) {
            val sideInsetPx = with(LocalDensity.current) { 4.dp.toPx() }
            // 槽宽 = (总宽 - 两侧内缩) / 4，与参考实现一致
            val slotPx = with(LocalDensity.current) { maxWidth.toPx() }
            val tabPx = tabWidth(slotPx, sideInsetPx)
            val tabDp: Dp = with(LocalDensity.current) { tabPx.toDp() }
            val sideInsetDp = 4.dp

            // 胶囊 tab 坐标：拖动中用手势值，否则跟 Pager 真实滚动位置
            // （currentPage + currentPageOffsetFraction，向右翻页为正，左移为负 → 胶囊左移）。
            val shownTab = dragTabPos ?: capsuleTab.value

            // ① 玻璃底板：只负责磨砂/折射 + 承载点击区（highlight 用柔和高光，
            //    不再用 0.7 alpha 的白色硬描边——那是「白框框」的来源）。
            Box(Modifier.fillMaxSize().dockContainer(dockBackdrop)
                .pointerInput(rtl, slotPx) {
                    // 底栏拖拽：照参考实现 `updateValue(targetValue + dragAmount.x / tabWidth)`，
                    // 即**手指位移直接加到 tab 坐标上**（右移 = tab 增大 = 胶囊右移）。
                    // 之前把「页面滚动方向」的相对量套到胶囊绝对坐标上，
                    // 于是手指左移、胶囊右移——这一次以参考实现为准。
                    if (tabPx <= 0f) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // 锚点 = 按下瞬间胶囊的位置（不是手指所在位置）：手感与参考实现一致。
                        val anchor = dragTabPos ?: capsuleTab.value
                        var travelled = 0f
                        var dragging = false
                        var current = anchor
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            travelled = change.position.x - down.position.x
                            if (!dragging && kotlin.math.abs(travelled) > slop) dragging = true
                            if (dragging) {
                                current = dragTab(anchor, travelled, tabPx)
                                dragTabPos = current
                                change.consume()
                            }
                        }
                        if (dragging) {
                            val target = landingTab(current, tracker.calculateVelocity().x, tabPx)
                            dragTabPos = null
                            // 指针手势的作用域是 restricted 的，动画必须在外部 scope 里跑
                            // （参考实现同样用 animationScope.launch）。
                            scope.launch {
                                capsuleTab.snapTo(current)
                                capsuleTab.animateTo(target.toFloat(), snapSpec)
                            }
                            if (pagerState.currentPage != target) {
                                scope.launch { pagerState.animateScrollToPage(target, animationSpec = snapSpec) }
                            }
                        } else {
                            dragTabPos = null
                        }
                    }
                }) {
                // ② 图标层：图标**永远可见**；只有胶囊正下方的那个 tab 在按压/拖动时淡出
                //    （参考实现 LocalLiquidBottomTabOriginalAlpha 的同款做法），
                //    这样胶囊里透出的是背景，而不是一团糊住的图标。
                Row(Modifier.fillMaxSize()) {
                    DockItems(
                        tab = nearestTab(shownTab), badge = badge, interactions = interactions,
                        // 胶囊覆盖到哪个 tab，就把哪个 tab 的内容让给胶囊（透明），其余保持可见。
                        fadedIndex = if (pressProgress > .02f) nearestTab(shownTab) else -1,
                        onSelect = { index ->
                            scope.launch {
                                capsuleTab.animateTo(index.toFloat(), snapSpec)
                                pagerState.animateScrollToPage(index, animationSpec = snapSpec)
                            }
                        })
                }
                // ③ 给胶囊做折射采样的副本（含标签文字），alpha = 0，只进 backdrop。
                Row(Modifier.fillMaxSize().alpha(0f).layerBackdrop(labelsBackdrop).clearAndSetSemantics { }) {
                    DockItems(nearestTab(shownTab), badge, interactions, -1, interactive = false) { }
                }
            }

            // ④ 玻璃胶囊：与容器同级、后绘制，所以能越出底栏边界（按压 78/56 放大）。
            //    translationX 与手指同向；普通模式下走实心分支。
            Box(Modifier
                .offset(x = sideInsetDp + tabDp * shownTab)
                .size(width = tabDp, height = 56.dp)
                .graphicsLayer {
                    val scale = 1f + .39f * pressProgress
                    scaleX = scale
                    scaleY = scale
                }
                .dockLens(lensBackdrop, pressProgress))
        }
    }
}

/**
 * 底栏内容：图标 + 标签。
 *
 * `fadedIndex` 对应的 tab 整列淡出（alpha 0），用于让玻璃胶囊那一格干净地
 * 透出背景玻璃——参考实现就是靠这一步保证「图标不会被胶囊糊住」。
 */
@Composable
private fun RowScope.DockItems(tab: Int, badge: Int, interactions: List<MutableInteractionSource>,
                              fadedIndex: Int, interactive: Boolean = true,
                              onSelect: (Int) -> Unit) {
    val titles = listOf("首页", "应用", "拓展", "设置")
    val icons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_apps,
        R.drawable.ic_tab_extension, R.drawable.ic_tab_settings)
    titles.forEachIndexed { index, title ->
        val click = if (interactive) Modifier.clickable(interactionSource = interactions[index],
            indication = null, role = Role.Tab) { onSelect(index) } else Modifier
        val fade by androidx.compose.animation.core.animateFloatAsState(
            if (index == fadedIndex) 0f else 1f,
            androidx.compose.animation.core.tween(140), label = "dockFade$index")
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).then(click)
            .graphicsLayer { alpha = fade },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                Icon(painterResource(icons[index]), contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (tab == index) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface)
                if (index == 1 && badge > 0) Text(badge.toString(),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                        .padding(horizontal = 3.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = if (tab == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
        }
    }
}
