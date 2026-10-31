package com.yz8023.purestart.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.yz8023.purestart.Prefs
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 设置仓库（v15.2 第一阶段重构：Java → Kotlin）。
 *
 * 职责没变：**设置页唯一的偏好读写入口**，不加载任何 Hook 类
 * （见 `scripts/check.py` 的 UI/Hook 隔离守门）。
 *
 * 相对旧 Java 实现的三点变化：
 *  1. **同一个偏好监听同时驱动两种通知**：老的 `Observer` 回调（主线程）和新的
 *     `Flow`（[revision] 自增后重读）。界面可以按需选用，不会出现两套监听互相竞争；
 *  2. **复合键语义收进仓库**：`hook_<pkg>` / `sub_*_<pkg>` 这类前缀拼接不再散落在界面层，
 *     改成 [hookEnabledFlow] / [ruleEnabledFlow]，界面只传包名与规则；
 *  3. **生命周期显式**：实现了 `AutoCloseable`，`close()` 之后 Flow 与 Observer 都不再收到通知
 *     （旧实现在关闭后仍可能因主线程消息队列残留而回调）。
 *
 * 线程约定：偏好变化回调可能发生在任意线程，统一 `post` 回主线程后再通知，
 * 避免在回调里直接写 Compose 状态。
 */
class SettingsRepository(context: Context) : AutoCloseable {

    /** 广告拦截细分规则（前缀即偏好键前缀，与 `Prefs` 常量一一对应）。 */
    enum class Rule(val prefix: String) {
        SPLASH(Prefs.PREFIX_SUB_SPLASH),
        FEED(Prefs.PREFIX_SUB_FEED),
        POPUP(Prefs.PREFIX_SUB_POPUP),
        DEXFINDER(Prefs.PREFIX_SUB_DEXFINDER),
    }

    /** 老接口：任意偏好键变化时回调（主线程、多观察者并存）。 */
    fun interface Observer {
        fun onSettingChanged(key: String)
    }

    private val appContext: Context = context.applicationContext ?: context
    private val preferences: SharedPreferences = Prefs.sp(appContext)
    private val selfCheckPreferences: SharedPreferences =
        appContext.getSharedPreferences(Prefs.FILE_SELFCHECK, Context.MODE_PRIVATE)

    /**
     * v15.1.5 的修复保留：旧实现只有一个 observer 字段，四个页面各自 setObserver
     * 会互相顶掉，设置页从此收不到配置变化 → 开关像「点了没反应」。这里仍用多观察者。
     */
    private val observers = CopyOnWriteArrayList<Observer>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 每次偏好变化 +1，驱动所有 [Flow] 重读当前值（Flow 不各自注册监听）。 */
    private val revision = MutableStateFlow(0L)

    @Volatile
    private var closed = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> dispatch(key) }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        selfCheckPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun dispatch(key: String?) {
        val changed = key ?: return
        mainHandler.post {
            if (closed) return@post
            revision.update { it + 1 }
            for (observer in observers) {
                // 单个界面回调异常不能影响其他界面
                runCatching { observer.onSettingChanged(changed) }
            }
        }
    }

    // ---------------------------------------------------------------- 观察者

    /**
     * 以「替换」语义注册观察者（单页面使用的老接口）。
     * 多页面同时存活时请用 [addObserver] / [removeObserver]，否则会互相顶掉。
     */
    fun setObserver(observer: Observer?) {
        observers.clear()
        if (observer != null) observers.add(observer)
    }

    /** 注册观察者（可多个并存；建议在 DisposableEffect 里配对 [removeObserver]）。 */
    fun addObserver(observer: Observer?) {
        if (observer != null) observers.add(observer)
    }

    fun removeObserver(observer: Observer?) {
        if (observer != null) observers.remove(observer)
    }

    // ---------------------------------------------------------------- Flow 接口

    /**
     * 任意「读取动作」包成 Flow：偏好一变就重读，值不变不重复发射。
     * 读的是内存中的 SharedPreferences，因此不需要切线程。
     */
    fun <T> valueFlow(read: () -> T): Flow<T> = revision.map { read() }.distinctUntilChanged()

    fun hookEnabledFlow(packageName: String): Flow<Boolean> = valueFlow { isHookEnabled(packageName) }

    fun ruleEnabledFlow(packageName: String, rule: Rule): Flow<Boolean> =
        valueFlow { isRuleEnabled(packageName, rule) }

    /** 自检报告（`selfcheck` 文件里的 `pkg → 报告`），内容变化同样会推送。 */
    fun selfCheckFlow(packageName: String): Flow<String?> = valueFlow { selfCheck(packageName) }

    fun themeModeFlow(): Flow<Int> = valueFlow { themeMode() }

    fun liquidBlurFlow(): Flow<Boolean> = valueFlow { liquidBlurEnabled() }

    fun appBadgeFlow(): Flow<Boolean> = valueFlow { appBadgeEnabled() }

    // ---------------------------------------------------------------- 读写

    fun isHookEnabled(packageName: String): Boolean = Prefs.hookEnabled(appContext, packageName)

    fun setHookEnabled(packageName: String, enabled: Boolean) {
        Prefs.setHookEnabled(appContext, packageName, enabled)
    }

    fun isRuleEnabled(packageName: String, rule: Rule): Boolean =
        Prefs.subEnabled(appContext, rule.prefix, packageName)

    fun setRuleEnabled(packageName: String, rule: Rule, enabled: Boolean) {
        Prefs.setSubEnabled(appContext, rule.prefix, packageName, enabled)
    }

    /** Same read-only report produced by the existing exported provider. */
    fun selfCheck(packageName: String): String? = Prefs.readSelfCheck(appContext, packageName)

    fun themeMode(): Int = Prefs.themeMode(appContext)

    fun setThemeMode(mode: Int) = Prefs.setThemeMode(appContext, mode)

    fun localeMode(): Int = Prefs.localeMode(appContext)

    fun liquidBlurEnabled(): Boolean = Prefs.liquidBlurEnabled(appContext)

    fun appBadgeEnabled(): Boolean = Prefs.appBadgeEnabled(appContext)

    fun autoUpdateEnabled(): Boolean = Prefs.autoUpdateEnabled(appContext)

    override fun close() {
        if (closed) return
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        selfCheckPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        observers.clear()
    }
}
