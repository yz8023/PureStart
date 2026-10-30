package com.example.l;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * 液态玻璃质感工具箱 (LiquidGlass)
 * 参考 yz8023/ui 液态玻璃规范：
 *  - 28dp+ 胶囊与多层圆角
 *  - 双层半透明白色/深色底色采样
 *  - 1dp 0.45 物理折射高光边框 (Rim/Highlight Border)
 */
public final class LiquidGlass {
    private LiquidGlass() {}

    /**
     * 生成液态玻璃卡片背景（独立窗口弹窗用退化版）
     * 模板规则：跨窗无法采样 backdrop → 退化为半透明蒙层，
     * 公式 = 0.26 + glassTintAlpha + opacity × 0.18（clamp 0..0.72），描边随 glassBorderAlpha。
     * @param isDark 是否深色模式
     * @param cornerRadiusDp 圆角 (dp)
     */
    public static GradientDrawable glassCard(Context context, boolean isDark, float cornerRadiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        android.content.SharedPreferences sp = context.getSharedPreferences(
                "settings", android.content.Context.MODE_PRIVATE);
        float opacity = clamp(sp.getInt("glass_opacity", 55) / 100f, 0f, 1f);
        float tint = clamp(sp.getInt("glass_tint_alpha", 8) / 100f, 0f, 0.35f);
        float border = clamp(sp.getInt("glass_border", 20) / 100f, 0f, 0.45f);
        float alpha = clamp(0.26f + tint + opacity * 0.18f, 0f, 0.72f);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(cornerRadiusDp * density);
        int surface = isDark ? 0x28292D : 0xF0F0F4;   // M3 surfaceContainer
        gd.setColor((Math.round(alpha * 255f) << 24) | surface);
        gd.setStroke((int) (1f * density), (Math.round(border * 255f) << 24) | 0xFFFFFF);
        return gd;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /**
     * 生成液态玻璃 Dock 栏背景
     */
    public static GradientDrawable glassDock(Context context, boolean isDark, float cornerRadiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(cornerRadiusDp * density);

        if (isDark) {
            gd.setColor(0xE6121215);
            gd.setStroke((int) (1.2f * density), 0x33FFFFFF);
        } else {
            gd.setColor(0xE6FFFFFF);
            gd.setStroke((int) (1.2f * density), 0x80FFFFFF);
        }
        return gd;
    }

    /**
     * 生成液态玻璃高光胶囊按钮/标签
     */
    public static GradientDrawable glassPill(Context context, int bgColor, int strokeColor) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(100f * density); // 药丸全圆角
        gd.setColor(bgColor);
        if (strokeColor != 0) {
            gd.setStroke((int) (1f * density), strokeColor);
        }
        return gd;
    }
}
