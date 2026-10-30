package com.example.l;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 模块设置页 —— 悬浮磨砂底栏 + 4 Tab + 点击动效
 *
 * 效果对照 M3 (Fuck for VIP)：
 *  - 悬浮圆角底栏，半透明白 + API31+ 背景模糊 = 磨砂玻璃
 *  - 4 个 Tab：未选中线性图标，选中切换实心图标
 *  - M3 滑动胶囊指示条：点击后滑动到目标 Tab（带回弹）
 *  - 点击动效：按下缩小回弹 + 选中图标弹跳 + 颜色渐变 + 页面淡入淡出
 *
 * 纯原生 View 实现，零第三方依赖（javac + d8 + aapt2 可直接构建）
 */
public class SettingsActivity extends Activity implements GlassCardView.Host {

    // ===== 主题色（applyTheme() 按“外观”选择赋值：跟随系统/浅色/深色） =====
    private int PAGE_BG;
    int ACCENT;
    int INK;
    int SUB;
    int IDLE_TINT;
    int BAR_BG;
    private int RIPPLE;
    int STATUS_GREEN;
    int BADGE_RED;
    private int CARD_BG;        // 卡片背景
    int FIELD_BG;       // 输入框/按钮底色
    private int PILL_GRAY;      // 灰色胶囊（未安装）
    int HINT;           // 弱化文字（未安装应用名）
    int BACKDROP_BASE;  // 背景光斑底色
    int BLOB1, BLOB2, BLOB3; // 三色光斑
    private int PILL_TAB;       // 底栏指示条底色
    boolean darkMode;   // 当前解析结果是否深色
    FrameLayout pageContainer;
    FrameLayout barWrap;
    List<AppEntry> appsData;         // 应用页数据（后台加载）
    private android.content.SharedPreferences hookPrefs;   // 与 Hook 侧共享的开关偏好
    /** 各作用域应用的功能描述（“应用”页卡片展示用；新增 Hook 目标时在此加一行） */
    private static final java.util.HashMap<String, String> APP_DESC = new java.util.HashMap<>();
    static {
        // 示例：APP_DESC.put("com.example.app", "去广告 · 解锁会员");
        // 与 Targets.LIST / xposed/scope.list 三处同步
        APP_DESC.put("com.realtech.xiaocan", "小蚕惠生活 · 开屏及信息流去广告、弹窗拦截");
        APP_DESC.put("com.netease.cloudmusic", "网易云音乐 · 开屏广告/横幅与信息流净化");
        APP_DESC.put("com.luna.music", "汽水音乐 · 冷启动开屏广告拦截");
        APP_DESC.put("com.xiaomi.shop", "小米商城 · 开屏与促销广告净化");
        APP_DESC.put("com.yek.android.kfc.activitys", "肯德基 · 启动页与横幅广告净化");
        APP_DESC.put("com.mcdonalds.gma.cn", "麦当劳 · 启动开屏与横幅广告净化");
        APP_DESC.put("com.tencent.qqmusic", "QQ音乐 · 开屏/Feed/插屏及精简优化");
        APP_DESC.put("com.coolapk.market", "酷安 · 头条流/评论区/Sponsor广告净化");
        APP_DESC.put("com.baidu.BaiduMap", "百度地图 · 极速启动/开屏及营销广告去除");
        APP_DESC.put("com.xiaomi.market", "小米应用商店 · 启动开屏广告跳过");
        APP_DESC.put("com.glodon.drawingexplorer", "CAD快速看图 · 启动与插屏广告拦截");
        APP_DESC.put("com.xbxxhz.box", "小白学习打印 · 启动与通用SDK广告拦截");
        APP_DESC.put("tv.danmaku.bili", "哔哩哔哩 · 开屏与Landing页广告净化");
        APP_DESC.put("com.autonavi.minimap", "高德地图 · 开屏与推广弹窗拦截");
        APP_DESC.put("com.zhihu.android", "知乎 · 开屏与信息流回答卡片广告拦截");
        APP_DESC.put("com.dragon.read", "红果免费短剧 · 开屏与激励广告跳过");
        APP_DESC.put("com.baidu.netdisk", "百度网盘 · 启动页与营销弹窗净化");
    }
    GradientDrawable barBgDrawable;          // 底栏容器底色引用（实时预览改透明度）
    private int barSurfaceColor = 0xF0F0F4;          // 当前主题的 surfaceContainer 底色
    private final Paint cardSamplePaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);   // 卡片背景裁剪画笔（vibrancy 随动）
    GlassEngine.BackdropView backdrop;       // 背景层引用（视图本体由 GlassEngine 创建）
    final GlassEngine engine = new GlassEngine(this);   // 磨砂玻璃捕获/绘制引擎（P1-2 拆出）
    final HomePage homePage = new HomePage(this);      // 「首页」页组装（P1-2 拆出）
    final AppsPage appsPage = new AppsPage(this);      // 「应用」页组装（P1-2 拆出）
    final ExtensionPage extPage = new ExtensionPage(this); // 「拓展」页组装（P1-2 拆出）
    final SettingsCards settingsPage = new SettingsCards(this); // 「设置」页组装（P1-2 拆出）
    final LiquidBar liquidBar = new LiquidBar(this);  // 液态玻璃底栏（P1-2 拆出）
    float dp;

    /** 解析外观模式（跟随系统/浅色/深色）并赋值主题色；必须在 onCreate 最先调用 */
    private void applyTheme() {
        int mode = Prefs.themeMode(this);
        darkMode = mode == 2 || (mode == 0
                && (getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        if (mode == 3) {
            darkMode = true;
            setTheme(android.R.style.Theme_Material_NoActionBar);
            PAGE_BG = 0xFF0B132B;
            ACCENT = 0xFF38BDF8;
            INK = 0xFFF0F9FF;
            SUB = 0xFF94A3B8;
            IDLE_TINT = 0xFFBAE6FD;
            barSurfaceColor = 0x2A3B55;
            RIPPLE = 0x3338BDF8;
            STATUS_GREEN = 0xFF34D399;
            BADGE_RED = 0xFFF87171;
            CARD_BG = 0xB31E293B;
            FIELD_BG = 0xCC1E293B;
            PILL_GRAY = 0xFF475569;
            HINT = 0xFF64748B;
            BACKDROP_BASE = 0xFF0A0F1D;
            BLOB1 = 0xFF0284C7;
            BLOB2 = 0xFF2563EB;
            BLOB3 = 0xFF0D9488;
            PILL_TAB = 0x5538BDF8;
        } else if (darkMode) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            PAGE_BG = 0xFF09090B;
            ACCENT = 0xFF60A5FA;
            INK = 0xFFF4F4F5;
            SUB = 0xFFA1A1AA;
            IDLE_TINT = 0xFFD4D4D8;
            barSurfaceColor = 0x28292D;
            RIPPLE = 0x3360A5FA;
            STATUS_GREEN = 0xFF4ADE80;
            BADGE_RED = 0xFFF87171;
            CARD_BG = 0xB318181B;
            FIELD_BG = 0xCC27272A;
            PILL_GRAY = 0xFF52525B;
            HINT = 0xFF71717A;
            BACKDROP_BASE = 0xFF050507;
            BLOB1 = 0xFF1E3A8A;
            BLOB2 = 0xFF581C87;
            BLOB3 = 0xFF065F46;
            PILL_TAB = 0x4460A5FA;
        } else {
            setTheme(R.style.AppTheme);
            PAGE_BG = 0xFFF6F7FC;
            ACCENT = 0xFF312E81;
            INK = 0xFF1F2430;
            SUB = 0xFF2E333A;
            IDLE_TINT = 0xFF15181C;
            barSurfaceColor = 0xF0F0F4;
            RIPPLE = 0x2E312E81;
            STATUS_GREEN = 0xFF1B7A46;
            BADGE_RED = 0xFFE5484D;
            CARD_BG = 0xB3FFFFFF;
            FIELD_BG = 0xFFF0F1F7;
            PILL_GRAY = 0xFFB9BEC7;
            HINT = 0xFF9AA0AC;
            BACKDROP_BASE = 0xFFE9EBF7;
            BLOB1 = 0xFF818CF8;
            BLOB2 = 0xFFF0ABFC;
            BLOB3 = 0xFF99F6E4;
            PILL_TAB = 0x339AA0AC;
        }
        recomputeBarTint();   // 玻璃细则：容器底色 = surfaceContainer @ 0.10 + opacity × 0.55
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        // 语言设置（跟随系统 / 简体中文）：配置上下文包一层 locale
        int mode = 0;
        try {
            mode = Prefs.localeMode(base);
        } catch (Throwable ignored) {}   // 纯防御：读不到语言偏好按默认（跟随系统）处理
        if (mode == 1) {
            android.content.res.Configuration cfg = new android.content.res.Configuration();
            cfg.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);
            cfg.setLocales(android.os.LocaleList.forLanguageTags("zh-CN"));
            base = base.createConfigurationContext(cfg);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();   // 最先解析主题（跟随系统/浅色/深色）
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        liquidBar.applyMotionTuning(); // 玻璃细则：动效阻尼 → 全部弹簧
        applyVibrancy();              // 玻璃细则：鲜艳增强 → 采样背景饱和度

        // 状态栏与页面整合：透明状态栏 + 内容延伸到状态栏下方
        getWindow().setStatusBarColor(0x00000000);
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!darkMode) uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(uiFlags);

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);   // 允许子元素超出边界（指示条跳出底栏用）
        root.setBackgroundColor(PAGE_BG);

        // ---- 背景光斑层：API 31+ 模糊后作为磨砂底 ----
        backdrop = engine.createBackdrop(this);
        if (Build.VERSION.SDK_INT >= 31) {
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(
                    26f, 26f, Shader.TileMode.CLAMP));
        }
        root.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // ---- 四个页面（顶部让出状态栏高度，背景仍铺满到状态栏底下） ----
        pageContainer = new FrameLayout(this);
        int sbId = getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (sbId > 0) {
            pageContainer.setPadding(0, getResources().getDimensionPixelSize(sbId), 0, 0);
        }
        pageContainer.addView(homePage.build());
        pageContainer.addView(appsPage.build());
        pageContainer.addView(extPage.build());
        pageContainer.addView(settingsPage.build());
        for (int i = 1; i < pageContainer.getChildCount(); i++) {
            pageContainer.getChildAt(i).setVisibility(View.GONE);
        }
        root.addView(pageContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));



        // ---- 液态玻璃底栏（P1-2 起由 LiquidBar 组装）----
        liquidBar.build(root);

        setContentView(root);

        // 滚动时实时刷新玻璃层（1/2 基准 + 2x 放大 = 柔和模糊无马赛克）
        for (int i = 0; i < pageContainer.getChildCount(); i++) {
            View pg = pageContainer.getChildAt(i);
            if (pg instanceof ScrollView) {
                ((ScrollView) pg).setOnScrollChangeListener(
                        (v, x, y, ox, oy) -> requestGlassRefresh());
            }
        }
        // 首帧先不重捕：延迟 120ms 再做首次玻璃重捕，避免冷启动前两帧被拖慢
        barWrap.postDelayed(this::requestGlassRefresh, 120);

    }

    /** 切屏动效：旧页反向滑出缩小淡出，新页从滑动方向滑入回弹淡入 */
    void animatePageSwitch(View outPage, View inPage, boolean forward) {
        if (outPage == null || inPage == null) return;
        float w = Math.max(1f, pageContainer.getWidth());
        // 快速连点：先取消旧动画并复位状态，杜绝中途残留
        outPage.animate().cancel();
        inPage.animate().cancel();
        outPage.setVisibility(View.VISIBLE);
        outPage.setAlpha(1f);
        inPage.setVisibility(View.VISIBLE);
        inPage.setAlpha(0f);
        inPage.setScaleX(0.97f);
        inPage.setScaleY(0.97f);
        inPage.setTranslationX(forward ? 0.25f * w : -0.25f * w);
        outPage.animate()
                .translationX(forward ? -0.18f * w : 0.18f * w)
                .scaleX(0.96f).scaleY(0.96f).alpha(0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    outPage.setVisibility(View.GONE);
                    outPage.setTranslationX(0f);
                    outPage.setScaleX(1f);
                    outPage.setScaleY(1f);
                    outPage.setAlpha(1f);
                })
                .start();
        inPage.animate()
                .translationX(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(340).setStartDelay(30)
                .setInterpolator(new OvershootInterpolator(0.6f))
                .start();
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ==== 偏好读取统一经 GlassPrefs（单一事实来源，不再散落各处直读键名） ====

    private float glassOpacity() {
        return Prefs.opacity(this);
    }

    /** 模糊强度 dp → 5×5 偏移采样间距；0 = 清晰通透（免 25 抽头） */
    /** 鲜艳增强（模板 vibrancy）：采样背景饱和度 +18% */
    private void applyVibrancy() {
        ColorMatrixColorFilter cf = null;
        if (Prefs.vibrancy(this)) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1.18f);
            cf = new ColorMatrixColorFilter(cm);
        }
        engine.applyVibrancyFilter(cf);
        cardSamplePaint.setColorFilter(cf);   // 全部磨砂卡片背景层同步鲜艳增强
    }


    /** 首页：状态卡 + 双统计卡 + 信息卡列表 + 反馈卡（对照参考模块版式） */
    /** 全局磨砂玻璃卡片：裁剪窗口内背景位图模糊 + 染色层 + 棱光 + 高光描边
     *  （GlassCardView，磨砂细则滑杆实时生效；详见类注释） */
    LinearLayout makeCard() {
        GlassCardView card = new GlassCardView(this, this);
        card.setOrientation(LinearLayout.VERTICAL);
        return card;
    }

    void addCard(LinearLayout box, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * dp);
        box.addView(card, lp);
    }

    private LinearLayout statCard(String label, String value) {
        LinearLayout card = makeCard();
        card.setPadding((int) (20 * dp), (int) (16 * dp), (int) (20 * dp), (int) (16 * dp));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(SUB);
        card.addView(l);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(24);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(INK);
        v.setPadding(0, (int) (4 * dp), 0, 0);
        card.addView(v);
        return card;
    }

    /** 磨砂圆形图标按钮：主题 surface 半透明底 + 1dp 高光描边（随主题与细则实时变化） */
    ImageView roundButton(int iconRes) {
        ImageView b = new ImageView(this);
        b.setImageResource(iconRes);
        b.setColorFilter(INK);
        b.setPadding((int) (11 * dp), (int) (11 * dp), (int) (11 * dp), (int) (11 * dp));
        GradientDrawable bg = new GradientDrawable();
        int alpha = Math.round(clamp(0.10f + glassOpacity() * 0.55f, 0f, 1f) * 255f);
        bg.setColor((alpha << 24) | (barSurfaceColor & 0xFFFFFF));
        bg.setCornerRadius(22 * dp);
        bg.setStroke((int) (1f * dp), cardStrokeColor());
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(RIPPLE), bg, bg));
        return b;
    }

    LinearLayout.LayoutParams roundLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (44 * dp), (int) (44 * dp));
        lp.leftMargin = leftMargin;
        return lp;
    }

    /** 检测 Xposed / LSPosed 框架是否可用（框架激活时会将 XposedBridge 注入 boot classpath） */
    static boolean isFrameworkActive() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Xposed 框架版本（getXposedVersion()），取不到返回 -1 */
    static int frameworkVersion() {
        try {
            Class<?> c = Class.forName("de.robv.android.xposed.XposedBridge");
            return (Integer) c.getMethod("getXposedVersion").invoke(null);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 开关偏好（模块私有）；Hook 侧经 HookStateProvider 跨进程读取 */
    android.content.SharedPreferences hookPrefs() {
        if (hookPrefs == null) {
            hookPrefs = Prefs.sp(this);
        }
        return hookPrefs;
    }

    boolean isHookEnabled(String pkg) {
        return Prefs.hookEnabled(this, pkg);   // P0-1：默认关
    }

    /** 刷新底栏“应用”角标：= 已安装且勾选启用的作用域应用数；为 0 或设置页关掉角标则隐藏 */
    void updateScopeBadge() {
        if (liquidBar.scopeBadge == null) return;
        if (!Prefs.appBadgeEnabled(this)) {   // 设置页总开关：不喜欢看就不显示
            liquidBar.scopeBadge.setVisibility(View.GONE);
            return;
        }
        int n = 0;
        if (appsData != null) {
            for (AppEntry e : appsData) {
                if (e.installed && isHookEnabled(e.pkg)) n++;
            }
        }
        if (n > 0) {
            liquidBar.scopeBadge.setText(String.valueOf(n));
            liquidBar.scopeBadge.setVisibility(View.VISIBLE);
        } else {
            liquidBar.scopeBadge.setVisibility(View.GONE);
        }
    }

    void setHookEnabled(String pkg, boolean enabled) {
        // 只写模块私有偏好；Hook 侧经 HookStateProvider 读取（不受 ROM 权限限制）
        Prefs.setHookEnabled(this, pkg, enabled);
    }

    /** "设置"页：分组 + 卡片行（对标 FFV 设置页；行内功能待接） */
    /** 设置页分组小标题 */
    TextView sectionHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(SUB);
        t.setPadding((int) (6 * dp), (int) (16 * dp), 0, (int) (8 * dp));
        return t;
    }

    /** 设置行卡片：图标 + 标题/副标题 +（可选）Switch（switchKey 非空时显示并持久化到偏好） */
    LinearLayout setRow(int iconRes, String title, String sub, String switchKey) {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int vpad = (int) (16 * dp);
        card.setPadding((int) (14 * dp), vpad, (int) (14 * dp), vpad);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ACCENT);
        card.addView(icon, new LinearLayout.LayoutParams(
                (int) (26 * dp), (int) (26 * dp)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (12 * dp), 0, 0, 0);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(15);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(INK);
        mid.addView(t1);
        if (sub != null) {
            TextView t2 = new TextView(this);
            t2.setText(sub);
            t2.setTextSize(12);
            t2.setTextColor(SUB);
            t2.setPadding(0, (int) (2 * dp), 0, 0);
            mid.addView(t2);
        }
        card.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (switchKey != null) {
            android.widget.Switch sw = new android.widget.Switch(this);
            // P0-1：hook 类键（hook_/sub_）默认关（未设置=不动作）；界面功能键保持原默认
            boolean hookKey = Prefs.isHookKey(switchKey);
            sw.setChecked(hookPrefs().getBoolean(switchKey, !hookKey));
            sw.setOnCheckedChangeListener((v, checked) -> {
                hookPrefs().edit().putBoolean(switchKey, checked).apply();
                barWrap.invalidate();      // 立即重绘（液态模糊开关即时生效）
                requestGlassRefresh();
                updateScopeBadge();        // 应用角标开关即时生效（其余开关调用无副作用）
            });
            card.addView(sw, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    /** 玻璃细则实时预览：一处改动，底栏 + 卡片 + 弹簧 + 采样全部即时刷新 */
    void applyGlassLive() {
        recomputeBarTint();
        requestGlassRefresh();
        liquidBar.invalidatePill();
        liquidBar.applyMotionTuning();
        applyVibrancy();
        restrokeCards();
        if (barWrap != null) barWrap.invalidate();
    }

    /** 容器底色 = surfaceContainer @ 0.10 + opacity × 0.55（模板公式，实时可变） */
    private void recomputeBarTint() {
        float alpha = clamp(0.10f + glassOpacity() * 0.55f, 0f, 1f);
        BAR_BG = (Math.round(alpha * 255f) << 24) | (barSurfaceColor & 0xFFFFFF);
        if (barBgDrawable != null) barBgDrawable.setColor(BAR_BG);
    }

    // ==== GlassCardView.Host：全卡玻璃实时采样宿主（玻璃细则参数逐次实时读取） ====

    @Override
    public Bitmap glassBackdropSample(int blurDp) {
        if (backdrop == null) return null;
        int n = backdrop.levelCount();
        if (n == 0) return null;
        int idx = (int) clamp(Math.round(blurDp / 8f), 0, n - 1);
        return backdrop.level(idx);
    }

    @Override
    public int glassBackdropWidth() { return backdrop != null ? Math.max(1, backdrop.getWidth()) : 1; }

    @Override
    public int glassBackdropHeight() { return backdrop != null ? Math.max(1, backdrop.getHeight()) : 1; }

    /** 玻璃染色滑杆（0..0.35，默认 0.08）——经 GlassPrefs 收口 */
    private float glassTintAlphaPref() {
        return Prefs.tintAlpha(this);
    }

    /** 模板 backdrop 路径染色公式（Glass.kt:181）：glassTintAlpha + opacity × 0.04 */
    @Override
    public float glassCardTintAlpha() {
        return clamp(glassTintAlphaPref() + glassOpacity() * 0.04f, 0f, 0.45f);
    }

    /** 模板退化路径公式（Glass.kt:222）：0.26 + glassTintAlpha + opacity × 0.18 */
    @Override
    public float glassFallbackAlpha() {
        return clamp(0.26f + glassTintAlphaPref() + glassOpacity() * 0.18f, 0f, 0.72f);
    }

    @Override
    public int glassSurfaceColor() { return barSurfaceColor; }

    @Override
    public int glassRimColor() { return cardStrokeColor(); }

    /** 折射高度滑杆（0..40dp，默认 15）→ 棱光厚度比例：15dp=1.0 标准，40dp=2.0 厚棱（近似映射） */
    @Override
    public float glassPrismScale() {
        return Prefs.clamp(Prefs.prismHeightDp(this) / 15f, 0f, 2f);
    }

    /** 折射幅度滑杆（0..60dp，默认 20）→ 背景层放大（透镜内容偏移的 View 近似映射） */
    @Override
    public int glassRefractionAmountDp() {
        return Prefs.prismAmountDp(this);
    }

    /** 色差折射开关（默认开）→ 棱光 RGB 色散（近似） */
    @Override
    public boolean glassChromaticEnabled() {
        return Prefs.chromatic(this);
    }

    @Override
    public boolean isGlassCapturePass() { return engine.isCapturePass(); }

    @Override
    public int glassBlurDp() {
        return Prefs.blurDp(this);
    }

    @Override
    public Paint glassCardSamplePaint() { return cardSamplePaint; }

    private int cardStrokeColor() {
        int bAlpha = Math.round(Prefs.borderAlpha(this) * 255f);
        return (bAlpha << 24) | 0xFFFFFF;
    }

    /** 全视图树重绘玻璃卡片：染色/棱光/描边/采样层级全部实时刷新 */
    private void restrokeCards() {
        if (pageContainer != null) invalidateGlassRecursive(pageContainer);
    }

    private void invalidateGlassRecursive(View v) {
        if (v == null) return;
        if (v instanceof GlassCardView) {
            v.invalidate();   // 染色层/棱光/描边在 onDraw 实时读取玻璃细则
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateGlassRecursive(g.getChildAt(i));
        }
    }

    /** 外观行：点击弹出液态玻璃质感弹窗，副标题显示当前选择 */
    /** 作用域应用数：读 APK 内 META-INF/xposed/scope.list，逐行统计（空行与 # 注释忽略）
     *  ⚠ LSPosed 管理器里动态勾选的存在其私有数据库，App 进程读不到；这里是模块声明的静态作用域 */
    int scopeAppCount() {
        java.util.zip.ZipFile zf = null;
        try {
            zf = new java.util.zip.ZipFile(getPackageCodePath());
            java.util.zip.ZipEntry e = zf.getEntry("META-INF/xposed/scope.list");
            if (e == null) return 0;
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(zf.getInputStream(e), "UTF-8"));
            android.content.pm.PackageManager pm = getPackageManager();
            int n = 0;
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // 只统计本机真实安装的应用（模板占位包名/不存在的包自动忽略）
                try {
                    pm.getPackageInfo(line, 0);
                    n++;
                } catch (Throwable ignored) {
                    // 纯防御：包未安装不计入作用域数（NNF 属预期）
                }
            }
            r.close();
            return n;
        } catch (Throwable t) {
            return 0;
        } finally {
            if (zf != null) try { zf.close(); } catch (Throwable ignored) {}   // 纯防御：zip 关闭失败无需留痕
        }
    }

    /** 目标应用已装数量（仅查 17 个目标包：配合 Manifest <queries>，不再需要 QUERY_ALL_PACKAGES） */
    int installedAppCount() {
        int n = 0;
        for (String[] t : Targets.LIST) {
            try {
                getPackageManager().getPackageInfo(t[0], 0);
                n++;
            } catch (Throwable ignored) {}   // 纯防御：包未安装不计入已装数（NNF 属预期）
        }
        return n;
    }

    String appVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) {
            return "1.0";
        }
    }

    /** P0-2：版本号运行时取自 PackageManager（唯一来源 Manifest），不硬编码 */
    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** 占位页面：标题 + 副标题 + 卡片 */    /** 占位页面：标题 + 副标题 + 卡片 */
    /** 拓展页：激活指引 + 配置备份/恢复 + 关于（真实可用功能） */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null || data.getData() == null) return;
        if (requestCode == 41 && resultCode == RESULT_OK) extPage.exportPrefs(data.getData());
        else if (requestCode == 42 && resultCode == RESULT_OK) extPage.importPrefs(data.getData());
    }

    private View makePage(String title, String subtitle, String body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * dp);
        box.setPadding(pad, (int) (26 * dp), pad, (int) (108 * dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(26);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(INK);
        box.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(subtitle);
        t2.setTextSize(14);
        t2.setTextColor(SUB);
        t2.setPadding(0, (int) (6 * dp), 0, (int) (16 * dp));
        box.addView(t2);

        TextView card = new TextView(this);
        card.setText(body);
        card.setTextSize(14);
        card.setLineSpacing(4 * dp, 1f);
        card.setTextColor(INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(20 * dp);
        card.setBackground(bg);
        int cpad = (int) (16 * dp);
        card.setPadding(cpad, cpad, cpad, cpad);
        box.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** "应用"页：适配应用列表（scope.list ∩ 本机已装），对标 Fuck for VIP */
    /** 合并式刷新入口：无论谁调多频，实际重捕最多 ≈30fps 且不同帧堆积（快速连点卡顿的根治） */
    /** 玻璃重捕入口（P1-2 起由 GlassEngine 实现，保留原调用点不变） */
    private void requestGlassRefresh() {
        engine.requestRefresh();
    }

    /** 「应用」列表重渲染（P1-2 起由 AppsPage 实现；首页盾牌与弹窗回调经此薄委托） */
    void renderAppsList() {
        appsPage.renderList();
    }

    /** "应用"页单条数据 */
    static class AppEntry {
        final String pkg;
        final String label;
        final String version;
        final boolean installed;
        final String desc;

        AppEntry(String pkg, String label, String version, boolean installed) {
            this.pkg = pkg;
            this.label = label;
            this.version = version;
            this.installed = installed;
            this.desc = APP_DESC.containsKey(pkg) ? APP_DESC.get(pkg) : "LSPosed 作用域应用";
        }
    }
}
