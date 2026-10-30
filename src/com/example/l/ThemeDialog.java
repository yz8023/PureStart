package com.example.l;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/**
 * 外观点击弹窗设置界面 —— 进行适配应用主题液态玻璃 (ThemeDialog)
 * 允许用户选择：跟随系统 / 浅色液态玻璃 / 深色液态玻璃 / 极光蓝液态玻璃
 */
public final class ThemeDialog {

    public interface OnThemeSelectedListener {
        void onThemeSelected(int themeIndex);
    }

    private ThemeDialog() {}

    public static void show(final Context context, boolean isDark, final OnThemeSelectedListener listener) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = context.getResources().getDisplayMetrics().density;
        int dp24 = (int) (24 * density);
        int dp16 = (int) (16 * density);
        int dp12 = (int) (12 * density);

        FrameLayout root = new FrameLayout(context);
        root.setPadding(dp16, dp16, dp16, dp16);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp24, dp24, dp24, dp24);
        card.setBackground(LiquidGlass.glassCard(context, isDark, 28));

        TextView titleView = new TextView(context);
        titleView.setText("液态玻璃外观设置");
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(isDark ? 0xFFF4F4F5 : 0xFF18181B);
        card.addView(titleView);

        TextView subView = new TextView(context);
        subView.setText("选择适合您设备界面的液态玻璃高光与通透材质");
        subView.setTextSize(12);
        subView.setTextColor(isDark ? 0xFFA1A1AA : 0xFF71717A);
        subView.setPadding(0, (int) (4 * density), 0, dp16);
        card.addView(subView);

        final SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int currentTheme = sp.getInt("theme_mode", 0); // 0:跟随 1:浅色 2:深色 3:极光蓝

        final String[] options = {
                "跟随系统 (自适应浅色/深色玻璃)",
                "浅色液态玻璃 (高透乳白微光)",
                "深色液态玻璃 (深空磨砂玻璃)",
                "极光流体玻璃 (灵动蓝高透流光)"
        };

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView item = new TextView(context);
            item.setText(options[i]);
            item.setTextSize(14);
            item.setTextColor(isDark ? 0xFFF4F4F5 : 0xFF18181B);
            item.setPadding(dp12, dp12, dp12, dp12);

            boolean selected = (currentTheme == index);
            if (selected) {
                item.setBackground(LiquidGlass.glassPill(context, 0x333B82F6, 0xFF3B82F6));
                item.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                item.setBackground(LiquidGlass.glassPill(context, 0x11888888, 0));
            }

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sp.edit().putInt("theme_mode", index).apply();
                    dialog.dismiss();
                    if (listener != null) listener.onThemeSelected(index);
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * density);
            card.addView(item, lp);
        }

        root.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (320 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
}
