package com.yz8023.purestart;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * 磨砂玻璃捕获引擎（P1-2 自 SettingsActivity 拆出，行为不变）。
 *
 * 职责：底栏身后内容的限频截图降分辨率模糊（位图链 + 5×5 box 模糊）、
 * 彩色光斑背景（BackdropView）、捕获期间降级标志。属「磨砂玻璃（View 近似）」：
 * 窗口内内容位图模糊，非 Compose backdrop 语义。
 *
 * 对 Activity 暴露：requestRefresh() / settleTask / drawBarGlass(...) /
 * isCapturePass() / applyVibrancyFilter(cf) / createBackdrop(...)。
 */
final class GlassEngine {
    private final SettingsActivity a;

    GlassEngine(SettingsActivity a) {
        this.a = a;
        contentPaint.setAlpha(220);   // 内容虚影透明度（0-255），220≈86% 保内容可辨
    }

    private boolean blurBusy;                // 捕获中标志（防重入）
    private long lastGlassMs;                // 上次玻璃重捕时刻（节流用）
    private boolean glassScheduled;          // 已有延迟补刷在排队
    /** 玻璃重捕最小间隔：快速连点/滚动的请求会被合并，绝不同帧堆积 */
    private static final long GLASS_MIN_INTERVAL_MS = 33;
    /** 连点停止 350ms 后补刷一次 */
    final Runnable settleTask = this::requestRefresh;
    private final Path glassClip = new Path();       // 底栏圆角裁剪（尺寸不变时复用，免每帧 new）
    private final RectF glassRect = new RectF();
    private int clipW = -1, clipH = -1;
    final Paint glassPaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    final Paint contentPaint = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap[] glassChainBg, glassChainFg;   // 双降分辨率链（预分配，滚动零分配）
    private Canvas[] glassCanvasBg, glassCanvasFg;
    private RectF[] glassLevels;
    private int glassW = -1, glassH = -1;    // 链基准尺寸
    private Bitmap fgBlurBmp;                // 内容 box 模糊结果（与 fg[0] 同尺寸 1/2 分辨率）
    private Canvas fgBlurCanvas;
    private final Paint blurTapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF tapRect = new RectF();
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 捕获期间：磨砂卡片只画蒙层（细节会被磨砂掉 → 滑动帧省 CPU） */
    private volatile boolean glassCapturePass;

    boolean isCapturePass() { return glassCapturePass; }

    void applyVibrancyFilter(ColorMatrixColorFilter cf) {
        glassPaint.setColorFilter(cf);
        contentPaint.setColorFilter(cf);
    }

    void requestRefresh() {
        long now = android.os.SystemClock.uptimeMillis();
        long since = now - lastGlassMs;
        if (since >= GLASS_MIN_INTERVAL_MS) {
            runRefresh();
        } else if (!glassScheduled) {
            glassScheduled = true;
            a.barWrap.postDelayed(this::runRefresh, GLASS_MIN_INTERVAL_MS - since);
        }
        // else：已有补刷排队，到点自会刷，无需重复排队
    }

    private void runRefresh() {
        glassScheduled = false;
        lastGlassMs = android.os.SystemClock.uptimeMillis();
        refreshBarGlass();
    }

    /** 重捕底栏身后的内容并降采样（滚动 / 切页时调用） */
    private void refreshBarGlass() {
        if (blurBusy || a.barWrap.getWidth() == 0 || a.barWrap.getHeight() == 0) return;
        if (!Prefs.liquidBlurEnabled(a)) return;   // 关闭时跳过重捕，省 CPU
        blurBusy = true;
        try {
            int bw = a.barWrap.getWidth() / 2, bh = a.barWrap.getHeight() / 2;
            if (glassChainBg == null || glassW != bw || glassH != bh) {
                buildGlassChain(bw, bh);
                // 背景链只依赖静态 backdrop 光斑 → 仅尺寸变化时重建一次，滚动/切页零开销
                glassCanvasBg[0].drawColor(0, PorterDuff.Mode.CLEAR);
                a.backdrop.draw(glassCanvasBg[0]);
                for (int i = 1; i < glassChainBg.length; i++) {
                    glassCanvasBg[i].drawBitmap(glassChainBg[i - 1], null,
                            glassLevels[i], glassPaint);
                }
            }
            // 内容链：1/2 高分辨率捕获（滚动量化仅 2px → 顺滑不跳）
            glassCanvasFg[0].drawColor(0, PorterDuff.Mode.CLEAR);
            glassCapturePass = true;
            try {
                a.pageContainer.draw(glassCanvasFg[0]);
            } finally {
                glassCapturePass = false;
            }
            // 真·box 模糊：5×5 均匀偏移绘制（模糊量与分辨率解耦 → 有磨砂、无马赛克）
            if (fgBlurCanvas != null) {
                int w0 = glassChainFg[0].getWidth(), h0 = glassChainFg[0].getHeight();
                int s = fgBlurStep();   // 模糊强度 dp → 绘制间距（0 = 清晰）
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
        a.barWrap.invalidate();
    }

    /** 预分配降分辨率链：基准 1/2；bg 逐级减半做模糊，fg 仅用基准层（2x 放大即柔和模糊） */
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
        a.barWrap.getLocationOnScreen(loc);
        int[] rloc = new int[2];
        a.barWrap.getRootView().getLocationOnScreen(rloc);
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

    /** 模糊强度 dp → 5×5 偏移绘制间距；0 = 清晰通透（免 25 抽头） */
    private int fgBlurStep() {
        return (int) Prefs.clamp(Math.round(Prefs.blurDp(a) / 4f), 0, 10);
    }

    /** 在胶囊内绘制身后内容的模糊版 + 白色蒙层（时机：背景之上、图标之下） */
    void drawBarGlass(Canvas canvas, float pressProgress, float slotValue,
                      float tabWidthPx, float barX) {
        if (glassChainBg == null) return;
        if (!Prefs.liquidBlurEnabled(a)) return;   // 磨砂关闭 → 纯半透明底栏
        int w = a.barWrap.getWidth(), h = a.barWrap.getHeight();
        // 尺寸没变就复用缓存的裁剪 Path（动画期间 onDraw 每帧进来，避免每帧 new → GC 抑制）
        if (clipW != w || clipH != h) {
            clipW = w;
            clipH = h;
            glassRect.set(0, 0, w, h);
            glassClip.reset();
            glassClip.addRoundRect(glassRect, 32 * a.dp, 32 * a.dp, Path.Direction.CW);
        }
        canvas.save();
        canvas.clipPath(glassClip);
        canvas.drawBitmap(glassChainBg[glassChainBg.length - 1], null, glassRect, glassPaint);
        canvas.drawBitmap(fgBlurBmp != null ? fgBlurBmp : glassChainFg[0],
                null, glassRect, contentPaint);
        canvas.drawColor(a.BAR_BG);
        // 指尖镜面高光（模板 交互高亮：0.08 平涂 + 0.16 径向，Plus 混合 → SCREEN 近似）
        float pr = pressProgress;
        if (pr > 0.01f && tabWidthPx > 0) {
            float hx = barX + (slotValue + 0.5f) * tabWidthPx;
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

    /** 彩色光斑背景：模糊后即为磨砂玻璃底（读 Activity 主题色） */
    BackdropView createBackdrop(Context c) {
        return new BackdropView(c);
    }

    class BackdropView extends View {
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
            canvas.drawColor(a.BACKDROP_BASE);
            if (blurred != null) {
                canvas.drawBitmap(blurred, null, new Rect(0, 0, w, h), filterPaint);
            }
        }

        /** 彩色光斑背景（最初观感，位图管线再柔化一遍） */
        private void drawPattern(Canvas c, int w, int h) {
            float fw = w, fh = h;
            blob(c, fw * 0.15f, fh * 0.10f, fw * 0.75f, a.BLOB1);
            blob(c, fw * 0.90f, fh * 0.32f, fw * 0.60f, a.BLOB2);
            blob(c, fw * 0.35f, fh * 0.95f, fw * 0.80f, a.BLOB3);
        }

        private void blob(Canvas c, float cx, float cy, float r, int color) {
            paint.setShader(new RadialGradient(cx, cy, r,
                    color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(cx - r, cy - r, cx + r, cy + r, paint);
        }
    }
}
