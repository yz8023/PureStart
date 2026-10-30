package com.example.l;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 玻璃细则配置弹窗 —— 对标 yz8023/ui 模板 AppChrome 玻璃参数（View 近似）。
 *
 * 同名参数逐一对齐模板：
 *  - glassOpacity        玻璃不透明度 0..1（默认 0.55）：容器底色 alpha = 0.10 + opacity × 0.55
 *  - glassBlurRadiusDp   玻璃模糊半径 dp（底栏默认 4dp，即模板 blur(4dp)）
 *  - glassRefraction     折射强度 0..1（默认 1.0）：View 近似中驱动边缘亮棱与镜面高光
 *  - glassBorderAlpha    高光描边 0..0.8（默认 0.20）：卡片高光描边强度
 *  - motionDamping       动效阻尼 0..1（默认 0.5）：底栏全部弹簧的 DampedRatio 缩放
 *  - glassVibrancyEnabled 鲜艳增强（默认开）：采样背景饱和度 +18%
 *
 * 修改后点「立即应用」重建界面生效（与外观主题弹窗同一交互）。
 */
public final class GlassConfigDialog {

    public interface OnGlassChangedListener {
        void onGlassChanged();
    }

    private GlassConfigDialog() {}

    public static void show(final Context context, boolean isDark,
                            final OnGlassChangedListener listener) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = context.getResources().getDisplayMetrics().density;
        final SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);

        int ink = isDark ? 0xFFF4F4F5 : 0xFF18181B;
        int sub = isDark ? 0xFFA1A1AA : 0xFF71717A;

        ScrollView scroll = new ScrollView(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int dp24 = (int) (24 * density);
        card.setPadding(dp24, dp24, dp24, dp24);
        card.setBackground(LiquidGlass.glassCard(context, isDark, 28));
        scroll.addView(card, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("玻璃细则配置");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ink);
        card.addView(title);

        TextView desc = new TextView(context);
        desc.setText("逐项对标 ui 模板 AppChrome 玻璃参数（磨砂玻璃 · View 近似）");
        desc.setTextSize(12);
        desc.setTextColor(sub);
        desc.setPadding(0, (int) (4 * density), 0, (int) (14 * density));
        card.addView(desc);

        // ---- 滑杆参数（模板同名） ----
        addSlider(context, card, ink, sub, density, "玻璃不透明度",
                "容器底色浓度 = 0.10 + 不透明度 × 0.55（模板 glassOpacity，默认 55%）",
                0, 100, sp.getInt("glass_opacity", 55), "%",
                v -> sp.edit().putInt("glass_opacity", v).apply());
        addSlider(context, card, ink, sub, density, "玻璃模糊半径",
                "磨砂采样间距，0 为清晰通透（模板 blur(4dp)，底栏默认 4dp）",
                0, 40, sp.getInt("glass_blur", 4), "dp",
                v -> sp.edit().putInt("glass_blur", v).apply());
        addSlider(context, card, ink, sub, density, "折射强度",
                "边缘亮棱与镜面高光随动强度（模板 glassRefraction，默认 100%）",
                0, 100, sp.getInt("glass_refraction", 100), "%",
                v -> sp.edit().putInt("glass_refraction", v).apply());
        addSlider(context, card, ink, sub, density, "高光描边",
                "卡片玻璃高光描边强度（模板 glassBorderAlpha，默认 20%）",
                0, 80, sp.getInt("glass_border", 20), "%",
                v -> sp.edit().putInt("glass_border", v).apply());
        addSlider(context, card, ink, sub, density, "动效阻尼",
                "底栏全部弹簧的阻尼比例，一处旋钮调节整体手感（模板 motionDamping，默认 50%）",
                0, 100, sp.getInt("glass_damping", 50), "%",
                v -> sp.edit().putInt("glass_damping", v).apply());

        // ---- 开关（模板同名） ----
        addSwitch(context, card, ink, sub, density, "鲜艳增强 (Vibrancy)",
                "采样背景饱和度 +18%（模板 glassVibrancyEnabled）",
                "glass_vibrancy", true, sp);

        // ---- 与模板真玻璃的差异（模板规矩：如实列出做不到的项） ----
        TextView note = new TextView(context);
        note.setText("与模板真玻璃的差异（View 体系限制）：无 backdrop 实时透镜折射 (lens)、"
                + "无色差 (chromatic aberration)、无跨窗背景采样；胶囊绘制于标签之下而非折射标签。");
        note.setTextSize(11);
        note.setTextColor(sub);
        note.setPadding(0, (int) (14 * density), 0, (int) (14 * density));
        card.addView(note);

        // ---- 底部按钮：立即应用（重建界面） ----
        TextView apply = new TextView(context);
        apply.setText("立即应用");
        apply.setTextSize(15);
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setTextColor(0xFFFFFFFF);
        apply.setGravity(Gravity.CENTER);
        GradientDrawable pill = LiquidGlass.glassPill(context,
                isDark ? 0x6660A5FA : 0x66312E81, 0x66FFFFFF);
        apply.setBackground(pill);
        apply.setPadding((int) (18 * density), (int) (12 * density),
                (int) (18 * density), (int) (12 * density));
        apply.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) listener.onGlassChanged();
        });
        card.addView(apply, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout root = new FrameLayout(context);
        int dp16 = (int) (16 * density);
        root.setPadding(dp16, dp16, dp16, dp16);
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        dialog.setContentView(root, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (340 * density), (int) (520 * density));
        }
    }

    private interface IntPrefWriter {
        void write(int v);
    }

    /** 滑杆行：名称 + 当前值 + 说明 + SeekBar（值实时写偏好，重建后生效） */
    private static void addSlider(Context c, LinearLayout box, int ink, int sub,
                                  float density, String name, String desc,
                                  int min, int max, int cur, String unit,
                                  IntPrefWriter writer) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView nm = new TextView(c);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(ink);
        head.addView(nm, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView[] val = new TextView[1];
        val[0] = new TextView(c);
        val[0].setText((cur) + unit);
        val[0].setTextSize(13);
        val[0].setTextColor(sub);
        head.addView(val[0], new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(head);

        TextView d = new TextView(c);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(sub);
        d.setPadding(0, (int) (2 * density), 0, (int) (6 * density));
        row.addView(d);

        SeekBar sb = new SeekBar(c);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p;
                val[0].setText(v + unit);
                writer.write(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(sb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 开关行：名称 + 说明 + Switch（实时写偏好） */
    private static void addSwitch(Context c, LinearLayout box, int ink, int sub,
                                  float density, String name, String desc,
                                  String key, boolean def, SharedPreferences sp) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView nm = new TextView(c);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(ink);
        mid.addView(nm);
        TextView d = new TextView(c);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(sub);
        d.setPadding(0, (int) (2 * density), 0, 0);
        mid.addView(d);
        row.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        CompoundButton sw = new Switch(c);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                sp.edit().putBoolean(key, isChecked).apply());
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
