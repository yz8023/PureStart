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
 * 全局磨砂玻璃卡片（View 近似）。命名遵循 yz8023/ui prompts/03 路线 B：
 * 无 backdrop 实时采样（背景位图裁剪近似）、无 lens 折射（棱光/放大近似）。
 *
 * 参数与模板设置页（SettingsScreen.kt「动效」区）一一对应：
 *  - 模糊强度 glassBlurRadiusDp      → 采样背景的磨砂档位
 *  - 折射高度 glassRefractionHeightDp → 边缘棱光厚度（透镜边缘高度的 View 近似）
 *  - 折射幅度 glassRefractionAmountDp → 采样层放大（透镜内壁纸内容偏移的 View 近似）
 *  - 玻璃染色 glassTintAlpha          → 染色层（Glass.kt: tintAlpha + opacity×0.04）
 *  - 高光边框 glassBorderAlpha        → 1dp 镜面棱
 *  - 色差折射 glassChromaticAberration→ 棱光 RGB 色散（View 近似）
 *
 * 逐层：①按卡片实时窗口位置裁剪模糊背景（backdrop 采样近似）
 *      ②染色层 ③折射棱光(+色散) ④高光描边 ⑤无背景时退化蒙层。
 * 底栏软捕获期间（isGlassCapturePass）只画蒙层 —— 细节反正会被底栏磨砂掉，
 * 跳过采样与描边可把滑动帧开销压回实色卡片水平（滑动卡顿修复）。
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
        float glassPrismScale();
        int glassRefractionAmountDp();
        boolean glassChromaticEnabled();
        int glassBlurDp();
        Paint glassCardSamplePaint();
        boolean isGlassCapturePass();
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

        // 底栏软捕获期间：只画退化蒙层（细节会被底栏磨砂掉，跳过采样/描边省 CPU）
        if (host != null && host.isGlassCapturePass()) {
            int fbA = Math.round(host.glassFallbackAlpha() * 255f);
            c.drawColor((fbA << 24) | (host.glassSurfaceColor() & 0xFFFFFF));
            c.restore();
            return;
        }

        Bitmap bmp = null;
        if (host != null && isAttachedToWindow() && isShown()) {
            bmp = host.glassBackdropSample(host.glassBlurDp());
        }
        if (bmp != null) {
            // ① 采样层：按卡片实时窗口位置裁剪模糊背景（滚动随动）
            getLocationInWindow(loc);
            float sx = bmp.getWidth() / (float) Math.max(1, host.glassBackdropWidth());
            float sy = bmp.getHeight() / (float) Math.max(1, host.glassBackdropHeight());
            src.set(
                    Math.max(0, Math.round(loc[0] * sx)),
                    Math.max(0, Math.round(loc[1] * sy)),
                    Math.min(bmp.getWidth(), Math.round((loc[0] + w) * sx)),
                    Math.min(bmp.getHeight(), Math.round((loc[1] + h) * sy)));
            if (src.right > src.left && src.bottom > src.top) {
                // 折射幅度 → 采样层轻微放大（透镜内壁纸内容偏移的 View 近似，20dp ≈ 1.05×）
                float mag = 1f + host.glassRefractionAmountDp() / 400f;
                float gw = w * mag, gh = h * mag;
                dst.set((w - gw) / 2f, (h - gh) / 2f, (w + gw) / 2f, (h + gh) / 2f);
                c.drawBitmap(bmp, src, dst, host.glassCardSamplePaint());
            }
            // ② 染色层（模板 backdrop 路径公式：glassTintAlpha + opacity × 0.04）
            int tintA = Math.round(host.glassCardTintAlpha() * 255f);
            c.drawColor((tintA << 24) | (host.glassSurfaceColor() & 0xFFFFFF));
        } else {
            // ⑤ 退化：无背景可采样 → 半透明蒙层（模板独立窗口弹窗同款降级）
            int fbA = Math.round(host != null ? host.glassFallbackAlpha() * 255f : 0x99);
            int surface = host != null ? host.glassSurfaceColor() : 0xFFF0F0F4;
            c.drawColor((fbA << 24) | (surface & 0xFFFFFF));
        }
        c.restore();

        // ③ 折射棱光：厚度随折射高度（15dp=1.5dp 标准棱，40dp=3dp 厚棱）
        float prism = host != null ? host.glassPrismScale() : 0f;
        if (prism > 0.01f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.min(3f, 1.5f * prism) * density);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(Math.round(0.28f * 255f * Math.min(1f, prism)));
            c.drawRoundRect(rect, r, r, paint);

            // 色差折射：棱光上叠加 RGB 色散描边（模板 chromaticAberration 的 View 近似）
            if (host.glassChromaticEnabled()) {
                int ca = Math.round(0.10f * 255f * Math.min(1f, prism));
                float inset = 0.5f * density;
                paint.setStrokeWidth(1f * density);
                paint.setAlpha(ca);
                paint.setColor(0xFFEF4444);   // 红
                c.drawRoundRect(shrink(rect, 0f), r, r, paint);
                paint.setColor(0xFF22C55E);   // 绿
                c.drawRoundRect(shrink(rect, inset), r, r, paint);
                paint.setColor(0xFF3B82F6);   // 蓝
                c.drawRoundRect(shrink(rect, 2 * inset), r, r, paint);
            }
        }

        // ④ 高光描边：1dp 白色镜面棱 @ glassBorderAlpha
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(host != null ? host.glassRimColor() : 0x33FFFFFF);
        c.drawRoundRect(rect, r, r, paint);
    }

    private static RectF shrink(RectF r, float inset) {
        tmp.set(r);
        if (inset > 0f) {
            tmp.inset(inset, inset);
            if (tmp.width() <= 0 || tmp.height() <= 0) tmp.set(r);
        }
        return tmp;
    }

    private static final RectF tmp = new RectF();
}
