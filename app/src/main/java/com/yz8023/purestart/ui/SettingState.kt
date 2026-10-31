package com.yz8023.purestart.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yz8023.purestart.repository.SettingsRepository

/**
 * 界面与偏好的桥（v15.2 第二阶段新增）。
 *
 * 之前四个页面各自抄了同一段样板：`mutableIntStateOf(0)` + `DisposableEffect` +
 * `repository.addObserver(SettingsRepository.Observer { revision++ })`，设置页还额外挂了一份
 * 直接监听 SharedPreferences 的「双保险」——那正是 v15.1.5「开关卡死」和重复刷新的来源。
 * 现在所有界面状态收敛到下面两个函数 + 一个图标控制器：
 *
 *  - [rememberSettingsRevision]：偏好变化版本号（由仓库统一通知，主线程）；
 *  - [rememberResumeRevision]：从系统设置页/桌面返回时刷新（图标别名、壁纸授权）；
 *  - [rememberLauncherIconState]：桌面图标别名的显示状态与切换（含失败提示）。
 *
 * 版本号直接用于 `remember(revision) { ... }` 的键，不需要任何手动失效代码。
 */

/** 偏好变化版本号：仓库每收到一次偏好回调就 +1（含自检文件变化）。 */
@Composable
fun rememberSettingsRevision(repository: SettingsRepository): Int {
    var revision by remember(repository) { mutableIntStateOf(0) }
    DisposableEffect(repository) {
        val observer = SettingsRepository.Observer { revision++ }
        repository.addObserver(observer)
        onDispose { repository.removeObserver(observer) }
    }
    return revision
}

/** 每次 Activity 回到前台（ON_RESUME）+1，用于刷新 PackageManager / 权限这类外部状态。 */
@Composable
fun rememberResumeRevision(): Int {
    val context = LocalContext.current
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return revision
}

/** 桌面图标别名的状态机（两处入口共用：首页「更多」菜单、设置页「桌面图标」）。 */
@Stable
class LauncherIconState(val shown: Boolean, private val toggle: (Boolean) -> Unit) {
    /** 切换桌面图标；失败（个别 ROM 的 PackageManager 限制）时给出提示并刷新状态。 */
    fun set(show: Boolean) = toggle(show)
}

@Composable
fun rememberLauncherIconState(): LauncherIconState {
    val context = LocalContext.current
    val controller = remember(context) { LauncherIconController(context) }
    val resumeRevision = rememberResumeRevision()
    var revision by remember { mutableIntStateOf(0) }
    val shown = remember(controller, resumeRevision, revision) {
        try { controller.isShown() } catch (_: Exception) { true }
    }
    return remember(controller, shown) {
        LauncherIconState(shown) { show ->
            try {
                controller.setShown(show)
            } catch (e: Exception) {
                Toast.makeText(context, "切换失败：$e", Toast.LENGTH_SHORT).show()
            }
            revision++
        }
    }
}

/** 隐藏桌面图标前必须展示的恢复方式（两处入口共用同一段文案）。 */
const val HIDE_ICON_HINT: String =
    "隐藏后桌面入口会消失。可尝试从 LSPosed 模块设置打开，或运行：\n\n" +
        "adb shell am broadcast -a com.yz8023.purestart.action.SHOW_ICON\n\n" +
        "也可运行：\nadb shell pm enable com.yz8023.purestart/.IconAlias"

/** 隐藏桌面图标确认框（首页与设置页共用，避免两份文案漂移）。 */
@Composable
fun HideIconDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("隐藏桌面图标") },
        text = { Text(HIDE_ICON_HINT) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("仍要隐藏") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
