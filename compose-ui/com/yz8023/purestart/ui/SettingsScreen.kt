package com.yz8023.purestart.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.yz8023.purestart.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.Prefs
import com.yz8023.purestart.Targets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Compose settings page. */
@Composable
fun SettingsScreen(repository: SettingsRepository, bottomInset: Dp = 96.dp) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs.sp(context) }
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }
    val iconController = remember(context) { LauncherIconController(context) }
    fun aliasEnabled(): Boolean = try { iconController.isShown() } catch (_: Exception) { true }
    fun setAlias(show: Boolean) {
        try { iconController.setShown(show); revision++ }
        catch (e: Exception) { Toast.makeText(context, "切换失败：$e", Toast.LENGTH_SHORT).show(); revision++ }
    }
    DisposableEffect(repository) {
        repository.setObserver { revision++ }
        onDispose { repository.setObserver(null) }
    }
    // PackageManager is the authority. Refresh when returning from a manager or
    // launcher that may have changed the alias outside this process.
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    GlassScene {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text("个性化", style = MaterialTheme.typography.titleMedium)
        val modes = listOf("跟随系统", "浅色磨砂玻璃", "深色磨砂玻璃", "极光流体磨砂玻璃")
        val theme = remember(revision) { repository.themeMode() }
        Text("外观：${modes.getOrElse(theme) { modes[0] }}", Modifier.fillMaxWidth().sceneGlassPanel().clickable { dialog = "theme" }.padding(12.dp))
        PreferenceSwitch("磨砂模糊", Prefs.KEY_LIQUID_BLUR, true, revision)
        val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "glassDetailsChevron")
        Row(Modifier.fillMaxWidth().sceneGlassPanel().clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("玻璃细则", style = MaterialTheme.typography.bodyLarge)
                Text("调整模糊、折射与动效", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_down),
                contentDescription = if (expanded) "收起玻璃细则" else "展开玻璃细则",
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevronRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            PreferenceSlider("动效阻尼", Prefs.KEY_GLASS_DAMPING, 100, 50, "%", revision)
            PreferenceSlider("模糊强度", Prefs.KEY_GLASS_BLUR, 40, 0, "dp", revision)
            PreferenceSlider("折射高度", Prefs.KEY_GLASS_REFRACT_HEIGHT, 40, 15, "dp", revision)
            PreferenceSlider("折射幅度", Prefs.KEY_GLASS_REFRACT_AMOUNT, 60, 20, "dp", revision)
            PreferenceSlider("玻璃染色", Prefs.KEY_GLASS_TINT_ALPHA, 35, 8, "%", revision)
            PreferenceSlider("高光边框", Prefs.KEY_GLASS_BORDER, 45, 20, "%", revision)
            PreferenceSwitch("增强色彩层次", Prefs.KEY_GLASS_VIBRANCY, true, revision)
            PreferenceSwitch("色差折射", Prefs.KEY_GLASS_CHROMATIC, true, revision)
        }
        val locale = remember(revision) { repository.localeMode() }
        Text("语言：${if (locale == 1) "简体中文（强制）" else "跟随系统"}", Modifier.fillMaxWidth().sceneGlassPanel().clickable { dialog = "locale" }.padding(12.dp))
        PreferenceSwitch("底栏应用角标", Prefs.KEY_APP_BADGE, true, revision)
        PreferenceSwitch("页面左右滑动翻页", Prefs.KEY_PAGE_SWIPE, true, revision)
        Text("桌面图标", style = MaterialTheme.typography.titleMedium)
        val aliasState = remember(revision) { aliasEnabled() }
        Row(Modifier.fillMaxWidth().sceneGlassPanel().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("在桌面显示图标")
                Text("隐藏后可通过模块设置或 adb 恢复", style = MaterialTheme.typography.bodySmall)
            }
            Switch(aliasState, onCheckedChange = { if (it) setAlias(true) else dialog = "hide" })
        }
        Text("Hook 自检", style = MaterialTheme.typography.titleMedium)
        GlassGroup {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("目标应用上报", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { revision++ }) { Text("刷新") }
        }
        Text("请在 LSPosed 勾选作用域、打开应用总开关及所需子开关，然后强制停止并重启目标应用。刷新只读取已有上报。", style = MaterialTheme.typography.bodySmall)
        for (target in Targets.LIST) {
            val enabled = remember(revision, target[0]) { repository.isHookEnabled(target[0]) }
            val installed = remember(revision, target[0]) {
                try { context.packageManager.getPackageInfo(target[0], 0); true }
                catch (_: Exception) { false }
            }
            val report = remember(revision, target[0]) { repository.selfCheck(target[0]) }
            val name = target[1].split(" · ")[0]
            val message = when {
                !installed -> "$name：未安装"
                report == null -> "$name：未上报（打开一次目标应用后自动回传）"
                else -> {
                    val p = report.split("|")
                    val ok = p.getOrNull(0)?.toIntOrNull() ?: 0
                    val miss = p.getOrNull(1)?.toIntOrNull() ?: 0
                    val fails = p.getOrNull(2).orEmpty()
                    val version = p.getOrNull(3) ?: "?"
                    val at = p.getOrNull(4)?.toLongOrNull() ?: 0L
                    val state = if (ok > 0) "已安装 $ok 项 Hook（不代表已拦截）" + if (miss > 0) " · 缺 $miss" else ""
                                else "未报告已安装的 Hook（详见日志）"
                    val failText = if (fails.isNotEmpty()) " · 失败: ${fails.take(80)}" else ""
                    val time = if (at > 0) SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at)) else "—"
                    "$name：$state$failText\nv$version · $time"
                }
            }
            Text((if (installed && !enabled) "总开关关闭 · " else "") + message,
                style = MaterialTheme.typography.bodySmall)
        }
        }
        Text("更新", style = MaterialTheme.typography.titleMedium)
        PreferenceSwitch("自动检查更新", Prefs.KEY_AUTO_UPDATE, true, revision)
        GlassActionRow("获取更新")
        Spacer(Modifier.height(bottomInset))
    }
    }
    when (dialog) {
        "theme" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("外观") },
            text = { Column { listOf("跟随系统", "浅色磨砂玻璃", "深色磨砂玻璃", "极光流体磨砂玻璃").forEachIndexed { index, name ->
                Text(name, Modifier.fillMaxWidth().clickable { repository.setThemeMode(index); dialog = null; (context as? Activity)?.recreate() }.padding(12.dp))
            } } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
        "locale" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("界面语言") },
            text = { Column { listOf("跟随系统", "简体中文（强制）").forEachIndexed { index, name ->
                Text(name, Modifier.fillMaxWidth().clickable { prefs.edit().putInt(Prefs.KEY_LOCALE_MODE, index).apply(); dialog = null; (context as? Activity)?.recreate() }.padding(12.dp))
            }; Text("英文界面翻译筹备中，当前界面为简体中文。") } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
        "hide" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("隐藏桌面图标") },
            text = { Text("隐藏后桌面入口会消失。可尝试从 LSPosed 模块设置打开，或运行：\n\nadb shell am broadcast -a com.yz8023.purestart.action.SHOW_ICON\n\n也可运行：\nadb shell pm enable com.yz8023.purestart/.IconAlias") },
            confirmButton = { TextButton(onClick = { dialog = null; setAlias(false) }) { Text("仍要隐藏") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
    }
}

@Composable
private fun PreferenceSwitch(label: String, key: String, default: Boolean, revision: Int) {
    val prefs = Prefs.sp(LocalContext.current)
    val checked = remember(prefs, key, revision) { prefs.getBoolean(key, default) }
    Row(Modifier.fillMaxWidth().sceneGlassPanel().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = { prefs.edit().putBoolean(key, it).apply() })
    }
}

@Composable
private fun PreferenceSlider(label: String, key: String, max: Int, default: Int, unit: String, revision: Int) {
    val prefs = Prefs.sp(LocalContext.current)
    val value = remember(prefs, key, revision) { prefs.getInt(key, default).coerceIn(0, max) }
    Column(Modifier.fillMaxWidth().sceneGlassPanel().padding(12.dp)) { Text("$label：$value$unit"); Slider(value.toFloat(),
        onValueChange = { prefs.edit().putInt(key, it.roundToInt()).apply() }, valueRange = 0f..max.toFloat()) }
}
