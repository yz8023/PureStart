package com.yz8023.purestart.repository

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 把 SharedPreferences 包成 Flow（v15.1.5 引入，第一阶段重构后归入 `repository` 包）。
 *
 * 为什么需要：设置页原来的控件只在**首次组合**时读一次偏好
 * （`remember(...) { prefs.getBoolean(...) }`），点击后如果状态没有重新读取，Switch 会
 * 弹回去、滑杆不跟手，看起来就是「开关卡死」。
 * 现在每个控件订阅自己的键，写入即回读，状态始终一致。
 *
 * 与 [SettingsRepository] 的关系：
 *  - 单键订阅（控件自己知道键名）用这里的扩展函数，注册/注销随 Composable 生命周期；
 *  - 需要「按键前缀 + 包名」这类复合语义（hook 开关、规则开关）时用
 *    `SettingsRepository.hookEnabledFlow(...)` 之类的封装，不要在外层重复拼键名。
 */

/** 布尔键：订阅单个键，写入即回读。 */
fun SharedPreferences.booleanFlow(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
    trySend(getBoolean(key, default))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
        if (changed == key) trySend(prefs.getBoolean(key, default))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()

/** 整数键（滑杆 / 枚举档位）：订阅单个键，写入即回读。 */
fun SharedPreferences.intFlow(key: String, default: Int): Flow<Int> = callbackFlow {
    trySend(getInt(key, default))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
        if (changed == key) trySend(prefs.getInt(key, default))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()

/** 字符串键（路径 / 状态文案）：订阅单个键，写入即回读。 */
fun SharedPreferences.stringFlow(key: String, default: String? = null): Flow<String?> = callbackFlow {
    trySend(getString(key, default))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
        if (changed == key) trySend(prefs.getString(key, default))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
