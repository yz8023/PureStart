package com.yz8023.purestart.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.yz8023.purestart.Prefs;

import java.util.concurrent.CopyOnWriteArrayList;

/** UI-facing adapter for the existing settings store. Does not load Hook classes. */
public final class SettingsRepository implements AutoCloseable {
    public enum Rule {
        SPLASH(Prefs.PREFIX_SUB_SPLASH), FEED(Prefs.PREFIX_SUB_FEED),
        POPUP(Prefs.PREFIX_SUB_POPUP), DEXFINDER(Prefs.PREFIX_SUB_DEXFINDER);
        final String prefix;
        Rule(String prefix) { this.prefix = prefix; }
    }

    public interface Observer { void onSettingChanged(String key); }

    private final Context context;
    private final SharedPreferences preferences;
    private final SharedPreferences selfCheckPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    /**
     * v15.1.5 修复：原先只有一个 observer 字段，四个页面各自 setObserver 会互相顶掉，
     * 设置页从此收不到任何配置变化 → 开关像是「点了没反应/卡死」。改为支持多个观察者。
     */
    private final CopyOnWriteArrayList<Observer> observers = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean closed;

    public SettingsRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = Prefs.sp(this.context);
        this.selfCheckPreferences = this.context.getSharedPreferences(Prefs.FILE_SELFCHECK, Context.MODE_PRIVATE);
        this.listener = (sharedPreferences, key) -> {
            // 偏好变化回调可能不在主线程：统一切回主线程再通知，
            // 避免在回调里直接写 Compose 状态。
            mainHandler.post(() -> {
                if (closed) return;
                for (Observer current : observers) {
                    try {
                        current.onSettingChanged(key);
                    } catch (Throwable ignored) {
                        // 单个界面回调异常不能影响其他界面
                    }
                }
            });
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        selfCheckPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * 以「替换」语义注册观察者（单页面使用的老接口）。
     * 多页面同时存活时请用 addObserver / removeObserver，否则会互相顶掉。
     */
    public void setObserver(Observer observer) {
        observers.clear();
        if (observer != null) observers.add(observer);
    }

    /** 注册观察者（可多个并存；建议在 DisposableEffect 里配对 removeObserver）。 */
    public void addObserver(Observer observer) {
        if (observer != null) observers.add(observer);
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }
    public boolean isHookEnabled(String packageName) { return Prefs.hookEnabled(context, packageName); }
    public void setHookEnabled(String packageName, boolean enabled) {
        Prefs.setHookEnabled(context, packageName, enabled);
    }
    public boolean isRuleEnabled(String packageName, Rule rule) {
        return Prefs.subEnabled(context, rule.prefix, packageName);
    }
    public void setRuleEnabled(String packageName, Rule rule, boolean enabled) {
        Prefs.setSubEnabled(context, rule.prefix, packageName, enabled);
    }
    /** Same read-only report produced by the existing exported provider. */
    public String selfCheck(String packageName) { return Prefs.readSelfCheck(context, packageName); }
    public int themeMode() { return Prefs.themeMode(context); }
    public void setThemeMode(int mode) { Prefs.setThemeMode(context, mode); }
    public int localeMode() { return Prefs.localeMode(context); }
    public boolean liquidBlurEnabled() { return Prefs.liquidBlurEnabled(context); }
    public boolean appBadgeEnabled() { return Prefs.appBadgeEnabled(context); }
    public boolean autoUpdateEnabled() { return Prefs.autoUpdateEnabled(context); }

    @Override public void close() {
        if (!closed) {
            closed = true;
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
            selfCheckPreferences.unregisterOnSharedPreferenceChangeListener(listener);
            observers.clear();
        }
    }
}
