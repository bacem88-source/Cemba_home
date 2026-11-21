package com.bacem.launcher;

/** v4.44: full-screen WebView for a web site (cartoony.net) inside Cemba
 *  Home. The site's videos play in its own in-page HTML5 player, so the
 *  site itself is the player -- Cemba just gives it a remote-friendly
 *  full-screen home. JavaScript + DOM storage on, wide viewport, D-pad
 *  focus navigation is the WebView's own. Back goes back a page, and
 *  exits to Cemba when there is no page to go back to. */
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebViewActivity extends Activity {
    WebView wv;
    String pageUrl;
    // v4.52: redirect-once guard. If the WebView cannot render the site
    // (old WebView, sendBeacon SecurityError), we open the Cartoony app
    // itself instead. Only redirect once per activity, so a page that
    // errors twice does not loop between the two.
    boolean redirectedOut = false;
    void redirectOut() {
        if (redirectedOut || pageUrl == null) return;
        redirectedOut = true;
        openCartoonyApp();
        finish();
    }
    // v4.52: open the Cartoony app itself (the cartoony.net TWA,
    // tv.ufogrendizer.twa) -- not a generic browser. If it is not
    // installed, fall back to the system browser chooser for the URL.
    void openCartoonyApp() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("tv.ufogrendizer.twa");
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
        } catch (Exception e) { /* fall through */ }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(pageUrl));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { /* ignore */ }
    }
    // v4.47: virtual mouse like Downloader's -- a red circle the remote
    // moves with the arrows, OK clicks where it points. v4.46 used a
    // TextView with a bullet glyph and onKeyDown: the glyph did not
    // render on the box (white arrow instead of a red circle) and the
    // WebView eats D-pad keys before onKeyDown sees them, so the mouse
    // never moved. Fixed two ways: the circle is now a drawn shape
    // (GradientDrawable oval, no font needed) and the keys are caught
    // in dispatchKeyEvent, which runs before the focused WebView.
    View mouse;
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
        pageUrl = url;

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
        // v4.47: report load errors so a blank page can be diagnosed
        // on the box. (The v4.47 "Loaded:" title toast and the JS
        // console-error toast were diagnostic noise -- the sendBeacon
        // cause is now understood -- so they are gone in v4.50.)
        // v4.48: the site's own analytics call throws an uncaught
        // SecurityError (navigator.sendBeacon with a Blob whose
        // Content-Type is not CORS-safelisted -- an old-WebView
        // behaviour, crbug.com/490015) and that uncaught throw stops
        // the app from rendering, so the page stays dark. Neutralise
        // sendBeacon before the page's scripts run, so the call
        // succeeds silently and the app can initialise.
        wv.setWebViewClient(new WebViewClient() {
            // v4.49: the onPageStarted JS injection in v4.48 did not
            // take effect -- the page's own scripts run before the
            // injected override lands, so sendBeacon still throws and
            // the app still stays dark. The only reliable way to get
            // code in front of a page's scripts is to put it INTO the
            // HTML itself. Intercept the main document, fetch it,
            // inject the sendBeacon override as the very first script
            // right after <head>, and serve the modified HTML. That
            // guarantees the override exists before any site script
            // runs. Only the main HTML document is touched; every
            // other request (JS, images, API) passes through normally.
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try {
                    if (r == null || !r.isForMainFrame()) return null;
                    String u = r.getUrl() != null ? r.getUrl().toString() : "";
                    if (!u.startsWith("http")) return null;
                    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", v.getSettings().getUserAgentString());
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    if (code < 200 || code >= 300) return null;
                    String ct = c.getContentType();
                    if (ct != null && !ct.contains("text/html")) return null;
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                    in.close();
                    String html = bos.toString("UTF-8");
                    String patch = "<script>window.navigator.sendBeacon=function(){return true;};</script>";
                    int head = html.indexOf("<head");
                    if (head >= 0) {
                        int gt = html.indexOf(">", head);
                        if (gt >= 0) html = html.substring(0, gt + 1) + patch + html.substring(gt + 1);
                    } else {
                        html = patch + html;
                    }
                    return new WebResourceResponse("text/html", "UTF-8",
                            new ByteArrayInputStream(html.getBytes("UTF-8")));
                } catch (Exception e) {
                    return null;
                }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r != null && r.isForMainFrame()) {
                    toast("Page error: " + e.getDescription());
                }
            }
        });
        // v4.45: a bare WebChromeClient cannot show a fullscreen video --
        // the player's fullscreen button does nothing and some players
        // misbehave without it. Handle the custom (fullscreen) view.
        wv.setWebChromeClient(new WebChromeClient() {
            View customView;
            WebChromeClient.CustomViewCallback customCb;
            // v4.52: if the site's JS throws the sendBeacon SecurityError,
            // the old WebView cannot render the page (it stays dark).
            // Instead of leaving a black screen, redirect out to an
            // external browser (TV Bro / Downloader / Chrome), which may
            // use a newer WebView. Only the sendBeacon failure triggers
            // this -- other console messages are ignored.
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                if (m != null && m.message() != null
                        && m.message().contains("sendBeacon")
                        && m.message().contains("SecurityError")) {
                    redirectOut();
                }
                return true;
            }
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

        // v4.47: the mouse cursor -- a drawn red circle (GradientDrawable
        // oval), NOT a text glyph, so it renders on any font. Hidden
        // until an arrow key moves it. It sits above the WebView so it
        // is always visible, and OK clicks through it at its position.
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            screenW = dm.widthPixels; screenH = dm.heightPixels;
            mx = screenW / 2; my = screenH / 2;
        } catch (Exception e) { /* keep defaults */ }
        mouse = new View(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.RED);
        circle.setStroke(4, Color.WHITE);
        mouse.setBackground(circle);
        mouse.setVisibility(View.GONE);
        // v4.50: position with setX/setY, not layout margins -- moving
        // the mouse then only redraws it instead of running a full
        // layout pass on every arrow press.
        root.addView(mouse, new FrameLayout.LayoutParams(48, 48));
        mouse.setX(mx);
        mouse.setY(my);

        setContentView(root);
        wv.loadUrl(url);
        wv.requestFocus();
    }

    // v4.47: dispatchKeyEvent runs BEFORE the focused WebView gets the
    // key, so the arrows always move the mouse. onKeyDown is too late --
    // the WebView eats D-pad keys for its own focus navigation and the
    // Activity never sees them. Only act on key-down (not key-up).
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (wv != null && wv.canGoBack()) { wv.goBack(); return true; }
            finish();
            return true;
        }
        // v4.47: arrow keys move the mouse cursor. The site is not built
        // for D-pad focus, so the mouse is the way to reach its links.
        // v4.50: when the mouse hits an edge and the user keeps
        // pressing that way, scroll the page too -- otherwise the user
        // can never reach content below the fold, the mouse just stops
        // at the bottom of the screen and the page never moves.
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (mouse != null) {
                int step = 60;
                boolean atEdge = false;
                if (code == KeyEvent.KEYCODE_DPAD_UP) { atEdge = (my <= 0); my -= step; }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) { atEdge = (my >= screenH - 48); my += step; }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { atEdge = (mx <= 0); mx -= step; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { atEdge = (mx >= screenW - 48); mx += step; }
                if (mx < 0) mx = 0; if (my < 0) my = 0;
                if (mx > screenW - 48) mx = screenW - 48;
                if (my > screenH - 48) my = screenH - 48;
                mouse.setX(mx);
                mouse.setY(my);
                mouse.setVisibility(View.VISIBLE);
                mouseHide.removeCallbacks(hideMouseRun);
                mouseHide.postDelayed(hideMouseRun, 6000);
                if (atEdge && wv != null) {
                    if (code == KeyEvent.KEYCODE_DPAD_DOWN) wv.scrollBy(0, 300);
                    else if (code == KeyEvent.KEYCODE_DPAD_UP) wv.scrollBy(0, -300);
                    else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) wv.scrollBy(300, 0);
                    else if (code == KeyEvent.KEYCODE_DPAD_LEFT) wv.scrollBy(-300, 0);
                }
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
                    float x = mx + 24, y = my + 24;
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
        return super.dispatchKeyEvent(e);
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
