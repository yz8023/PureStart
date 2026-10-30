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
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * 适配应用点击打开子项 —— 磨砂玻璃（View 近似）详情弹窗 (AppDetailDialog)
 * 展示开屏广告、信息流广告、弹窗/激励视频、DexFinder 特征自动匹配等多项细分开关。
 */
public final class AppDetailDialog {

    public interface OnSettingChangedListener {
        void onChanged();
    }

    private AppDetailDialog() {}

    public static void show(Context context, final String pkg, String appName, String desc, final boolean isDark, final OnSettingChangedListener listener) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = context.getResources().getDisplayMetrics().density;
        int dp24 = (int) (24 * density);
        int dp16 = (int) (16 * density);
        int dp12 = (int) (12 * density);
        int dp8 = (int) (8 * density);

        FrameLayout root = new FrameLayout(context);
        root.setPadding(dp16, dp16, dp16, dp16);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp24, dp24, dp24, dp24);
        card.setBackground(LiquidGlass.glassCard(context, isDark, 28));

        // 顶部标题与包名
        TextView titleView = new TextView(context);
        titleView.setText(appName);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(isDark ? 0xFFF4F4F5 : 0xFF18181B);
        card.addView(titleView);

        TextView subView = new TextView(context);
        subView.setText(pkg + " · 磨砂玻璃细分配置");
        subView.setTextSize(12);
        subView.setTextColor(isDark ? 0xFFA1A1AA : 0xFF71717A);
        subView.setPadding(0, (int) (4 * density), 0, dp16);
        card.addView(subView);

        // 细分规则列表
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        final SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);

        // 子项 1: 开屏冷热启动去广告
        list.addView(createSwitchRow(context, isDark, "开屏冷热启动广告", "阻止启动屏倒计时与广告卡片渲染", "sub_splash_" + pkg, sp, listener));
        // 子项 2: 信息流与横幅净化
        list.addView(createSwitchRow(context, isDark, "信息流与横幅净化", "隐藏推荐列表、动态流卡片中的广告项", "sub_feed_" + pkg, sp, listener));
        // 子项 3: 弹窗与激励视频阻断
        list.addView(createSwitchRow(context, isDark, "营销弹窗与全屏插播", "拦截自动弹出的活动推广与悬浮广告", "sub_popup_" + pkg, sp, listener));
        // 子项 4: DexFinder 自动特征模糊匹配
        list.addView(createSwitchRow(context, isDark, "DexFinder 自动特征识别", "版本更新混淆后通过字节码特征自动找回拦截点", "sub_dexfinder_" + pkg, sp, listener));

        scroll.addView(list, new LinearLayout.LayoutParams(-1, (int) (240 * density)));
        card.addView(scroll);

        // 底部关闭按钮（液态高光胶囊）
        TextView closeBtn = new TextView(context);
        closeBtn.setText("完成并保存");
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(0, dp12, 0, dp12);
        closeBtn.setBackground(LiquidGlass.glassPill(context, 0xFF3B82F6, 0x4DFFFFFF));
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.topMargin = dp16;
        card.addView(closeBtn, btnLp);

        root.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            LiquidGlass.applyFrostedWindow(dialog.getWindow(), density);
            dialog.getWindow().setLayout((int) (340 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static View createSwitchRow(Context context, boolean isDark, String title, String desc, final String key, final SharedPreferences sp, final OnSettingChangedListener listener) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(isDark ? 0xFFF4F4F5 : 0xFF18181B);
        texts.addView(t);

        TextView d = new TextView(context);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(isDark ? 0xFFA1A1AA : 0xFF71717A);
        texts.addView(d);

        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch sw = new Switch(context);
        sw.setChecked(sp.getBoolean(key, false));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean(key, isChecked).apply();
                if (listener != null) listener.onChanged();
            }
        });
        row.addView(sw);

        return row;
    }
}
