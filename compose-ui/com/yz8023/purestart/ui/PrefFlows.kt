package com.yz8023.purestart.ui

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 把 SharedPreferences 包成 Flow（v15.1.5）。
 *
 * 为什么需要：设置页原来的控件只在**首次组合**时读一次偏好
 * （`remember(...) { prefs.getBoolean(...) }`），点击后如果状态没有重新读取，
 * Switch 会弹回去、滑杆不跟手，看起来就是「开关卡死」。
 * 现在每个控件订阅自己的键，写入即回读，状态始终一致。
 */
fun SharedPreferences.booleanFlow(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
    trySend(getBoolean(key, default))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
        if (changed == key) trySend(prefs.getBoolean(key, default))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()

fun SharedPreferences.intFlow(key: String, default: Int): Flow<Int> = callbackFlow {
    trySend(getInt(key, default))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
        if (changed == key) trySend(prefs.getInt(key, default))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
