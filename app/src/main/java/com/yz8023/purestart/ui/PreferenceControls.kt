package com.yz8023.purestart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.repository.SettingsRepository
import kotlin.math.roundToInt

/**
 * 设置页的两个通用控件（v15.2 第二阶段：从 SettingsScreen 里抽出来）。
 *
 * 两处改进：
 *  1. **状态只有一个来源**。旧实现直接读 `Prefs.sp(context)` 再各自注册一个
 *     `SharedPreferences` 监听（每个开关一个监听器），点完只更新自己那一份；
 *     现在统一订阅 `repository.valueFlow { ... }`，由仓库的那一条偏好回调驱动，
 *     写入路径也走 `repository.putXxx`，界面层不再持有 SharedPreferences。
 *  2. 删掉了从没被用过的 `revision` 参数（旧签名 `PreferenceSwitch(label, key, default, revision)`
 *     里的 revision 在函数体内一次都没读过，纯噪声）。
 */
@Composable
fun PreferenceSwitch(label: String, key: String, default: Boolean, repository: SettingsRepository) {
    val checked by remember(repository, key) { repository.valueFlow { repository.boolean(key, default) } }
        .collectAsState(initial = repository.boolean(key, default))
    Row(Modifier.fillMaxWidth().sceneGlassPanel().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { repository.putBoolean(key, it) })
    }
}

@Composable
fun PreferenceSlider(label: String, key: String, max: Int, default: Int, unit: String,
                     repository: SettingsRepository) {
    val stored by remember(repository, key) { repository.valueFlow { repository.int(key, default) } }
        .collectAsState(initial = repository.int(key, default))
    val value = stored.coerceIn(0, max)
    Column(Modifier.fillMaxWidth().sceneGlassPanel().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label：$value$unit")
        Slider(value = value.toFloat(),
            onValueChange = { repository.putInt(key, it.roundToInt()) },
            valueRange = 0f..max.toFloat())
    }
}
