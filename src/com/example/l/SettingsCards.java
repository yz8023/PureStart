package com.example.l;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ScrollView;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 「设置」页组装（P1-2 自 SettingsActivity 拆出，行为不变）。
 *
 * 内容：个性化（外观/磨砂模糊/玻璃细则折叠卡/语言/底栏角标）、桌面图标、
 * Hook 自检、更新。玻璃细则参数与默认值对标 ui 模板 SettingsScreen.kt，
 * 拖动经 activity.applyGlassLive() 实时预览；差异说明文案见卡内 note。
 * 共用行工具 sectionHeader/setRow 与预览胶水 applyGlassLive 留 activity
 * （拓展页/设置行也使用）。
 */
final class SettingsCards {
    private final SettingsActivity a;

    private LinearLayout glassSectionContent;   // 玻璃细则折叠内容（平铺可折叠）
    private TextView glassChevron;              // 折叠指示箭头

    SettingsCards(SettingsActivity a) {
        this.a = a;
    }

    View build() {
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * a.dp);
        box.setPadding(pad, (int) (10 * a.dp), pad, (int) (108 * a.dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ① 大标题
        TextView title = new TextView(a);
        title.setText("设置");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(a.INK);
        title.setPadding((int) (2 * a.dp), 0, 0, (int) (14 * a.dp));
        box.addView(title);

        // ===== 分组：个性化 =====
        box.addView(a.sectionHeader("个性化"));
        a.addCard(box, appearanceRow());
        a.addCard(box, a.setRow(R.drawable.ic_set_drop, "磨砂模糊", "为模块界面增加磨砂玻璃（View 近似）效果", Prefs.KEY_LIQUID_BLUR));
        a.addCard(box, glassSectionCard());
        a.addCard(box, languageRow());
        a.addCard(box, a.setRow(R.drawable.ic_set_bell, "底栏应用角标", "在底栏“应用”按钮上显示数量角标", Prefs.KEY_APP_BADGE));

        // ===== 分组：桌面图标 =====
        box.addView(a.sectionHeader("桌面图标"));
        a.addCard(box, a.homePage.desktopIconRow());

        // ===== 分组：Hook 自检 =====
        box.addView(a.sectionHeader("Hook 自检"));
        a.addCard(box, a.homePage.selfCheckCard());

        // ===== 分组：更新 =====
        box.addView(a.sectionHeader("更新"));
        a.addCard(box, a.setRow(R.drawable.ic_set_refresh, "自动检查更新",
                "应用启动时自动检测新版本", Prefs.KEY_AUTO_UPDATE));
        a.addCard(box, a.setRow(R.drawable.ic_set_refresh, "获取更新", null, null));

        return scroll;
    }

    /** 语言行：跟随系统 / 简体中文（即时生效，重建界面） */
    private LinearLayout languageRow() {
        int mode = Prefs.localeMode(a);
        LinearLayout row = a.setRow(R.drawable.ic_set_lang, "语言",
                mode == 1 ? "简体中文（强制）" : "跟随系统", null);
        row.setOnClickListener(v -> showLanguageDialog());
        return row;
    }

    private void showLanguageDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(a);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int dp24 = (int) (24 * a.dp);
        card.setPadding(dp24, dp24, dp24, dp24);
        card.setBackground(LiquidGlass.glassCard(a, a.darkMode, 28));

        TextView title = new TextView(a);
        title.setText("界面语言");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(a.INK);
        card.addView(title);

        int cur = Prefs.localeMode(a);
        final String[] names = {"跟随系统", "简体中文（强制）"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView item = new TextView(a);
            item.setText(names[i] + (i == cur ? "  ✓" : ""));
            item.setTextSize(14.5f);
            item.setTextColor(a.INK);
            item.setPadding((int) (12 * a.dp), (int) (14 * a.dp), (int) (12 * a.dp), (int) (14 * a.dp));
            item.setOnClickListener(v -> {
                Prefs.sp(a).edit().putInt(Prefs.KEY_LOCALE_MODE, idx).apply();
                dialog.dismiss();
                a.recreate();
            });
            card.addView(item);
        }
        TextView note = new TextView(a);
        note.setText("英文界面翻译筹备中，当前界面为简体中文。");
        note.setTextSize(11);
        note.setTextColor(a.SUB);
        note.setPadding(0, (int) (10 * a.dp), 0, 0);
        card.addView(note);

        android.widget.FrameLayout rootWrap = new android.widget.FrameLayout(a);
        int dp16 = (int) (16 * a.dp);
        rootWrap.setPadding(dp16, dp16, dp16, dp16);
        rootWrap.addView(card);
        dialog.setContentView(rootWrap);
        dialog.show();
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (320 * a.dp), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            LiquidGlass.applyFrostedWindow(w, a.dp);
        }
    }

    /** 玻璃细则：平铺可折叠卡片（对标 ui 模板 AppChrome，改动即时实时预览） */
    private LinearLayout glassSectionCard() {
        LinearLayout card = a.makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        // 头部：图标 + 标题 + 折叠箭头
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int) (14 * a.dp), (int) (14 * a.dp), (int) (14 * a.dp), (int) (14 * a.dp));
        ImageView hIcon = new ImageView(a);
        hIcon.setImageResource(R.drawable.ic_set_palette);
        hIcon.setColorFilter(a.ACCENT);
        header.addView(hIcon, new LinearLayout.LayoutParams((int) (26 * a.dp), (int) (26 * a.dp)));
        LinearLayout hMid = new LinearLayout(a);
        hMid.setOrientation(LinearLayout.VERTICAL);
        hMid.setPadding((int) (12 * a.dp), 0, 0, 0);
        TextView hTitle = new TextView(a);
        hTitle.setText("磨砂玻璃（View 近似）");
        hTitle.setTextSize(15);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hTitle.setTextColor(a.INK);
        hMid.addView(hTitle);
        TextView hSub = new TextView(a);
        hSub.setText("参数取值借鉴 ui 模板 · 拖动实时预览");
        hSub.setTextSize(12);
        hSub.setTextColor(a.SUB);
        hSub.setPadding(0, (int) (2 * a.dp), 0, 0);
        hMid.addView(hSub);
        header.addView(hMid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        glassChevron = new TextView(a);
        glassChevron.setText("▾");
        glassChevron.setTextSize(18);
        glassChevron.setTextColor(a.SUB);
        glassChevron.setRotation(180f);   // 默认展开（平铺）
        header.addView(glassChevron);
        header.setOnClickListener(v -> toggleGlassSection());
        card.addView(header);

        // 内容：默认平铺展开，滑杆改动实时预览
        glassSectionContent = new LinearLayout(a);
        glassSectionContent.setOrientation(LinearLayout.VERTICAL);
        glassSectionContent.setPadding((int) (14 * a.dp), 0, (int) (14 * a.dp), (int) (12 * a.dp));
        // 参数、范围、默认值、副标题文案均与模板 SettingsScreen.kt「动效」区一一对应
        glassSliderRow(glassSectionContent, "动效阻尼", Prefs.KEY_GLASS_DAMPING, 0, 100, 50, "%",
                v -> "全局弹簧阻尼：" + v + "%，越低越弹");
        glassSliderRow(glassSectionContent, "模糊强度", Prefs.KEY_GLASS_BLUR, 0, 40, 0, "a.dp",
                v -> "背景模糊强度：" + v + "a.dp");
        glassSliderRow(glassSectionContent, "折射高度", Prefs.KEY_GLASS_REFRACT_HEIGHT, 0, 40, 15, "a.dp",
                v -> "液态透镜边缘高度：" + v + "a.dp");
        glassSliderRow(glassSectionContent, "折射幅度", Prefs.KEY_GLASS_REFRACT_AMOUNT, 0, 60, 20, "a.dp",
                v -> "壁纸内容偏移幅度：" + v + "a.dp");
        glassSliderRow(glassSectionContent, "玻璃染色", Prefs.KEY_GLASS_TINT_ALPHA, 0, 35, 8, "%",
                v -> "白色玻璃涂层：" + v + "%");
        glassSliderRow(glassSectionContent, "高光边框", Prefs.KEY_GLASS_BORDER, 0, 45, 20, "%",
                v -> "玻璃边缘强度：" + v + "%");
        glassSwitchRow(glassSectionContent, "增强色彩层次", Prefs.KEY_GLASS_VIBRANCY,
                "提升玻璃后的壁纸色彩通透感");
        glassSwitchRow(glassSectionContent, "色差折射", Prefs.KEY_GLASS_CHROMATIC,
                "在液态透镜边缘显示更明显的色散");
        TextView note = new TextView(a);
        note.setText("【与模板真玻璃的差异】\n"
                + "· 无 backdrop 实时折射：View 无法录制任意图层，卡片=窗口内背景位图按坐标裁剪，底栏=限频截图降分辨率模糊（均非 Compose backdrop 语义）；\n"
                + "· 无 lens 折射：折射高度→棱光厚度、折射幅度→背景层放大均为自创近似映射（模板中不存在此对应关系），滚动经过下方的内容不会被折射；\n"
                + "· 弹窗在 Android 12+ 用系统窗口背景模糊（真磨砂），12 以下退化为半透明蒙层。\n"
                + "【验收】滑动不掉帧 · 深浅色均可读 · 可点区域不受影响。");
        note.setTextSize(11);
        note.setTextColor(a.SUB);
        note.setLineSpacing(3 * a.dp, 1f);
        note.setPadding(0, (int) (10 * a.dp), 0, 0);
        glassSectionContent.addView(note);
        card.addView(glassSectionContent);
        return card;
    }

    /** 折叠 / 展开（高度过渡 + 箭头旋转） */
    private void toggleGlassSection() {
        if (glassSectionContent == null) return;
        boolean expand = glassSectionContent.getVisibility() != View.VISIBLE;
        try {
            android.transition.TransitionManager.beginDelayedTransition(
                    (ViewGroup) glassSectionContent.getParent(),
                    new android.transition.AutoTransition().setDuration(240));
        } catch (Throwable ignored) {}
        glassSectionContent.setVisibility(expand ? View.VISIBLE : View.GONE);
        if (glassChevron != null) {
            glassChevron.animate().rotation(expand ? 180f : 0f).setDuration(240).start();
        }
    }

    /** 滑杆行（模板 SliderCard 同构）：标题 + 副标题（内嵌实时值，文案照搬模板）+ 着色 SeekBar */
    private void glassSliderRow(LinearLayout box, String name, final String key,
                                final int min, final int max, final int def, String unit,
                                final java.util.function.IntFunction<String> subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (8 * a.dp), 0, (int) (8 * a.dp));

        TextView nm = new TextView(a);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(a.INK);
        row.addView(nm);
        final TextView sub = new TextView(a);
        sub.setTextSize(12);
        sub.setTextColor(a.SUB);
        sub.setPadding(0, (int) (2 * a.dp), 0, (int) (4 * a.dp));
        sub.setText(subtitle.apply(a.hookPrefs().getInt(key, def)));
        row.addView(sub);

        SeekBar sb = new SeekBar(a);
        sb.setMax(max - min);
        sb.setProgress(a.hookPrefs().getInt(key, def) - min);
        sb.setProgressTintList(ColorStateList.valueOf(a.ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(a.ACCENT));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(
                a.darkMode ? 0x33FFFFFF : 0x1A18181B));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p;
                sub.setText(subtitle.apply(v));
                a.hookPrefs().edit().putInt(key, v).apply();
                a.applyGlassLive();   // 实时预览：拖动即刻生效
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

    /** 开关行（模板 PreferenceRow 同构）：标题 + 副标题 + Switch（改动实时预览） */
    private void glassSwitchRow(LinearLayout box, String name, final String key, String subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (8 * a.dp), 0, (int) (8 * a.dp));
        LinearLayout tm = new LinearLayout(a);
        tm.setOrientation(LinearLayout.VERTICAL);
        TextView nm = new TextView(a);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(a.INK);
        tm.addView(nm);
        TextView sb = new TextView(a);
        sb.setText(subtitle);
        sb.setTextSize(12);
        sb.setTextColor(a.SUB);
        sb.setPadding(0, (int) (2 * a.dp), 0, 0);
        tm.addView(sb);
        row.addView(tm, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch sw = new android.widget.Switch(a);
        sw.setChecked(a.hookPrefs().getBoolean(key, true));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            a.hookPrefs().edit().putBoolean(key, isChecked).apply();
            a.applyGlassLive();
        });
        row.addView(sw);
        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout appearanceRow() {
        final String[] names = {"跟随系统", "浅色液态玻璃", "深色液态玻璃", "极光流体玻璃"};
        int cur = Prefs.themeMode(a);
        String sub = names[cur >= 0 && cur < names.length ? cur : 0];
        if (cur == 0) sub += a.darkMode ? "（当前深色）" : "（当前浅色）";
        LinearLayout row = a.setRow(R.drawable.ic_set_palette, "外观", sub, null);
        row.setOnClickListener(v -> {
            ThemeDialog.show(a, a.darkMode, which -> {
                a.recreate();
            });
        });
        return row;
    }
}
