package com.yz8023.purestart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * 移植自原版 PureStart 的主页盾牌图标 (HoldShieldView)：
 * 支持长按 1 秒触发全开/恢复广告拦截（带圆环环绕动效与振动反馈）；
 * 兼容纯点击提示与磨砂柔光色彩高光。
 */
public final class HoldShieldView extends View {
    public interface Listener {
        void onHoldComplete();
        void onTapHint();
    }

    private static final long HOLD_MS = 1000L;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shield = new Path();
    private final RectF arc = new RectF();
    private final float ringWidth;
    private Listener listener;
    private boolean active;
    private float progress;
    private int ticks;
    private ValueAnimator animator;

    public HoldShieldView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        ringWidth = 4f * density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringWidth);
        ring.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        glyph.setStyle(Paint.Style.FILL);

        // 盾牌矢量路径 (108-unit viewport)
        shield.setFillType(Path.FillType.EVEN_ODD);
        shield.moveTo(54f, 28f);
        shield.lineTo(75f, 36.5f);
        shield.lineTo(75f, 53f);
        shield.cubicTo(75f, 66.5f, 66f, 76.5f, 54f, 80f);
        shield.cubicTo(42f, 76.5f, 33f, 66.5f, 33f, 53f);
        shield.lineTo(33f, 36.5f);
        shield.close();
        shield.moveTo(57.5f, 37f);
        shield.lineTo(44f, 56.5f);
        shield.lineTo(52.5f, 56.5f);
        shield.lineTo(49.5f, 71f);
        shield.lineTo(64f, 50.5f);
        shield.lineTo(55.5f, 50.5f);
        shield.close();

        setClickable(true);
        setFocusable(true);
        setHapticFeedbackEnabled(true);
        setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                complete();
                return true;
            }
        });
    }

    public boolean isHolding() {
        return progress > 0f;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        invalidate();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startHold();
                return true;
            case MotionEvent.ACTION_UP:
                if (progress < 1f) {
                    rewind();
                    if (listener != null && event.getEventTime() - event.getDownTime() < 250L) {
                        listener.onTapHint();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                rewind();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void startHold() {
        if (animator != null) animator.cancel();
        ticks = 0;
        animator = ValueAnimator.ofFloat(progress, 1f);
        animator.setDuration((long) ((1f - progress) * HOLD_MS));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                progress = ((Float) a.getAnimatedValue()).floatValue();
                int nextTick = (int) (progress * 3f);
                if (nextTick > ticks && nextTick < 3) {
                    ticks = nextTick;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (progress >= 1f) complete();
            }
        });
        animator.start();
    }

    private void rewind() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, 0f);
        animator.setDuration((long) (progress * 260L));
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                progress = ((Float) a.getAnimatedValue()).floatValue();
                invalidate();
            }
        });
        animator.start();
    }

    private void complete() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ValueAnimator pop = ValueAnimator.ofFloat(1f, 1.15f, 1f);
        pop.setDuration(300L);
        pop.setInterpolator(new OvershootInterpolator(2.5f));
        pop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                setScaleX((Float) a.getAnimatedValue());
                setScaleY((Float) a.getAnimatedValue());
            }
        });
        pop.start();
        progress = 0f;
        invalidate();
        if (listener != null) listener.onHoldComplete();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - ringWidth;

        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 磨砂柔光渐变底色（View 近似）
        if (active) {
            fill.setShader(new LinearGradient(cx, cy - radius, cx, cy + radius,
                    0xFF3B82F6, 0xFF1D4ED8, Shader.TileMode.CLAMP));
            glyph.setColor(Color.WHITE);
        } else {
            fill.setShader(null);
            fill.setColor(0x22FFFFFF);
            glyph.setColor(0x99FFFFFF);
        }

        canvas.drawCircle(cx, cy, radius, fill);

        // 长按进度圆环（柔光光环）
        if (progress > 0f) {
            ring.setColor(active ? 0xFF60A5FA : 0xFF3B82F6);
            canvas.drawArc(arc, -90f, progress * 360f, false, ring);
        }

        // 绘制盾牌
        canvas.save();
        float scale = (radius * 1.35f) / 108f;
        canvas.translate(cx - 54f * scale, cy - 54f * scale);
        canvas.scale(scale, scale);
        canvas.drawPath(shield, glyph);
        canvas.restore();
    }
}
