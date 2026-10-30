package com.example.l;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.LinearLayout;

/**
 * 全局液态玻璃卡片（View 近似）—— 对标 yz8023/ui 模板 Glass.kt 的 liquidGlass：
 *
 *   1. 采样层：裁取卡片当前屏幕位置背后的模糊极光背景（View 体系无法实时
 *      采样任意图层，以「背景位图按窗口坐标裁剪」近似 backdrop 采样）；
 *   2. 染色层：surfaceContainer @ (glassTintAlpha + opacity × 0.04)，模板同款公式，
 *      卡片保持高通透（玻璃质感来自背景折射而非蒙层）；
 *   3. 折射棱光：1.5dp 白色内棱，强度随 glassRefraction（透镜折射的 View 近似）；
 *   4. 高光描边：1dp 白色镜面棱 @ glassBorderAlpha；
 *   5. 无背景可用（弹窗独立窗口 / 首帧未布局）时退化为半透明蒙层 ——
 *      模板对独立窗口弹窗同样是降级为 tint（跨窗无法采样 backdrop）。
 */
public class GlassCardView extends LinearLayout {

    /** 宿主：由 SettingsActivity 实现，提供采样位图与玻璃细则参数（全部实时读取） */
    public interface Host {
        Bitmap glassBackdropSample(int blurDp);
        int glassBackdropWidth();
        int glassBackdropHeight();
        float glassCardTintAlpha();
        float glassFallbackAlpha();
        int glassSurfaceColor();
        int glassRimColor();
        float glassRefraction();
        int glassBlurDp();
        Paint glassCardSamplePaint();
    }

    private final Host host;
    private final float density;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final int[] loc = new int[2];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassCardView(Context context, Host host) {
        super(context);
        this.host = host;
        this.density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);   // ViewGroup 默认不调 onDraw，必须打开
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float r = 24 * density;   // 模板 GlassCard 大圆角
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);

        c.save();
        c.clipPath(clipPath);

        Bitmap bmp = null;
        if (host != null && isAttachedToWindow() && isShown()) {
            bmp = host.glassBackdropSample(host.glassBlurDp());
        }
        if (bmp != null) {
            // ① 采样层：按卡片实时窗口位置裁剪模糊背景（滚动时随动）
            getLocationInWindow(loc);
            float sx = bmp.getWidth() / (float) Math.max(1, host.glassBackdropWidth());
            float sy = bmp.getHeight() / (float) Math.max(1, host.glassBackdropHeight());
            src.set(
                    Math.max(0, Math.round(loc[0] * sx)),
                    Math.max(0, Math.round(loc[1] * sy)),
                    Math.min(bmp.getWidth(), Math.round((loc[0] + w) * sx)),
                    Math.min(bmp.getHeight(), Math.round((loc[1] + h) * sy)));
            if (src.right > src.left && src.bottom > src.top) {
                dst.set(0, 0, w, h);
                c.drawBitmap(bmp, src, dst, host.glassCardSamplePaint());
            }
            // ② 染色层（模板 backdrop 路径公式）
            int tintA = Math.round(host.glassCardTintAlpha() * 255f);
            c.drawColor((tintA << 24) | (host.glassSurfaceColor() & 0xFFFFFF));
        } else {
            // ⑤ 退化：无背景可采样 → 半透明蒙层（模板独立窗口弹窗同款降级）
            int fbA = Math.round(host != null ? host.glassFallbackAlpha() * 255f : 0x99);
            int surface = host != null ? host.glassSurfaceColor() : 0xFFF0F0F4;
            c.drawColor((fbA << 24) | (surface & 0xFFFFFF));
        }
        c.restore();

        // ③ 折射棱光：1.5dp 白色内棱，强度 × glassRefraction（透镜的 View 近似）
        float refr = host != null ? host.glassRefraction() : 0f;
        if (refr > 0.01f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(Math.round(0.28f * 255f * refr));
            c.drawRoundRect(rect, r, r, paint);
        }

        // ④ 高光描边：1dp 白色镜面棱 @ glassBorderAlpha
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(host != null ? host.glassRimColor() : 0x33FFFFFF);
        c.drawRoundRect(rect, r, r, paint);
    }
}
