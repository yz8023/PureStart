package com.yz8023.purestart.ui;

import android.content.Context;
import android.content.SharedPreferences;
import com.yz8023.purestart.Prefs;

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
    private Observer observer;
    private boolean closed;

    public SettingsRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = Prefs.sp(this.context);
        this.selfCheckPreferences = this.context.getSharedPreferences(Prefs.FILE_SELFCHECK, Context.MODE_PRIVATE);
        this.listener = (sharedPreferences, key) -> {
            Observer current = observer;
            if (current != null) current.onSettingChanged(key);
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        selfCheckPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    /** The observer should refresh its state by reading from this repository. */
    public void setObserver(Observer observer) { this.observer = observer; }
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
            observer = null;
        }
    }
}
