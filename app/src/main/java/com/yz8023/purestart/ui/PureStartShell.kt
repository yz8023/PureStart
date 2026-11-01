package com.yz8023.purestart.ui

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yz8023.purestart.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yz8023.purestart.repository.SettingsRepository

/**
 * 主壳：四个横向页面 + 一条底栏。
 *
 * ## v15.2 第三阶段：底栏三层重排（跟手 / 缓速 / 放大液态）
 *
 * 上一版把玻璃胶囊画在**图标层之上**，并靠按压时放大到 139% 来表现「液态」。
 * 结果是：胶囊一旦放大就盖住邻格 2~3 个图标，再叠 4dp 模糊 + 底色染色，
 * 图标看起来就是「被遮挡 + 被糊住」；代码只能把胶囊正下方那一格 alpha 归零补救，
 * 于是那一格的 SVG 干脆消失。用户看到的就是「滑动时几个功能图标被挡住/糊掉」。
 *
 * 现在绘制顺序反过来，而且顺序本身就是契约（见 tests/check_ui_contract.py）：
 *
 *  1. **底板**（`dockContainer`）：磨砂/折射 + 承载手势区；
 *  2. **胶囊**（`dockLens`）：画在底板之上、**图标之下**，只当「液态底」，
 *     按压时只放大 6%（不再 39%），不会去挤压邻格；
 *  3. **图标层**（`DockItems`）：**最上层**，永远不会被胶囊盖住；
 *     离胶囊越近放大越多（`DragMath.proximityScale`，1.00 → 1.28），
 *     拖动过程中连续变化 —— 这就是「放大液态处理」，不再有任何淡出。
 *
 * ## 跟手（v15.1.9：绝对定位）
 *
 * - **按下即到位**：`tabAtCenter(按下点 x)` 把手指横坐标换算成「胶囊中心正好压在手指下面」的
 *   tab 坐标，按下那一帧胶囊就出现在手指下面 —— 胶囊在左边、手指按在右边时立刻跳过去，
 *   不再「留在原地跟着手指左右晃」。
 * - **拖动 1:1**：每帧都取手指的绝对横坐标重算，胶囊中心与手指像素严格重合，不累加、不漂移；
 *   触摸阈值只用来判断「这次算拖动还是轻点」。
 * - **松手吸附**：拖动走 `snapSpec`（速度前瞻），轻点走 `glideSpec` 在**半格之内**缓速归位。
 */
@Composable
fun PureStartShell(repository: SettingsRepository) {
    val context = LocalContext.current
    // v15.2 第二阶段：不再自带 SharedPreferences 监听，偏好变化由仓库统一通知。
    val revision = rememberSettingsRevision(repository)
    val apps by produceState<List<AppRecord>?>(null, context) {
        value = withContext(Dispatchers.IO) { loadApps(context.applicationContext) }
    }
    val badge = remember(apps, revision) {
        if (!repository.appBadgeEnabled()) 0
        else apps?.count { it.installed && repository.isHookEnabled(it.pkg) } ?: 0
    }
    val damping = remember(revision) { repository.damping() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    // 松手吸附：落点干脆不越界。「动效阻尼」滑杆只影响这里，下限抬到 .80。
    val snapSpec = remember(damping) {
        spring<Float>(
            dampingRatio = (0.80f + damping * 0.20f).coerceIn(0.80f, 1f),
            stiffness = 700f + 300f * damping,
            visibilityThreshold = 0.001f
        )
    }
    // v15.2 第三阶段：轻点 / 点击走这一档 —— 比吸附慢、比吸附软，
    // 观感是「滑过去」而不是「跳过去」。stiffness 700+ 的吸附用在这里就是瞬移。
    val glideSpec = remember {
        spring<Float>(dampingRatio = 0.92f, stiffness = 260f, visibilityThreshold = 0.001f)
    }

    // 胶囊位置（tab 坐标，0..3）。唯一同向约定：手指右移 = tab 增大（见 DragMath.tabDelta）。
    val capsuleTab = remember { Animatable(pagerState.currentPage.toFloat()) }
    // pager 静止时把胶囊同步到真实页（侧滑翻页 / 首次布局走这里；点击已经在手势里处理）。
    LaunchedEffect(pagerState.settledPage) {
        capsuleTab.animateTo(pagerState.settledPage.toFloat(), snapSpec)
    }
    var dragTabPos by remember { mutableStateOf<Float?>(null) }
    val interactions = remember { List(4) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState().value }
    val dockPressing = dragTabPos != null || pressed.any { it }
    // 按压强度：驱动胶囊缩放 / 高光（参考实现 pressProgress）
    val pressProgress by androidx.compose.animation.core.animateFloatAsState(
        if (dockPressing) 1f else 0f,
        spring(dampingRatio = .65f, stiffness = 350f), label = "dockPress")

    // 页面内容自己再录一层：底栏玻璃要折射「背景 + 页面内容」两者。
    val pageBackdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val sceneBackdrop = currentSceneBackdrop()
    val dockBackdrop = if (sceneBackdrop != null) rememberCombinedBackdrop(sceneBackdrop, pageBackdrop)
                       else pageBackdrop
    // v15.2 第三阶段：不再为「图标采样副本」单独录一层（图标现在画在胶囊之上，
    // 不需要也不应该被胶囊折射），胶囊只采样「背景 + 页面内容」。
    val lensBackdrop = dockBackdrop

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

            // 胶囊 tab 坐标：拖动中用手势值，否则跟 Pager 真实位置。
            val shownTab = dragTabPos ?: capsuleTab.value

            // 手势层是**父节点**（包住底板 / 胶囊 / 图标三层），
            // 所以不管手指落在图标上还是底栏空白处，都能收到事件。
            // contentAlignment = Center：56dp 的胶囊在 64dp 的底栏里上下各留 4dp
            //（之前是 TopStart，胶囊整体偏上 4dp）。图标行 / 底板都是 matchParentSize，不受影响。
            Box(Modifier.fillMaxSize().pointerInput(rtl, slotPx) {
                if (tabPx <= 0f) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // 绝对定位（v15.1.9）：按下瞬间就把胶囊**放到手指所在的格**上，
                    // 不再「以胶囊当前位置为锚 + 手指相对位移」。后者在「胶囊在左、手指在右」
                    // 时会让胶囊留在原地只跟着手指晃动，得先攒够一格位移才挪过去。
                    var current = tabAtCenter(down.position.x, tabPx, sideInsetPx)
                    dragTabPos = current
                    var travelled = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        travelled = change.position.x - down.position.x
                        // 阈值只决定「这次算拖动还是轻点」；位置永远取手指的**绝对**横坐标，
                        // 胶囊中心因此始终压在手指下面（1:1，不漂移）。
                        if (kotlin.math.abs(travelled) > slop) dragging = true
                        current = tabAtCenter(change.position.x, tabPx, sideInsetPx)
                        dragTabPos = current
                        if (dragging) change.consume()
                    }
                    // 拖动 → 按速度前瞻吸附；轻点 → 点在哪一格就滑到哪一格。
                    val target = if (dragging) landingTab(current, tracker.calculateVelocity().x, tabPx)
                                 else tapTab(down.position.x, tabPx, sideInsetPx)
                    scope.launch {
                        // 先 snap 到手指停下的位置，再动画到落点：
                        // dragTabPos 在 snap 之后清掉，切回 capsuleTab 时不会回跳一帧。
                        capsuleTab.snapTo(current)
                        dragTabPos = null
                        capsuleTab.animateTo(target.toFloat(), if (dragging) snapSpec else glideSpec)
                    }
                    if (pagerState.currentPage != target) {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                target, animationSpec = if (dragging) snapSpec else glideSpec)
                        }
                    }
                }
            }) {
                // ① 玻璃底板：只负责磨砂/折射，不承载图标（图标在最上层）。
                //    位置不变，所以胶囊的 backdrop 采样区域照旧。
                Box(Modifier.matchParentSize().dockContainer(dockBackdrop))

                // ② 玻璃胶囊：与底板同级、后绘制，画在**图标之下**。
                //    横向位置统一由 DragMath.capsuleOffset 导出（与 DragMathTest 同一份公式）。
                //    缩放只到 106%：上一版的 139% 会越过邻格，把两三个图标压在下面。
                val capsuleX = with(LocalDensity.current) {
                    capsuleOffset(shownTab, tabPx, sideInsetPx).toDp()
                }
                Box(Modifier
                    // CenterStart：竖向居中（56dp 在 64dp 里上下各 4dp），横向仍从 x = 0 起算，
                    // 这样 offset 里的 capsuleX 就是胶囊的真实左缘（Center 会把横向也对半偏）。
                    .align(Alignment.CenterStart)
                    .offset(x = capsuleX)
                    .size(width = tabDp, height = 56.dp)
                    .graphicsLayer {
                        val scale = 1f + .06f * pressProgress
                        scaleX = scale
                        scaleY = scale
                    }
                    .dockLens(lensBackdrop, pressProgress))

                // ③ 图标层：**最上层**，永远清晰；离胶囊越近放大越多（放大液态处理）。
                Row(Modifier.fillMaxSize()) {
                    DockItems(
                        shownTab = shownTab, badge = badge, interactions = interactions,
                        onSelect = { index ->
                            // 无障碍 / 兜底入口：正常触摸由上面手势层统一处理胶囊与翻页，
                            // 这里只负责「点了就翻过去」，两者目标与动画一致，不会互相打架。
                            scope.launch {
                                capsuleTab.animateTo(index.toFloat(), glideSpec)
                                pagerState.animateScrollToPage(index, animationSpec = glideSpec)
                            }
                        })
                }
            }
        }
    }
}

/**
 * 底栏内容：图标 + 标签。
 *
 * `shownTab` 是胶囊当前的 tab 坐标（小数）：每个 tab 按距离算自己的放大系数与强调度，
 * 拖动时连续变化 —— 胶囊滑到哪，那一格就「放大」到哪，旁边两格跟着过渡放大，
 * 图标本身**始终不透明、不被胶囊覆盖**（胶囊画在这一层的下面）。
 */
@Composable
private fun RowScope.DockItems(shownTab: Float, badge: Int,
                              interactions: List<MutableInteractionSource>,
                              onSelect: (Int) -> Unit) {
    val titles = listOf("首页", "应用", "拓展", "设置")
    val icons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_apps,
        R.drawable.ic_tab_extension, R.drawable.ic_tab_settings)
    val active = nearestTab(shownTab)
    titles.forEachIndexed { index, title ->
        val click = Modifier.clickable(interactionSource = interactions[index],
            indication = null, role = Role.Tab) { onSelect(index) }
        // 距离胶囊的亲近度（0..1）与放大系数（1.00..1.28）都来自 DragMath，纯函数可测。
        val weight = proximity(shownTab, index)
        val scale = proximityScale(shownTab, index)
        val isActive = index == active
        val tint = if (isActive) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = .70f + .30f * weight)
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).then(click),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }) {
                Icon(painterResource(icons[index]), contentDescription = null,
                    modifier = Modifier.size(22.dp), tint = tint)
                if (index == 1 && badge > 0) Text(badge.toString(),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                        .padding(horizontal = 3.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Text(title, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.graphicsLayer {
                    // 标签只做很轻的跟随（1.00 → 1.06），避免文字糊成一团
                    val labelScale = 1f + .06f * weight
                    scaleX = labelScale
                    scaleY = labelScale
                },
                color = tint)
        }
    }
}
