package com.yz8023.purestart.repository

import android.content.Context
import android.net.Uri
import com.yz8023.purestart.Prefs
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

/**
 * 配置备份/恢复（v15.2 第一阶段重构：Java → Kotlin）。
 *
 * **JSON 格式与合并语义与旧实现完全一致**（`tests/test_backup_repository.py` 逐条核对）：
 *  - 导出：只写 Boolean / Int / Long / Float / String 五种类型，其余键跳过；
 *  - 导入：逐键 put 后一次性 `apply()`，未出现在 JSON 里的键保持原值（增量合并，不整体覆盖）；
 *  - 非法 JSON 抛 [JSONException]、读写流为空抛 [IOException]，调用方自己弹提示。
 *
 * 不依赖 UI，也不加载 Hook 类。
 */
class BackupRepository(context: Context) {

    private val appContext: Context = context.applicationContext ?: context

    /** 导出全部偏好为 JSON，返回写入的键数量（旧实现返回 `JSONObject.length()`，语义相同）。 */
    @Throws(IOException::class, JSONException::class)
    fun exportTo(uri: Uri): Int {
        val json = JSONObject()
        for ((key, value) in Prefs.sp(appContext).all) {
            if (value is Boolean || value is Int || value is Long || value is Float || value is String) {
                json.put(key, value)
            }
        }
        val opened = appContext.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法写入")
        opened.use { stream ->
            stream.write(json.toString().toByteArray(StandardCharsets.UTF_8))
        }
        return json.length()
    }

    /** 从 JSON 增量合并偏好，返回实际应用的键数量。 */
    @Throws(IOException::class, JSONException::class)
    fun importFrom(uri: Uri): Int {
        val opened = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取")
        val buffer = ByteArrayOutputStream()
        opened.use { stream ->
            val bytes = ByteArray(4096)
            while (true) {
                val count = stream.read(bytes)
                if (count <= 0) break
                buffer.write(bytes, 0, count)
            }
        }
        val json = JSONObject(String(buffer.toByteArray(), StandardCharsets.UTF_8))
        val editor = Prefs.sp(appContext).edit()
        var count = 0
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = json.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                else -> continue
            }
            count++
        }
        editor.apply()
        return count
    }
}
