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
     * 生成液态玻璃卡片背景
     * @param isDark 是否深色模式
     * @param cornerRadiusDp 圆角 (dp)
     */
    public static GradientDrawable glassCard(Context context, boolean isDark, float cornerRadiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(cornerRadiusDp * density);

        if (isDark) {
            // 深色液态玻璃：高透深灰底色 + 浅白物理微光边框
            gd.setColor(0xCC18181B);
            gd.setStroke((int) (1f * density), 0x26FFFFFF);
        } else {
            // 浅色液态玻璃：极高通透乳白 + 高光边框
            gd.setColor(0xD9FFFFFF);
            gd.setStroke((int) (1f * density), 0x4DFFFFFF);
        }
        return gd;
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
