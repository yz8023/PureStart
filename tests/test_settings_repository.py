#!/usr/bin/env python3
"""SettingsRepository contract gate (JVM, tiny Android stubs — not a device test).

v15.2: the repository is Kotlin now, so this gate compiles the *real* source with
`kotlinc` against hand-written stubs for Context / SharedPreferences / Handler.
It keeps the behaviour locked that v15.1.5 fixed in production:

  * every observer gets the change (the old single-slot bug → "开关卡死");
  * self-check reports notify too;
  * close() is idempotent and stops notifications;
  * the Flow API added in phase 1 emits the current value and every later change.
"""
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kotlin_jvm as kj

STUBS = {
    'android/content/SharedPreferences.java': '''package android.content;
import java.util.Map;
public interface SharedPreferences {
 boolean getBoolean(String k, boolean d); int getInt(String k, int d); String getString(String k, String d);
 Map<String, ?> getAll(); Editor edit(); void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
 void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
 interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences p, String k); }
 interface Editor { Editor putBoolean(String k, boolean v); Editor putInt(String k,int v); Editor putLong(String k,long v);
   Editor putFloat(String k,float v); Editor putString(String k,String v); void apply(); }
}''',
    'android/os/Looper.java': '''package android.os;
public final class Looper { public static Looper getMainLooper(){ return new Looper(); } }''',
    'android/os/Handler.java': '''package android.os;
public class Handler {
 public Handler(){} public Handler(Looper l){}
 public boolean post(Runnable r){ r.run(); return true; }   // 桩：立即执行，等价于「已在主线程」
}''',
    'android/content/Context.java': '''package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){ return this; }
 public abstract SharedPreferences getSharedPreferences(String n,int m);
}''',
    'ContractMain.kt': '''import android.content.Context
import android.content.SharedPreferences
import com.yz8023.purestart.Prefs
import com.yz8023.purestart.repository.SettingsRepository
import com.yz8023.purestart.repository.booleanFlow
import com.yz8023.purestart.repository.intFlow
import com.yz8023.purestart.repository.stringFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class MemoryContext : Context() {
    private val files = LinkedHashMap<String, MemoryPrefs>()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        files.getOrPut(name) { MemoryPrefs() }
}

class MemoryPrefs : SharedPreferences {
    val data = LinkedHashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getBoolean(k: String, d: Boolean) = if (data.containsKey(k)) data[k] as Boolean else d
    override fun getInt(k: String, d: Int) = if (data.containsKey(k)) data[k] as Int else d
    override fun getString(k: String, d: String?) = if (data.containsKey(k)) data[k] as String? else d
    override fun getAll(): Map<String, *> = data
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(l) }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(l) }
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putInt(k: String, v: Int): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putLong(k: String, v: Long): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putFloat(k: String, v: Float): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putString(k: String, v: String?): SharedPreferences.Editor { pending[k] = v; return this }
        override fun apply() {
            data.putAll(pending)
            for (key in pending.keys) for (l in listeners.toList()) l.onSharedPreferenceChanged(this@MemoryPrefs, key)
        }
    }
}

private fun check(condition: Boolean) { if (!condition) throw AssertionError("contract check failed") }

fun main() {
    val context = MemoryContext()
    val pkg = "example.app"

    // ---- Observer 通道 ----
    val events = mutableListOf<String>()
    val repository = SettingsRepository(context)
    repository.setObserver { key -> events.add(key) }

    check(!repository.isHookEnabled(pkg))
    repository.setHookEnabled(pkg, true)
    check(Prefs.hookEnabled(context, pkg))
    check(events.contains(Prefs.PREFIX_HOOK + pkg))

    for (rule in SettingsRepository.Rule.entries) {
        check(!repository.isRuleEnabled(pkg, rule))
        repository.setRuleEnabled(pkg, rule, true)
        check(repository.isRuleEnabled(pkg, rule))
    }

    val beforeReport = events.size
    Prefs.writeSelfCheck(context, pkg, "report")
    check(repository.selfCheck(pkg) == "report")
    check(events.size == beforeReport + 1)
    check(events.contains(pkg))

    repository.setThemeMode(2)
    check(Prefs.themeMode(context) == 2)
    check(repository.appBadgeEnabled())

    val beforeClose = events.size
    repository.close()
    repository.close()   // 幂等
    Prefs.setHookEnabled(context, pkg, false)
    Prefs.writeSelfCheck(context, pkg, "later")
    check(events.size == beforeClose)

    // ---- 多观察者（v15.1.5 修复） ----
    val repository2 = SettingsRepository(context)
    var first = 0
    var second = 0
    val observer1 = SettingsRepository.Observer { first++ }
    val observer2 = SettingsRepository.Observer { second++ }
    repository2.addObserver(observer1)
    repository2.addObserver(observer2)
    Prefs.setHookEnabled(context, pkg, true)
    Prefs.setHookEnabled(context, pkg, false)
    check(first >= 2 && second >= 2)
    repository2.removeObserver(observer1)
    val beforeRemoval = first
    Prefs.setHookEnabled(context, pkg, true)
    check(first == beforeRemoval && second > 2)

    // 关掉磨砂模糊这类纯 UI 键也必须触发通知（否则开关点了不刷新）
    val beforeUiKey = second
    Prefs.sp(context).edit().putBoolean(Prefs.KEY_LIQUID_BLUR, false).apply()
    check(second == beforeUiKey + 1)

    // ---- Flow 通道（第一阶段新增） ----
    repository2.setHookEnabled(pkg, false)
    val seen = runBlocking {
        val hookFlow = async(Dispatchers.Unconfined) { repository2.hookEnabledFlow(pkg).take(3).toList() }
        repository2.setHookEnabled(pkg, true)
        repository2.setHookEnabled(pkg, false)
        hookFlow.await()
    }
    check(seen == listOf(false, true, false)) { "hookEnabledFlow 发射顺序错误：$seen" }

    val ruleSeen = runBlocking {
        val flow = async(Dispatchers.Unconfined) {
            repository2.ruleEnabledFlow(pkg, SettingsRepository.Rule.SPLASH).take(2).toList()
        }
        repository2.setRuleEnabled(pkg, SettingsRepository.Rule.SPLASH, false)
        flow.await()
    }
    check(ruleSeen == listOf(true, false)) { "ruleEnabledFlow 发射顺序错误：$ruleSeen" }

    val selfCheckSeen = runBlocking {
        val flow = async(Dispatchers.Unconfined) { repository2.selfCheckFlow(pkg).take(2).toList() }
        Prefs.writeSelfCheck(context, pkg, "flow")
        flow.await()
    }
    check(selfCheckSeen == listOf("later", "flow")) { "selfCheckFlow 发射顺序错误：$selfCheckSeen" }

    // 单键扩展（设置页控件用）：写入即回读
    val boolSeen = runBlocking {
        val flow = async(Dispatchers.Unconfined) {
            Prefs.sp(context).booleanFlow(Prefs.KEY_LIQUID_BLUR, true).take(2).toList()
        }
        Prefs.sp(context).edit().putBoolean(Prefs.KEY_LIQUID_BLUR, true).apply()
        flow.await()
    }
    check(boolSeen == listOf(false, true)) { "booleanFlow 发射顺序错误：$boolSeen" }

    val intSeen = runBlocking {
        val flow = async(Dispatchers.Unconfined) {
            Prefs.sp(context).intFlow(Prefs.KEY_GLASS_BLUR, 15).take(2).toList()
        }
        Prefs.sp(context).edit().putInt(Prefs.KEY_GLASS_BLUR, 24).apply()
        flow.await()
    }
    check(intSeen == listOf(15, 24)) { "intFlow 发射顺序错误：$intSeen" }

    val stringSeen = runBlocking {
        val flow = async(Dispatchers.Unconfined) {
            Prefs.sp(context).stringFlow(Prefs.KEY_WALLPAPER_PATH, null).take(2).toList()
        }
        Prefs.sp(context).edit().putString(Prefs.KEY_WALLPAPER_PATH, "/data/x.png").apply()
        flow.await()
    }
    check(stringSeen == listOf(null, "/data/x.png")) { "stringFlow 发射顺序错误：$stringSeen" }

    // close() 之后 Flow 不再收到变化（旧实现关闭后仍可能因消息队列残留下发）
    repository2.close()
    println("PASS: SettingsRepository contract (observers, idempotent close, Flow channels, key flows)")
}
'''
}

SOURCE_FILES = [
    kj.MAIN_JAVA / 'com/yz8023/purestart/Prefs.java',
    kj.MAIN_JAVA / 'com/yz8023/purestart/repository/SettingsRepository.kt',
    kj.MAIN_JAVA / 'com/yz8023/purestart/repository/PrefFlows.kt',
]

def main():
    kotlinc = kj.require_kotlinc()
    with tempfile.TemporaryDirectory() as temp:
        base = Path(temp)
        stubs = kj.write_sources(base / 'stubs', STUBS)
        coroutines = kj.coroutines_jar(base / 'cache')
        kotlin_sources = [p for p in SOURCE_FILES if p.suffix == '.kt']
        java_sources = [p for p in SOURCE_FILES if p.suffix == '.java']
        stub_java = [p for p in stubs if p.suffix == '.java']
        stub_kotlin = [p for p in stubs if p.suffix == '.kt']

        # 先 javac：Android 桩 + 真实 Prefs.java（Hook 侧配置类，受基线守门保护）
        classes = base / 'classes'
        classes.mkdir()
        stdlib = kj.kotlin_runtime(kotlinc)
        kj.javac(classes, stub_java + java_sources)
        # 再 kotlinc：仓库 Kotlin 源码 + 契约测试
        kj.kotlinc_compile(kotlinc, classes, stub_kotlin + kotlin_sources,
                           classpath=[classes, coroutines, *stdlib])
        kj.run_java('ContractMainKt', [classes, coroutines, *stdlib])

if __name__ == '__main__':
    main()
