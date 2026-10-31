package com.yz8023.purestart.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.repository.SettingsRepository

/**
 * Compose rule editor connected to the existing preferences adapter.
 * Backdrop/lens setup follows yz8023/ui Glass.kt (MIT), with a same-window scene.
 */
@Composable
fun RuleEditor(repository: SettingsRepository, packageName: String, appName: String, onBack: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    // v15.1.5：用 add/removeObserver（原来四个页面抢同一个 observer 槽，
    // 设置页会收不到变化，开关看起来「点了没反应/卡死」）。
    DisposableEffect(repository) {
        val observer = SettingsRepository.Observer { revision++ }
        repository.addObserver(observer)
        onDispose { repository.removeObserver(observer) }
    }
    // 背景沿用所在页面的 GlassScene（极光/壁纸），不再自铺 #F5F8FC 白底。
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(appName, style = MaterialTheme.typography.headlineMedium)
            Text(packageName, style = MaterialTheme.typography.bodyMedium)
            Column(
                Modifier.fillMaxWidth().sceneGlassPanel(24).padding(20.dp)
            ) {
                RuleRow("开屏冷热启动广告", "阻止启动屏倒计时与广告卡片渲染", repository, packageName, SettingsRepository.Rule.SPLASH, revision)
                RuleRow("信息流与横幅净化", "隐藏推荐列表、动态流卡片中的广告项", repository, packageName, SettingsRepository.Rule.FEED, revision)
                RuleRow("营销弹窗与全屏插播", "拦截自动弹出的活动推广与悬浮广告", repository, packageName, SettingsRepository.Rule.POPUP, revision)
                RuleRow("DexFinder 自动特征识别", "在通用开屏扫描中尝试特征方法匹配；不保证适配混淆", repository, packageName, SettingsRepository.Rule.DEXFINDER, revision)
            }
            TextButton(onClick = onBack) { Text("完成并保存") }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun RuleRow(label: String, description: String, repository: SettingsRepository, pkg: String,
                    rule: SettingsRepository.Rule, revision: Int) {
    val enabled = remember(repository, pkg, rule, revision) { repository.isRuleEnabled(pkg, rule) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = { repository.setRuleEnabled(pkg, rule, it) })
    }
}
