package com.example.l;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * 磨砂玻璃（View 近似）质感工具箱
 * 命名遵循 yz8023/ui prompts/03 路线 B 规矩：View 项目交付物称「磨砂玻璃（View 近似）」，
 * 参数取值可借鉴模板（圆角/透明度/高光），但无 backdrop 实时采样与 lens 折射。
 *  - 28dp+ 胶囊与多层圆角
 *  - 半透明 surface 底色 + 1dp 高光描边
 *  - API 31+ 弹窗用系统窗口背景模糊（真磨砂），以下退化半透明蒙层
 */
public final class LiquidGlass {
    private LiquidGlass() {}

    /**
     * 系统窗口磨砂（prompts/03「近似实现优先用系统能力」）：
     * Android 12+ 用 Window.setBackgroundBlurRadius 对弹窗身后的内容做真背景模糊；
     * 12 以下无效果（窗口背景保持半透明蒙层，由调用方的 glassCard 退化公式兜底）。
     */
    public static void applyFrostedWindow(android.view.Window w, float density) {
        if (w == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                w.setBackgroundBlurRadius((int) (20 * density));
            }
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.35f);
        } catch (Throwable ignored) {}
    }

    /**
     * 生成磨砂玻璃卡片背景（独立窗口弹窗用退化版）
     * View 无法跨窗采样 → 退化为半透明蒙层（API 31+ 配合 applyFrostedWindow 为真磨砂），
     * 公式 = 0.26 + glassTintAlpha + opacity × 0.18（clamp 0..0.72），描边随 glassBorderAlpha。
     * @param isDark 是否深色模式
     * @param cornerRadiusDp 圆角 (dp)
     */
    public static GradientDrawable glassCard(Context context, boolean isDark, float cornerRadiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        float opacity = GlassPrefs.opacity(context);
        float tint = GlassPrefs.tintAlpha(context);
        float border = GlassPrefs.borderAlpha(context);
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
