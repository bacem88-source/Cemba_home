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
import android.view.Gravity;
import android.view.KeyEvent;
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
        // Desktop-style UA: the site serves its full layout (the mobile
        // layout is cramped on a TV); the page is responsive either way.
        s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
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
