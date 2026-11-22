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
    ------------------------------------------------------------------ */
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class KidsTimerService extends Service {
    static final String CHANNEL_ID = "kids_timer";
    static final int NOTIF_ID = 77;
    static final long CHECK_MS = 30000L;

    Handler handler;
    Runnable tickR;

    @Override public void onCreate() {
        super.onCreate();
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Kids time",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Kids timer");
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
            }
        } catch (Exception e) { /* ignore */ }
        handler = new Handler(Looper.getMainLooper());
        tickR = new Runnable() {
            @Override public void run() {
                try {
                    if (timeUp()) {
                        // v4.58: bring the home forward, but DO NOT stop
                        // the service. The first version stopped itself
                        // here, and that broke the second YouTube session:
                        // after the first "Time's up" the service was dead,
                        // the home was already in front so onResume never
                        // fired to restart it, and when Jannah opened
                        // YouTube again nothing was watching -- the timer
                        // never closed it. The service must keep watching
                        // while Kids mode is on, so every time she goes
                        // back into YouTube it pulls her out again within
                        // 30 s. It only stops when Kids mode is turned off
                        // (setKidsMode(false) stops it). bringHome is a
                        // no-op when MainActivity is already on top
                        // (SINGLE_TOP), so calling it every 30 s while
                        // time is up is harmless.
                        bringHome();
                    }
                } catch (Exception e) { /* ignore */ }
                try { handler.postDelayed(tickR, CHECK_MS); } catch (Exception e) { /* ignore */ }
            }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
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
        try { handler.removeCallbacks(tickR); handler.postDelayed(tickR, CHECK_MS); } catch (Exception e) { /* ignore */ }
        return START_STICKY;
    }

    boolean timeUp() {
        try {
            SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
            if (!p.getBoolean("kids_mode", false)) return false;
            // Bedtime.
            String bt = p.getString("kids_bedtime", "");
            if (bt.length() > 0) {
                String[] parts = bt.split(":");
                if (parts.length == 2) {
                    int bed = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
                    if (now >= bed || now < 420) return true;
                }
            }
            // Daily limit.
            int limit = p.getInt("kids_limit_min", 0);
            if (limit > 0) {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                int used = 0;
                if (today.equals(p.getString("kids_used_date", ""))) used = p.getInt("kids_used_min", 0);
                long start = p.getLong("kids_session_start", 0);
                if (start > 0) used += Logic.kidsElapsedMin(start, System.currentTimeMillis());
                if (used >= limit) return true;
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    void bringHome() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception e) { /* ignore */ }
    }

    @Override public void onDestroy() {
        try { handler.removeCallbacks(tickR); } catch (Exception e) { /* ignore */ }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
