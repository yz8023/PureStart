package com.example.l;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 磨砂玻璃底栏（View 近似；P1-2 自 SettingsActivity 拆出，行为不变）。
 *
 * 版式取值借鉴模板 LiquidBottomTabs（64dp 胶囊容器 + 4dp 内衬 + 56dp 活动胶囊），
 * 渲染为 View 近似（tint/高光棱/内阴影/折射近似，非模板 backdrop 语义）；
 * 阻尼拖拽手势（按压/槽位空间拖拽/松手吸附）由五组弹簧驱动，Choreographer 逐帧渲染。
 * 页面切换动效（animatePageSwitch）与玻璃捕获锚（barWrap 字段）留 activity。
 */
final class LiquidBar {
    private final SettingsActivity a;

    LiquidBar(SettingsActivity a) {
        this.a = a;
    }

    private LinearLayout bar;
    private LinearLayout[] tabItems;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    TextView scopeBadge;   // "应用"Tab 作用域数量角标（activity.updateScopeBadge 读取）

    /** activity.applyGlassLive 预览胶水：胶囊 tint/高光棱随参数重绘 */
    void invalidatePill() {
        if (pill != null) pill.invalidate();
    }

    private static final String[] TAB_NAMES = {"首页", "应用", "拓展", "设置"};
    private static final int[] TAB_ICON_IDLE = {
            R.drawable.ic_tab_home, R.drawable.ic_tab_apps,
            R.drawable.ic_tab_extension, R.drawable.ic_tab_settings};
    private static final int[] TAB_ICON_ACTIVE = {
            R.drawable.ic_tab_home_fill, R.drawable.ic_tab_apps,
            R.drawable.ic_tab_extension_fill, R.drawable.ic_tab_settings_fill};

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
            if (a.isFinishing()) { choreoRunning = false; return; }
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
    int selectedTab = 0;

    /** 组装底栏（onCreate 调用；barWrap 同时回填 activity 供玻璃引擎锚定） */
    void build(android.widget.FrameLayout root) {
        a.barWrap = new FrameLayout(a) {
            @Override
            protected void onDraw(Canvas canvas) {
                a.engine.drawBarGlass(canvas, pressProgress, slotSpring.value,
                        tabWidthPx, bar.getX());   // 实时磨砂画在背景之上、图标之下
            }
        };
        a.barWrap.setWillNotDraw(false);   // ViewGroup 默认不调 onDraw，必须打开
        GradientDrawable barBg = new GradientDrawable();
        a.barBgDrawable = barBg;   // 玻璃细则实时预览：留底便于即时改透明度
        // 模板容器公式：surfaceContainer.copy(alpha = 0.10f + opacity * 0.55f)
        barBg.setColor(a.BAR_BG);
        barBg.setCornerRadius(32 * a.dp);   // 64dp 全胶囊半径
        // 模板 Highlight.Default.copy(alpha = 0.75f)：1dp 白色镜面高光棱
        barBg.setStroke((int) (1f * a.dp), 0xBFFFFFFF);
        a.barWrap.setBackground(barBg);
        // 模板 Shadow(radius = 10dp, Black, alpha 0.1 浅色 / 0.2 深色)
        a.barWrap.setElevation(10 * a.dp);
        a.barWrap.setClipChildren(false);   // 关闭裁剪：胶囊按压放大 / 过冲时能跳出容器
        a.barWrap.setClipToPadding(false);
        int barPad = (int) (4 * a.dp);      // 模板 padding(4dp)
        a.barWrap.setPadding(barPad, barPad, barPad, barPad);

        // ==== 底栏手势（模板 阻尼拖拽动画：按压 / 槽位空间拖拽 / 松手吸附）====
        a.barWrap.setOnTouchListener(new View.OnTouchListener() {
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
                            float slot = SettingsActivity.clamp((ev.getX() - bar.getX()) / tabWidthPx - 0.5f,
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
                            if (Math.abs(ev.getX() - dockDownX) > 2 * a.dp) dockDragging = true;
                            // 槽位空间浮点驱动：dx → dx / tabWidth（模板 F08 规则，禁止像素坐标反推）
                            // 任意起点、全程跟手（点击即定位 + 拖动即跟随）
                            slotSpring.value = SettingsActivity.clamp(slotSpring.value + dx / tabWidthPx,
                                    0f, tabItems.length - 1f);
                            slotSpring.target = slotSpring.value;
                            slotSpring.vel = 0f;
                            // 面板微位移累积（模板 offsetAnimation.snapTo）
                            offsetSpring.value = SettingsActivity.clamp(offsetSpring.value + dx,
                                    -totalWidthPx, totalWidthPx);
                            // 速度反馈（模板 VelocityTracker → velSpring）
                            velSpring.value = SettingsActivity.clamp(dockVel / 3000f, -1.5f, 1.5f);
                            kickSprings();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dockPressed && tabWidthPx > 0) {
                            // 松手吸附最近槽位（点击定位与拖动跟随统一走这里）
                            int target = (int) SettingsActivity.clamp(Math.round(slotSpring.value), 0, tabItems.length - 1);
                            if (target != selectedTab) selectTab(target);
                            else slotSpring.target = selectedTab;
                            velSpring.value = SettingsActivity.clamp(dockVel / 3000f, -1.5f, 1.5f);
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
        pill = new View(a) {
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
                capPaint.setColor(a.darkMode ? 0xFFFFFFFF : 0xFF000000);
                capPaint.setAlpha((int) (0.10f * 255 * (1f - p)));
                c.drawRoundRect(capRect, r, r, capPaint);
                // ② 按压加深 Black 3%×p（模板 drawRect(Black.copy(0.03f * progress))）
                if (p > 0.01f) {
                    capPaint.setColor(0xFF000000);
                    capPaint.setAlpha((int) (0.03f * 255 * p));
                    c.drawRoundRect(capRect, r, r, capPaint);
                }
                // ③ 折射近似（View 无透镜）：折射高度滑杆驱动边缘亮棱厚度（15dp=标准）
                float prism = a.glassPrismScale();
                if (prism > 0.01f) {
                    capPaint.setStyle(Paint.Style.STROKE);
                    capPaint.setStrokeWidth(Math.min(3f, 1.5f * prism) * a.dp);
                    capPaint.setColor(0xFFFFFFFF);
                    capPaint.setAlpha((int) (0.28f * 255 * Math.min(1f, prism)));
                    c.drawRoundRect(capRect, r, r, capPaint);
                }
                // ④ 高光描边 1dp，alpha = 0.75×p（模板 Highlight.Default.copy(alpha = pressProgress)）
                if (p > 0.01f) {
                    capPaint.setStyle(Paint.Style.STROKE);
                    capPaint.setStrokeWidth(1f * a.dp);
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
                    capPaint.setShader(new LinearGradient(0, 0, 0, Math.max(1f, 8 * a.dp * p),
                            0x4D000000, 0x00000000, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, 8 * a.dp * p, capPaint);
                    capPaint.setShader(null);
                    c.restore();
                }
            }
        };
        pill.setAlpha(0f);
        a.barWrap.addView(pill, new FrameLayout.LayoutParams(
                (int) (56 * a.dp), FrameLayout.LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        a.barWrap.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        tabItems = new LinearLayout[4];
        tabIcons = new ImageView[4];
        tabLabels = new TextView[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout item = new LinearLayout(a);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumWidth((int) (76 * a.dp));   // 模板 defaultMinSize(minWidth = 76dp)

            // 图标包一层 FrameLayout：四个 Tab 统一占位 26dp，应用 Tab 可叠角标
            ImageView icon = new ImageView(a);
            icon.setImageResource(i == 0 ? TAB_ICON_ACTIVE[i] : TAB_ICON_IDLE[i]);
            icon.setColorFilter(i == 0 ? a.ACCENT : a.IDLE_TINT);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                    (int) (24 * a.dp), (int) (24 * a.dp), Gravity.CENTER);
            FrameLayout iconWrap = new FrameLayout(a);
            iconWrap.addView(icon, iconLp);
            if (i == 1) {
                // 作用域角标：初始隐藏，后台读 scope.list 后有作用域应用才显示数量
                TextView badge = new TextView(a);
                badge.setVisibility(View.GONE);
                badge.setTextSize(9);
                badge.setTextColor(0xFFFFFFFF);
                badge.setGravity(Gravity.CENTER);
                badge.setIncludeFontPadding(false);
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(a.BADGE_RED);
                badgeBg.setCornerRadius(7 * a.dp);
                badge.setBackground(badgeBg);
                iconWrap.addView(badge, new FrameLayout.LayoutParams(
                        (int) (14 * a.dp), (int) (14 * a.dp),
                        Gravity.TOP | Gravity.END));
                scopeBadge = badge;
            }
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                    (int) (26 * a.dp), (int) (26 * a.dp));
            wrapLp.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(iconWrap, wrapLp);

            TextView label = new TextView(a);
            label.setText(TAB_NAMES[i]);
            label.setTextSize(11f);
            label.setTextColor(i == 0 ? a.ACCENT : a.IDLE_TINT);
            label.setGravity(Gravity.CENTER_HORIZONTAL);   // 修复：文字水平居中
            label.setPadding(0, (int) (1 * a.dp), 0, 0);   // 模板 spacedBy(1dp)
            item.addView(label);   // 垂直 LinearLayout 默认 MATCH_PARENT 宽，配合 gravity 居中

            // 模板 LiquidBottomTab：无涟漪无按压缩小，按压反馈全部由活动胶囊承担，
            // 点击 / 拖拽统一由 a.barWrap 手势处理（槽位空间）
            tabItems[i] = item;
            tabIcons[i] = icon;
            tabLabels[i] = label;
            bar.addView(item, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }

        // 作用域角标初始值：由“应用”页加载数据后统一刷新（见 updateScopeBadge）
        a.updateScopeBadge();

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int) (64 * a.dp));   // 模板 height(64dp)
        barLp.gravity = Gravity.BOTTOM;
        barLp.leftMargin = (int) (14 * a.dp);
        barLp.rightMargin = (int) (14 * a.dp);
        barLp.bottomMargin = (int) (14 * a.dp);   // 模板 padding(bottom = 14dp)
        root.addView(a.barWrap, barLp);
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
        a.barWrap.removeCallbacks(a.engine.settleTask);
        a.barWrap.postDelayed(a.engine.settleTask, 350);
        if (idx == 1) a.appsPage.loadAppsAsync();   // 每次切到"应用"页都刷新安装状态

        // ① 活动胶囊：槽位空间浮点弹簧驱动（模板 valueAnimation，弹跳全部来自弹簧本身）
        slotSpring.target = idx;
        kickSprings();

        // ② 图标换装 + 颜色渐变
        tabIcons[prev].setImageResource(TAB_ICON_IDLE[prev]);
        tabIcons[idx].setImageResource(TAB_ICON_ACTIVE[idx]);
        animateTint(tabIcons[prev], a.ACCENT, a.IDLE_TINT);
        animateTint(tabIcons[idx], a.IDLE_TINT, a.ACCENT);
        tabLabels[prev].setTextColor(a.IDLE_TINT);
        tabLabels[idx].setTextColor(a.ACCENT);

        // ③ 页面切换动效：方向性滑动 + 缩放 + 淡入淡出（对标模板动效 Standard 320ms）
        a.animatePageSwitch(a.pageContainer.getChildAt(prev),
                a.pageContainer.getChildAt(idx), idx > prev);
    }

    /** 每帧把弹簧状态落到视图（底栏唯一渲染出口，模板 graphicsLayer 的 View 版） */
    private void renderDock() {
        if (pill == null || tabWidthPx <= 0) return;
        pressProgress = pressSpring.value;
        // 面板微位移：4dp × sign × EaseOut(|offset/total|)（模板 panelOffset）
        float frac = totalWidthPx > 0 ? SettingsActivity.clamp(offsetSpring.value / totalWidthPx, -1f, 1f) : 0f;
        float shift = 4 * a.dp * Math.signum(frac) * easeOut(Math.abs(frac));
        pill.setX(bar.getX() + slotSpring.value * tabWidthPx + shift);
        // 保体积速度拉伸（模板 layerBlock：scaleX ÷、scaleY ×）
        float v1 = SettingsActivity.clamp(velSpring.value * 0.75f, -0.2f, 0.2f);
        float v2 = SettingsActivity.clamp(velSpring.value * 0.25f, -0.2f, 0.2f);
        pill.setScaleX(scaleXSpring.value / (1f - v1));
        pill.setScaleY(scaleYSpring.value * (1f - v2));
        // 容器按压放大：1 + 16dp / width × pressProgress（模板 layerBlock）
        float cs = 1f + (16 * a.dp / Math.max(1f, a.barWrap.getWidth())) * pressProgress;
        a.barWrap.setScaleX(cs);
        a.barWrap.setScaleY(cs);
        // 活动标签内容 1.2 倍按压缩放（模板 LocalLiquidBottomTabScale）
        int act = (int) SettingsActivity.clamp(Math.round(slotSpring.value), 0, tabItems.length - 1);
        for (int i = 0; i < tabItems.length; i++) {
            float s = i == act ? 1f + 0.2f * pressProgress : 1f;
            if (tabIcons[i] != null) { tabIcons[i].setScaleX(s); tabIcons[i].setScaleY(s); }
            if (tabLabels[i] != null) { tabLabels[i].setScaleX(s); tabLabels[i].setScaleY(s); }
        }
        pill.invalidate();      // tint / 高光棱 / 内阴影随 pressProgress 重绘
        a.barWrap.invalidate();   // 指尖镜面高光重绘
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }


    /** 玻璃细则：动效阻尼 → 全部弹簧 DampedRatio 缩放（模板 运动.kt 公式） */
    void applyMotionTuning() {
        float d = Prefs.damping(a);
        slotSpring.dampingRatio = dampedRatio(1f, d);
        pressSpring.dampingRatio = dampedRatio(1f, d);
        velSpring.dampingRatio = dampedRatio(0.5f, d);
        scaleXSpring.dampingRatio = dampedRatio(0.6f, d);
        scaleYSpring.dampingRatio = dampedRatio(0.7f, d);
        offsetSpring.dampingRatio = dampedRatio(1f, d);
    }

    private static float dampedRatio(float base, float damping) {
        return SettingsActivity.clamp(base * (0.72f + 0.56f * damping), 0.25f, 1.25f);
    }


    private void animateTint(ImageView iv, int from, int to) {
        ValueAnimator va = ValueAnimator.ofArgb(from, to);
        va.setDuration(220);
        va.addUpdateListener(a -> iv.setColorFilter((Integer) a.getAnimatedValue()));
        va.start();
    }
}
