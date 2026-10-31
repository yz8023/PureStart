package com.yz8023.purestart.ui

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import com.yz8023.purestart.Prefs

/** Rendering parameters read from the original preference names and defaults. */
@Immutable
data class GlassAppearance(val dark: Boolean, val aurora: Boolean, val enabled: Boolean,
    val opacity: Float, val tint: Float, val blur: Int, val height: Int, val amount: Int,
    val border: Float, val vibrant: Boolean, val chromatic: Boolean)

private val LocalAppearance = staticCompositionLocalOf {
    GlassAppearance(false, false, true, .55f, .08f, 0, 15, 20, .20f, true, true)
}
private val LocalSceneBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun PureStartTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs.sp(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val systemDark = isSystemInDarkTheme()
    val appearance = remember(revision, systemDark) {
        val mode = Prefs.themeMode(context)
        GlassAppearance(mode == 2 || mode == 3 || (mode == 0 && systemDark), mode == 3,
            Prefs.liquidBlurEnabled(context), Prefs.opacity(context), Prefs.tintAlpha(context),
            Prefs.blurDp(context), Prefs.prismHeightDp(context), Prefs.prismAmountDp(context),
            Prefs.borderAlpha(context), Prefs.vibrancy(context), Prefs.chromatic(context))
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

@Composable
fun glassBackground(): Brush {
    val appearance = LocalAppearance.current
    val phase by rememberInfiniteTransition(label = "backgroundLight").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "backgroundLightPhase")
    return if (appearance.dark) Brush.linearGradient(listOf(Color(0xFF111E35),
        lerp(Color(0xFF281C39), Color(0xFF233456), phase), Color(0xFF103632)))
    else Brush.linearGradient(listOf(Color(0xFFDDEAFF),
        lerp(Color(0xFFF7EAF3), Color(0xFFE5ECFF), phase), Color(0xFFD3EEEB)))
}

@Composable
fun Modifier.glassPanel(backdrop: Backdrop, radius: Int = 22, navigation: Boolean = false): Modifier {
    val appearance = LocalAppearance.current
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(radius.dp)
    // RenderEffect/backdrop effects are unavailable on older Android releases.
    // Keep text legible with the reference project's translucent fallback rather
    // than showing an almost invisible, falsely advertised refraction effect.
    if (!appearance.enabled || Build.VERSION.SDK_INT < 31) {
        val fallback = if (appearance.enabled)
            (0.26f + appearance.tint + appearance.opacity * .18f).coerceIn(0f, .72f)
            else .88f
        return this.shadow(2.dp, shape, clip = false).clip(shape)
            .background(surface.copy(alpha = fallback))
            .border(1.dp, Color.White.copy(alpha = appearance.border.coerceIn(0f, .45f)), shape)
    }
    // The reference capsule uses its own 4dp blur, independently of the
    // user-controlled page-card blur; do not change the persisted preference.
    val blurDp = if (navigation) maxOf(4, appearance.blur) else appearance.blur
    val alpha = if (navigation) (0.10f + appearance.opacity * .55f).coerceIn(0f, .72f)
                else (appearance.tint + appearance.opacity * .04f).coerceIn(0f, .45f)
    return drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
        if (appearance.vibrant) vibrancy()
        if (blurDp > 0) blur(blurDp.dp.toPx())
        if (appearance.height > 0 || appearance.amount > 0) lens(
            refractionHeight = appearance.height.dp.toPx(), refractionAmount = appearance.amount.dp.toPx(),
            chromaticAberration = appearance.chromatic)
    }, highlight = { Highlight(width = 1.dp, alpha = appearance.border.coerceIn(0f, .8f)) },
        shadow = { Shadow(radius = 8.dp, color = Color.Black, alpha = if (appearance.dark) .20f else .08f) },
        onDrawSurface = { drawRect(surface.copy(alpha = alpha)) })
}

/** Same-window background layer for screens built without a dedicated backdrop. */
@Composable
fun GlassScene(content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer()) { drawContent() }
    val background = glassBackground()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(background))
        CompositionLocalProvider(LocalSceneBackdrop provides backdrop) { content() }
    }
}

@Composable
fun Modifier.sceneGlassPanel(radius: Int = 22): Modifier {
    val backdrop = LocalSceneBackdrop.current
    return if (backdrop != null) glassPanel(backdrop, radius)
           else background(MaterialTheme.colorScheme.surface, RoundedCornerShape(radius.dp))
}

/** Visible transparent glass container: unlike the old grey slab, its fill is
 * a low-alpha light tint over the real page backdrop, with a distinct rim. */
@Composable
fun Modifier.dockContainer(backdrop: Backdrop): Modifier {
    val appearance = LocalAppearance.current
    val shape = RoundedCornerShape(50)
    if (!appearance.enabled || Build.VERSION.SDK_INT < 31) {
        val tint = if (appearance.dark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .36f)
        return this.shadow(3.dp, shape, clip = false).clip(shape).background(tint)
            .border(1.dp, Color.White.copy(alpha = if (appearance.dark) .22f else .62f), shape)
    }
    return drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
        if (appearance.vibrant) vibrancy()
        blur(maxOf(4, appearance.blur).dp.toPx())
        lens(refractionHeight = maxOf(8, appearance.height).dp.toPx(),
            refractionAmount = maxOf(10, appearance.amount).dp.toPx(),
            chromaticAberration = appearance.chromatic)
    }, highlight = { Highlight(width = 1.dp, alpha = if (appearance.dark) .42f else .70f) },
        shadow = { Shadow(radius = 10.dp, color = Color.Black, alpha = if (appearance.dark) .14f else .08f) },
        onDrawSurface = { drawRect(Color.White.copy(alpha = if (appearance.dark) .055f else .18f)) })
}

/**
 * 移动的底栏玻璃胶囊（不带底板，只有这一颗胶囊）。
 *
 * `press` 0..1 为按压进度：折射、高光、内阴影与投影都随按压增强，
 * 与模板 LiquidBottomTabs 的活动胶囊一致（模板用 pressProgress 驱动
 * lens / Highlight / InnerShadow）。放大由调用方在 graphicsLayer 上做，
 * 因此这里不再裁剪，胶囊可以溢出底栏容器。
 */
@Composable
fun Modifier.dockLens(backdrop: Backdrop, press: Float): Modifier {
    val appearance = LocalAppearance.current
    val shape = RoundedCornerShape(50)
    val surface = MaterialTheme.colorScheme.surface
    val p = press.coerceIn(0f, 1f)
    if (!appearance.enabled || Build.VERSION.SDK_INT < 31) {
        // 旧系统没有 RenderEffect：用更明显的半透明底 + 白色描边表达「浮起」，
        // 并按按压进度加深投影，保持与玻璃路径一致的视觉反馈。
        val lift = 4.dp + 6.dp * p
        return this.shadow(lift, shape, clip = false).clip(shape)
            .background(surface.copy(alpha = (if (appearance.dark) .52f else .68f) + .12f * p))
            .border(1.dp, Color.White.copy(alpha = .35f + .25f * p), shape)
    }
    return drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
        if (appearance.vibrant) vibrancy()
        blur(4.dp.toPx())
        lens(refractionHeight = (maxOf(8, appearance.height) + 10f * p).dp.toPx(),
            refractionAmount = (maxOf(10, appearance.amount) + 14f * p).dp.toPx(),
            depthEffect = true, chromaticAberration = appearance.chromatic)
    }, highlight = { Highlight(width = 1.5.dp, alpha = (.35f + p * .45f).coerceAtMost(.85f)) },
        // 按压时投影变大：胶囊确实「浮」在底栏之上，而不是陷在容器里。
        shadow = { Shadow(radius = 10.dp + 6.dp * p, color = Color.Black,
            alpha = (if (appearance.dark) .22f else .12f) + .10f * p) },
        onDrawSurface = {
            drawRect(surface.copy(alpha = (if (appearance.dark) .14f else .20f) + .10f * p))
        })
}
