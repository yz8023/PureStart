package com.yz8023.purestart.ui

import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.painterResource
import com.yz8023.purestart.R
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.BuildInfo
import com.yz8023.purestart.Targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yz8023.purestart.repository.SettingsRepository

/** Compose status and home page. */
@Composable
fun HomeScreen(repository: SettingsRepository, bottomInset: Dp = 96.dp) {
    val context = LocalContext.current
    val apps by produceState<List<AppRecord>?>(null, context) {
        value = withContext(Dispatchers.IO) { loadApps(context.applicationContext) }
    }
    // v15.2 第二阶段：偏好刷新与桌面图标状态改由 SettingState.kt 统一提供。
    val revision = rememberSettingsRevision(repository)
    val allOn = remember(apps, revision) { apps?.filter { it.installed }?.all { repository.isHookEnabled(it.pkg) } ?: true }
    val icon = rememberLauncherIconState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    fun openWeb(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) { Toast.makeText(context, "无法打开浏览器：${e.message}", Toast.LENGTH_SHORT).show() }
    }
    var holding by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    val latestApps by rememberUpdatedState(apps)
    val latestAllOn by rememberUpdatedState(allOn)
    val holdProgress = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val pressedScale by animateFloatAsState(if (holding) .92f else 1f,
        spring(dampingRatio = .62f, stiffness = 360f), label = "shieldPress")
    LaunchedEffect(holding) {
        if (holding) {
            holdProgress.snapTo(0f)
            holdProgress.animateTo(1f, tween(1000, easing = LinearEasing))
        } else holdProgress.animateTo(0f, tween(150))
    }
    LaunchedEffect(completed) {
        if (completed > 0) {
            pulse.snapTo(1.16f)
            pulse.animateTo(1f, spring(dampingRatio = .52f, stiffness = 300f))
        }
    }
    val frameworkVersion = remember {
        try { Class.forName("de.robv.android.xposed.XposedBridge").getMethod("getXposedVersion").invoke(null) as Int }
        catch (_: Throwable) { -1 }
    }
    val active = remember {
        try { Class.forName("de.robv.android.xposed.XposedBridge"); true } catch (_: Throwable) { false }
    }
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.versionCode})" } }
        catch (_: Exception) { "1.0" }
    }
    val targetCount by produceState<Int?>(null, context) {
        value = withContext(Dispatchers.IO) { Targets.LIST.count { target ->
            try { context.packageManager.getPackageInfo(target[0], 0); true } catch (_: Exception) { false }
        } }
    }
    GlassScene {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PureStart", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "更多选项")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(if (icon.shown) "隐藏桌面图标" else "显示桌面图标") },
                        onClick = { menuOpen = false; if (icon.shown) confirmHide = true else icon.set(true) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().sceneGlassPanel(24).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)
                .graphicsLayer { scaleX = pressedScale * pulse.value; scaleY = pressedScale * pulse.value }
                .background(if (allOn) Color(0xFF3157A7) else Color(0xFF778599), RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        holding = true
                        try {
                            val released = withTimeoutOrNull(1000L) { waitForUpOrCancellation() != null }
                            if (released == null) {
                                val next = !latestAllOn
                                latestApps?.forEach { repository.setHookEnabled(it.pkg, next) }
                                holding = false
                                completed++
                                Toast.makeText(context, if (next) "已一键开启所有应用广告拦截与优化" else "已重置并关闭所有应用适配拦截", Toast.LENGTH_SHORT).show()
                                waitForUpOrCancellation()
                            } else if (released) {
                                Toast.makeText(context, "长按盾牌图标 1 秒，一键全局开关全部应用净化", Toast.LENGTH_SHORT).show()
                            }
                        } finally { holding = false }
                    }
                }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(26.dp)) {
                    val shield = Path().apply {
                        moveTo(size.width * .5f, 0f)
                        lineTo(size.width * .88f, size.height * .18f)
                        lineTo(size.width * .84f, size.height * .57f)
                        quadraticTo(size.width * .8f, size.height * .84f, size.width * .5f, size.height)
                        quadraticTo(size.width * .2f, size.height * .84f, size.width * .16f, size.height * .57f)
                        lineTo(size.width * .12f, size.height * .18f)
                        close()
                    }
                    drawPath(shield, Color.White)
                }
                if (holding || holdProgress.value > .01f) {
                    val rotation by rememberInfiniteTransition(label = "holdRing").animateFloat(
                        0f, 360f, infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
                        label = "holdRingRotation")
                    Canvas(Modifier.matchParentSize().padding(3.dp)) {
                        val stroke = 2.5.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(Color.White.copy(alpha = .22f), 0f, 360f, false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                        drawArc(Color.White, -90f, 360f * holdProgress.value, false,
                            topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(Color.White.copy(alpha = .65f), rotation, 24f, false,
                            topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (active) "已激活" else "未激活", style = MaterialTheme.typography.titleMedium)
                Text(if (active) version + if (frameworkVersion > 0) " · 框架 v$frameworkVersion" else ""
                     else "$version · 未检测到 LSPosed / Xposed 框架")
            }
        }
        Text("适配应用：${apps?.count { it.installed } ?: "…"}")
        Text("目标应用已装：${targetCount ?: "…"}")
        listOf("构建时间" to BuildInfo.BUILD_TIME,
            "安卓版本" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, REL)",
            "设备型号" to "${Build.MODEL} (${Build.PRODUCT})",
            "系统架构" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "—"),
            "框架 & API" to "libxposed API 102（Modern Xposed）").forEach { (label, value) ->
            Column(Modifier.fillMaxWidth().sceneGlassPanel(20).padding(16.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(Modifier.fillMaxWidth().sceneGlassPanel(22).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("在 GitHub 反馈", style = MaterialTheme.typography.titleSmall)
                Text("查看项目或提交问题", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { openWeb("https://github.com/yz8023/PureStart") }) {
                Icon(painterResource(R.drawable.ic_action_code), contentDescription = "项目主页")
            }
            IconButton(onClick = { openWeb("https://github.com/yz8023/PureStart/issues") }) {
                Icon(painterResource(R.drawable.ic_action_chat), contentDescription = "提交问题")
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
    }
    if (confirmHide) {
        HideIconDialog(onDismiss = { confirmHide = false },
            onConfirm = { confirmHide = false; icon.set(false) })
    }
}
