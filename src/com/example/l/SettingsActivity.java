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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
public class SettingsActivity extends Activity {

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
        // 与 Main.TARGETS / xposed/scope.list 三处同步
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
    private View pill;                       // M3 滑动指示条
    private GradientDrawable pillBg;         // 指示条背景（圆角需动态调整）
    private ValueAnimator pillAnimX, pillAnimY;   // 膨胀动画引用（连点时先取消）
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
        if (darkMode) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            PAGE_BG = 0xFF14161D;
            ACCENT = 0xFF818CF8;
            INK = 0xFFE8EAF2;
            SUB = 0xFFA3A9BC;
            IDLE_TINT = 0xFFD0D4DE;
            BAR_BG = 0x66202430;
            RIPPLE = 0x2E818CF8;
            STATUS_GREEN = 0xFF4ADE80;
            BADGE_RED = 0xFFE5484D;
            CARD_BG = 0xFF1E212B;
            FIELD_BG = 0xFF262A36;
            PILL_GRAY = 0xFF565B66;
            HINT = 0xFF7A8090;
            BACKDROP_BASE = 0xFF12141C;
            BLOB1 = 0xFF4C51BF;
            BLOB2 = 0xFF7E3FB8;
            BLOB3 = 0xFF158F83;
            PILL_TAB = 0x2E8B92A8;
        } else {
            setTheme(R.style.AppTheme);
            PAGE_BG = 0xFFF6F7FC;
            ACCENT = 0xFF312E81;
            INK = 0xFF1F2430;
            SUB = 0xFF2E333A;
            IDLE_TINT = 0xFF15181C;
            BAR_BG = 0x66FFFFFF;
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();   // 最先解析主题（跟随系统/浅色/深色）
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        contentPaint.setAlpha(220);   // 内容虚影透明度（0-255），220≈86% 保内容可辨

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
        pageContainer.addView(makePage("拓展", "功能插件",
                "此页预留：自定义 Hook 配置、配置备份 / 恢复等扩展功能。"));
        pageContainer.addView(makeSettingsPage());
        for (int i = 1; i < pageContainer.getChildCount(); i++) {
            pageContainer.getChildAt(i).setVisibility(View.GONE);
        }
        root.addView(pageContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));



        // ---- 悬浮磨砂底栏（外层 FrameLayout 承载背景与指示条）----
        barWrap = new FrameLayout(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawBarGlass(canvas);   // 实时磨砂画在背景之上、图标之下
            }
        };
        barWrap.setWillNotDraw(false);   // ViewGroup 默认不调 onDraw，必须打开
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(BAR_BG);
        barBg.setCornerRadius(26 * dp);
        barBg.setStroke((int) dp, 0x33FFFFFF);
        barWrap.setBackground(barBg);
        barWrap.setElevation(9 * dp);
        barWrap.setClipChildren(false);   // 关闭裁剪：指示条过冲时能跳出底栏边框
        barWrap.setClipToPadding(false);
        int barPad = (int) (6 * dp);
        barWrap.setPadding(barPad, barPad, barPad, barPad);

        // 滑动指示条（全高分段胶囊，透明浅灰）
        pill = new View(this);
        pillBg = new GradientDrawable();
        pillBg.setColor(PILL_TAB);
        pill.setBackground(pillBg);   // 尺寸与圆角在布局完成后按 Tab 实际大小计算
        pill.setAlpha(0f);
        barWrap.addView(pill, new FrameLayout.LayoutParams(
                (int) (56 * dp), (int) (32 * dp)));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        barWrap.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        tabItems = new LinearLayout[4];
        tabIcons = new ImageView[4];
        tabLabels = new TextView[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(20 * dp);
            item.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, mask));
            int vpad = (int) (7 * dp);
            item.setPadding(0, vpad, 0, vpad);

            // 图标包一层 FrameLayout：四个 Tab 统一占位 26dp，应用 Tab 可叠角标
            ImageView icon = new ImageView(this);
            icon.setImageResource(i == 0 ? TAB_ICON_ACTIVE[i] : TAB_ICON_IDLE[i]);
            icon.setColorFilter(i == 0 ? ACCENT : IDLE_TINT);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                    (int) (23 * dp), (int) (23 * dp), Gravity.CENTER);
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
            label.setTextSize(10.5f);
            label.setTextColor(i == 0 ? ACCENT : IDLE_TINT);
            label.setGravity(Gravity.CENTER_HORIZONTAL);   // 修复：文字水平居中
            label.setPadding(0, (int) (3 * dp), 0, 0);
            item.addView(label);   // 垂直 LinearLayout 默认 MATCH_PARENT 宽，配合 gravity 居中

            final int idx = i;
            item.setOnClickListener(v -> {
                nudgeBar();          // 大胶囊跟着轻弹一下
                selectTab(idx);
            });
            // 按下缩小 / 松手 Overshoot 回弹
            item.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.82f).scaleY(0.82f).setDuration(80)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                                .setInterpolator(new OvershootInterpolator(3f)).start();
                        break;
                }
                return false; // 不消费事件，点击照常触发
            });

            tabItems[i] = item;
            tabIcons[i] = icon;
            tabLabels[i] = label;
            bar.addView(item, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        // 作用域角标初始值：由“应用”页加载数据后统一刷新（见 updateScopeBadge）
        updateScopeBadge();

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        barLp.leftMargin = (int) (14 * dp);
        barLp.rightMargin = (int) (14 * dp);
        barLp.bottomMargin = (int) (10 * dp);
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

        // 布局完成后：指示条 = 底栏内容区全高的分段胶囊，弹入
        pill.post(() -> {
            LinearLayout item = tabItems[selectedTab];
            float inset = 3 * dp;    // 胶囊左右留边
            float h = bar.getHeight();   // 全高：与底栏内容区等高
            FrameLayout.LayoutParams plp =
                    (FrameLayout.LayoutParams) pill.getLayoutParams();
            plp.width = Math.round(item.getWidth() - inset * 2);
            plp.height = Math.round(h);
            pill.setLayoutParams(plp);
            pillBg.setCornerRadius(h / 2f);
            pill.setX(bar.getX() + item.getX() + inset);
            pill.setY(bar.getY());
            pill.setScaleX(0.6f);
            pill.setScaleY(0.6f);
            pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new OvershootInterpolator(1.6f)).start();
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

        // ① 指示条平滑滑过去，弹跳全部来自中心向外的膨胀（不再左右甩）
        pill.animate()
                .x(bar.getX() + tabItems[idx].getX() + 3 * dp)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
        // 快速连点：先取消上一个缩放动画，并从当前缩放值继续，避免跳变
        if (pillAnimX != null) pillAnimX.cancel();
        if (pillAnimY != null) pillAnimY.cancel();
        pillAnimX = ValueAnimator.ofFloat(pill.getScaleX(), 1.15f, 1f);
        pillAnimY = ValueAnimator.ofFloat(pill.getScaleY(), 1.35f, 1f);
        pillAnimX.setDuration(380);
        pillAnimY.setDuration(380);
        pillAnimX.addUpdateListener(a -> pill.setScaleX((Float) a.getAnimatedValue()));
        pillAnimY.addUpdateListener(a -> pill.setScaleY((Float) a.getAnimatedValue()));
        pillAnimX.start();
        pillAnimY.start();

        // ② 图标换装 + 颜色渐变
        tabIcons[prev].setImageResource(TAB_ICON_IDLE[prev]);
        tabIcons[idx].setImageResource(TAB_ICON_ACTIVE[idx]);
        animateTint(tabIcons[prev], ACCENT, IDLE_TINT);
        animateTint(tabIcons[idx], IDLE_TINT, ACCENT);
        tabLabels[prev].setTextColor(IDLE_TINT);
        tabLabels[idx].setTextColor(ACCENT);

        // ③ 选中图标弹一下（加大）
        final ImageView pop = tabIcons[idx];
        pop.animate().scaleX(1.4f).scaleY(1.4f).setDuration(130)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> pop.animate().scaleX(1f).scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(new OvershootInterpolator(4f)).start())
                .start();

        // ④ 页面淡入淡出
        View outPage = pageContainer.getChildAt(prev);
        View inPage = pageContainer.getChildAt(idx);
        outPage.animate().alpha(0f).setDuration(140)
                .withEndAction(() -> outPage.setVisibility(View.GONE)).start();
        inPage.setVisibility(View.VISIBLE);
        inPage.setAlpha(0f);
        inPage.animate().alpha(1f).setDuration(200).setStartDelay(60)
                .start();
    }

    /** 整个底栏（大胶囊）点击时轻弹一下 */
    private void nudgeBar() {
        barWrap.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> barWrap.animate().scaleX(1f).scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(new OvershootInterpolator(2.5f)).start())
                .start();
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
        box.setPadding(pad, (int) (10 * dp), pad, (int) (100 * dp));
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

        ImageView modIcon = new ImageView(this);
        // ⚠R.mipmap.ic_launcher 前景是 2048×2048 JPG，直接 setImageResource 会同步解码
        //   16MB 位图（启动变卡 + GC 压力全局抖动）→ 按目标 40dp(256px 上限) 降采样解码
        android.graphics.BitmapFactory.Options bo = new android.graphics.BitmapFactory.Options();
        bo.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(
                getResources(), R.drawable.ic_launcher_foreground, bo);
        bo.inJustDecodeBounds = false;
        bo.inSampleSize = 1;
        while (bo.outWidth / (bo.inSampleSize * 2) >= 256) bo.inSampleSize *= 2;
        modIcon.setImageBitmap(android.graphics.BitmapFactory.decodeResource(
                getResources(), R.drawable.ic_launcher_foreground, bo));
        statusCard.addView(modIcon, new LinearLayout.LayoutParams(
                (int) (40 * dp), (int) (40 * dp)));

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
        // 已安装软件卡：getInstalledPackages 枚举全部包很慢（可上百 ms），
        // 绝不能在 onCreate 主线程同步调 → 先占位，后台线程统计后回填
        LinearLayout appsCard = makeCard();
        appsCard.setPadding((int) (20 * dp), (int) (16 * dp),
                (int) (20 * dp), (int) (16 * dp));
        TextView appsLabel = new TextView(this);
        appsLabel.setText("已安装软件");
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
                {"框架 & API", "LSPosed API 100+（libxposed）"},
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

    /** 半透明白圆角卡片 */
    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(20 * dp);
        card.setBackground(bg);
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

    private ImageView roundButton(int iconRes) {
        ImageView b = new ImageView(this);
        b.setImageResource(iconRes);
        b.setColorFilter(INK);
        b.setPadding((int) (11 * dp), (int) (11 * dp), (int) (11 * dp), (int) (11 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FIELD_BG);
        bg.setCornerRadius(22 * dp);
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
        box.setPadding(pad, (int) (10 * dp), pad, (int) (100 * dp));
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
        addCard(box, setRow(R.drawable.ic_set_drop, "液态模糊", "为模块界面增加液态玻璃效果", "set_liquid_blur"));
        addCard(box, setRow(R.drawable.ic_set_lang, "语言", "跟随系统", null));
        addCard(box, setRow(R.drawable.ic_set_bell, "底栏应用角标", "在底栏“应用”按钮上显示数量角标", "set_app_badge"));

        // ===== 分组：桌面图标 =====
        box.addView(sectionHeader("桌面图标"));
        addCard(box, setRow(R.drawable.ic_set_home, "在桌面显示图标",
                "请务必在 LSPosed 中关闭\"强制显示桌面图标\"功能", "set_desktop_icon"));

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

    /** 外观行：点击弹出三选一（跟随系统/浅色/深色），副标题显示当前选择 */
    private LinearLayout appearanceRow() {
        final String[] names = {"跟随系统", "浅色模式", "深色模式"};
        int cur = hookPrefs().getInt("theme_mode", 0);
        String sub = names[cur];
        if (cur == 0) sub += darkMode ? "（当前深色）" : "（当前浅色）";
        LinearLayout row = setRow(R.drawable.ic_set_palette, "外观", sub, null);
        row.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("外观")
                .setSingleChoiceItems(names, hookPrefs().getInt("theme_mode", 0),
                        (d, which) -> {
                            d.dismiss();
                            if (which != hookPrefs().getInt("theme_mode", -1)) {
                                hookPrefs().edit().putInt("theme_mode", which).apply();
                                recreate();   // 重建以应用新主题
                            }
                        })
                .show());
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

    /** 已安装应用数量（Manifest 已加 QUERY_ALL_PACKAGES，API 30+ 才能数到全部） */
    private int installedAppCount() {
        try {
            return getPackageManager().getInstalledPackages(0).size();
        } catch (Throwable t) {
            return 0;
        }
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
    private View makePage(String title, String subtitle, String body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * dp);
        box.setPadding(pad, (int) (26 * dp), pad, (int) (100 * dp));
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
        box.setPadding(pad, (int) (10 * dp), pad, (int) (100 * dp));
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
        if (!hookPrefs().getBoolean("set_liquid_blur", true)) return;   // 关闭时跳过重捕，省 CPU
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
            pageContainer.draw(glassCanvasFg[0]);
            // 真·box 模糊：5×5 均匀偏移采样（模糊量与分辨率解耦 → 有磨砂、无马赛克）
            if (fgBlurCanvas != null) {
                int w0 = glassChainFg[0].getWidth(), h0 = glassChainFg[0].getHeight();
                int s = FG_BLUR_STEP;
                fgBlurCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
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
        if (!hookPrefs().getBoolean("set_liquid_blur", true)) return;   // 液态模糊关闭 → 纯半透明底栏
        int w = barWrap.getWidth(), h = barWrap.getHeight();
        // 尺寸没变就复用缓存的裁剪 Path（动画期间 onDraw 每帧进来，避免每帧 new → GC 抑制）
        if (clipW != w || clipH != h) {
            clipW = w;
            clipH = h;
            glassRect.set(0, 0, w, h);
            glassClip.reset();
            glassClip.addRoundRect(glassRect, 26 * dp, 26 * dp, Path.Direction.CW);
        }
        canvas.save();
        canvas.clipPath(glassClip);
        canvas.drawBitmap(glassChainBg[glassChainBg.length - 1], null, glassRect, glassPaint);
        canvas.drawBitmap(fgBlurBmp != null ? fgBlurBmp : glassChainFg[0],
                null, glassRect, contentPaint);
        canvas.drawColor(BAR_BG);
        canvas.restore();
    }

    /** 彩色光斑背景：模糊后即为磨砂玻璃底（非静态内联类：读外层主题色） */
    private class BackdropView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint filterPaint = new Paint(
                Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private Bitmap blurred;   // 缓存模糊结果，只在尺寸变化时重算

        BackdropView(Context c) {
            super(c);
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
            // ② 连续减半 → 近似高斯模糊
            Bitmap src = base;
            while (src.getWidth() > 60) {
                Bitmap dst = Bitmap.createBitmap(
                        src.getWidth() / 2, src.getHeight() / 2, Bitmap.Config.ARGB_8888);
                Canvas cc = new Canvas(dst);
                cc.drawBitmap(src, null,
                        new Rect(0, 0, dst.getWidth(), dst.getHeight()), filterPaint);
                if (src != base) src.recycle();
                src = dst;
            }
            if (blurred != null && blurred != src) blurred.recycle();
            blurred = src;
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
