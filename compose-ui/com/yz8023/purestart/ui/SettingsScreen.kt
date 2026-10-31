package com.yz8023.purestart.ui

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * 应用背景选择（v15.1.3）：极光（内置，参考 yz8023/UI）／系统桌面壁纸／相册自定义图片。
 *
 * 选图走系统照片选择器（ActivityResultContracts.PickVisualMedia），不需要任何存储权限；
 * 图片会被复制到应用私有目录，原图删除或权限撤销都不会让背景变空白。
 */
@Composable
private fun BackgroundCard(revision: Int) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs.sp(context) }
    val mode = prefs.getInt(Prefs.KEY_WALLPAPER_MODE, WallpaperRepository.MODE_AURORA)
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    } ?: error("无法读取所选图片")
                    WallpaperRepository.saveCustom(context, bitmap)
                }.getOrNull()
            }
            if (saved != null) {
                prefs.edit().putString(Prefs.KEY_WALLPAPER_PATH, saved)
                    .putInt(Prefs.KEY_WALLPAPER_MODE, WallpaperRepository.MODE_CUSTOM).apply()
                Toast.makeText(context, "已设为应用背景", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "图片读取失败，请换一张", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val names = listOf("极光", "系统壁纸", "自定义图片")
    Column(Modifier.fillMaxWidth().sceneGlassPanel().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用背景", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { index, name ->
                FilterChip(selected = mode == index, onClick = {
                    if (index == WallpaperRepository.MODE_CUSTOM) {
                        picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        val wasSystem = prefs.getInt(Prefs.KEY_WALLPAPER_MODE, 0) ==
                            WallpaperRepository.MODE_SYSTEM
                        prefs.edit().putInt(Prefs.KEY_WALLPAPER_MODE, index).apply()
                        // 只有「系统壁纸」的进出才需要重建：窗口透明标志只在 onCreate 生效。
                        // 极光 <-> 自定义图片只是换背景层内容，重建反而会丢滚动位置。
                        val needsSystemWindow = index == WallpaperRepository.MODE_SYSTEM || wasSystem
                        if (index == WallpaperRepository.MODE_SYSTEM) {
                            scope.launch {
                                withContext(Dispatchers.IO) { WallpaperRepository.rememberProbe(context) }
                                if (needsSystemWindow) (context as? Activity)?.recreate()
                            }
                        } else if (wasSystem) {
                            (context as? Activity)?.recreate()
                        }
                    }
                }, label = { Text(name) })
            }
        }
        Text(when (mode) {
            WallpaperRepository.MODE_SYSTEM ->
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
                    "使用系统桌面壁纸：Android 13 起系统不再允许应用读取壁纸图片（Google 已标记 Won't Fix），" +
                        "因此改用透明窗口让系统把壁纸画在下层，玻璃依然能折射。仅显示、不修改系统壁纸，无需任何权限。"
                else
                    "使用系统桌面壁纸：只读取显示，不修改系统设置，无需任何权限。"
            WallpaperRepository.MODE_CUSTOM -> "已使用相册图片，点「自定义图片」可重新选择"
            else -> "内置极光背景，取自 yz8023/UI 的 AuroraBackground"
        }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (mode == WallpaperRepository.MODE_SYSTEM) {
            val reason = remember(revision, mode) { Prefs.wallpaperError(context) }
            if (!reason.isNullOrBlank()) {
                Text("读取状态：$reason", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (mode != WallpaperRepository.MODE_AURORA) {
            val dim = remember(revision, mode) { prefs.getInt(Prefs.KEY_WALLPAPER_DIM, 30) }
            Text("背景压暗：$dim%", style = MaterialTheme.typography.bodySmall)
            Slider(value = dim.toFloat(), onValueChange = {
                prefs.edit().putInt(Prefs.KEY_WALLPAPER_DIM, it.roundToInt()).apply()
            }, valueRange = 0f..70f)
        }
    }
}

/** Compose settings page. */
@Composable
fun SettingsScreen(repository: SettingsRepository, bottomInset: Dp = 96.dp) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs.sp(context) }
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    // v15.1.3：玻璃细则默认折叠，避免设置页一进来就被十条滑杆塞满。
    var expanded by rememberSaveable { mutableStateOf(false) }
    val iconController = remember(context) { LauncherIconController(context) }
    fun aliasEnabled(): Boolean = try { iconController.isShown() } catch (_: Exception) { true }
    fun setAlias(show: Boolean) {
        try { iconController.setShown(show); revision++ }
        catch (e: Exception) { Toast.makeText(context, "切换失败：$e", Toast.LENGTH_SHORT).show(); revision++ }
    }
    // v15.1.5：用 add/removeObserver（原来四个页面抢同一个 observer 槽，
    // 设置页会收不到变化，开关看起来「点了没反应/卡死」）。
    DisposableEffect(repository) {
        val observer = SettingsRepository.Observer { revision++ }
        repository.addObserver(observer)
        onDispose { repository.removeObserver(observer) }
    }
    // 自己再监听一遍偏好：不依赖 repository 的通知也能刷新界面。
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
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
        BackgroundCard(revision)
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
            // 卡片底色是唯一决定卡片「发白」程度的参数：0 = 完全透明，只留淡描边
            PreferenceSlider("卡片底色", Prefs.KEY_CARD_TINT, 40, 10, "%", revision)
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
    // v15.1.5：改成 collectAsState 订阅，而不是只读一次的快照；
    // 这样开关状态始终跟偏好一致（原来点完 UI 不刷新，看起来像卡死）。
    val checked by remember(prefs, key) {
        prefs.booleanFlow(key, default)
    }.collectAsState(initial = prefs.getBoolean(key, default))
    Row(Modifier.fillMaxWidth().sceneGlassPanel().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { prefs.edit().putBoolean(key, it).apply() })
    }
}

@Composable
private fun PreferenceSlider(label: String, key: String, max: Int, default: Int, unit: String, revision: Int) {
    val prefs = Prefs.sp(LocalContext.current)
    val stored by remember(prefs, key) { prefs.intFlow(key, default) }
        .collectAsState(initial = prefs.getInt(key, default))
    val value = stored.coerceIn(0, max)
    Column(Modifier.fillMaxWidth().sceneGlassPanel().padding(12.dp)) {
        Text("$label：$value$unit")
        Slider(value = value.toFloat(),
            onValueChange = { prefs.edit().putInt(key, it.roundToInt()).apply() },
            valueRange = 0f..max.toFloat())
    }
}
