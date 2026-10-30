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

    private static final String[] TAB_NAMES = {"首页", "应用", "拓展", "设置"};
    private static final int[] TAB_ICON_IDLE = {
            R.drawable.ic_tab_home, R.drawable.ic_tab_apps,
            R.drawable.ic_tab_extension, R.drawable.ic_tab_settings};
    private static final int[] TAB_ICON_ACTIVE = {
            R.drawable.ic_tab_home_fill, R.drawable.ic_tab_apps,
            R.drawable.ic_tab_extension_fill, R.drawable.ic_tab_settings_fill};

    // ===== 主题色（applyTheme() 按“外观”选择赋值：跟随系统/浅色/深色） =====
    private int PAGE_BG;
    int ACCENT;
    int INK;
    int SUB;
    private int IDLE_TINT;
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
    private LinearLayout bar;
    private LinearLayout[] tabItems;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private TextView scopeBadge;             // "应用"Tab 作用域数量角标（读 APK 内 scope.list）
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
    private View pill;                       // 活动玻璃胶囊（56dp，槽位空间浮点驱动）
    /** 模板 阻尼拖拽动画 的五组独立弹簧（View 版物理积分） */
    private static final class Spring {
        float value, vel, target;
        float stiffness, dampingRatio;
        Spring(float v, float s, float d) { value = target = v; stiffness = s; dampingRatio = d; }
        void step(float dt) {
            float omega = (float) Math.sqrt(stiffness);
            float a = stiffness * (target - value) - 2f * dampingRatio * omega * vel;
            vel += a * dt;
            value += vel * dt;
        }
        boolean settled() { return Math.abs(target - value) < 0.0015f && Math.abs(vel) < 0.004f; }
    }
    private final Spring slotSpring = new Spring(0f, 1000f, 1f);   // 胶囊位置（槽位空间 0..n-1）
    private final Spring pressSpring = new Spring(0f, 1000f, 1f);  // 按压进度 0..1
    private final Spring velSpring = new Spring(0f, 300f, 0.5f);   // 速度反馈（保体积拉伸）
    private final Spring scaleXSpring = new Spring(1f, 250f, 0.6f);// 胶囊 X 缩放
    private final Spring scaleYSpring = new Spring(1f, 250f, 0.7f);// 胶囊 Y 缩放
    private final Spring offsetSpring = new Spring(0f, 300f, 1f);  // 面板微位移（拖动整体轻移）
    /** 模板 pressedScale = 78f / 56f（按压时活动胶囊放大） */
    private static final float PRESSED_SCALE = 78f / 56f;
    private float pressProgress;             // 按压进度渲染值
    private float tabWidthPx, totalWidthPx;  // 槽位宽 / 底栏内容宽
    private boolean dockPressed, dockDragging;
    private float dockDownX, dockLastX, dockLastT, dockVel, touchX;
    private boolean choreoRunning;
    private final Choreographer.FrameCallback springFrame = new Choreographer.FrameCallback() {
        private long lastNanos;
        @Override public void doFrame(long frameTimeNanos) {
            if (isFinishing()) { choreoRunning = false; return; }
            float dt = lastNanos == 0f ? 1f / 60f
                    : (float) Math.min(1f / 30f, Math.max(1f / 240f, (frameTimeNanos - lastNanos) / 1e9));
            lastNanos = frameTimeNanos;
            slotSpring.step(dt); pressSpring.step(dt); velSpring.step(dt);
            scaleXSpring.step(dt); scaleYSpring.step(dt); offsetSpring.step(dt);
            renderDock();
            boolean active = !slotSpring.settled() || !pressSpring.settled() || !velSpring.settled()
                    || !scaleXSpring.settled() || !scaleYSpring.settled() || !offsetSpring.settled();
            if (active) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                choreoRunning = false;
                lastNanos = 0;
            }
        }
    };
    private void kickSprings() {
        renderDock();
        if (!choreoRunning) {
            choreoRunning = true;
            Choreographer.getInstance().postFrameCallback(springFrame);
        }
    }
    private final Path capsuleClip = new Path();     // 胶囊内阴影裁剪（独立于底栏 glassClip）
    private GradientDrawable barBgDrawable;          // 底栏容器底色引用（实时预览改透明度）
    private int barSurfaceColor = 0xF0F0F4;          // 当前主题的 surfaceContainer 底色
    private final Paint cardSamplePaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);   // 卡片背景裁剪画笔（vibrancy 随动）
    GlassEngine.BackdropView backdrop;       // 背景层引用（视图本体由 GlassEngine 创建）
    final GlassEngine engine = new GlassEngine(this);   // 磨砂玻璃捕获/绘制引擎（P1-2 拆出）
    final HomePage homePage = new HomePage(this);      // 「首页」页组装（P1-2 拆出）
    final AppsPage appsPage = new AppsPage(this);      // 「应用」页组装（P1-2 拆出）
    final ExtensionPage extPage = new ExtensionPage(this); // 「拓展」页组装（P1-2 拆出）
    final SettingsCards settingsPage = new SettingsCards(this); // 「设置」页组装（P1-2 拆出）
    private int selectedTab = 0;
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
        } catch (Throwable ignored) {}
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
        applyMotionTuning();          // 玻璃细则：动效阻尼 → 全部弹簧
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



        // ---- 液态玻璃底栏（完全对齐模板 LiquidBottomTabs：64dp 胶囊容器 + 4dp 内衬）----
        barWrap = new FrameLayout(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                engine.drawBarGlass(canvas, pressProgress, slotSpring.value,
                        tabWidthPx, bar.getX());   // 实时磨砂画在背景之上、图标之下
            }
        };
        barWrap.setWillNotDraw(false);   // ViewGroup 默认不调 onDraw，必须打开
        GradientDrawable barBg = new GradientDrawable();
        barBgDrawable = barBg;   // 玻璃细则实时预览：留底便于即时改透明度
        // 模板容器公式：surfaceContainer.copy(alpha = 0.10f + opacity * 0.55f)
        barBg.setColor(BAR_BG);
        barBg.setCornerRadius(32 * dp);   // 64dp 全胶囊半径
        // 模板 Highlight.Default.copy(alpha = 0.75f)：1dp 白色镜面高光棱
        barBg.setStroke((int) (1f * dp), 0xBFFFFFFF);
        barWrap.setBackground(barBg);
        // 模板 Shadow(radius = 10dp, Black, alpha 0.1 浅色 / 0.2 深色)
        barWrap.setElevation(10 * dp);
        barWrap.setClipChildren(false);   // 关闭裁剪：胶囊按压放大 / 过冲时能跳出容器
        barWrap.setClipToPadding(false);
        int barPad = (int) (4 * dp);      // 模板 padding(4dp)
        barWrap.setPadding(barPad, barPad, barPad, barPad);

        // ==== 底栏手势（模板 阻尼拖拽动画：按压 / 槽位空间拖拽 / 松手吸附）====
        barWrap.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dockDownX = touchX = ev.getX();
                        dockLastX = ev.getX();
                        dockLastT = android.os.SystemClock.uptimeMillis();
                        dockVel = 0f;
                        dockDragging = false;
                        // 点击位置直接定位：底栏任意位置按下，胶囊立即跳到手指槽位
                        dockPressed = tabWidthPx > 0;
                        if (dockPressed) {
                            // 胶囊中心对准手指（修复左缘对齐造成的半槽偏移）
                            float slot = clamp((ev.getX() - bar.getX()) / tabWidthPx - 0.5f,
                                    0f, tabItems.length - 1f);
                            slotSpring.value = slotSpring.target = slot;
                            slotSpring.vel = 0f;
                            pressSpring.target = 1f;
                            scaleXSpring.target = PRESSED_SCALE;
                            scaleYSpring.target = PRESSED_SCALE;
                            velSpring.target = 0f;
                            kickSprings();
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        touchX = ev.getX();
                        float dx = ev.getX() - dockLastX;
                        long now = android.os.SystemClock.uptimeMillis();
                        float dtSec = Math.max(1f, now - dockLastT) / 1000f;
                        dockVel = 0.75f * dockVel + 0.25f * (dx / dtSec);
                        dockLastX = ev.getX();
                        dockLastT = now;
                        if (dockPressed) {
                            if (Math.abs(ev.getX() - dockDownX) > 2 * dp) dockDragging = true;
                            // 槽位空间浮点驱动：dx → dx / tabWidth（模板 F08 规则，禁止像素坐标反推）
                            // 任意起点、全程 1:1 跟手（点击即定位 + 拖动即跟随）
                            slotSpring.value = clamp(slotSpring.value + dx / tabWidthPx,
                                    0f, tabItems.length - 1f);
                            slotSpring.target = slotSpring.value;
                            slotSpring.vel = 0f;
                            // 面板微位移累积（模板 offsetAnimation.snapTo）
                            offsetSpring.value = clamp(offsetSpring.value + dx,
                                    -totalWidthPx, totalWidthPx);
                            // 速度反馈（模板 VelocityTracker → velSpring）
                            velSpring.value = clamp(dockVel / 3000f, -1.5f, 1.5f);
                            kickSprings();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dockPressed && tabWidthPx > 0) {
                            // 松手吸附最近槽位（点击定位与拖动跟随统一走这里）
                            int target = (int) clamp(Math.round(slotSpring.value), 0, tabItems.length - 1);
                            if (target != selectedTab) selectTab(target);
                            else slotSpring.target = selectedTab;
                            velSpring.value = clamp(dockVel / 3000f, -1.5f, 1.5f);
                            velSpring.target = 0f;
                            offsetSpring.target = 0f;
                        }
                        dockPressed = false;
                        dockDragging = false;
                        pressSpring.target = 0f;
                        scaleXSpring.target = 1f;
                        scaleYSpring.target = 1f;
                        kickSprings();
                        v.performClick();
                        return true;
                }
                return false;
            }
        });

                // 活动玻璃胶囊（模板第 3 层：56dp 移动透镜；View 近似 = tint + 高光棱 + 内阴影）
        pill = new View(this) {
            private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF capRect = new RectF();
            @Override
            protected void onDraw(Canvas c) {
                int w = getWidth(), h = getHeight();
                if (w == 0 || h == 0) return;
                float p = pressProgress;
                float r = h / 2f;
                capRect.set(0, 0, w, h);
                // ① 基础 tint：浅色 Black10% / 深色 White10%，按压时淡出（模板 onDrawSurface）
                capPaint.setStyle(Paint.Style.FILL);
                capPaint.setColor(darkMode ? 0xFFFFFFFF : 0xFF000000);
                capPaint.setAlpha((int) (0.10f * 255 * (1f - p)));
                c.drawRoundRect(capRect, r, r, capPaint);
                // ② 按压加深 Black 3%×p（模板 drawRect(Black.copy(0.03f * progress))）
                if (p > 0.01f) {
                    capPaint.setColor(0xFF000000);
                    capPaint.setAlpha((int) (0.03f * 255 * p));
                    c.drawRoundRect(capRect, r, r, capPaint);
                }
                // ③ 折射近似（View 无透镜）：折射高度滑杆驱动边缘亮棱厚度（15dp=标准）
                float prism = glassPrismScale();
                if (prism > 0.01f) {
                    capPaint.setStyle(Paint.Style.STROKE);
                    capPaint.setStrokeWidth(Math.min(3f, 1.5f * prism) * dp);
                    capPaint.setColor(0xFFFFFFFF);
                    capPaint.setAlpha((int) (0.28f * 255 * Math.min(1f, prism)));
                    c.drawRoundRect(capRect, r, r, capPaint);
                }
                // ④ 高光描边 1dp，alpha = 0.75×p（模板 Highlight.Default.copy(alpha = pressProgress)）
                if (p > 0.01f) {
                    capPaint.setStyle(Paint.Style.STROKE);
                    capPaint.setStrokeWidth(1f * dp);
                    capPaint.setColor(0xFFFFFFFF);
                    capPaint.setAlpha((int) (0.75f * 255 * p));
                    c.drawRoundRect(capRect, r, r, capPaint);
                }
                // ⑤ 内阴影 8dp×p（模板 InnerShadow(radius = 8dp * progress)，近似为上沿暗带）
                if (p > 0.01f) {
                    c.save();
                    capsuleClip.reset();
                    capsuleClip.addRoundRect(capRect, r, r, Path.Direction.CW);
                    c.clipPath(capsuleClip);
                    capPaint.setStyle(Paint.Style.FILL);
                    capPaint.setShader(new LinearGradient(0, 0, 0, Math.max(1f, 8 * dp * p),
                            0x4D000000, 0x00000000, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, 8 * dp * p, capPaint);
                    capPaint.setShader(null);
                    c.restore();
                }
            }
        };
        pill.setAlpha(0f);
        barWrap.addView(pill, new FrameLayout.LayoutParams(
                (int) (56 * dp), FrameLayout.LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        barWrap.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        tabItems = new LinearLayout[4];
        tabIcons = new ImageView[4];
        tabLabels = new TextView[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumWidth((int) (76 * dp));   // 模板 defaultMinSize(minWidth = 76dp)

            // 图标包一层 FrameLayout：四个 Tab 统一占位 26dp，应用 Tab 可叠角标
            ImageView icon = new ImageView(this);
            icon.setImageResource(i == 0 ? TAB_ICON_ACTIVE[i] : TAB_ICON_IDLE[i]);
            icon.setColorFilter(i == 0 ? ACCENT : IDLE_TINT);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                    (int) (24 * dp), (int) (24 * dp), Gravity.CENTER);
            FrameLayout iconWrap = new FrameLayout(this);
            iconWrap.addView(icon, iconLp);
            if (i == 1) {
                // 作用域角标：初始隐藏，后台读 scope.list 后有作用域应用才显示数量
                TextView badge = new TextView(this);
                badge.setVisibility(View.GONE);
                badge.setTextSize(9);
                badge.setTextColor(0xFFFFFFFF);
                badge.setGravity(Gravity.CENTER);
                badge.setIncludeFontPadding(false);
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(BADGE_RED);
                badgeBg.setCornerRadius(7 * dp);
                badge.setBackground(badgeBg);
                iconWrap.addView(badge, new FrameLayout.LayoutParams(
                        (int) (14 * dp), (int) (14 * dp),
                        Gravity.TOP | Gravity.END));
                scopeBadge = badge;
            }
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                    (int) (26 * dp), (int) (26 * dp));
            wrapLp.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(iconWrap, wrapLp);

            TextView label = new TextView(this);
            label.setText(TAB_NAMES[i]);
            label.setTextSize(11f);
            label.setTextColor(i == 0 ? ACCENT : IDLE_TINT);
            label.setGravity(Gravity.CENTER_HORIZONTAL);   // 修复：文字水平居中
            label.setPadding(0, (int) (1 * dp), 0, 0);   // 模板 spacedBy(1dp)
            item.addView(label);   // 垂直 LinearLayout 默认 MATCH_PARENT 宽，配合 gravity 居中

            // 模板 LiquidBottomTab：无涟漪无按压缩小，按压反馈全部由活动胶囊承担，
            // 点击 / 拖拽统一由 barWrap 手势处理（槽位空间）
            tabItems[i] = item;
            tabIcons[i] = icon;
            tabLabels[i] = label;
            bar.addView(item, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }

        // 作用域角标初始值：由“应用”页加载数据后统一刷新（见 updateScopeBadge）
        updateScopeBadge();

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int) (64 * dp));   // 模板 height(64dp)
        barLp.gravity = Gravity.BOTTOM;
        barLp.leftMargin = (int) (14 * dp);
        barLp.rightMargin = (int) (14 * dp);
        barLp.bottomMargin = (int) (14 * dp);   // 模板 padding(bottom = 14dp)
        root.addView(barWrap, barLp);

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

        // 布局完成后：胶囊宽度 = 槽位宽（模板 tabWidthPx = 内容宽 / 标签数），槽位弹簧就位
        pill.post(() -> {
            tabWidthPx = tabItems[0].getWidth();
            totalWidthPx = bar.getWidth();
            FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) pill.getLayoutParams();
            plp.width = Math.round(tabWidthPx);
            pill.setLayoutParams(plp);
            slotSpring.value = slotSpring.target = selectedTab;
            renderDock();
            pill.animate().alpha(1f).setDuration(260).start();
        });
    }

    /** Tab 切换：滑动指示条 + 换图标 + 弹跳 + 颜色渐变 + 页面淡入淡出 */
    private void selectTab(int idx) {
        if (idx == selectedTab) return;
        int prev = selectedTab;
        selectedTab = idx;
        // 连点风暴期间玻璃零开销：每次点击重置 350ms 定时，停止点击后才补刷一次
        barWrap.removeCallbacks(engine.settleTask);
        barWrap.postDelayed(engine.settleTask, 350);
        if (idx == 1) appsPage.loadAppsAsync();   // 每次切到"应用"页都刷新安装状态

        // ① 活动胶囊：槽位空间浮点弹簧驱动（模板 valueAnimation，弹跳全部来自弹簧本身）
        slotSpring.target = idx;
        kickSprings();

        // ② 图标换装 + 颜色渐变
        tabIcons[prev].setImageResource(TAB_ICON_IDLE[prev]);
        tabIcons[idx].setImageResource(TAB_ICON_ACTIVE[idx]);
        animateTint(tabIcons[prev], ACCENT, IDLE_TINT);
        animateTint(tabIcons[idx], IDLE_TINT, ACCENT);
        tabLabels[prev].setTextColor(IDLE_TINT);
        tabLabels[idx].setTextColor(ACCENT);

        // ③ 页面切换动效：方向性滑动 + 缩放 + 淡入淡出（对标模板动效 Standard 320ms）
        animatePageSwitch(pageContainer.getChildAt(prev),
                pageContainer.getChildAt(idx), idx > prev);
    }

    /** 切屏动效：旧页反向滑出缩小淡出，新页从滑动方向滑入回弹淡入 */
    private void animatePageSwitch(View outPage, View inPage, boolean forward) {
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

    /** 每帧把弹簧状态落到视图（底栏唯一渲染出口，模板 graphicsLayer 的 View 版） */
    private void renderDock() {
        if (pill == null || tabWidthPx <= 0) return;
        pressProgress = pressSpring.value;
        // 面板微位移：4dp × sign × EaseOut(|offset/total|)（模板 panelOffset）
        float frac = totalWidthPx > 0 ? clamp(offsetSpring.value / totalWidthPx, -1f, 1f) : 0f;
        float shift = 4 * dp * Math.signum(frac) * easeOut(Math.abs(frac));
        pill.setX(bar.getX() + slotSpring.value * tabWidthPx + shift);
        // 保体积速度拉伸（模板 layerBlock：scaleX ÷、scaleY ×）
        float v1 = clamp(velSpring.value * 0.75f, -0.2f, 0.2f);
        float v2 = clamp(velSpring.value * 0.25f, -0.2f, 0.2f);
        pill.setScaleX(scaleXSpring.value / (1f - v1));
        pill.setScaleY(scaleYSpring.value * (1f - v2));
        // 容器按压放大：1 + 16dp / width × pressProgress（模板 layerBlock）
        float cs = 1f + (16 * dp / Math.max(1f, barWrap.getWidth())) * pressProgress;
        barWrap.setScaleX(cs);
        barWrap.setScaleY(cs);
        // 活动标签内容 1.2 倍按压缩放（模板 LocalLiquidBottomTabScale）
        int act = (int) clamp(Math.round(slotSpring.value), 0, tabItems.length - 1);
        for (int i = 0; i < tabItems.length; i++) {
            float s = i == act ? 1f + 0.2f * pressProgress : 1f;
            if (tabIcons[i] != null) { tabIcons[i].setScaleX(s); tabIcons[i].setScaleY(s); }
            if (tabLabels[i] != null) { tabLabels[i].setScaleX(s); tabLabels[i].setScaleY(s); }
        }
        pill.invalidate();      // tint / 高光棱 / 内阴影随 pressProgress 重绘
        barWrap.invalidate();   // 指尖镜面高光重绘
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ==== 偏好读取统一经 GlassPrefs（单一事实来源，不再散落各处直读键名） ====

    private float glassOpacity() {
        return Prefs.opacity(this);
    }

    /** 模糊强度 dp → 5×5 偏移采样间距；0 = 清晰通透（免 25 抽头） */
    /** 玻璃细则：动效阻尼 → 全部弹簧 DampedRatio 缩放（模板 运动.kt 公式） */
    private void applyMotionTuning() {
        float d = Prefs.damping(this);
        slotSpring.dampingRatio = dampedRatio(1f, d);
        pressSpring.dampingRatio = dampedRatio(1f, d);
        velSpring.dampingRatio = dampedRatio(0.5f, d);
        scaleXSpring.dampingRatio = dampedRatio(0.6f, d);
        scaleYSpring.dampingRatio = dampedRatio(0.7f, d);
        offsetSpring.dampingRatio = dampedRatio(1f, d);
    }

    private static float dampedRatio(float base, float damping) {
        return clamp(base * (0.72f + 0.56f * damping), 0.25f, 1.25f);
    }

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

    private void animateTint(ImageView iv, int from, int to) {
        ValueAnimator va = ValueAnimator.ofArgb(from, to);
        va.setDuration(220);
        va.addUpdateListener(a -> iv.setColorFilter((Integer) a.getAnimatedValue()));
        va.start();
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
        if (scopeBadge == null) return;
        if (!Prefs.appBadgeEnabled(this)) {   // 设置页总开关：不喜欢看就不显示
            scopeBadge.setVisibility(View.GONE);
            return;
        }
        int n = 0;
        if (appsData != null) {
            for (AppEntry e : appsData) {
                if (e.installed && isHookEnabled(e.pkg)) n++;
            }
        }
        if (n > 0) {
            scopeBadge.setText(String.valueOf(n));
            scopeBadge.setVisibility(View.VISIBLE);
        } else {
            scopeBadge.setVisibility(View.GONE);
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
        if (pill != null) pill.invalidate();
        applyMotionTuning();
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
                }
            }
            r.close();
            return n;
        } catch (Throwable t) {
            return 0;
        } finally {
            if (zf != null) try { zf.close(); } catch (Throwable ignored) {}
        }
    }

    /** 目标应用已装数量（仅查 17 个目标包：配合 Manifest <queries>，不再需要 QUERY_ALL_PACKAGES） */
    int installedAppCount() {
        int n = 0;
        for (String[] t : Targets.LIST) {
            try {
                getPackageManager().getPackageInfo(t[0], 0);
                n++;
            } catch (Throwable ignored) {}
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
