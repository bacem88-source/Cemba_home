package com.bacem.launcher;

import android.graphics.Color;
import android.os.Handler;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Cemba Dream (v3.37): the launcher's screensaver. Pick it in the box's
// Screen saver settings. A slow slideshow of his photos with the clock;
// any remote press wakes the box back to Cemba Home.
public class CembaDream extends DreamService {
    Handler handler = new Handler();
    ImageView photo;
    TextView clock;
    int idx = 0;
    Runnable tick = new Runnable() {
        @Override public void run() {
            if (photo != null) {
                photo.setImageResource(MainActivity.PHOTOS[idx % MainActivity.PHOTOS.length]);
                idx++;
            }
            if (clock != null) {
                clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            }
            handler.postDelayed(this, 15000);
        }
    };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        FrameLayout root = new FrameLayout(this);
        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(64);
        clock.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        clock.setShadowLayer(8f, 0f, 2f, Color.BLACK);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.bottomMargin = 80;
        root.addView(clock, lp);
        setContentView(root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tick);
    }

    @Override public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }
}
