package com.bacem.launcher;

/** Animated soft-color background (Wallpaper cycle palettes). Photos are the default;
 *  this draws the gradient palettes when he cycles past them. */
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
public class DynBg extends View {
    static final int[][] PALETTES = {
        { 0xFF0A0E1E, 0xFF1E3A8A, 0xFF4C1D95, 0xFF0E7490 },
        { 0xFF140A08, 0xFF9A3412, 0xFF7F1D1D, 0xFFB45309 },
        { 0xFF04120E, 0xFF065F46, 0xFF14532D, 0xFF0E7490 },
        { 0xFF0C0818, 0xFF86198F, 0xFF3730A3, 0xFF9D2464 },
        { 0xFF101216, 0xFF2B3444, 0xFF1F2733, 0xFF39465C },
        { 0xFF160405, 0xFF991B1B, 0xFF5B1120, 0xFFB91C1C },
    };
    int palette = 0;
    Bitmap small;
    Canvas sc;
    Paint blobPaint = new Paint();
    Handler h = new Handler(Looper.getMainLooper());
    long t0 = System.currentTimeMillis();
    boolean running = false;
    final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            h.postDelayed(this, 50);
        }
    };
    public DynBg(Context ctx) { super(ctx); }
    public void setPalette(int i) {
        palette = Math.max(0, Math.min(PALETTES.length - 1, i));
        invalidate();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        h.post(frame);
    }
    @Override protected void onDetachedFromWindow() {
        running = false;
        h.removeCallbacks(frame);
        super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return;
        if (small == null) {
            small = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
            sc = new Canvas(small);
        }
        int[] pal = PALETTES[palette];
        sc.drawColor(pal[0]);
        double t = (System.currentTimeMillis() - t0) / 1000.0;
        for (int i = 1; i <= 3; i++) {
            double sp = 0.045 + i * 0.02;
            double ph = i * 2.1;
            float cx = (float) (240 + 175 * Math.sin(t * sp + ph));
            float cy = (float) (125 + 82 * Math.cos(t * sp * 1.3 + ph));
            float r = 150 + i * 32;
            blobPaint.setShader(new RadialGradient(cx, cy, r, pal[i], pal[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            sc.drawCircle(cx, cy, r, blobPaint);
        }
        blobPaint.setShader(null);
        c.drawBitmap(small, null, new Rect(0, 0, W, H), null);
    }
}
