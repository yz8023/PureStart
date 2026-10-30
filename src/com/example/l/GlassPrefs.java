package com.example.l;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 磨砂玻璃（View 近似）参数单点收口（单一事实来源）。
 *
 * 此前 glass_* 偏好键在 SettingsActivity 十余处直接读取，无集中配置对象；
 * 现全部经此类进出（对标模板 AppSettingsState 的收口做法）。
 * 取值范围/默认值与 ui 模板设置页一致，但行为映射（棱光厚度/放大倍数等）
 * 是自创近似映射，模板中不存在对应关系（详见设置页差异声明）。
 */
public final class GlassPrefs {
    private GlassPrefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** 磨砂总开关（关闭=纯半透明，无捕获模糊） */
    public static boolean liquidBlurEnabled(Context c) {
        return sp(c).getBoolean("set_liquid_blur", true);
    }

    /** 不透明度 0..1（容器 alpha = 0.10 + opacity × 0.55） */
    public static float opacity(Context c) {
        return clamp(sp(c).getInt("glass_opacity", 55) / 100f, 0f, 1f);
    }

    /** 玻璃染色 0..0.35（默认 0.08） */
    public static float tintAlpha(Context c) {
        return clamp(sp(c).getInt("glass_tint_alpha", 8) / 100f, 0f, 0.35f);
    }

    /** 模糊强度 dp 0..40（默认 0） */
    public static int blurDp(Context c) {
        return (int) clamp(sp(c).getInt("glass_blur", 0), 0, 40);
    }

    /** 折射高度 dp 0..40（默认 15；View 近似=棱光厚度） */
    public static int prismHeightDp(Context c) {
        return (int) clamp(sp(c).getInt("glass_refract_height", 15), 0, 40);
    }

    /** 折射幅度 dp 0..60（默认 20；View 近似=背景层放大） */
    public static int prismAmountDp(Context c) {
        return (int) clamp(sp(c).getInt("glass_refract_amount", 20), 0, 60);
    }

    /** 高光边框 0..0.45（默认 0.20） */
    public static float borderAlpha(Context c) {
        return clamp(sp(c).getInt("glass_border", 20) / 100f, 0f, 0.45f);
    }

    /** 动效阻尼 0..1（默认 0.5） */
    public static float damping(Context c) {
        return clamp(sp(c).getInt("glass_damping", 50) / 100f, 0f, 1f);
    }

    /** 增强色彩层次（饱和度 +18%） */
    public static boolean vibrancy(Context c) {
        return sp(c).getBoolean("glass_vibrancy", true);
    }

    /** 色差折射（棱光 RGB 色散近似） */
    public static boolean chromatic(Context c) {
        return sp(c).getBoolean("glass_chromatic", true);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
