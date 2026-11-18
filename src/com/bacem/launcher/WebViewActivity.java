package com.bacem.launcher;

/** v4.44: full-screen WebView for a web site (cartoony.net) inside Cemba
 *  Home. The site's videos play in its own in-page HTML5 player, so the
 *  site itself is the player -- Cemba just gives it a remote-friendly
 *  full-screen home. JavaScript + DOM storage on, wide viewport, D-pad
 *  focus navigation is the WebView's own. Back goes back a page, and
 *  exits to Cemba when there is no page to go back to. */
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WebViewActivity extends Activity {
    WebView wv;
    // v4.46: virtual mouse like Downloader's -- a cursor the remote moves
    // with the arrows, OK clicks where it points. The site is not built
    // for D-pad focus, so a mouse is the only way to reach its links and
    // its play button with a remote.
    TextView mouse;
    int mx = 540, my = 960;
    int screenW = 1920, screenH = 1080;
    Handler mouseHide = new Handler();
    Runnable hideMouseRun = new Runnable() {
        @Override public void run() { if (mouse != null) mouse.setVisibility(View.GONE); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("name");
        if (url == null) { toast("No page"); finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        // v4.45: the video stream is often served over http while the
        // page is https -- the WebView blocks that mixed content by
        // default, so the player loads but the video never starts.
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // Desktop-style UA: the site serves its full layout (the mobile
        // layout is cramped on a TV); the page is responsive either way.
        s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        wv.setWebViewClient(new WebViewClient());
        // v4.45: a bare WebChromeClient cannot show a fullscreen video --
        // the player's fullscreen button does nothing and some players
        // misbehave without it. Handle the custom (fullscreen) view.
        wv.setWebChromeClient(new WebChromeClient() {
            View customView;
            WebChromeClient.CustomViewCallback customCb;
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                FrameLayout root2 = (FrameLayout) findViewById(android.R.id.content);
                root2.addView(view, new FrameLayout.LayoutParams(-1, -1));
                wv.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                FrameLayout root2 = (FrameLayout) findViewById(android.R.id.content);
                root2.removeView(customView);
                customView = null;
                if (customCb != null) customCb.onCustomViewHidden();
                customCb = null;
                wv.setVisibility(View.VISIBLE);
            }
        });
        root.addView(wv, new FrameLayout.LayoutParams(-1, -1));

        // Thin top hint bar, fades after a few seconds.
        final TextView hint = new TextView(this);
        hint.setText((name != null ? name : "Web") + "  \u2014  Back: previous page / exit");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(18);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0xAA000000);
        hint.setPadding(0, 8, 0, 8);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.TOP;
        root.addView(hint, hp);
        hint.postDelayed(new Runnable() {
            @Override public void run() { hint.setVisibility(View.GONE); }
        }, 6000);

        // v4.46: the mouse cursor. A big yellow arrow, hidden until an
        // arrow key moves it. It sits above the WebView so it is always
        // visible, and OK clicks through it at its position.
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            screenW = dm.widthPixels; screenH = dm.heightPixels;
            mx = screenW / 2; my = screenH / 2;
        } catch (Exception e) { /* keep defaults */ }
        mouse = new TextView(this);
        mouse.setText("\u25cf");
        mouse.setTextColor(Color.RED);
        mouse.setTextSize(34);
        mouse.setShadowLayer(6, 2, 2, Color.BLACK);
        mouse.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = mx; mp.topMargin = my;
        root.addView(mouse, mp);

        setContentView(root);
        wv.loadUrl(url);
        wv.requestFocus();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (wv != null && wv.canGoBack()) { wv.goBack(); return true; }
            finish();
            return true;
        }
        // v4.46: arrow keys move the mouse cursor. The site is not built
        // for D-pad focus, so the mouse is the way to reach its links.
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (mouse != null) {
                int step = 60;
                if (code == KeyEvent.KEYCODE_DPAD_UP) my -= step;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) my += step;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) mx -= step;
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) mx += step;
                if (mx < 0) mx = 0; if (my < 0) my = 0;
                if (mx > screenW - 40) mx = screenW - 40;
                if (my > screenH - 40) my = screenH - 40;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mouse.getLayoutParams();
                lp.leftMargin = mx; lp.topMargin = my;
                mouse.setLayoutParams(lp);
                mouse.setVisibility(View.VISIBLE);
                mouseHide.removeCallbacks(hideMouseRun);
                mouseHide.postDelayed(hideMouseRun, 6000);
            }
            return true;
        }
        // v4.45: the player's own play button is often not reachable
        // with the TV remote (D-pad focus skips it). OK / Enter plays
        // the video element directly, so the episode starts even when
        // the button cannot be focused. The remote's Play / Pause
        // button does the same, so it works even when OK is busy
        // clicking a focused link.
        // v4.46: when the mouse is showing, OK clicks where the mouse
        // points instead (tap at the cursor). When the mouse is hidden,
        // OK keeps the old play/pause behaviour.
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                    && mouse != null && mouse.getVisibility() == View.VISIBLE && wv != null) {
                try {
                    long now = SystemClock.uptimeMillis();
                    float x = mx + 17, y = my + 17;
                    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                    MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0);
                    wv.dispatchTouchEvent(down);
                    wv.dispatchTouchEvent(up);
                    down.recycle(); up.recycle();
                } catch (Exception ex) { /* ignore */ }
                return true;
            }
            if (wv != null) {
                wv.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');"
                    + "if(v){if(v.paused){v.play();}else{v.pause();}return 'ok';}"
                    + "var b=document.querySelector('[class*=play],button[aria-label*=lay]');"
                    + "if(b){b.click();return 'ok';}return 'none';})();", null);
                return true;
            }
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onPause() {
        super.onPause();
        if (wv != null) wv.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (wv != null) wv.onResume();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (wv != null) { wv.stopLoading(); wv.destroy(); }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
