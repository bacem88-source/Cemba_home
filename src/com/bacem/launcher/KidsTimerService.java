package com.bacem.launcher;

/** v4.55: Kids timer foreground service -- the PRIMARY timer on the
    Xiaomi box. It stays alive while Jannah watches YouTube / Cobalt,
    checks every 30 seconds, and when the daily limit or bedtime is
    reached it brings MainActivity forward (a foreground service may
    start an activity) so onResume shows Time's up. Started when Kids
    mode is entered, stopped when it is left. Shows a small "Kids time"
    notification while running.
    ------------------------------------------------------------------
    KIDS TIMER -- EVERY APPROACH TRIED, IN ORDER (kept in the code so
    we know what was done; the alarm path is still scheduled as a
    backup, see scheduleKidsAlarm / KidsTimerReceiver).
    v3.87 Handler tick in MainActivity (kidsTick, 60 s): counted minutes
      by ticks. DIED when the activity was destroyed in the background
      while Jannah watched YouTube, so the limit never fired. Kept only
      as a widget refresh + check while the home is up; counting is now
      time-based (kidsUsedToday) so missed ticks lose nothing.
    v4.29/4.30 PowerService (accessibility) lockdown: tried to force the
      home / lock apps via the accessibility service. BROKE on the box
      (fired on every system window, PIN untypable) and Cemba Power is
      blocked on the Xiaomi anyway (device admin policy, "Skipping
      enabling service disallowed by device admin policy"). ABANDONED.
      Do not retry the enable loop.
    v4.44/4.46 HOME intent: when time was up, send a HOME intent to
      bring the launcher forward. FAILED on all boxes -- the video app
      stayed open. Kept only as a fallback inside KidsTimerReceiver.
    v4.51 AlarmManager -> broadcast PendingIntent: the alarm fired a
      broadcast and the receiver tried to start MainActivity from the
      background. BLOCKED on Android 10+ (background activity start
      restriction). FAILED on every box. The receiver is kept for manual
      / broadcast triggers only; the alarm no longer goes through it.
    v4.52 AlarmManager -> activity PendingIntent, setExactAndAllowWhile-
      Idle: the alarm itself carries an ACTIVITY PendingIntent, delivered
      by the system, so the start is allowed even from the background
      and in Doze. BUT on Android 12+ it needs SCHEDULE_EXACT_ALARM,
      which the box never granted, so it fell back to the inexact
      setAndAllowWhileIdle and the alarm was deferred in Doze. FAILED
      on all boxes.
    v4.54 setAlarmClock: exempt from the exact-alarm permission, fires
      exactly even in Doze, activity PendingIntent brings MainActivity
      forward. WORKS on the N5 Max (Android 9). Does NOT work on the
      Xiaomi (Android TV 14 / MIUI defers or drops it -- and Android TV
      has no "Battery unrestricted" setting to fix it with). KEPT as a
      backup: scheduleKidsAlarm still arms it.
    v4.55 Foreground service (this file): the reliable path on the
      Xiaomi in theory -- a foreground service is not deferred like an
      alarm. FAILED on the Xiaomi at first (2026-11-20): no "Kids time"
      notification ever appeared while she watched, so the service never
      ran and nothing closed YouTube Kids or Cobalt when time was up.
      ROOT CAUSE: the manifest declared foregroundServiceType="dataSync"
      but was MISSING the FOREGROUND_SERVICE_DATA_SYNC permission, which
      Android 14 (API 34, targetSdk 34) requires for that type. Without
      it startForeground() throws a SecurityException (swallowed by the
      try/catch), so the service never became foreground. FIXED in
      v4.57 by adding that permission to the manifest. This service is
      now the primary timer on the Xiaomi; the setAlarmClock alarm is
      kept as a backup.
    v4.58 Second-session bug (2026-11-20): the service closed YouTube
      the FIRST time time was up, but NOT the second time. Cause: the
      tick called stopSelf() right after bringHome(). After the first
      "Time's up" the service was dead, the home was already in front
      so onResume never fired to restart it, and when Jannah opened
      YouTube again nothing was watching. FIXED by removing stopSelf()
      -- the service now keeps watching while Kids mode is on and pulls
      her out every time she goes back into YouTube. It only stops when
      Kids mode is turned off.
    v1.0 Second-session STILL failing (2026-11-22, on 1.0 / v4.59 code):
      the service closed YouTube the first time, but NOT the second
      time, and the "Kids time" notification was GONE during the second
      YouTube session -- the service was dead. Cause: the notification
      was IMPORTANCE_LOW, so the box (MIUI / 2 GB RAM) killed the
      service while YouTube was in front and it never came back.
      FIX: notification channel raised to IMPORTANCE_HIGH -- a visible
      banner while Kids mode is on, and the system treats the service
      as user-visible so it is much less likely to kill it. The
      version stays 1.0 (versionCode 181, so it updates over 180).
    v4.50 Timer STILL failing (2026-11-23, on 1.0 code 181): the
      "Kids time" banner IS visible on the Kids home, but GONE once
      YouTube opens -- the service starts fine, then the box kills the
      WHOLE app process to free memory for YouTube, and the service
      dies with it because it runs in the same process as MainActivity.
      The high-priority notification did not save it. FIX: run the
      service in its own process (android:process=":timer") so it
      survives even when the home screen's process is killed for
      memory. Because a separate process cannot see MainActivity's
      cached SharedPreferences, timeUp() now reads the prefs XML file
      directly from disk every tick, so a timer reset is seen at once.
      Built as UAT 4.50 (versionCode 182); production stays 1.0.
    v4.50 (2026-11-24) :timer process broke bringHome: the service
      survived (banner stayed on the Kids home) but YouTube never
      closed when time was up, and even after closing YouTube by hand
      the sound kept playing (Picture-in-Picture). The :timer process
      cannot start MainActivity -- startActivity() from a background
      process is BLOCKED on Android 14 (targetSdk 34) by Background
      Activity Launch restrictions.
    v4.51 Background Activity Launch fix (2026-11-24): bringHome() is
      now multi-pronged so at least one path pulls her out:
      (1) stopPlayback() steals audio focus (AUDIOFOCUS_GAIN) and
      dispatches MEDIA_PAUSE + MEDIA_STOP so the player stops at once
      (focus is abandoned again when time is no longer up, and on
      destroy, so it never blocks audio later);
      (2) a full-screen TYPE_APPLICATION_OVERLAY "Time's up" blocker
      covers whatever app is open (needs SYSTEM_ALERT_WINDOW /
      "Display over other apps" -- Settings > Kids > Time-up app
      blocker, or appops set com.bacem.launcher SYSTEM_ALERT_WINDOW
      allow), which also exempts the app from the Android 14 BAL
      restriction;
      (3) MainActivity is launched via startActivity AND a full-screen
      alarm notification PendingIntent (USE_FULL_SCREEN_INTENT) plus
      the PowerService GO_HOME broadcast if enabled;
      (4) killBackgroundProcesses() on the video-app packages once
      they are out of the foreground (it cannot kill a foreground
      app, so it only cleans up after the home is already in front).
      Also: timer prefs writes changed from apply() to commit() so
      the :timer process reading the prefs XML file sees a reset at
      once instead of a stale async write. PiP: turn off
      Picture-in-Picture for YouTube (Settings > Apps > YouTube) so
      it cannot keep playing in a floating window after being closed.
      TESTED on box 2026-11-24: "It seems working." Became PRODUCTION
      1.0 (code 183) the same day on his go.
    v4.52 Time's up bypass (2026-11-25, on 1.0 code 183): when time
      was up, tapping Cancel (on the grown-up PIN prompt) or pressing
      Back let her land back on a usable Kids home and open YouTube
      again. FIX: (1) onBackPressed() now swallows Back entirely
      while Kids time is up (new kidsTimeUp() helper: Kids mode on
      AND bedtime passed or daily limit reached); only the grown-up
      PIN gets her out. (2) The Time's up dialog is self-healing: an
      OnDismissListener brings it straight back if it is ever
      dismissed while time is still up and Kids mode is on, so no
      dismissal path leaves her on a usable home. (3) The overlay
      blocker now swallows Back via an OnKeyListener on its root, so
      Back cannot dismiss it back into YouTube; only its button
      (which goes home, where the Time's up dialog holds) gets her
      out. Built as UAT 4.52 (versionCode 184); production stays 1.0.
    ------------------------------------------------------------------ */
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;

public class KidsTimerService extends Service {
    static final String CHANNEL_ID = "kids_timer";
    static final String ALARM_CHANNEL_ID = "kids_timer_alarm";
    static final int NOTIF_ID = 77;
    static final int ALARM_NOTIF_ID = 78;
    static final long CHECK_MS = 30000L;
    public static final String ACTION_HIDE_OVERLAY = "com.bacem.launcher.HIDE_TIMEUP_OVERLAY";
    public static final String ACTION_HOME_PAUSED = "com.bacem.launcher.HOME_PAUSED";

    private static final String[] VIDEO_PKGS = {
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "io.gh.reisxd.tizentube.cobalt",
        "org.smarttube.stable",
        "org.smarttube.beta",
        "com.netflix.ninja",
        "com.amazon.avod",
        "com.disney.disneyplus",
        "com.tubitv",
        "com.tvtv.tubi.app",
        "tv.pluto.android",
        "xyz.netfly",
        "org.drive_hunter",
        "ver3.ycntivi.off",
        "com.yacene.tvplus",
        "com.t4w.ostora516",
        "local.tvmorph.tv",
        "com.stremio.one",
        "org.videolan.vlc",
        "com.playeregy.egyplayerapp",
        "com.linkinapp.tunisielivetv",
        "ca.cbc.android.cbctv"
    };

    Handler handler;
    Runnable tickR;
    View overlayView;
    boolean homeVisible = false;
    AudioManager.OnAudioFocusChangeListener focusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int focusChange) { /* no-op */ }
    };

    @Override public void onCreate() {
        super.onCreate();
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Kids time",
                            NotificationManager.IMPORTANCE_HIGH);
                    ch.setDescription("Kids timer");
                    nm.createNotificationChannel(ch);

                    NotificationChannel alarmCh = new NotificationChannel(ALARM_CHANNEL_ID, "Kids time up",
                            NotificationManager.IMPORTANCE_HIGH);
                    alarmCh.setDescription("Alerts when kids screen time ends");
                    nm.createNotificationChannel(alarmCh);
                }
            }
        } catch (Exception e) { /* ignore */ }
        handler = new Handler(Looper.getMainLooper());
        tickR = new Runnable() {
            @Override public void run() {
                try {
                    TimerPrefs p = readTimerPrefs();
                    if (isTimeUp(p)) {
                        bringHome(p);
                    } else {
                        removeOverlay();
                        cancelAlarmNotification();
                        abandonAudioFocus();
                    }
                } catch (Exception e) { /* ignore */ }
                try { handler.postDelayed(tickR, CHECK_MS); } catch (Exception e) { /* ignore */ }
            }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_HIDE_OVERLAY.equals(action)) {
            homeVisible = true;
            removeOverlay();
            cancelAlarmNotification();
            return START_STICKY;
        }
        if (ACTION_HOME_PAUSED.equals(action)) {
            homeVisible = false;
            return START_STICKY;
        }
        try {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 44, home,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                n = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("Kids time")
                        .setContentText("Kids timer is running")
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentIntent(pi)
                        .build();
            } else {
                n = new Notification.Builder(this)
                        .setContentTitle("Kids time")
                        .setContentText("Kids timer is running")
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentIntent(pi)
                        .build();
            }
            startForeground(NOTIF_ID, n);
        } catch (Exception e) { /* ignore */ }
        try {
            handler.removeCallbacks(tickR);
            handler.postDelayed(tickR, CHECK_MS);
        } catch (Exception e) { /* ignore */ }
        return START_STICKY;
    }

    // v4.50: read the timer settings fresh from the prefs XML file on
    // disk, NOT from the cached SharedPreferences. This service runs in
    // its own process (:timer), and a separate process cannot see the
    // prefs MainActivity (main process) writes -- the cached copy in
    // this process would go stale, so a timer reset would be missed and
    // the service would keep closing YouTube after a reset. Reading the
    // file directly sees every write at once. Any read failure returns
    // defaults (kidsMode false), so the timer just does nothing.
    static class TimerPrefs {
        boolean kidsMode;
        String bedtime = "";
        int limitMin;
        String usedDate = "";
        int usedMin;
        long sessionStart;
        String kidsApps = "";
    }

    TimerPrefs readTimerPrefs() {
        TimerPrefs r = new TimerPrefs();
        java.io.File f = null;
        try {
            f = new java.io.File(getApplicationInfo().dataDir, "shared_prefs/ui.xml");
        } catch (Exception e) { return r; }
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser();
            p.setInput(in, "UTF-8");
            int ev;
            while ((ev = p.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
                String tag = p.getName();
                String name = p.getAttributeValue(null, "name");
                if (name == null) continue;
                if (tag.equals("string")) {
                    String text = "";
                    try { text = p.nextText(); } catch (Exception e) { text = ""; }
                    if (text == null) text = "";
                    if (name.equals("kids_bedtime")) r.bedtime = text;
                    else if (name.equals("kids_used_date")) r.usedDate = text;
                    else if (name.equals("kids_apps")) r.kidsApps = text;
                    continue;
                }
                String v = p.getAttributeValue(null, "value");
                if (v == null) continue;
                if (tag.equals("boolean") && name.equals("kids_mode")) r.kidsMode = Boolean.parseBoolean(v);
                else if (tag.equals("int") && name.equals("kids_limit_min")) r.limitMin = Integer.parseInt(v);
                else if (tag.equals("int") && name.equals("kids_used_min")) r.usedMin = Integer.parseInt(v);
                else if (tag.equals("long") && name.equals("kids_session_start")) r.sessionStart = Long.parseLong(v);
            }
        } catch (Exception e) { /* keep defaults */ }
        finally {
            try { if (in != null) in.close(); } catch (Exception e) { /* ignore */ }
        }
        return r;
    }

    boolean isTimeUp(TimerPrefs p) {
        try {
            if (!p.kidsMode) return false;
            String bt = p.bedtime;
            if (bt.length() > 0) {
                String[] parts = bt.split(":");
                if (parts.length == 2) {
                    int bed = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
                    if (now >= bed || now < 420) return true;
                }
            }
            int limit = p.limitMin;
            if (limit > 0) {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                int used = 0;
                if (today.equals(p.usedDate)) used = p.usedMin;
                long start = p.sessionStart;
                if (start > 0) used += Logic.kidsElapsedMin(start, System.currentTimeMillis());
                if (used >= limit) return true;
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    boolean timeUp() {
        return isTimeUp(readTimerPrefs());
    }

    void stopPlayback() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP));
            }
        } catch (Exception e) { /* ignore */ }
    }

    // v4.51 fix: stopPlayback() steals audio focus so the player stops,
    // but the service must give it back -- otherwise it holds focus
    // forever and no app can play sound again after time is up (even
    // after a timer reset or Kids mode off). Called whenever time is
    // no longer up and when the service stops.
    void abandonAudioFocus() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.abandonAudioFocus(focusListener);
        } catch (Exception e) { /* ignore */ }
    }

    void killKidsApps(TimerPrefs p) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            Set<String> targets = new HashSet<String>();
            for (String pkg : VIDEO_PKGS) targets.add(pkg);
            if (p != null && p.kidsApps != null && p.kidsApps.length() > 0) {
                String[] parts = p.kidsApps.split(",");
                for (String s : parts) {
                    String pkg = s.trim();
                    if (pkg.length() > 0) targets.add(pkg);
                }
            }
            String self = getPackageName();
            for (String pkg : targets) {
                if (self.equals(pkg)) continue;
                try { am.killBackgroundProcesses(pkg); } catch (Exception e) { /* ignore */ }
            }
        } catch (Exception e) { /* ignore */ }
    }

    void showOverlayIfNeeded() {
        if (homeVisible || overlayView != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(0xF010141F);
            root.setPadding(64, 48, 64, 48);
            // v4.52: swallow Back on the blocker so it cannot be
            // dismissed back into YouTube; only the button (which goes
            // home, where the Time's up dialog holds) gets her out.
            root.setFocusable(true);
            root.setFocusableInTouchMode(true);
            root.setOnKeyListener(new View.OnKeyListener() {
                @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) return true;
                    return false;
                }
            });

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(56, 44, 56, 44);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(0xFF1B2233);
            cardBg.setCornerRadius(24f);
            cardBg.setStroke(2, 0xFFEC407A);
            card.setBackground(cardBg);

            TextView title = new TextView(this);
            title.setText("Time's up");
            title.setTextColor(Color.WHITE);
            title.setTextSize(34f);
            title.setTypeface(null, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            card.addView(title);

            TextView msg = new TextView(this);
            msg.setText("Ask a grown-up to continue.");
            msg.setTextColor(0xFFD0D8E8);
            msg.setTextSize(20f);
            msg.setGravity(Gravity.CENTER);
            msg.setPadding(0, 16, 0, 28);
            card.addView(msg);

            Button btn = new Button(this);
            btn.setText("Go to Cemba Home");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(18f);
            btn.setAllCaps(false);
            btn.setPadding(36, 18, 36, 18);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(0xFFEC407A);
            btnBg.setCornerRadius(16f);
            btn.setBackground(btnBg);
            btn.setFocusable(true);
            btn.setFocusableInTouchMode(true);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    launchMainActivity();
                }
            });
            card.addView(btn);
            root.addView(card);

            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            wm.addView(root, lp);
            overlayView = root;
            btn.requestFocus();
        } catch (Throwable t) {
            overlayView = null;
        }
    }

    void removeOverlay() {
        if (overlayView == null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.removeViewImmediate(overlayView);
        } catch (Throwable t) { /* ignore */ }
        overlayView = null;
    }

    void sendFullScreenAlarmNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 45, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, ALARM_CHANNEL_ID)
                    : new Notification.Builder(this);
            b.setContentTitle("Time's up")
             .setContentText("Kids screen time has ended")
             .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
             .setCategory(Notification.CATEGORY_ALARM)
             .setContentIntent(pi)
             .setFullScreenIntent(pi, true)
             .setAutoCancel(true);
            nm.notify(ALARM_NOTIF_ID, b.build());
        } catch (Exception e) { /* ignore */ }
    }

    void cancelAlarmNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ALARM_NOTIF_ID);
        } catch (Exception e) { /* ignore */ }
    }

    void launchMainActivity() {
        try { sendBroadcast(new Intent(PowerService.ACTION_GO_HOME)); } catch (Exception e) { /* ignore */ }
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception e) { /* ignore */ }
    }

    void bringHome(TimerPrefs p) {
        stopPlayback();
        showOverlayIfNeeded();
        launchMainActivity();
        if (!homeVisible) sendFullScreenAlarmNotification();
        killKidsApps(p);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                killKidsApps(readTimerPrefs());
            }
        }, 1200L);
    }

    void bringHome() {
        bringHome(readTimerPrefs());
    }

    @Override public void onDestroy() {
        try { handler.removeCallbacks(tickR); } catch (Exception e) { /* ignore */ }
        removeOverlay();
        cancelAlarmNotification();
        abandonAudioFocus();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
