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
    private int ACCENT;
    private int INK;
    private int SUB;
    private int IDLE_TINT;
    private int BAR_BG;
    private int RIPPLE;
    private int STATUS_GREEN;
    private int BADGE_RED;
    private int CARD_BG;        // 卡片背景
    private int FIELD_BG;       // 输入框/按钮底色
    private int PILL_GRAY;      // 灰色胶囊（未安装）
    private int HINT;           // 弱化文字（未安装应用名）
    private int BACKDROP_BASE;  // 背景光斑底色
    private int BLOB1, BLOB2, BLOB3; // 三色光斑
    private int PILL_TAB;       // 底栏指示条底色
    private boolean darkMode;   // 当前解析结果是否深色
    private FrameLayout pageContainer;
    private FrameLayout barWrap;
    private LinearLayout bar;
    private LinearLayout[] tabItems;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private TextView scopeBadge;             // "应用"Tab 作用域数量角标（读 APK 内 scope.list）
    private LinearLayout appsListContainer;  // "应用"页列表容器
    private android.widget.EditText appsSearch;  // 应用搜索框
    private TextView appsCountView;          // 标题旁数量
    private List<AppEntry> appsData;         // 应用页数据（后台加载）
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
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearLayout glassSectionContent;        // 玻璃细则折叠内容（平铺可折叠）
    private TextView glassChevron;                   // 折叠指示箭头
    private GradientDrawable barBgDrawable;          // 底栏容器底色引用（实时预览改透明度）
    private int barSurfaceColor = 0xF0F0F4;          // 当前主题的 surfaceContainer 底色
    private final Paint cardSamplePaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);   // 卡片背景裁剪画笔（vibrancy 随动）
    private volatile boolean glassCapturePass;   // 底栏软捕获期间：卡片只画蒙层（省 CPU）
    private BackdropView backdrop;           // 背景层引用
    private boolean blurBusy;                // 捕获中标志（防重入）
    private long lastGlassMs;                // 上次玻璃重捕时刻（节流用）
    private boolean glassScheduled;          // 已有延迟补刷在排队
    /** 玻璃重捕最小间隔：快速连点/滚动的请求会被合并，绝不同帧堆积 */
    private static final long GLASS_MIN_INTERVAL_MS = 33;
    private final Runnable glassSettleTask = this::requestGlassRefresh;   // 连点停止 350ms 后补刷一次
    private final Path glassClip = new Path();       // 底栏圆角裁剪（尺寸不变时复用，免每帧 new）
    private final RectF glassRect = new RectF();
    private int clipW = -1, clipH = -1;
    private final Paint glassPaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint contentPaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);   // 内容虚影：60% 透明度
    private Bitmap[] glassChainBg, glassChainFg;   // 双降采样链（预分配，滚动零分配）
    private Canvas[] glassCanvasBg, glassCanvasFg;
    private RectF[] glassLevels;
    private int glassW = -1, glassH = -1;    // 链基准尺寸
    private Bitmap fgBlurBmp;                // 内容 box 模糊结果（与 fg[0] 同尺寸 1/2 分辨率）
    private Canvas fgBlurCanvas;
    private final Paint blurTapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF tapRect = new RectF();
    /** 内容模糊档位：5×5 采样间距（1/2分辨率像素，×2=屏幕px），越大越糊，推荐 3~5 */
    private static final int FG_BLUR_STEP = 4;
    private int selectedTab = 0;
    private float dp;

    /** 解析外观模式（跟随系统/浅色/深色）并赋值主题色；必须在 onCreate 最先调用 */
    private void applyTheme() {
        int mode = hookPrefs().getInt("theme_mode", 0);
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
            mode = base.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                    .getInt("locale_mode", 0);
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
        contentPaint.setAlpha(220);   // 内容虚影透明度（0-255），220≈86% 保内容可辨
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
        backdrop = new BackdropView(this);
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
        pageContainer.addView(makeHomePage());
        pageContainer.addView(makeAppsPage());
        pageContainer.addView(makeExtensionPage());
        pageContainer.addView(makeSettingsPage());
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
                drawBarGlass(canvas);   // 实时磨砂画在背景之上、图标之下
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
        barWrap.removeCallbacks(glassSettleTask);
        barWrap.postDelayed(glassSettleTask, 350);
        if (idx == 1) loadAppsAsync();   // 每次切到"应用"页都刷新安装状态

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
        return GlassPrefs.opacity(this);
    }

    /** 模糊强度 dp → 5×5 偏移采样间距；0 = 清晰通透（免 25 抽头） */
    private int fgBlurStep() {
        return (int) GlassPrefs.clamp(Math.round(GlassPrefs.blurDp(this) / 4f), 0, 10);
    }

    /** 玻璃细则：动效阻尼 → 全部弹簧 DampedRatio 缩放（模板 运动.kt 公式） */
    private void applyMotionTuning() {
        float d = GlassPrefs.damping(this);
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
        if (GlassPrefs.vibrancy(this)) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1.18f);
            cf = new ColorMatrixColorFilter(cm);
        }
        contentPaint.setColorFilter(cf);
        glassPaint.setColorFilter(cf);
        cardSamplePaint.setColorFilter(cf);   // 全部磨砂卡片背景层同步鲜艳增强
    }

    private void animateTint(ImageView iv, int from, int to) {
        ValueAnimator va = ValueAnimator.ofArgb(from, to);
        va.setDuration(220);
        va.addUpdateListener(a -> iv.setColorFilter((Integer) a.getAnimatedValue()));
        va.start();
    }

    /** 首页：状态卡 + 双统计卡 + 信息卡列表 + 反馈卡（对照参考模块版式） */
    private View makeHomePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * dp);
        box.setPadding(pad, (int) (10 * dp), pad, (int) (108 * dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ① 大标题
        TextView title = new TextView(this);
        title.setText(getApplicationInfo().loadLabel(getPackageManager()));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setPadding((int) (2 * dp), 0, 0, (int) (20 * dp));
        box.addView(title);

        // ② 状态卡：图标 + 激活状态 + 版本号 + Beta 徽章
        LinearLayout statusCard = makeCard();
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding((int) (20 * dp), (int) (18 * dp),
                (int) (14 * dp), (int) (18 * dp));

        final HoldShieldView shield = new HoldShieldView(this);
        boolean allOn = true;
        if (appsData != null && !appsData.isEmpty()) {
            for (AppEntry entry : appsData) {
                if (entry.installed && !isHookEnabled(entry.pkg)) {
                    allOn = false;
                    break;
                }
            }
        }
        shield.setActive(allOn);
        shield.setListener(new HoldShieldView.Listener() {
            @Override
            public void onHoldComplete() {
                boolean targetState = !shield.isActive();
                shield.setActive(targetState);
                if (appsData != null) {
                    for (AppEntry e : appsData) {
                        setHookEnabled(e.pkg, targetState);
                    }
                }
                updateScopeBadge();
                renderAppsList();
                android.widget.Toast.makeText(SettingsActivity.this,
                        targetState ? "已一键开启所有应用广告拦截与优化" : "已重置并关闭所有应用适配拦截",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onTapHint() {
                android.widget.Toast.makeText(SettingsActivity.this,
                        "长按盾牌图标 1 秒，一键全局开关全部应用净化",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        statusCard.addView(shield, new LinearLayout.LayoutParams(
                (int) (48 * dp), (int) (48 * dp)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (14 * dp), 0, 0, 0);
        // 真实激活检测：LSPosed 激活时会把 XposedBridge 注入 boot classpath（任何进程可查）
        boolean fwActive = isFrameworkActive();
        int fwVer = fwActive ? frameworkVersion() : -1;
        TextView status = new TextView(this);
        status.setText(fwActive ? "已激活" : "未激活");
        status.setTextSize(17);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(fwActive ? STATUS_GREEN : BADGE_RED);
        mid.addView(status);
        TextView ver = new TextView(this);
        ver.setText(fwActive
                ? appVersion() + (fwVer > 0 ? " · 框架 v" + fwVer : "")
                : appVersion() + " · 未检测到 LSPosed / Xposed 框架");
        ver.setTextSize(12.5f);
        ver.setTextColor(SUB);
        ver.setPadding(0, (int) (3 * dp), 0, 0);
        mid.addView(ver);
        statusCard.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chip = new TextView(this);
        chip.setText("Beta");
        chip.setTextSize(11);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding((int) (12 * dp), (int) (5 * dp), (int) (12 * dp), (int) (5 * dp));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(ACCENT);
        chipBg.setCornerRadius(99 * dp);
        chip.setBackground(chipBg);
        statusCard.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addCard(box, statusCard);

        // ③ 双统计卡
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        // 适配应用卡：scopeAppCount() 要读 APK zip + 逐包 getPackageInfo（慢），
        // 与“已安装软件”卡同策略：先占位，后台线程统计后回填（v1.0.11 接真）
        LinearLayout scopeCard = makeCard();
        scopeCard.setPadding((int) (20 * dp), (int) (16 * dp),
                (int) (20 * dp), (int) (16 * dp));
        TextView scopeLabel = new TextView(this);
        scopeLabel.setText("适配应用");
        scopeLabel.setTextSize(13);
        scopeLabel.setTextColor(SUB);
        scopeCard.addView(scopeLabel);
        final TextView scopeVal = new TextView(this);
        scopeVal.setText("…");
        scopeVal.setTextSize(24);
        scopeVal.setTypeface(Typeface.DEFAULT_BOLD);
        scopeVal.setTextColor(INK);
        scopeVal.setPadding(0, (int) (4 * dp), 0, 0);
        scopeCard.addView(scopeVal);
        statRow.addView(scopeCard, half1);
        new Thread(() -> {
            final int n = scopeAppCount();
            runOnUiThread(() -> scopeVal.setText(String.valueOf(n)));
        }, "scope-count").start();
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = (int) (12 * dp);
        // 目标应用卡：逐包查 17 个目标（<queries> 已声明可见），后台线程统计后回填
        LinearLayout appsCard = makeCard();
        appsCard.setPadding((int) (20 * dp), (int) (16 * dp),
                (int) (20 * dp), (int) (16 * dp));
        TextView appsLabel = new TextView(this);
        appsLabel.setText("目标应用已装");
        appsLabel.setTextSize(13);
        appsLabel.setTextColor(SUB);
        appsCard.addView(appsLabel);
        final TextView appsVal = new TextView(this);
        appsVal.setText("…");
        appsVal.setTextSize(24);
        appsVal.setTypeface(Typeface.DEFAULT_BOLD);
        appsVal.setTextColor(INK);
        appsVal.setPadding(0, (int) (4 * dp), 0, 0);
        appsCard.addView(appsVal);
        statRow.addView(appsCard, half2);
        new Thread(() -> {
            final int n = installedAppCount();
            runOnUiThread(() -> appsVal.setText(String.valueOf(n)));
        }, "app-count").start();
        LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statLp.bottomMargin = (int) (12 * dp);
        box.addView(statRow, statLp);

        // ④ 信息卡列表（全部为运行时真实设备数据）
        String[][] infos = {
                {"构建时间", BuildInfo.BUILD_TIME},
                {"安卓版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ", REL)"},
                {"设备型号", Build.MODEL + " (" + Build.PRODUCT + ")"},
                {"系统架构", Build.SUPPORTED_ABIS[0]},
                {"框架 & API", "libxposed API 102（Modern Xposed）"},
        };
        for (String[] row : infos) {
            LinearLayout card = makeCard();
            card.setPadding((int) (20 * dp), (int) (16 * dp),
                    (int) (20 * dp), (int) (16 * dp));
            TextView l = new TextView(this);
            l.setText(row[0]);
            l.setTextSize(13);
            l.setTextColor(SUB);
            card.addView(l);
            TextView v = new TextView(this);
            v.setText(row[1]);
            v.setTextSize(15);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            v.setTextColor(INK);
            v.setPadding(0, (int) (3 * dp), 0, 0);
            card.addView(v);
            addCard(box, card);
        }

        // ⑤ 反馈卡：文字 + 两个圆形图标按钮
        LinearLayout feedCard = makeCard();
        feedCard.setOrientation(LinearLayout.HORIZONTAL);
        feedCard.setGravity(Gravity.CENTER_VERTICAL);
        feedCard.setPadding((int) (20 * dp), (int) (18 * dp),
                (int) (14 * dp), (int) (18 * dp));
        TextView feed = new TextView(this);
        feed.setText("在 GitHub 反馈");
        feed.setTextSize(15);
        feed.setTypeface(Typeface.DEFAULT_BOLD);
        feed.setTextColor(INK);
        feedCard.addView(feed, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        feedCard.addView(roundButton(R.drawable.ic_action_code), roundLp(0));
        feedCard.addView(roundButton(R.drawable.ic_action_chat), roundLp((int) (12 * dp)));
        addCard(box, feedCard);

        return scroll;
    }

    /** Hook 自检卡：读取目标应用进程经 Provider 回传的安装结果（未上报/已装/失败明细） */
    private LinearLayout selfCheckCard() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (16 * dp), (int) (14 * dp), (int) (16 * dp), (int) (8 * dp));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("目标应用安装状态");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(13);
        refresh.setTextColor(ACCENT);
        refresh.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        refresh.setOnClickListener(v -> renderSelfCheck(list));
        head.addView(refresh);
        card.addView(head);
        card.addView(list);
        renderSelfCheck(list);
        return card;
    }

    private void renderSelfCheck(LinearLayout list) {
        list.removeAllViews();
        android.content.SharedPreferences sp =
                getSharedPreferences("selfcheck", MODE_PRIVATE);
        java.text.DateFormat tf = new java.text.SimpleDateFormat("MM-dd HH:mm",
                java.util.Locale.getDefault());
        for (String[] t : Targets.LIST) {
            String row = sp.getString(t[0], null);
            TextView line = new TextView(this);
            line.setTextSize(12);
            line.setPadding(0, (int) (4 * dp), 0, (int) (4 * dp));
            if (row == null) {
                line.setText(t[1].split(" · ")[0] + "：未上报（打开一次目标应用后自动回传）");
                line.setTextColor(SUB);
            } else {
                String[] p = row.split("\\|", -1);   // ok|miss|fails|version|at
                int ok = 0, miss = 0;
                try { ok = Integer.parseInt(p[0]); } catch (Throwable ignored) {}
                try { miss = Integer.parseInt(p[1]); } catch (Throwable ignored) {}
                String fails = p.length > 2 ? p[2] : "";
                String ver = p.length > 3 ? p[3] : "?";
                long at = 0L;
                try { at = Long.parseLong(p.length > 4 ? p[4] : "0"); } catch (Throwable ignored) {}
                StringBuilder sb = new StringBuilder();
                sb.append(t[1].split(" · ")[0]).append("：已装 ").append(ok).append(" 项");
                if (miss > 0) sb.append(" · 缺 ").append(miss);
                if (fails != null && !fails.isEmpty()) {
                    sb.append(" · 失败: ").append(fails, 0, Math.min(fails.length(), 80));
                    line.setTextColor(BADGE_RED);
                } else {
                    line.setTextColor(ok > 0 ? STATUS_GREEN : SUB);
                }
                sb.append("\n").append("v").append(ver).append(" · ")
                        .append(at > 0 ? tf.format(new java.util.Date(at)) : "—");
                line.setText(sb.toString());
            }
            list.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** 桌面图标开关：真实切换 activity-alias（禁用即从桌面隐藏，启用即恢复） */
    private LinearLayout desktopIconRow() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int vpad = (int) (16 * dp);
        card.setPadding((int) (14 * dp), vpad, (int) (14 * dp), vpad);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_set_home);
        icon.setColorFilter(ACCENT);
        card.addView(icon, new LinearLayout.LayoutParams((int) (26 * dp), (int) (26 * dp)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (12 * dp), 0, 0, 0);
        TextView t1 = new TextView(this);
        t1.setText("在桌面显示图标");
        t1.setTextSize(15);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(INK);
        mid.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText("隐藏后可从 LSPosed 模块列表重新打开本页；若图标未消失，请检查 LSPosed 是否开启了强制显示桌面图标");
        t2.setTextSize(12);
        t2.setTextColor(SUB);
        t2.setPadding(0, (int) (2 * dp), 0, 0);
        mid.addView(t2);
        card.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(isLauncherAliasEnabled());
        sw.setOnCheckedChangeListener((v, checked) -> {
            if (!checked) {
                // 隐藏前必须确认：隐藏后桌面与 LSPosed「打开」都会失效，给足恢复方式
                sw.setChecked(true);   // 先回弹，确认后才真正隐藏
                new android.app.AlertDialog.Builder(this)
                        .setTitle("隐藏桌面图标")
                        .setMessage("隐藏后将无法从桌面打开，LSPosed 的「打开」也可能失效。\n\n"
                                + "恢复方式（任选其一）：\n"
                                + "① adb shell am broadcast -a com.example.l.action.SHOW_ICON\n"
                                + "② adb shell pm enable com.example.l/.IconAlias\n"
                                + "③ 系统设置 → 应用 → PureStart → 卸载后重装（设置会丢失）")
                        .setPositiveButton("仍要隐藏", (d, w) -> setDesktopIcon(sw, false))
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                setDesktopIcon(sw, true);
            }
        });
        card.addView(sw);
        return card;
    }

    private void setDesktopIcon(android.widget.Switch sw, boolean show) {
        try {
            getPackageManager().setComponentEnabledSetting(
                    new android.content.ComponentName(this, "com.example.l.IconAlias"),
                    show ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            hookPrefs().edit().putBoolean("set_desktop_icon", show).apply();
            sw.setChecked(show);
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "切换失败：" + t,
                    android.widget.Toast.LENGTH_SHORT).show();
            sw.setChecked(isLauncherAliasEnabled());
        }
    }

    /** 桌面图标的真实状态以 PackageManager 为准（LSPosed 强制显示等外部改动也能正确反映） */
    private boolean isLauncherAliasEnabled() {
        try {
            return getPackageManager().getComponentEnabledSetting(
                    new android.content.ComponentName(this, "com.example.l.IconAlias"))
                    != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 全局磨砂玻璃卡片：裁剪窗口内背景位图模糊 + 染色层 + 棱光 + 高光描边
     *  （GlassCardView，磨砂细则滑杆实时生效；详见类注释） */
    private LinearLayout makeCard() {
        GlassCardView card = new GlassCardView(this, this);
        card.setOrientation(LinearLayout.VERTICAL);
        return card;
    }

    private void addCard(LinearLayout box, LinearLayout card) {
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
    private ImageView roundButton(int iconRes) {
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

    private LinearLayout.LayoutParams roundLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (44 * dp), (int) (44 * dp));
        lp.leftMargin = leftMargin;
        return lp;
    }

    /** 检测 Xposed / LSPosed 框架是否可用（框架激活时会将 XposedBridge 注入 boot classpath） */
    private static boolean isFrameworkActive() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Xposed 框架版本（getXposedVersion()），取不到返回 -1 */
    private static int frameworkVersion() {
        try {
            Class<?> c = Class.forName("de.robv.android.xposed.XposedBridge");
            return (Integer) c.getMethod("getXposedVersion").invoke(null);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 开关偏好（模块私有）；Hook 侧经 HookStateProvider 跨进程读取 */
    private android.content.SharedPreferences hookPrefs() {
        if (hookPrefs == null) {
            hookPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        }
        return hookPrefs;
    }

    private boolean isHookEnabled(String pkg) {
        // 唯一事实源 = 模块私有偏好（Hook 侧经 HookStateProvider 跨进程读取）
        return hookPrefs().getBoolean("hook_" + pkg, true);   // 默认开启
    }

    /** 刷新底栏“应用”角标：= 已安装且勾选启用的作用域应用数；为 0 或设置页关掉角标则隐藏 */
    private void updateScopeBadge() {
        if (scopeBadge == null) return;
        if (!hookPrefs().getBoolean("set_app_badge", true)) {   // 设置页总开关：不喜欢看就不显示
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

        private void setHookEnabled(String pkg, boolean enabled) {
        // 只写模块私有偏好；Hook 侧经 HookStateProvider 读取（不受 ROM 权限限制）
        hookPrefs().edit().putBoolean("hook_" + pkg, enabled).apply();
    }

    /** "设置"页：分组 + 卡片行（对标 FFV 设置页；行内功能待接） */
    private View makeSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * dp);
        box.setPadding(pad, (int) (10 * dp), pad, (int) (108 * dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ① 大标题
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setPadding((int) (2 * dp), 0, 0, (int) (14 * dp));
        box.addView(title);

        // ===== 分组：个性化 =====
        box.addView(sectionHeader("个性化"));
        addCard(box, appearanceRow());
        addCard(box, setRow(R.drawable.ic_set_drop, "磨砂模糊", "为模块界面增加磨砂玻璃（View 近似）效果", "set_liquid_blur"));
        addCard(box, glassSectionCard());
        addCard(box, languageRow());
        addCard(box, setRow(R.drawable.ic_set_bell, "底栏应用角标", "在底栏“应用”按钮上显示数量角标", "set_app_badge"));

        // ===== 分组：桌面图标 =====
        box.addView(sectionHeader("桌面图标"));
        addCard(box, desktopIconRow());

        // ===== 分组：Hook 自检 =====
        box.addView(sectionHeader("Hook 自检"));
        addCard(box, selfCheckCard());

        // ===== 分组：更新 =====
        box.addView(sectionHeader("更新"));
        addCard(box, setRow(R.drawable.ic_set_refresh, "自动检查更新",
                "应用启动时自动检测新版本", "set_auto_update"));
        addCard(box, setRow(R.drawable.ic_set_refresh, "获取更新", null, null));

        return scroll;
    }

    /** 设置页分组小标题 */
    private TextView sectionHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(SUB);
        t.setPadding((int) (6 * dp), (int) (16 * dp), 0, (int) (8 * dp));
        return t;
    }

    /** 设置行卡片：图标 + 标题/副标题 +（可选）Switch（switchKey 非空时显示并持久化到偏好） */
    private LinearLayout setRow(int iconRes, String title, String sub, String switchKey) {
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
            sw.setChecked(hookPrefs().getBoolean(switchKey, true));
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

    /** 语言行：跟随系统 / 简体中文（即时生效，重建界面） */
    private LinearLayout languageRow() {
        int mode = hookPrefs().getInt("locale_mode", 0);
        LinearLayout row = setRow(R.drawable.ic_set_lang, "语言",
                mode == 1 ? "简体中文（强制）" : "跟随系统", null);
        row.setOnClickListener(v -> showLanguageDialog());
        return row;
    }

    private void showLanguageDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int dp24 = (int) (24 * dp);
        card.setPadding(dp24, dp24, dp24, dp24);
        card.setBackground(LiquidGlass.glassCard(this, darkMode, 28));

        TextView title = new TextView(this);
        title.setText("界面语言");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        card.addView(title);

        int cur = hookPrefs().getInt("locale_mode", 0);
        final String[] names = {"跟随系统", "简体中文（强制）"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView item = new TextView(this);
            item.setText(names[i] + (i == cur ? "  ✓" : ""));
            item.setTextSize(14.5f);
            item.setTextColor(INK);
            item.setPadding((int) (12 * dp), (int) (14 * dp), (int) (12 * dp), (int) (14 * dp));
            item.setOnClickListener(v -> {
                hookPrefs().edit().putInt("locale_mode", idx).apply();
                dialog.dismiss();
                recreate();
            });
            card.addView(item);
        }
        TextView note = new TextView(this);
        note.setText("英文界面翻译筹备中，当前界面为简体中文。");
        note.setTextSize(11);
        note.setTextColor(SUB);
        note.setPadding(0, (int) (10 * dp), 0, 0);
        card.addView(note);

        android.widget.FrameLayout rootWrap = new android.widget.FrameLayout(this);
        int dp16 = (int) (16 * dp);
        rootWrap.setPadding(dp16, dp16, dp16, dp16);
        rootWrap.addView(card);
        dialog.setContentView(rootWrap);
        dialog.show();
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (320 * dp), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            LiquidGlass.applyFrostedWindow(w, dp);
        }
    }

    /** 玻璃细则：平铺可折叠卡片（对标 ui 模板 AppChrome，改动即时实时预览） */
    private LinearLayout glassSectionCard() {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        // 头部：图标 + 标题 + 折叠箭头
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int) (14 * dp), (int) (14 * dp), (int) (14 * dp), (int) (14 * dp));
        ImageView hIcon = new ImageView(this);
        hIcon.setImageResource(R.drawable.ic_set_palette);
        hIcon.setColorFilter(ACCENT);
        header.addView(hIcon, new LinearLayout.LayoutParams((int) (26 * dp), (int) (26 * dp)));
        LinearLayout hMid = new LinearLayout(this);
        hMid.setOrientation(LinearLayout.VERTICAL);
        hMid.setPadding((int) (12 * dp), 0, 0, 0);
        TextView hTitle = new TextView(this);
        hTitle.setText("磨砂玻璃（View 近似）");
        hTitle.setTextSize(15);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hTitle.setTextColor(INK);
        hMid.addView(hTitle);
        TextView hSub = new TextView(this);
        hSub.setText("参数取值借鉴 ui 模板 · 拖动实时预览");
        hSub.setTextSize(12);
        hSub.setTextColor(SUB);
        hSub.setPadding(0, (int) (2 * dp), 0, 0);
        hMid.addView(hSub);
        header.addView(hMid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        glassChevron = new TextView(this);
        glassChevron.setText("▾");
        glassChevron.setTextSize(18);
        glassChevron.setTextColor(SUB);
        glassChevron.setRotation(180f);   // 默认展开（平铺）
        header.addView(glassChevron);
        header.setOnClickListener(v -> toggleGlassSection());
        card.addView(header);

        // 内容：默认平铺展开，滑杆改动实时预览
        glassSectionContent = new LinearLayout(this);
        glassSectionContent.setOrientation(LinearLayout.VERTICAL);
        glassSectionContent.setPadding((int) (14 * dp), 0, (int) (14 * dp), (int) (12 * dp));
        // 参数、范围、默认值、副标题文案均与模板 SettingsScreen.kt「动效」区一一对应
        glassSliderRow(glassSectionContent, "动效阻尼", "glass_damping", 0, 100, 50, "%",
                v -> "全局弹簧阻尼：" + v + "%，越低越弹");
        glassSliderRow(glassSectionContent, "模糊强度", "glass_blur", 0, 40, 0, "dp",
                v -> "背景模糊强度：" + v + "dp");
        glassSliderRow(glassSectionContent, "折射高度", "glass_refract_height", 0, 40, 15, "dp",
                v -> "液态透镜边缘高度：" + v + "dp");
        glassSliderRow(glassSectionContent, "折射幅度", "glass_refract_amount", 0, 60, 20, "dp",
                v -> "壁纸内容偏移幅度：" + v + "dp");
        glassSliderRow(glassSectionContent, "玻璃染色", "glass_tint_alpha", 0, 35, 8, "%",
                v -> "白色玻璃涂层：" + v + "%");
        glassSliderRow(glassSectionContent, "高光边框", "glass_border", 0, 45, 20, "%",
                v -> "玻璃边缘强度：" + v + "%");
        glassSwitchRow(glassSectionContent, "增强色彩层次", "glass_vibrancy",
                "提升玻璃后的壁纸色彩通透感");
        glassSwitchRow(glassSectionContent, "色差折射", "glass_chromatic",
                "在液态透镜边缘显示更明显的色散");
        TextView note = new TextView(this);
        note.setText("【与模板真玻璃的差异】\n"
                + "· 无 backdrop 实时折射：View 无法录制任意图层，卡片=窗口内背景位图按坐标裁剪，底栏=限频截图降分辨率模糊（均非 Compose backdrop 语义）；\n"
                + "· 无 lens 折射：折射高度→棱光厚度、折射幅度→背景层放大均为自创近似映射（模板中不存在此对应关系），滚动经过下方的内容不会被折射；\n"
                + "· 弹窗在 Android 12+ 用系统窗口背景模糊（真磨砂），12 以下退化为半透明蒙层。\n"
                + "【验收】滑动不掉帧 · 深浅色均可读 · 可点区域不受影响。");
        note.setTextSize(11);
        note.setTextColor(SUB);
        note.setLineSpacing(3 * dp, 1f);
        note.setPadding(0, (int) (10 * dp), 0, 0);
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));

        TextView nm = new TextView(this);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(INK);
        row.addView(nm);
        final TextView sub = new TextView(this);
        sub.setTextSize(12);
        sub.setTextColor(SUB);
        sub.setPadding(0, (int) (2 * dp), 0, (int) (4 * dp));
        sub.setText(subtitle.apply(hookPrefs().getInt(key, def)));
        row.addView(sub);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(hookPrefs().getInt(key, def) - min);
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(ACCENT));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(
                darkMode ? 0x33FFFFFF : 0x1A18181B));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p;
                sub.setText(subtitle.apply(v));
                hookPrefs().edit().putInt(key, v).apply();
                applyGlassLive();   // 实时预览：拖动即刻生效
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        LinearLayout tm = new LinearLayout(this);
        tm.setOrientation(LinearLayout.VERTICAL);
        TextView nm = new TextView(this);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(INK);
        tm.addView(nm);
        TextView sb = new TextView(this);
        sb.setText(subtitle);
        sb.setTextSize(12);
        sb.setTextColor(SUB);
        sb.setPadding(0, (int) (2 * dp), 0, 0);
        tm.addView(sb);
        row.addView(tm, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(hookPrefs().getBoolean(key, true));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hookPrefs().edit().putBoolean(key, isChecked).apply();
            applyGlassLive();
        });
        row.addView(sw);
        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 玻璃细则实时预览：一处改动，底栏 + 卡片 + 弹簧 + 采样全部即时刷新 */
    private void applyGlassLive() {
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
        return GlassPrefs.tintAlpha(this);
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
        return GlassPrefs.clamp(GlassPrefs.prismHeightDp(this) / 15f, 0f, 2f);
    }

    /** 折射幅度滑杆（0..60dp，默认 20）→ 背景层放大（透镜内容偏移的 View 近似映射） */
    @Override
    public int glassRefractionAmountDp() {
        return GlassPrefs.prismAmountDp(this);
    }

    /** 色差折射开关（默认开）→ 棱光 RGB 色散（近似） */
    @Override
    public boolean glassChromaticEnabled() {
        return GlassPrefs.chromatic(this);
    }

    @Override
    public boolean isGlassCapturePass() { return glassCapturePass; }

    @Override
    public int glassBlurDp() {
        return GlassPrefs.blurDp(this);
    }

    @Override
    public Paint glassCardSamplePaint() { return cardSamplePaint; }

    private int cardStrokeColor() {
        int bAlpha = Math.round(GlassPrefs.borderAlpha(this) * 255f);
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
    private LinearLayout appearanceRow() {
        final String[] names = {"跟随系统", "浅色液态玻璃", "深色液态玻璃", "极光流体玻璃"};
        int cur = hookPrefs().getInt("theme_mode", 0);
        String sub = names[cur >= 0 && cur < names.length ? cur : 0];
        if (cur == 0) sub += darkMode ? "（当前深色）" : "（当前浅色）";
        LinearLayout row = setRow(R.drawable.ic_set_palette, "外观", sub, null);
        row.setOnClickListener(v -> {
            ThemeDialog.show(this, darkMode, which -> {
                recreate();
            });
        });
        return row;
    }

    /** 作用域应用数：读 APK 内 META-INF/xposed/scope.list，逐行统计（空行与 # 注释忽略）
     *  ⚠ LSPosed 管理器里动态勾选的存在其私有数据库，App 进程读不到；这里是模块声明的静态作用域 */
    private int scopeAppCount() {
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
    private int installedAppCount() {
        int n = 0;
        for (String[] t : Targets.LIST) {
            try {
                getPackageManager().getPackageInfo(t[0], 0);
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    private String appVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) {
            return "1.0";
        }
    }

    /** 占位页面：标题 + 副标题 + 卡片 */    /** 占位页面：标题 + 副标题 + 卡片 */
    /** 拓展页：激活指引 + 配置备份/恢复 + 关于（真实可用功能） */
    private View makeExtensionPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * dp);
        box.setPadding(pad, (int) (26 * dp), pad, (int) (108 * dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("拓展");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        box.addView(title);
        TextView sub = new TextView(this);
        sub.setText("激活指引 · 数据管理 · 关于模块");
        sub.setTextSize(14);
        sub.setTextColor(SUB);
        sub.setPadding(0, (int) (6 * dp), 0, (int) (16 * dp));
        box.addView(sub);

        // ===== 分组：激活指引 =====
        box.addView(sectionHeader("激活指引"));
        LinearLayout guide = makeCard();
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setPadding((int) (18 * dp), (int) (16 * dp), (int) (18 * dp), (int) (16 * dp));
        String[] steps = {
                "① 打开 LSPosed →「模块」→ 找到 PureStart 并启用",
                "② 在「作用域」中勾选需要净化的应用（本模块已内置推荐作用域）",
                "③ 强制停止或重启勾选的目标应用（无需重启手机）",
                "④ 回到本模块「应用」页确认对应应用已显示启用状态",
        };
        for (String s : steps) {
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextSize(13.5f);
            t.setTextColor(INK);
            t.setLineSpacing(3 * dp, 1f);
            t.setPadding(0, (int) (3 * dp), 0, (int) (3 * dp));
            guide.addView(t);
        }
        TextView faq = new TextView(this);
        faq.setText("排查：拦截无效时请依次确认模块已启用、作用域已勾选、目标应用已重启；"
                + "本模块基于 libxposed API 102，需 LSPosed 1.9.2+ 等现代框架。");
        faq.setTextSize(12);
        faq.setTextColor(SUB);
        faq.setLineSpacing(3 * dp, 1f);
        faq.setPadding(0, (int) (10 * dp), 0, 0);
        guide.addView(faq);
        addCard(box, guide);

        // ===== 分组：数据管理 =====
        box.addView(sectionHeader("数据管理"));
        LinearLayout backupCard = setRow(R.drawable.ic_set_refresh, "备份配置",
                "导出全部应用开关与偏好为 JSON 文件", null);
        backupCard.setOnClickListener(v -> {
            try {
                android.content.Intent i = new android.content.Intent(
                        android.content.Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(android.content.Intent.EXTRA_TITLE, "purestart-backup.json");
                startActivityForResult(i, 41);
            } catch (Throwable t) {
                toast("无法打开文件选择器：" + t.getMessage());
            }
        });
        addCard(box, backupCard);

        LinearLayout restoreCard = setRow(R.drawable.ic_set_drop, "恢复配置",
                "从备份 JSON 导入应用开关与偏好", null);
        restoreCard.setOnClickListener(v -> {
            try {
                android.content.Intent i = new android.content.Intent(
                        android.content.Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                startActivityForResult(i, 42);
            } catch (Throwable t) {
                toast("无法打开文件选择器：" + t.getMessage());
            }
        });
        addCard(box, restoreCard);

        // ===== 分组：关于 =====
        box.addView(sectionHeader("关于"));
        LinearLayout about = makeCard();
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding((int) (18 * dp), (int) (16 * dp), (int) (18 * dp), (int) (16 * dp));
        String[] infos = {
                "开源地址", "github.com/yz8023/PureStart",
                "模块版本", "v" + BuildVersion.VERSION_NAME + " (ModKit Edition)",
                "Hook 接口", "libxposed API 102（Modern Xposed）",
                "构建时间", BuildInfo.BUILD_TIME,
        };
        for (int i = 0; i < infos.length; i += 2) {
            TextView line = new TextView(this);
            line.setText(infos[i] + "：" + infos[i + 1]);
            line.setTextSize(13);
            line.setTextColor(i % 4 == 0 ? SUB : INK);
            line.setPadding(0, (int) (3 * dp), 0, (int) (3 * dp));
            about.addView(line);
        }
        addCard(box, about);
        return scroll;
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** 备份：全部偏好导出为 JSON */
    private void exportPrefs(android.net.Uri uri) {
        try {
            org.json.JSONObject jo = new org.json.JSONObject();
            java.util.Map<String, ?> all = hookPrefs().getAll();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) jo.put(e.getKey(), (Boolean) v);
                else if (v instanceof Integer) jo.put(e.getKey(), (Integer) v);
                else if (v instanceof Long) jo.put(e.getKey(), (Long) v);
                else if (v instanceof Float) jo.put(e.getKey(), (Float) v);
                else if (v instanceof String) jo.put(e.getKey(), (String) v);
            }
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) { toast("导出失败：无法写入"); return; }
            os.write(jo.toString().getBytes("UTF-8"));
            os.close();
            toast("已导出 " + jo.length() + " 项配置");
        } catch (Throwable t) {
            toast("导出失败：" + t.getMessage());
        }
    }

    /** 恢复：从 JSON 导入偏好并重建界面 */
    private void importPrefs(android.net.Uri uri) {
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { toast("导入失败：无法读取"); return; }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            org.json.JSONObject jo = new org.json.JSONObject(
                    new String(bos.toByteArray(), "UTF-8"));
            android.content.SharedPreferences.Editor ed = hookPrefs().edit();
            java.util.Iterator<String> it = jo.keys();
            int count = 0;
            while (it.hasNext()) {
                String k = it.next();
                Object v = jo.get(k);
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Long) ed.putLong(k, (Long) v);
                else if (v instanceof Float) ed.putFloat(k, (Float) v);
                else if (v instanceof String) ed.putString(k, (String) v);
                else continue;
                count++;
            }
            ed.apply();
            toast("已导入 " + count + " 项配置，界面即将刷新");
            barWrap.postDelayed(this::recreate, 600);
        } catch (Throwable t) {
            toast("导入失败：" + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null || data.getData() == null) return;
        if (requestCode == 41 && resultCode == RESULT_OK) exportPrefs(data.getData());
        else if (requestCode == 42 && resultCode == RESULT_OK) importPrefs(data.getData());
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
    private View makeAppsPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * dp);
        box.setPadding(pad, (int) (10 * dp), pad, (int) (108 * dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ① 标题行：适配应用 + 数量
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("适配应用");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setPadding((int) (2 * dp), 0, 0, 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appsCountView = new TextView(this);
        appsCountView.setText("…");
        appsCountView.setTextSize(13);
        appsCountView.setTextColor(SUB);
        titleRow.addView(appsCountView);
        box.addView(titleRow);

        // ② 搜索框
        appsSearch = new android.widget.EditText(this);
        appsSearch.setHint("搜索应用");
        appsSearch.setTextSize(14);
        appsSearch.setSingleLine(true);
        appsSearch.setTextColor(INK);
        appsSearch.setHintTextColor(HINT);
        appsSearch.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (12 * dp));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(FIELD_BG);
        searchBg.setCornerRadius(14 * dp);
        appsSearch.setBackground(searchBg);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = (int) (10 * dp);
        searchLp.bottomMargin = (int) (12 * dp);
        box.addView(appsSearch, searchLp);
        appsSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void afterTextChanged(android.text.Editable s) {
                renderAppsList();
            }
        });

        // ③ 列表容器（后台加载后填充）
        appsListContainer = new LinearLayout(this);
        appsListContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(appsListContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView loading = new TextView(this);
        loading.setText("正在读取作用域应用…");
        loading.setTextSize(13);
        loading.setTextColor(SUB);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, (int) (40 * dp), 0, (int) (40 * dp));
        appsListContainer.addView(loading);

        loadAppsAsync();
        return scroll;
    }

    /** 后台读 scope.list，解析每个包的安装状态/名称/版本 */
    private void loadAppsAsync() {
        final PackageManager pm = getPackageManager();
        new Thread(() -> {
            List<AppEntry> list = new ArrayList<>();
            java.util.zip.ZipFile zf = null;
            try {
                zf = new java.util.zip.ZipFile(getPackageCodePath());
                java.util.zip.ZipEntry entry = zf.getEntry("META-INF/xposed/scope.list");
                if (entry != null) {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(zf.getInputStream(entry), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        String label = line;
                        String version = "未安装";
                        boolean installed = false;
                        try {
                            PackageInfo pi = pm.getPackageInfo(line, 0);
                            installed = true;
                            label = pi.applicationInfo.loadLabel(pm).toString();
                            version = pi.versionName == null ? "—" : pi.versionName;
                        } catch (Throwable ignored) {
                        }
                        list.add(new AppEntry(line, label, version, installed));
                    }
                    r.close();
                }
            } catch (Throwable t) {
                // 读不到就按空列表处理
            } finally {
                if (zf != null) try { zf.close(); } catch (Throwable ignored) {
                }
            }
            // 已安装在前，名称排序
            Collections.sort(list, (a, b) -> {
                if (a.installed != b.installed) return a.installed ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            });
            final List<AppEntry> data = list;
            runOnUiThread(() -> {
                appsData = data;
                if (appsCountView != null) {
                    int inst = 0;
                    for (AppEntry e : data) if (e.installed) inst++;
                    appsCountView.setText(data.isEmpty() ? "0 个" : inst + " / " + data.size() + " 个");
                }
                renderAppsList();
                updateScopeBadge();   // 数据就绪后刷新底栏角标
            });
        }, "apps-page").start();
    }

    /** 按搜索词过滤渲染列表 */
    private void renderAppsList() {
        if (appsListContainer == null || appsData == null) return;
        appsListContainer.removeAllViews();
        String q = appsSearch.getText().toString().trim().toLowerCase();
        int shown = 0;
        for (AppEntry e : appsData) {
            if (!q.isEmpty()
                    && !e.label.toLowerCase().contains(q)
                    && !e.pkg.toLowerCase().contains(q)) {
                continue;
            }
            // 卡片间距：与首页一致（底部 12dp）
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = (int) (12 * dp);
            appsListContainer.addView(appCard(e), cardLp);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(appsData.isEmpty()
                    ? "scope.list 暂无作用域应用\n往 xposed/scope.list 加一行包名并重新构建"
                    : "没有匹配的应用");
            empty.setTextSize(13);
            empty.setTextColor(SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(4 * dp, 1f);
            empty.setPadding(0, (int) (48 * dp), 0, (int) (48 * dp));
            appsListContainer.addView(empty);
        }
    }

    /** 单张应用卡：图标 + 名称/包名/版本/功能 + 状态胶囊（对标 FFV 应用页） */
    private LinearLayout appCard(AppEntry e) {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (14 * dp), (int) (14 * dp),
                (int) (14 * dp), (int) (14 * dp));

        ImageView icon = new ImageView(this);
        if (e.installed) {
            try {
                icon.setImageDrawable(getPackageManager().getApplicationIcon(e.pkg));
            } catch (Throwable t) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            // 未安装：默认安卓机器人图标（与 FFV 一致）
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        card.addView(icon, new LinearLayout.LayoutParams(
                (int) (48 * dp), (int) (48 * dp)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (12 * dp), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(e.label);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(e.installed ? INK : HINT);
        mid.addView(name);
        TextView pkg = new TextView(this);
        pkg.setText(e.pkg);
        pkg.setTextSize(11.5f);
        pkg.setTextColor(SUB);
        pkg.setPadding(0, (int) (2 * dp), 0, 0);
        mid.addView(pkg);
        if (e.installed) {
            TextView ver = new TextView(this);
            ver.setText("v" + e.version);
            ver.setTextSize(11.5f);
            ver.setTextColor(SUB);
            ver.setPadding(0, (int) (2 * dp), 0, 0);
            mid.addView(ver);
        }
        TextView desc = new TextView(this);
        desc.setText(e.desc);
        desc.setTextSize(12);
        desc.setTextColor(SUB);
        desc.setPadding(0, (int) (4 * dp), 0, 0);
        mid.addView(desc);
        card.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧勾选框：仅已安装应用（勾选=启用该应用 Hook；写入偏好供 Hook 侧 getRemotePreferences 读取）
        if (e.installed) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setChecked(isHookEnabled(e.pkg));
            cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
            cb.setOnCheckedChangeListener((v, checked) -> {
                setHookEnabled(e.pkg, checked);
                updateScopeBadge();   // 角标跟随勾选实时变化
            });
            card.addView(cb, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        // 点击卡片进入细分选项液态玻璃弹窗
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            AppDetailDialog.show(this, e.pkg, e.label, e.desc, darkMode, () -> {
                // 子项开关更新回调
                renderAppsList();
            });
        });
        return card;
    }

    /** 合并式刷新入口：无论谁调多频，实际重捕最多 ≈30fps 且不同帧堆积（快速连点卡顿的根治） */
    private void requestGlassRefresh() {
        long now = android.os.SystemClock.uptimeMillis();
        long since = now - lastGlassMs;
        if (since >= GLASS_MIN_INTERVAL_MS) {
            runGlassRefresh();
        } else if (!glassScheduled) {
            glassScheduled = true;
            barWrap.postDelayed(this::runGlassRefresh, GLASS_MIN_INTERVAL_MS - since);
        }
        // else：已有补刷排队，到点自会刷，无需重复排队
    }

    private void runGlassRefresh() {
        glassScheduled = false;
        lastGlassMs = android.os.SystemClock.uptimeMillis();
        refreshBarGlass();
    }

    /** 重捕底栏身后的内容并降采样（滚动 / 切页时调用） */
    private void refreshBarGlass() {
        if (blurBusy || barWrap.getWidth() == 0 || barWrap.getHeight() == 0) return;
        if (!GlassPrefs.liquidBlurEnabled(this)) return;   // 关闭时跳过重捕，省 CPU
        blurBusy = true;
        try {
            int bw = barWrap.getWidth() / 2, bh = barWrap.getHeight() / 2;
            if (glassChainBg == null || glassW != bw || glassH != bh) {
                buildGlassChain(bw, bh);
                // 背景链只依赖静态 backdrop 光斑 → 仅尺寸变化时重建一次，滚动/切页零开销
                glassCanvasBg[0].drawColor(0, PorterDuff.Mode.CLEAR);
                backdrop.draw(glassCanvasBg[0]);
                for (int i = 1; i < glassChainBg.length; i++) {
                    glassCanvasBg[i].drawBitmap(glassChainBg[i - 1], null,
                            glassLevels[i], glassPaint);
                }
            }
            // 内容链：1/2 高分辨率捕获（滚动量化仅 2px → 顺滑不跳）
            glassCanvasFg[0].drawColor(0, PorterDuff.Mode.CLEAR);
            glassCapturePass = true;   // 捕获期间卡片只画蒙层（细节会被磨砂掉 → 滑动帧省 CPU）
            try {
                pageContainer.draw(glassCanvasFg[0]);
            } finally {
                glassCapturePass = false;
            }
            // 真·box 模糊：5×5 均匀偏移采样（模糊量与分辨率解耦 → 有磨砂、无马赛克）
            if (fgBlurCanvas != null) {
                int w0 = glassChainFg[0].getWidth(), h0 = glassChainFg[0].getHeight();
                int s = fgBlurStep();   // 玻璃细则：模糊半径 dp → 采样间距（0 = 清晰）
                fgBlurCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                if (s <= 0) {
                    blurTapPaint.setAlpha(255);
                    tapRect.set(0, 0, w0, h0);
                    fgBlurCanvas.drawBitmap(glassChainFg[0], null, tapRect, blurTapPaint);
                } else {
                    blurTapPaint.setAlpha(255 / 25);
                    for (int oy = -2; oy <= 2; oy++) {
                        for (int ox = -2; ox <= 2; ox++) {
                            int dx = ox * s, dy = oy * s;
                            tapRect.set(-3 * s + dx, -3 * s + dy,
                                    w0 + 3 * s + dx, h0 + 3 * s + dy);
                            fgBlurCanvas.drawBitmap(glassChainFg[0], null, tapRect, blurTapPaint);
                        }
                    }
                }
            }
        } finally {
            blurBusy = false;
        }
        barWrap.invalidate();
    }

    /** 预分配降采样链：基准 1/2；bg 逐级减半做模糊，fg 仅用基准层（2x 放大即柔和模糊） */
    private void buildGlassChain(int bw, int bh) {
        glassW = bw;
        glassH = bh;
        int n = 1;
        while (bw / (1 << n) > 20 && n < 6) n++;
        glassChainBg = new Bitmap[n];
        glassChainFg = new Bitmap[n];
        glassCanvasBg = new Canvas[n];
        glassCanvasFg = new Canvas[n];
        glassLevels = new RectF[n];
        int[] loc = new int[2];
        barWrap.getLocationOnScreen(loc);
        int[] rloc = new int[2];
        barWrap.getRootView().getLocationOnScreen(rloc);
        float sx = loc[0] - rloc[0], sy = loc[1] - rloc[1];
        for (int i = 0; i < n; i++) {
            int w = Math.max(1, bw / (1 << i)), h = Math.max(1, bh / (1 << i));
            glassLevels[i] = new RectF(0, 0, w, h);
            glassChainBg[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            glassChainFg[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            glassCanvasBg[i] = new Canvas(glassChainBg[i]);
            glassCanvasFg[i] = new Canvas(glassChainFg[i]);
            if (i == 0) {
                glassCanvasBg[0].scale(0.5f, 0.5f);
                glassCanvasBg[0].translate(-sx, -sy);
                glassCanvasFg[0].scale(0.5f, 0.5f);
                glassCanvasFg[0].translate(-sx, -sy);
            }
        }
        // 内容模糊暂存位图：与 fg[0] 同尺寸（1/2 分辨率，滚动量化仅 2px → 顺滑）
        if (fgBlurBmp == null || fgBlurBmp.getWidth() != bw
                || fgBlurBmp.getHeight() != bh) {
            if (fgBlurBmp != null) fgBlurBmp.recycle();
            fgBlurBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            fgBlurCanvas = new Canvas(fgBlurBmp);
        }
    }

    /** 在胶囊内绘制身后内容的模糊版 + 白色蒙层（时机：背景之上、图标之下） */
    private void drawBarGlass(Canvas canvas) {
        if (glassChainBg == null) return;
        if (!GlassPrefs.liquidBlurEnabled(this)) return;   // 磨砂关闭 → 纯半透明底栏
        int w = barWrap.getWidth(), h = barWrap.getHeight();
        // 尺寸没变就复用缓存的裁剪 Path（动画期间 onDraw 每帧进来，避免每帧 new → GC 抑制）
        if (clipW != w || clipH != h) {
            clipW = w;
            clipH = h;
            glassRect.set(0, 0, w, h);
            glassClip.reset();
            glassClip.addRoundRect(glassRect, 32 * dp, 32 * dp, Path.Direction.CW);
        }
        canvas.save();
        canvas.clipPath(glassClip);
        canvas.drawBitmap(glassChainBg[glassChainBg.length - 1], null, glassRect, glassPaint);
        canvas.drawBitmap(fgBlurBmp != null ? fgBlurBmp : glassChainFg[0],
                null, glassRect, contentPaint);
        canvas.drawColor(BAR_BG);
        // 指尖镜面高光（模板 交互高亮：0.08 平涂 + 0.16 径向，Plus 混合 → SCREEN 近似）
        float pr = pressProgress;
        if (pr > 0.01f && tabWidthPx > 0) {
            float hx = bar.getX() + (slotSpring.value + 0.5f) * tabWidthPx;
            specularPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            specularPaint.setColor(0xFFFFFFFF);
            specularPaint.setAlpha((int) (0.08f * 255 * pr));
            canvas.drawRect(glassRect, specularPaint);
            float rad = Math.min(w, h) * 0.72f;
            specularPaint.setShader(new RadialGradient(hx, h / 2f, rad,
                    0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            specularPaint.setAlpha((int) (0.16f * 255 * pr));
            canvas.drawCircle(hx, h / 2f, rad, specularPaint);
            specularPaint.setShader(null);
            specularPaint.setXfermode(null);
        }
        canvas.restore();
    }

    /** 彩色光斑背景：模糊后即为磨砂玻璃底（非静态内联类：读外层主题色） */
    private class BackdropView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint filterPaint = new Paint(
                Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private Bitmap blurred;   // 缓存模糊结果，只在尺寸变化时重算

        private java.util.ArrayList<Bitmap> levels = new java.util.ArrayList<>();

        BackdropView(Context c) {
            super(c);
        }

        /** 多级模糊链档位数（供卡片按模糊滑杆取档） */
        public int levelCount() { return levels.size(); }

        /** 取第 idx 级模糊位图（0 = 最清晰） */
        public Bitmap level(int idx) {
            if (levels.isEmpty()) return null;
            return levels.get(Math.max(0, Math.min(idx, levels.size() - 1)));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) return;
            // ① 把图形按 1/6 尺寸画进位图
            Bitmap base = Bitmap.createBitmap(
                    Math.max(1, w / 6), Math.max(1, h / 6), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(base);
            c.scale(base.getWidth() / (float) w, base.getHeight() / (float) h);
            drawPattern(c, w, h);
            // ② 连续减半 → 近似高斯模糊；保留全级别供卡片按模糊滑杆取档
            for (Bitmap old : levels) old.recycle();
            levels = new java.util.ArrayList<>();
            levels.add(base);
            Bitmap src = base;
            while (src.getWidth() > 60) {
                Bitmap dst = Bitmap.createBitmap(
                        src.getWidth() / 2, src.getHeight() / 2, Bitmap.Config.ARGB_8888);
                Canvas cc = new Canvas(dst);
                cc.drawBitmap(src, null,
                        new Rect(0, 0, dst.getWidth(), dst.getHeight()), filterPaint);
                levels.add(dst);
                src = dst;
            }
            // 自身展示用中档；背景重捕链引用旧位图的由 GC 兜底（尺寸变化时链会重建）
            blurred = levels.get(Math.min(2, levels.size() - 1));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            canvas.drawColor(BACKDROP_BASE);
            if (blurred != null) {
                canvas.drawBitmap(blurred, null, new Rect(0, 0, w, h), filterPaint);
            }
        }

        /** 彩色光斑背景（最初观感，位图管线再柔化一遍） */
        private void drawPattern(Canvas c, int w, int h) {
            float fw = w, fh = h;
            blob(c, fw * 0.15f, fh * 0.10f, fw * 0.75f, BLOB1);
            blob(c, fw * 0.90f, fh * 0.32f, fw * 0.60f, BLOB2);
            blob(c, fw * 0.35f, fh * 0.95f, fw * 0.80f, BLOB3);
        }

        private void blob(Canvas c, float cx, float cy, float r, int color) {
            paint.setShader(new RadialGradient(cx, cy, r,
                    color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(cx - r, cy - r, cx + r, cy + r, paint);
        }

        private void circle(Canvas c, float cx, float cy, float r, int color) {
            paint.setColor(color);
            c.drawCircle(cx, cy, r, paint);
        }

        private void ring(Canvas c, float cx, float cy, float r, float sw, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            paint.setColor(color);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** "应用"页单条数据 */
    private static class AppEntry {
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
