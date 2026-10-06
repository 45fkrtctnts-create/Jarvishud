package com.fikret.jarvishud;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class JarvisCoreView extends View {
    public enum Mode { IDLE, LISTENING, PROCESSING, SPEAKING, ERROR }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float rotation = 0f;
    private float amplitude = 0f;
    private float statePulse = 0f;
    private Mode mode = Mode.IDLE;
    private final ValueAnimator spinner;
    private final ValueAnimator pulseAnimator;

    public JarvisCoreView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        text.setColor(Color.rgb(225, 247, 255));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        spinner = ValueAnimator.ofFloat(0f, 360f);
        spinner.setDuration(8500);
        spinner.setRepeatCount(ValueAnimator.INFINITE);
        spinner.setInterpolator(new LinearInterpolator());
        spinner.addUpdateListener(v -> { rotation = (float) v.getAnimatedValue(); invalidate(); });
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(650);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(v -> { statePulse = (float) v.getAnimatedValue(); invalidate(); });
    }

    public void setMode(Mode value) {
        if (value == null || mode == value) return;
        mode = value;
        if (mode == Mode.IDLE || mode == Mode.ERROR) {
            pulseAnimator.cancel();
            statePulse = 0f;
        } else if (!pulseAnimator.isStarted()) {
            pulseAnimator.start();
        }
        spinner.setDuration(mode == Mode.PROCESSING ? 1800 : 8500);
        invalidate();
    }

    public Mode getMode() {
        return mode;
    }

    public void setAmplitude(float value) {
        amplitude = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float base = Math.min(w, h) * 0.29f;
        float visualAmplitude = Math.max(amplitude, mode == Mode.IDLE ? 0f : statePulse * 0.28f);
        float pulse = 1f + visualAmplitude * 0.13f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(65 + (int)(90 * visualAmplitude), 30, 145, 255));
        p.setShadowLayer(base * (0.45f + visualAmplitude * 0.7f), 0, 0, Color.rgb(40, 155, 255));
        c.drawCircle(cx, cy, base * 1.22f * pulse, p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, base * 0.035f));
        p.setColor(Color.rgb(205, 238, 255));
        c.drawCircle(cx, cy, base * 0.91f, p);

        p.setStrokeWidth(Math.max(3f, base * 0.065f));
        p.setColor(Color.rgb(74, 182, 255));
        RectF r = new RectF(cx-base*1.12f, cy-base*1.12f, cx+base*1.12f, cy+base*1.12f);
        c.save(); c.rotate(rotation, cx, cy);
        c.drawArc(r, -30, 74, false, p);
        c.drawArc(r, 116, 52, false, p);
        c.drawArc(r, 214, 92, false, p);
        c.restore();

        p.setStrokeWidth(Math.max(1f, base * 0.018f));
        p.setColor(Color.argb(180, 105, 202, 255));
        float rr = base * 1.42f;
        c.save(); c.rotate(-rotation * 0.55f, cx, cy);
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15);
            float x1 = cx + (float)Math.cos(a) * rr;
            float y1 = cy + (float)Math.sin(a) * rr;
            float x2 = cx + (float)Math.cos(a) * (rr + base * 0.10f);
            float y2 = cy + (float)Math.sin(a) * (rr + base * 0.10f);
            c.drawLine(x1, y1, x2, y2, p);
        }
        c.restore();

        text.setTextSize(base * 0.34f);
        text.setShadowLayer(base * 0.12f, 0, 0, Color.rgb(60, 170, 255));
        c.drawText("JARVIS", cx, cy - (text.ascent() + text.descent()) / 2f, text);
        text.clearShadowLayer();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!spinner.isStarted()) spinner.start();
        if (mode != Mode.IDLE && mode != Mode.ERROR && !pulseAnimator.isStarted()) pulseAnimator.start();
    }

    @Override protected void onDetachedFromWindow() {
        spinner.cancel();
        pulseAnimator.cancel();
        super.onDetachedFromWindow();
    }
}
