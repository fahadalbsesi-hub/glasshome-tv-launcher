package com.glasshome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** One translucent highlight that springs between targets instead of per-tile borders. */
public class GlassView extends View {
    public interface Target {
        /** Fills out with the target bounds in this view's coordinates; false if not on screen. */
        boolean get(RectF out);
        float radius();
    }

    private final RectF cur = new RectF(), from = new RectF(), tmp = new RectF();
    private float curR, fromR;
    private boolean has;
    private Target target;
    private ValueAnimator anim;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassView(Context c) {
        super(c);
        fill.setColor(0x1FFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(c.getResources().getDisplayMetrics().density * 1.5f);
        stroke.setColor(0x66FFFFFF);
    }

    public void moveTo(Target t, boolean animate) {
        target = t;
        if (anim != null) anim.cancel();
        if (!has || !animate) {
            refresh();
            return;
        }
        from.set(cur);
        fromR = curR;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(340);
        anim.setInterpolator(new OvershootInterpolator(1.15f));
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            if (target.get(tmp)) {
                cur.set(lerp(from.left, tmp.left, f), lerp(from.top, tmp.top, f),
                        lerp(from.right, tmp.right, f), lerp(from.bottom, tmp.bottom, f));
                curR = Math.max(0, lerp(fromR, target.radius(), f));
            }
            invalidate();
        });
        anim.start();
    }

    /** Snap to the live target position (after scrolling or layout). */
    public void refresh() {
        if (target == null || (anim != null && anim.isRunning())) return;
        if (target.get(tmp)) {
            cur.set(tmp);
            curR = target.radius();
            has = true;
            invalidate();
        }
    }

    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    @Override
    protected void onDraw(Canvas c) {
        if (!has) return;
        c.drawRoundRect(cur, curR, curR, fill);
        sheen.setShader(new LinearGradient(0, cur.top, 0, cur.top + cur.height() * 0.55f,
                0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(cur, curR, curR, sheen);
        c.drawRoundRect(cur, curR, curR, stroke);
    }
}
