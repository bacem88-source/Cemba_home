package com.bacem.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

/** v3.58: opens the system power menu (Power off / Restart) from Cemba Home.
 *  A launcher app cannot power the box off itself -- only an accessibility
 *  service can show the power dialog. The user enables this once in
 *  Settings > Accessibility > Cemba Power; the "Power off" row in Box
 *  settings then sends it a broadcast and it opens the power menu.
 *  v3.59: also sleeps the box (ACTION_SLEEP -> lock screen), so he can
 *  close the box from the keyboard by navigating to the Sleep row.
 *  v3.61: also goes home (ACTION_GO_HOME) -- when the kid's time is up the
 *  launcher pulls her out of whatever she is watching (YouTube, Cobalt)
 *  back to the home screen, so the video stops. */
public class PowerService extends AccessibilityService {
    public static final String ACTION_POWER_DIALOG = "com.bacem.launcher.POWER_DIALOG";
    public static final String ACTION_SLEEP = "com.bacem.launcher.SLEEP";
    public static final String ACTION_GO_HOME = "com.bacem.launcher.GO_HOME";
    BroadcastReceiver receiver;
    // v4.29: timer-up lockdown -- one home action per press. The app going
    // to the background fires its own state change right after, so a short
    // throttle stops the duplicate. A deliberate re-press takes a child
    // over a second, so it is still caught.
    long lastLockdownHome = 0;
    // v4.44: the lockdown above only fires on a window change. If a video
    // app is ALREADY open when the timer runs out, no window changes and
    // nothing kicks it. So remember the last foreground package and
    // re-check it on a tick: if the timer is up and a video app is still
    // in front, kick to Cemba Kids home.
    String lastPkg = "";
    android.os.Handler tickHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    Runnable tickR = new Runnable() {
        @Override public void run() {
            try {
                if (timerUp(PowerService.this) && isLockdownTarget(lastPkg)) kickHome();
            } catch (Throwable t) { /* ignore */ }
            tickHandler.postDelayed(this, 20000);
        }
    };
    void kickHome() {
        long now = System.currentTimeMillis();
        if (now - lastLockdownHome < 500) return;
        lastLockdownHome = now;
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable t2) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }
    @Override public void onServiceConnected() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (ACTION_POWER_DIALOG.equals(intent.getAction())) PowerService.this.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                else if (ACTION_SLEEP.equals(intent.getAction())) PowerService.this.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                else if (ACTION_GO_HOME.equals(intent.getAction())) PowerService.this.performGlobalAction(GLOBAL_ACTION_HOME);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_POWER_DIALOG);
        f.addAction(ACTION_SLEEP);
        f.addAction(ACTION_GO_HOME);
        // v3.99: on Android 14 (this box) the 2-arg registerReceiver throws
        // a SecurityException for an app targeting API 34 -- it must say
        // whether the receiver is exported. That crash killed the whole
        // app process (the service shares it with the launcher), so turning
        // Cemba Power on in Accessibility broke everything and Cemba Home
        // stopped working. These broadcasts are internal to Cemba Home
        // only, so the receiver is NOT_EXPORTED on API 33+; older boxes
        // keep the 2-arg call.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }
        tickHandler.postDelayed(tickR, 20000);
    }
    // v4.31: timer-up lockdown, denylist (not allowlist). v4.29 used an
    // allowlist -- it kicked home for EVERY window that was not Cemba or
    // a few system packages. But TYPE_WINDOW_STATE_CHANGED fires for the
    // keyboard, system UI, dialogs and the home transition itself, so the
    // lockdown fired on those too, restarted Cemba in a loop, and the
    // Time's up dialog flashed on and off so Cemba could not be used at
    // all. Now only a known video / streaming app is kicked; everything
    // else (keyboard, launcher, system UI, dialogs, Cemba itself) is
    // ignored by default. The lockdown only fires while Kids mode is on
    // and the time is up; while she still has time left, and in Family
    // mode, apps open normally. It stops the moment the parent enters
    // the PIN and exits Kids mode (kids_mode off).
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            CharSequence pkgCs = event.getPackageName();
            if (pkgCs == null) return;
            String pkg = pkgCs.toString();
            if (pkg.length() == 0) return;
            lastPkg = pkg;
            if (!isLockdownTarget(pkg)) return;
            if (!timerUp(this)) return;
            kickHome();
        } catch (Throwable t) { /* lockdown is best-effort */ }
    }

    /** v4.31: the video / streaming apps a child could open with the
        remote shortcuts or from the home screen when the Kids timer is
        up. Only these are kicked home; every other window (keyboard,
        launcher, system UI, dialogs) is left alone. */
    static boolean isLockdownTarget(String pkg) {
        if ("com.google.android.youtube.tv".equals(pkg)) return true;
        if ("com.google.android.youtube".equals(pkg)) return true;
        if ("com.google.android.apps.youtube.kids".equals(pkg)) return true;
        if ("io.gh.reisxd.tizentube.cobalt".equals(pkg)) return true;
        if ("org.smarttube.stable".equals(pkg)) return true;
        if ("org.smarttube.beta".equals(pkg)) return true;
        if ("com.netflix.ninja".equals(pkg)) return true;
        if ("com.amazon.avod".equals(pkg)) return true;
        if ("com.disney.disneyplus".equals(pkg)) return true;
        if ("com.tubitv".equals(pkg)) return true;
        if ("com.tvtv.tubi.app".equals(pkg)) return true;
        if ("tv.pluto.android".equals(pkg)) return true;
        if ("xyz.netfly".equals(pkg)) return true;
        if ("org.drive_hunter".equals(pkg)) return true;
        if ("ver3.ycntivi.off".equals(pkg)) return true;
        if ("com.yacene.tvplus".equals(pkg)) return true;
        if ("com.t4w.ostora516".equals(pkg)) return true;
        if ("local.tvmorph.tv".equals(pkg)) return true;
        if ("com.stremio.one".equals(pkg)) return true;
        if ("org.videolan.vlc".equals(pkg)) return true;
        if ("com.playeregy.egyplayerapp".equals(pkg)) return true;
        if ("com.linkinapp.tunisielivetv".equals(pkg)) return true;
        if ("ca.cbc.android.cbctv".equals(pkg)) return true;
        return false;
    }

    /** v4.29: is the Kids timer up right now? Reads the same "ui" prefs
        as MainActivity and applies the same rules (Logic.kidsTimerUp),
        so the service and the launcher agree. */
    static boolean timerUp(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE);
            boolean kids = p.getBoolean("kids_mode", false);
            int limit = p.getInt("kids_limit_min", 0);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
            int stored = today.equals(p.getString("kids_used_date", "")) ? p.getInt("kids_used_min", 0) : 0;
            long start = p.getLong("kids_session_start", 0);
            String bt = p.getString("kids_bedtime", "");
            java.util.Calendar c = java.util.Calendar.getInstance();
            int nowMin = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
            return Logic.kidsTimerUp(kids, limit, stored, start, System.currentTimeMillis(), bt, nowMin);
        } catch (Throwable t) { return false; }
    }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        try { tickHandler.removeCallbacks(tickR); } catch (Exception e) {}
        try { if (receiver != null) unregisterReceiver(receiver); } catch (Exception e) {}
        super.onDestroy();
    }
}
