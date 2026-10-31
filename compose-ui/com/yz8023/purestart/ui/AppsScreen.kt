package com.yz8023.purestart.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yz8023.purestart.Targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

internal data class AppRecord(val pkg: String, val label: String, val version: String,
                             val installed: Boolean, val description: String)

/* Mirrors the existing scope.list lookup, installed-first ordering and missing-package state. */
internal fun loadApps(context: Context): List<AppRecord> {
    val descriptions = Targets.LIST.associate { it[0] to it[1] }
    val records = ArrayList<AppRecord>()
    try {
        ZipFile(context.packageCodePath).use { zip ->
            val scope = zip.getEntry("META-INF/xposed/scope.list") ?: return emptyList()
            zip.getInputStream(scope).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { pkg ->
                    val info = try { context.packageManager.getPackageInfo(pkg, 0) }
                               catch (_: PackageManager.NameNotFoundException) { null }
                    val app = info?.applicationInfo
                    val label = app?.loadLabel(context.packageManager)?.toString() ?: pkg
                    records.add(AppRecord(pkg, label, info?.versionName ?: if (info == null) "未安装" else "—",
                        info != null, descriptions[pkg] ?: ""))
                }
            }
        }
    } catch (_: Exception) { return emptyList() }
    return records.sortedWith(compareByDescending<AppRecord> { it.installed }
        .thenComparator { a, b -> a.label.compareTo(b.label, ignoreCase = true) })
}

/** Compose app list backed by the existing scope and preferences. */
@Composable
fun AppsScreen(repository: SettingsRepository, bottomInset: Dp = 96.dp) {
    val context = LocalContext.current
    val apps by produceState<List<AppRecord>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { loadApps(context.applicationContext) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = apps?.find { it.pkg == selectedPackage }
    if (selected != null) {
        BackHandler { selectedPackage = null }
        RuleEditor(repository, selected.pkg, selected.label, onBack = { selectedPackage = null })
        return
    }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(repository) {
        repository.setObserver { revision++ }
        onDispose { repository.setObserver(null) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("适配应用", style = MaterialTheme.typography.headlineMedium)
            val data = apps
            Text(if (data == null) "正在读取作用域应用…" else "${data.count { it.installed }} / ${data.size} 个",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("搜索应用或包名") })
            Spacer(Modifier.height(16.dp))
            val q = query.trim()
            val filtered = data?.filter { q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true) }
            if (filtered != null && filtered.isEmpty()) {
                Text(if (data.isEmpty()) "scope.list 暂无作用域应用" else "没有匹配的应用",
                    modifier = Modifier.padding(vertical = 32.dp))
            }
            if (filtered != null) LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = bottomInset)) {
                items(filtered, key = { it.pkg }) { item ->
                    val enabled = remember(repository, item.pkg, revision) { repository.isHookEnabled(item.pkg) }
                    Row(Modifier.fillMaxWidth().sceneGlassPanel().clickable { selectedPackage = item.pkg }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(item.pkg, style = MaterialTheme.typography.bodySmall)
                            Text(if (item.installed) "v${item.version}" else "未安装", style = MaterialTheme.typography.bodySmall)
                            Text(item.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (item.installed) Switch(checked = enabled,
                            onCheckedChange = { repository.setHookEnabled(item.pkg, it) })
                    }
                }
            }
        }
    }
}
