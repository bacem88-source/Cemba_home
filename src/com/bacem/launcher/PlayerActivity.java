package com.bacem.launcher;

/** Minimal full-screen HLS player for the guide's free-playlist channels:
 *  framework VideoView (MediaPlayer, no new dependency). OK pauses/resumes,
 *  Back stops and exits. */
import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class PlayerActivity extends Activity {
    VideoView vv;
    TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("name");
        String ua = getIntent().getStringExtra("ua");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setText((name != null ? name : "Live") + "\nLoading\u2026");
        status.setTextColor(Color.WHITE);
        status.setTextSize(24);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (url == null) { toast("No stream"); finish(); return; }
        if (ua != null && ua.length() > 0) {
            // v3.66: some CDNs (L'Equipe) only answer a desktop browser UA.
            java.util.HashMap<String, String> headers = new java.util.HashMap<String, String>();
            headers.put("User-Agent", ua);
            vv.setVideoURI(Uri.parse(url), headers);
        } else {
            vv.setVideoURI(Uri.parse(url));
        }
        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                status.setVisibility(View.GONE);
                vv.start();
            }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                toast("Stream unavailable right now");
                finish();
                return true;
            }
        });
        vv.requestFocus();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (vv.isPlaying()) {
                vv.pause();
                status.setText("Paused \u2014 press OK to resume");
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.GONE);
                vv.start();
            }
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onPause() {
        super.onPause();
        if (vv != null) vv.pause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (vv != null) vv.stopPlayback();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
