package com.bacem.launcher;

/** v4.55: Kids timer foreground service. The AlarmManager path (alarm
    clock, activity PendingIntent) works on the N5 Max but never fires on
    the Xiaomi box -- MIUI/Android TV 14 defers or drops the alarm, and
    there is no "Battery unrestricted" setting on Android TV to fix it
    with. A foreground service is the reliable path on that box: it stays
    alive while Jannah watches YouTube, checks every 30 seconds, and
    when the daily limit or bedtime is reached it brings MainActivity
    forward (a foreground service may start an activity) so onResume
    shows Time's up. Started when Kids mode is entered, stopped when it
    is left. Shows a small "Kids time" notification while running. */
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
                        NotificationManager.IMPORTANCE_LOW);
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
                        bringHome();
                        stopSelf();
                        return;
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
