#!/usr/bin/env python3
"""BackupRepository contract gate (JVM, in-memory Android stubs + real org.json).

v15.2: the repository is Kotlin now; this gate compiles the real source with
`kotlinc` against stubs for Context / SharedPreferences / ContentResolver / Uri.
It locks the JSON format and merge semantics that users' existing backup files
depend on:

  * export only writes Boolean/Int/Long/Float/String, everything else is skipped;
  * import is an **incremental merge** — keys missing from the JSON keep their value;
  * invalid JSON → JSONException, missing stream → IOException, and a failed import
    must not touch existing preferences.
"""
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kotlin_jvm as kj

STUBS = {
    'android/net/Uri.java': '''package android.net; public class Uri {}''',
    'android/content/ContentResolver.java': '''package android.content;
import java.io.*; import android.net.Uri;
public abstract class ContentResolver {
 public abstract InputStream openInputStream(Uri uri) throws IOException;
 public abstract OutputStream openOutputStream(Uri uri) throws IOException; }''',
    'android/content/Context.java': '''package android.content;
public abstract class Context { public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){return this;}
 public abstract SharedPreferences getSharedPreferences(String n,int m);
 public abstract ContentResolver getContentResolver(); }''',
    'android/content/SharedPreferences.java': '''package android.content; import java.util.Map;
public interface SharedPreferences {
 boolean getBoolean(String k,boolean d); int getInt(String k,int d); String getString(String k,String d);
 Map<String,?> getAll(); Editor edit();
 interface Editor { Editor putBoolean(String k,boolean v); Editor putInt(String k,int v); Editor putLong(String k,long v);
  Editor putFloat(String k,float v); Editor putString(String k,String v); void apply(); } }''',
    'BackupMain.kt': '''import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.yz8023.purestart.Prefs
import com.yz8023.purestart.repository.BackupRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONException

class TestContext : Context() {
    private val files = LinkedHashMap<String, TestPrefs>()
    val resolver = TestResolver()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        files.getOrPut(name) { TestPrefs() }
    override fun getContentResolver(): ContentResolver = resolver
}

class TestResolver : ContentResolver() {
    var data: ByteArray? = null
    var nullRead = false
    var nullWrite = false
    override fun openInputStream(uri: Uri): InputStream? =
        if (nullRead) null else ByteArrayInputStream(data ?: ByteArray(0))
    override fun openOutputStream(uri: Uri): OutputStream? {
        if (nullWrite) return null
        return object : ByteArrayOutputStream() {
            override fun close() { data = toByteArray() }
        }
    }
}

class TestPrefs : SharedPreferences {
    val values = LinkedHashMap<String, Any?>()
    override fun getAll(): Map<String, *> = values
    override fun getBoolean(k: String, d: Boolean) = if (values.containsKey(k)) values[k] as Boolean else d
    override fun getInt(k: String, d: Int) = if (values.containsKey(k)) values[k] as Int else d
    override fun getString(k: String, d: String?) = if (values.containsKey(k)) values[k] as String? else d
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val writes = LinkedHashMap<String, Any?>()
        override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor { writes[k] = v; return this }
        override fun putInt(k: String, v: Int): SharedPreferences.Editor { writes[k] = v; return this }
        override fun putLong(k: String, v: Long): SharedPreferences.Editor { writes[k] = v; return this }
        override fun putFloat(k: String, v: Float): SharedPreferences.Editor { writes[k] = v; return this }
        override fun putString(k: String, v: String?): SharedPreferences.Editor { writes[k] = v; return this }
        override fun apply() { values.putAll(writes) }
    }
}

private fun check(condition: Boolean, message: String = "backup contract check failed") {
    if (!condition) throw AssertionError(message)
}

fun main() {
    val source = TestContext()
    val uri = Uri()
    val from = Prefs.sp(source) as TestPrefs
    from.values["switch"] = true
    from.values["number"] = 4
    from.values["text"] = "中文"
    from.values["unsupported"] = listOf("x")   // 非基本类型：导出时必须跳过

    val exporter = BackupRepository(source)
    check(exporter.exportTo(uri) == 3, "只应导出三个受支持的键")
    val payload = String(source.resolver.data!!, Charsets.UTF_8)
    check(payload.contains("中文"), "导出必须保留 UTF-8 中文")

    val destination = TestContext()
    destination.resolver.data = source.resolver.data
    val to = Prefs.sp(destination) as TestPrefs
    to.values["keep"] = "untouched"
    to.values["switch"] = false
    check(BackupRepository(destination).importFrom(uri) == 3, "导入应应用三个键")
    check(to.values["switch"] == true, "已有的键必须被覆盖")
    check(to.values["number"] == 4, "整数类型必须保持 Int")
    check(to.values["text"] == "中文", "字符串必须保持原样")
    check(to.values["keep"] == "untouched", "JSON 里没有的键必须保持原值（增量合并）")

    destination.resolver.data = "not json".toByteArray(Charsets.UTF_8)
    var threwJson = false
    try { BackupRepository(destination).importFrom(uri) } catch (expected: JSONException) { threwJson = true }
    check(threwJson, "非法 JSON 必须抛 JSONException")
    check(to.values["keep"] == "untouched", "失败的导入不得修改现有偏好")

    destination.resolver.nullRead = true
    var threwRead = false
    try { BackupRepository(destination).importFrom(uri) } catch (expected: IOException) { threwRead = true }
    check(threwRead, "读不到流必须抛 IOException")

    source.resolver.nullWrite = true
    var threwWrite = false
    try { exporter.exportTo(uri) } catch (expected: IOException) { threwWrite = true }
    check(threwWrite, "写不到流必须抛 IOException")

    println("PASS: BackupRepository contract (format, incremental merge, invalid JSON, I/O failures)")
}
'''
}

SOURCES = [
    kj.MAIN_JAVA / 'com/yz8023/purestart/Prefs.java',
    kj.MAIN_JAVA / 'com/yz8023/purestart/repository/BackupRepository.kt',
]


def main():
    kotlinc = kj.require_kotlinc()
    with tempfile.TemporaryDirectory() as temp:
        base = Path(temp)
        stubs = kj.write_sources(base / 'stubs', STUBS)
        kotlin_sources = [p for p in SOURCES if p.suffix == '.kt']
        java_sources = [p for p in SOURCES if p.suffix == '.java']
        stub_java = [p for p in stubs if p.suffix == '.java']
        stub_kotlin = [p for p in stubs if p.suffix == '.kt']

        json_jar = kj.download(
            'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',
            base / 'cache/json-20240303.jar')
        classes = base / 'classes'
        classes.mkdir()
        stdlib = kj.kotlin_runtime(kotlinc)
        kj.javac(classes, stub_java + java_sources)
        kj.kotlinc_compile(kotlinc, classes, stub_kotlin + kotlin_sources,
                           classpath=[classes, json_jar, *stdlib])
        kj.run_java('BackupMainKt', [classes, json_jar, *stdlib])


if __name__ == '__main__':
    main()
