package com.yz8023.purestart.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.yz8023.purestart.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 渲染参数（沿用原有偏好键与默认值）。
 *
 * `wallpaper` 对应「应用背景」：0 = 极光（内置，参考 yz8023/UI 的 AuroraBackground），
 * 1 = 系统壁纸，2 = 自定义图片（相册选择，复制到应用私有目录）。
 */
@Immutable
data class GlassAppearance(val dark: Boolean, val aurora: Boolean, val enabled: Boolean,
    val opacity: Float, val tint: Float, val blur: Int, val height: Int, val amount: Int,
    val border: Float, val vibrant: Boolean, val chromatic: Boolean,
    val wallpaper: Int = 0, val wallpaperPath: String? = null,
    val wallpaperDim: Float = .30f, val cardTint: Float = .10f, val plain: Boolean = false)

/**
 * 三档渲染（v15.2 第二阶段收敛）。
 *
 * 之前 `glassPanel` / `dockContainer` / `dockLens` 各自重复判断
 * 「普通模式？」「关了磨砂？」「API < 31？」三遍，那一句 SDK 判定原本在文件里出现 3 次——
 * 任何一处漏改就会出现「卡片是实心、底栏却还是玻璃」这种割裂。
 * 现在判定只有一处，三个表面按同一个枚举分支：
 *
 *  - [Plain]：普通模式，实心 Material 表面，完全不采样背景；
 *  - [Fallback]：无 RenderEffect（API < 31）或用户关了磨砂模糊，画极淡雾面；
 *  - [Backdrop]：真玻璃（维基色/模糊/折射/高光）。
 */
private enum class GlassTier { Plain, Fallback, Backdrop }

private val GlassAppearance.tier: GlassTier
    get() = when {
        plain -> GlassTier.Plain
        !enabled || Build.VERSION.SDK_INT < 31 -> GlassTier.Fallback
        else -> GlassTier.Backdrop
    }

private val LocalAppearance = staticCompositionLocalOf {
    GlassAppearance(false, false, true, .55f, .08f, 0, 15, 20, .20f, true, true)
}
private val LocalSceneBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 主题入口。外观参数统一从 [SettingsRepository] 读（界面层不再直接拿 SharedPreferences），
 * 变化由仓库的一条偏好回调驱动重算。
 */
@Composable
fun PureStartTheme(repository: SettingsRepository, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val revision = rememberSettingsRevision(repository)
    val appearance = remember(revision, systemDark) {
        val mode = repository.themeMode()
        GlassAppearance(
            dark = mode == 2 || mode == 3 || (mode == 0 && systemDark),
            aurora = mode == 3,
            enabled = repository.liquidBlurEnabled(),
            opacity = repository.opacity(),
            tint = repository.tintAlpha(),
            blur = repository.blurDp(),
            height = repository.prismHeightDp(),
            amount = repository.prismAmountDp(),
            border = repository.borderAlpha(),
            vibrant = repository.vibrancy(),
            chromatic = repository.chromatic(),
            wallpaper = repository.wallpaperMode(),
            wallpaperPath = repository.wallpaperPath(),
            wallpaperDim = repository.wallpaperDim(),
            cardTint = repository.cardTint(),
            plain = repository.plainMode())
    }
    val colors = if (appearance.dark) darkColorScheme(
        primary = if (appearance.aurora) Color(0xFF38BDF8) else Color(0xFF9ABEFF),
        background = if (appearance.aurora) Color(0xFF0B132B) else Color(0xFF121216),
        surface = if (appearance.aurora) Color(0xFF1E293B) else Color(0xFF232329))
    else lightColorScheme(primary = Color(0xFF312E81), background = Color(0xFFF6F7FC), surface = Color.White)
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/**
 * 背景层（玻璃采样对象）。
 *
 * v15.1.3 起三种来源统一在这里绘制：极光 / 系统壁纸 / 自定义图片。
 * v15.2 第二阶段：删掉了此处一个**永远在跑却从未被使用**的无限动画
 * （`phase` 算完就丢，返回的 Brush 里没有任何时间项）——它只白烧帧。
 */
@Composable
fun glassBackground(): Brush {
    val appearance = LocalAppearance.current
    val base = MaterialTheme.colorScheme.background
    val palette = auroraPalette(appearance.dark, appearance.aurora)
    return Brush.verticalGradient(listOf(base, base, palette[1].copy(alpha = .35f)))
}

/** 极光配色：浅色冰蓝 / 深色极光，取自参考项目 theme/Color.kt。 */
private fun auroraPalette(dark: Boolean, aurora: Boolean): List<Color> = when {
    dark && aurora -> listOf(Color(0xFF0A0F16), Color(0xFF102438), Color(0xFF1A1536), Color(0xFF0B2822))
    dark -> listOf(Color(0xFF0A0F16), Color(0xFF1B2130), Color(0xFF241B33), Color(0xFF12241F))
    else -> listOf(Color(0xFFFFFFFF), Color(0xFFB7C8FF), Color(0xFFE6ECFF), Color(0xFF8BA3FF))
}

/** 四团光斑：轨道/速度与参考项目一致，避免同时脉动。 */
@Composable
private fun BoxScope.AuroraBlobs() {
    val appearance = LocalAppearance.current
    val palette = auroraPalette(appearance.dark, appearance.aurora)
    val phase by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(28000, easing = LinearEasing)),
        label = "auroraPhase")
    Canvas(Modifier.fillMaxSize()) {
        val blobs = listOf(
            floatArrayOf(.20f, .16f, .62f, .10f, 1.0f, 0f),
            floatArrayOf(.86f, .28f, .52f, .12f, -1.35f, 0f),
            floatArrayOf(.34f, .82f, .66f, .09f, .78f, 0f),
            floatArrayOf(.92f, .88f, .48f, .08f, -.62f, 0f))
        blobs.forEachIndexed { index, b ->
            val color = palette[(index % 3) + 1]
            val x = (b[0] + cos(phase * b[4]) * b[3]) * size.width
            val y = (b[1] + sin(phase * b[4] * .8f) * b[3]) * size.height
            val radius = b[2] * size.minDimension
            drawCircle(brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = .55f), color.copy(alpha = .22f), Color.Transparent),
                center = Offset(x, y), radius = radius),
                radius = radius, center = Offset(x, y))
        }
    }
}

/**
 * 背景图片层：系统壁纸或自定义图片，带按主题的压暗/提亮遮罩。
 * 只在背景层（被玻璃采样的那一层）里调用。
 */
@Composable
private fun BoxScope.WallpaperSurface() {
    val appearance = LocalAppearance.current
    val painter = rememberWallpaperPainter(appearance.wallpaper, appearance.wallpaperPath)
    val veil = if (appearance.dark) Color.Black else Color.White
    val alpha = appearance.wallpaperDim.coerceIn(0f, .7f) * (if (appearance.dark) 1f else .55f)
    Box(Modifier.matchParentSize()) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(1.dp, BlurredEdgeTreatment.Unbounded))
        }
        Box(Modifier.matchParentSize().background(veil.copy(alpha = alpha)))
    }
}

/** 载入壁纸（IO 线程解码，失败静默回落到极光背景）。 */
@Composable
private fun rememberWallpaperPainter(mode: Int, path: String?): Painter? {
    val context = LocalContext.current
    val state = produceState<Painter?>(null, mode, path) {
        value = withContext(Dispatchers.IO) { WallpaperRepository.loadPainter(context, mode, path) }
    }
    return state.value
}

/**
 * 卡片表面。三个分支只决定「用什么材质」，几何与描边参数共用：
 *  - Plain：实心 Material 表面（参考 yz8023/UI 的 VisualMode.Normal）；
 *  - Fallback：极淡白色渐变雾面（不是实心白底，否则浅色主题下发白）；
 *  - Backdrop：玻璃（vibrancy/模糊/折射 + 1dp 柔和高光），不投阴影。
 */
@Composable
fun Modifier.glassPanel(backdrop: Backdrop, radius: Int = 22): Modifier {
    val appearance = LocalAppearance.current
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(radius.dp)
    // 描边统一压到很淡：v15.1.4 把卡片填充去掉后，用户看到的「白框框」
    // 其实就是这条 1dp 白色高光描边（以及降级分支的阴影光晕）。
    val rim = (appearance.border * .45f).coerceIn(0f, .18f)
    // 卡片底色完全由「卡片底色」这个滑杆决定（默认 10%，可拉到 0）。
    val tintAlpha = appearance.cardTint.coerceIn(0f, .40f)
    val sheen = cardSheen(appearance.dark, tintAlpha)

    return when (appearance.tier) {
        GlassTier.Plain -> solidSurface(shape, 1.dp, appearance)
        GlassTier.Fallback -> clip(shape).background(sheen)
            .border(1.dp, Color.White.copy(alpha = rim), shape)
        GlassTier.Backdrop -> drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
            if (appearance.vibrant) vibrancy()
            if (appearance.blur > 0) blur(appearance.blur.dp.toPx())
            if (appearance.height > 0 || appearance.amount > 0) lens(
                refractionHeight = appearance.height.dp.toPx(),
                refractionAmount = appearance.amount.dp.toPx(),
                chromaticAberration = appearance.chromatic)
        }, highlight = { Highlight(width = 1.dp, alpha = rim) },
            // 卡片不再投放大阴影：浅色背景上那圈灰白晕边同样会被当成「白框」。
            shadow = null,
            onDrawSurface = { if (tintAlpha > 0f) drawRect(solidTint(surface, tintAlpha)) })
    }
}

/**
 * 普通模式的实心表面：阴影 + 表面色 + 极淡描边。
 * 卡片用 1dp、底栏容器用 3dp，几何一致，之前是两段各写一遍的重复代码。
 */
@Composable
private fun Modifier.solidSurface(shape: Shape, elevation: Dp, appearance: GlassAppearance): Modifier =
    shadow(elevation, shape, clip = false)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, if (appearance.dark) Color.White.copy(alpha = .06f)
                        else Color.Black.copy(alpha = .05f), shape)

/** 卡片雾面渐变：深色主题取更低的白色比例，浅色主题直接用卡片底色。 */
private fun cardSheen(dark: Boolean, alpha: Float): Brush = if (dark) {
    Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha * .55f),
        Color.White.copy(alpha = alpha * .18f)))
} else {
    Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha),
        Color.White.copy(alpha = alpha * .30f)))
}

/** 把 sheen 平均成一个等效纯色（onDrawSurface 只接受单次 drawRect）。 */
private fun solidTint(surface: Color, alpha: Float): Color =
    if (surface.luminance() > .5f) Color.White.copy(alpha = alpha)
    else Color.White.copy(alpha = alpha * .22f)

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/**
 * 根背景层：极光或壁纸**只在这里画一次**，再作为 backdrop 交给所有页面与底栏采样。
 *
 * 这样做的原因：
 *  - 四个页面各自铺一层背景会重复解码壁纸、重复跑极光动画（内存与掉帧）；
 *  - 底栏玻璃需要同时折射「页面内容」和「背景」，共享同一份 backdrop 才能做到。
 */
@Composable
fun GlassSceneRoot(content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val appearance = LocalAppearance.current
    val background = glassBackground()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            when (appearance.wallpaper) {
                WallpaperRepository.MODE_AURORA ->
                    Box(Modifier.matchParentSize().background(background)) { AuroraBlobs() }
                WallpaperRepository.MODE_SYSTEM ->
                    // 系统壁纸：窗口透明（FLAG_SHOW_WALLPAPER），壁纸由系统画在窗口下层，
                    // 这里只叠压暗遮罩（API ≤ 32 能取到位图时也直接画出来）。
                    WallpaperSurface()
                else ->
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background)) {
                        WallpaperSurface()
                    }
            }
        }
        CompositionLocalProvider(LocalSceneBackdrop provides backdrop) { content() }
    }
}

/** 当前生效的背景采样层（底栏需要与页面内容合并采样时使用）。 */
@Composable
fun currentSceneBackdrop(): Backdrop? = LocalSceneBackdrop.current

/**
 * 页面级背景入口。
 *
 * 外层已经有 [GlassSceneRoot] 时直接复用它的背景（页面本身透明，背景只画一次）；
 * 单独使用时（例如以后要独立弹出的页面）自己搭一套，保证玻璃仍然有东西可折射。
 */
@Composable
fun GlassScene(content: @Composable BoxScope.() -> Unit) {
    // 已经有了外层背景层：包一层铺满的 Box 提供 BoxScope，页面自身保持透明。
    if (LocalSceneBackdrop.current != null) Box(Modifier.fillMaxSize(), content = content)
    else GlassSceneRoot(content)
}

@Composable
fun Modifier.sceneGlassPanel(radius: Int = 22): Modifier {
    val backdrop = LocalSceneBackdrop.current
    return if (backdrop != null) glassPanel(backdrop, radius)
           else background(Color.Transparent, RoundedCornerShape(radius.dp))
}

/**
 * 底栏容器（对照 yz8023/UI `LiquidBottomTabs` 的第 1 层）。
 *
 * 只做三件事：采样底层做磨砂/折射、给一个**柔和**高光、叠一层极淡 surface 染色。
 * 三档材质与卡片同源（见 [GlassTier]），普通模式下是实心 Material 底栏。
 */
@Composable
fun Modifier.dockContainer(backdrop: Backdrop): Modifier {
    val appearance = LocalAppearance.current
    val shape = RoundedCornerShape(50)
    val surface = MaterialTheme.colorScheme.surface

    return when (appearance.tier) {
        // 普通模式：实心 Material 底栏（对照 VisualMode.Normal / Md3 —— 实心面 + 阴影）。
        GlassTier.Plain -> solidSurface(shape, 3.dp, appearance)
        GlassTier.Fallback -> {
            val tint = if (appearance.dark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .30f)
            shadow(3.dp, shape, clip = false).clip(shape).background(tint)
                .border(1.dp, Color.White.copy(alpha = if (appearance.dark) .14f else .24f), shape)
        }
        GlassTier.Backdrop -> drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
            if (appearance.vibrant) vibrancy()
            blur(maxOf(4, appearance.blur).dp.toPx())
            lens(refractionHeight = maxOf(8, appearance.height).dp.toPx(),
                refractionAmount = maxOf(10, appearance.amount).dp.toPx(),
                chromaticAberration = appearance.chromatic)
        },
            // 参考实现的容器高光是 Highlight.Default（柔和、1dp 宽、不加 blur）——
            // 之前用的 .70 alpha 硬白边在浅色壁纸上就是那条「白框」。
            highlight = { Highlight.Default.copy(alpha = .75f) },
            shadow = { Shadow(radius = 10.dp, color = Color.Black, alpha = if (appearance.dark) .14f else .08f) },
            // 参考实现：containerColor = surfaceContainer.copy(alpha = .10 + opacity * .55)
            onDrawSurface = {
                // 取 .10 + opacity * .30（本项目 opacity 默认 .55），观感等同、更保守。
                drawRect(surface.copy(alpha = .10f + appearance.opacity.coerceIn(0f, 1f) * .30f))
            })
    }
}

/**
 * 移动的胶囊（对照 yz8023/UI `LiquidBottomTabs` 的第 3 层）。
 *
 * 参考实现的按压行为很关键：
 *  - 平时：几乎全透明，只有一点点色调，所以**图标位置的那层胶囊不会把图标糊掉**；
 *  - 按压：highlight / innerShadow / lens 增强，但 surface 染色反而按 `1f - progress`
 *    **变淡**——越按越透，图标透过玻璃看得更清楚；
 *  - 边框也只有按压时才出现（`Highlight(alpha = pressProgress)`）。
 *
 * 普通模式：不采样背景，退回一层很淡的同色面（`primary.copy(alpha ≈ .12)`）。
 */
@Composable
fun Modifier.dockLens(backdrop: Backdrop, press: Float): Modifier {
    val appearance = LocalAppearance.current
    val shape = RoundedCornerShape(50)
    val p = press.coerceIn(0f, 1f)
    val isLight = !appearance.dark

    return when (appearance.tier) {
        // 普通模式：实心主题色淡层，不做任何模糊/折射。
        GlassTier.Plain -> clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isLight) .12f else .20f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f), shape)
        // 无 RenderEffect 的降级：一层很淡的同色面 + 淡描边（绝不盖住图标）。
        GlassTier.Fallback -> clip(shape)
            .background(if (appearance.dark) Color.White.copy(alpha = .07f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = .08f))
            .border(1.dp, if (appearance.dark) Color.White.copy(alpha = .16f)
                           else MaterialTheme.colorScheme.primary.copy(alpha = .16f), shape)
        GlassTier.Backdrop -> drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
            if (appearance.vibrant) vibrancy()
            blur(4.dp.toPx())
            lens(refractionHeight = (maxOf(8, appearance.height) + 10f * p).dp.toPx(),
                refractionAmount = (maxOf(10, appearance.amount) + 14f * p).dp.toPx(),
                depthEffect = true, chromaticAberration = appearance.chromatic)
        },
            highlight = { Highlight(width = 1.dp, alpha = p) },
            shadow = null,
            onDrawSurface = {
                // 参考实现：底色只有 10%，且按压时按 1 - progress 变淡。
                drawRect((if (appearance.dark) Color.White else Color.Black).copy(alpha = .10f),
                    alpha = 1f - p)
                drawRect(Color.Black.copy(alpha = .03f * p))
            })
    }
}
