package com.yz8023.purestart.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.BuildInfo

/** Configuration file formats and merge behaviour remain in BackupRepository. */
@Composable
fun ExtensionScreen(bottomInset: Dp = 96.dp) {
    val context = LocalContext.current
    val backup = remember(context) { BackupRepository(context) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            val count = backup.exportTo(uri)
            Toast.makeText(context, "已导出 $count 项配置", Toast.LENGTH_SHORT).show()
        } catch (t: Exception) {
            Toast.makeText(context, "导出失败：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val count = backup.importFrom(uri)
            Toast.makeText(context, "已导入 $count 项配置，界面即将刷新", Toast.LENGTH_SHORT).show()
            val activity = context as? Activity
            activity?.window?.decorView?.postDelayed({ activity.recreate() }, 600)
        } catch (t: Exception) {
            Toast.makeText(context, "导入失败：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }
    val version = remember(context) {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" }
        catch (_: Exception) { "unknown" }
    }
    GlassScene {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("拓展", style = MaterialTheme.typography.headlineMedium)
            Text("激活指引 · 数据管理 · 关于模块", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("激活指引", style = MaterialTheme.typography.titleMedium)
            GlassGroup {
                listOf("① 打开 LSPosed →「模块」→ 找到 PureStart 并启用",
                    "② 在「作用域」中勾选需要净化的应用（本模块已内置推荐作用域）",
                    "③ 强制停止或重启勾选的目标应用（无需重启手机）",
                    "④ 回到本模块「应用」页确认对应应用已显示启用状态"
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("排查：拦截无效时请依次确认模块已启用、作用域已勾选、目标应用已重启；本模块基于 libxposed API 102，需 LSPosed 1.9.2+ 等现代框架。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("数据管理", style = MaterialTheme.typography.titleMedium)
            GlassActionRow("备份配置", "导出全部应用开关与偏好为 JSON 文件") {
                try { export.launch("purestart-backup.json") } catch (t: Exception) {
                    Toast.makeText(context, "无法打开文件选择器：${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
            GlassActionRow("恢复配置", "从备份 JSON 导入应用开关与偏好") {
                try { restore.launch(arrayOf("application/json")) } catch (t: Exception) {
                    Toast.makeText(context, "无法打开文件选择器：${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
            Text("关于", style = MaterialTheme.typography.titleMedium)
            GlassGroup {
                Text("开源地址：github.com/yz8023/PureStart", style = MaterialTheme.typography.bodyMedium)
                Text("模块版本：v$version (ModKit Edition)", style = MaterialTheme.typography.bodyMedium)
                Text("Hook 接口：libxposed API 102（Modern Xposed）", style = MaterialTheme.typography.bodyMedium)
                Text("构建时间：${BuildInfo.BUILD_TIME}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(bottomInset))
        }
    }
}
