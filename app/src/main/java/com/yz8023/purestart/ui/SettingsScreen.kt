package com.yz8023.purestart.ui

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.Prefs
import com.yz8023.purestart.R
import com.yz8023.purestart.Targets
import com.yz8023.purestart.repository.SelfCheckReport
import com.yz8023.purestart.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 外观档位文案（设置页正文与弹窗共用一份，避免两份列表改一处漏一处）。 */
private val THEME_MODES = listOf("跟随系统", "浅色磨砂玻璃", "深色磨砂玻璃", "极光流体磨砂玻璃")

/** 界面语言档位文案。 */
private val LOCALE_MODES = listOf("跟随系统", "简体中文（强制）")

/**
 * 应用背景选择（v15.1.3）：极光（内置，参考 yz8023/UI）／系统桌面壁纸／相册自定义图片。
 *
 * 选图走系统照片选择器（ActivityResultContracts.PickVisualMedia），不需要任何存储权限；
 * 图片会被复制到应用私有目录，原图删除或权限撤销都不会让背景变空白。
 *
 * v15.2 第二阶段：读写全部经 [SettingsRepository]，这里不再碰 SharedPreferences。
 */
@Composable
private fun BackgroundCard(revision: Int, resume: Int, repository: SettingsRepository) {
    val context = LocalContext.current
    val mode = remember(revision) { repository.wallpaperMode() }
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
                repository.setWallpaperPath(saved)
                repository.setWallpaperMode(WallpaperRepository.MODE_CUSTOM)
                Toast.makeText(context, "已设为应用背景", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "图片读取失败，请换一张", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // 读取状态与「所有文件访问」授权状态：从系统设置页返回（ON_RESUME）时重读。
    var status by remember { mutableStateOf("") }
    var needsGrant by remember { mutableStateOf(false) }
    fun readStatus() {
        if (mode != WallpaperRepository.MODE_SYSTEM) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                WallpaperRepository.systemWallpaperStatus(context) to
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !WallpaperRepository.hasAllFilesAccess())
            }
            status = result.first
            needsGrant = result.second
        }
    }
    LaunchedEffect(mode, resume) { readStatus() }
    // 系统「所有文件访问」设置页返回 → 重读壁纸状态
    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        scope.launch {
            val text = withContext(Dispatchers.IO) { WallpaperRepository.systemWallpaperStatus(context) }
            status = text
            needsGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !WallpaperRepository.hasAllFilesAccess()
            if (text.isEmpty()) Toast.makeText(context, "已读取到系统壁纸", Toast.LENGTH_SHORT).show()
        }
    }
    fun openGrantPage() {
        WallpaperRepository.allFilesAccessSettingsIntent(context)?.let { intent ->
            try { grantLauncher.launch(intent) } catch (_: Throwable) {
                Toast.makeText(context, "无法打开授权页，请在系统设置中手动授权", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxWidth().sceneGlassPanel().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用背景", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("极光", "系统壁纸", "自定义图片").forEachIndexed { index, name ->
                FilterChip(selected = mode == index, onClick = {
                    if (index == WallpaperRepository.MODE_CUSTOM) {
                        picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        repository.setWallpaperMode(index)
                        // 对照 yz8023/UI：先尝试直接读；Android 14+ 读不到就带用户去
                        // 系统「所有文件访问」设置页，授权返回后自动重读。
                        if (index == WallpaperRepository.MODE_SYSTEM) {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    WallpaperRepository.systemWallpaperStatus(context)
                                }
                                status = text
                                needsGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                    !WallpaperRepository.hasAllFilesAccess()
                                if (text.isNotEmpty()) openGrantPage()
                            }
                        }
                    }
                }, label = { Text(name) })
            }
        }
        Text(when (mode) {
            WallpaperRepository.MODE_SYSTEM ->
                "读取系统桌面壁纸（只读显示，不修改系统壁纸）。Android 14 起系统要求「所有文件访问」才允许读取，" +
                    "未授权时不会假装成功，会给出提示并可一键去授权。"
            WallpaperRepository.MODE_CUSTOM -> "已使用相册图片，点「自定义图片」可重新选择"
            else -> "内置极光背景，取自 yz8023/UI 的 AuroraBackground"
        }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (mode == WallpaperRepository.MODE_SYSTEM) {
            Text(if (status.isEmpty()) "读取状态：正常（已取得系统壁纸）" else "读取状态：$status",
                style = MaterialTheme.typography.bodySmall,
                color = if (status.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error)
            if (needsGrant && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                GlassActionRow("开启「所有文件访问」", "授权后返回即可读到系统壁纸") { openGrantPage() }
            }
        }
        if (mode != WallpaperRepository.MODE_AURORA) {
            val dim = remember(revision) { repository.int(Prefs.KEY_WALLPAPER_DIM, 30) }
            Text("背景压暗：$dim%", style = MaterialTheme.typography.bodySmall)
            Slider(value = dim.toFloat(),
                onValueChange = { repository.putInt(Prefs.KEY_WALLPAPER_DIM, it.roundToInt()) },
                valueRange = 0f..70f)
        }
    }
}

/** Compose settings page. */
@Composable
fun SettingsScreen(repository: SettingsRepository, bottomInset: Dp = 96.dp) {
    val context = LocalContext.current
    // v15.2 第二阶段：偏好变化与「回到前台」两个刷新源各一个版本号，
    // 不再由每个页面自己 addObserver / 注册 SharedPreferences 监听。
    val revision = rememberSettingsRevision(repository)
    val resume = rememberResumeRevision()
    var dialog by remember { mutableStateOf<String?>(null) }
    // 自检上报由目标进程写入，没有偏好回调可以依赖：点「刷新」只递增这个计数器。
    var selfCheckRefresh by remember { mutableIntStateOf(0) }
    // v15.1.3：玻璃细则默认折叠，避免设置页一进来就被十条滑杆塞满。
    var expanded by rememberSaveable { mutableStateOf(false) }
    val icon = rememberLauncherIconState()
    GlassScene {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text("个性化", style = MaterialTheme.typography.titleMedium)
        val theme = remember(revision) { repository.themeMode() }
        Text("外观：${THEME_MODES.getOrElse(theme) { THEME_MODES[0] }}",
            Modifier.fillMaxWidth().sceneGlassPanel().clickable { dialog = "theme" }.padding(12.dp))
        // 对照 yz8023/UI 的 VisualMode：普通模式 = 实心 Material 卡片（不采样背景），
        // 与「磨砂模糊」是两件事：关模糊仍走玻璃降级，普通模式则是完全实心。
        PreferenceSwitch("普通模式（实心卡片，不做玻璃）", Prefs.KEY_PLAIN_MODE, false, repository)
        PreferenceSwitch("磨砂模糊", Prefs.KEY_LIQUID_BLUR, true, repository)
        BackgroundCard(revision, resume, repository)
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
            PreferenceSlider("动效阻尼", Prefs.KEY_GLASS_DAMPING, 100, 50, "%", repository)
            PreferenceSlider("模糊强度", Prefs.KEY_GLASS_BLUR, 40, 0, "dp", repository)
            PreferenceSlider("折射高度", Prefs.KEY_GLASS_REFRACT_HEIGHT, 40, 15, "dp", repository)
            PreferenceSlider("折射幅度", Prefs.KEY_GLASS_REFRACT_AMOUNT, 60, 20, "dp", repository)
            PreferenceSlider("玻璃染色", Prefs.KEY_GLASS_TINT_ALPHA, 35, 8, "%", repository)
            // 卡片底色是唯一决定卡片「发白」程度的参数：0 = 完全透明，只留淡描边
            PreferenceSlider("卡片底色", Prefs.KEY_CARD_TINT, 40, 10, "%", repository)
            PreferenceSlider("高光边框", Prefs.KEY_GLASS_BORDER, 45, 20, "%", repository)
            PreferenceSwitch("增强色彩层次", Prefs.KEY_GLASS_VIBRANCY, true, repository)
            PreferenceSwitch("色差折射", Prefs.KEY_GLASS_CHROMATIC, true, repository)
        }
        val locale = remember(revision) { repository.localeMode() }
        Text("语言：${LOCALE_MODES.getOrElse(locale) { LOCALE_MODES[0] }}",
            Modifier.fillMaxWidth().sceneGlassPanel().clickable { dialog = "locale" }.padding(12.dp))
        PreferenceSwitch("底栏应用角标", Prefs.KEY_APP_BADGE, true, repository)
        PreferenceSwitch("页面左右滑动翻页", Prefs.KEY_PAGE_SWIPE, true, repository)
        Text("桌面图标", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().sceneGlassPanel().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("在桌面显示图标")
                Text("隐藏后可通过模块设置或 adb 恢复", style = MaterialTheme.typography.bodySmall)
            }
            Switch(icon.shown, onCheckedChange = { if (it) icon.set(true) else dialog = "hide" })
        }
        Text("Hook 自检", style = MaterialTheme.typography.titleMedium)
        GlassGroup {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("目标应用上报", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { selfCheckRefresh++ }) { Text("刷新") }
        }
        Text("请在 LSPosed 勾选作用域、打开应用总开关及所需子开关，然后强制停止并重启目标应用。刷新只读取已有上报。", style = MaterialTheme.typography.bodySmall)
        for (target in Targets.LIST) {
            val enabled = remember(revision, target[0]) { repository.isHookEnabled(target[0]) }
            val installed = remember(resume, target[0]) {
                try { context.packageManager.getPackageInfo(target[0], 0); true }
                catch (_: Exception) { false }
            }
            val report = remember(revision, selfCheckRefresh, target[0]) {
                SelfCheckReport.parse(repository.selfCheck(target[0]))
            }
            val name = target[1].split(" · ")[0]
            val message = when {
                !installed -> "$name：未安装"
                report == null -> "$name：未上报（打开一次目标应用后自动回传）"
                else -> {
                    val time = if (report.at > 0)
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(report.at)) else "—"
                    "$name：${report.summary()}\nv${report.version} · $time"
                }
            }
            Text((if (installed && !enabled) "总开关关闭 · " else "") + message,
                style = MaterialTheme.typography.bodySmall)
        }
        }
        Text("更新", style = MaterialTheme.typography.titleMedium)
        PreferenceSwitch("自动检查更新", Prefs.KEY_AUTO_UPDATE, true, repository)
        GlassActionRow("获取更新")
        Spacer(Modifier.height(bottomInset))
    }
    }
    when (dialog) {
        "theme" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("外观") },
            text = { Column { THEME_MODES.forEachIndexed { index, name ->
                Text(name, Modifier.fillMaxWidth()
                    .clickable { repository.setThemeMode(index); dialog = null; (context as? Activity)?.recreate() }
                    .padding(12.dp))
            } } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
        "locale" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("界面语言") },
            text = { Column { LOCALE_MODES.forEachIndexed { index, name ->
                Text(name, Modifier.fillMaxWidth().clickable {
                    repository.putInt(Prefs.KEY_LOCALE_MODE, index)
                    dialog = null
                    (context as? Activity)?.recreate()
                }.padding(12.dp))
            }; Text("英文界面翻译筹备中，当前界面为简体中文。") } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
        "hide" -> HideIconDialog(onDismiss = { dialog = null },
            onConfirm = { dialog = null; icon.set(false) })
    }
}
