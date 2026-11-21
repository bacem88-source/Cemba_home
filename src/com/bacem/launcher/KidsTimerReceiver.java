package com.bacem.launcher;

/** v3.87: Kids timer alarm. The Handler tick in MainActivity dies when the
    activity is destroyed in the background (e.g. while Jannah watches
    YouTube Kids), so the daily limit never fired. This exact alarm fires
    even if MainActivity is dead: it brings the launcher forward, and
    onResume -> kidsCheck (time-based) shows Time's up.
    v4.51: the alarm used to fire a broadcast PendingIntent, and the
    receiver then tried to start the Home / MainActivity from the
    background. That start is blocked on Android 10+ (background activity
    start restriction), and it failed on every box -- the timer fired,
    nothing came forward, YouTube stayed open. The fix: the alarm itself
    now carries an ACTIVITY PendingIntent for MainActivity. An alarm's
    activity PendingIntent is delivered by the system, so the start is
    allowed even from the background and even in Doze. MainActivity's
    onResume runs kidsCheck and shows Time's up. The receiver is kept
    for the GO_HOME broadcast path and for manual triggers, but the alarm
    no longer goes through it.
    v4.54: setExactAndAllowWhileIdle STILL did not close YouTube on any
    box. On Android 12+ it needs SCHEDULE_EXACT_ALARM, which the box
    never granted, so it fell back to the inexact setAndAllowWhileIdle
    and the alarm was deferred in Doze and never pulled the home forward
    in time. The fix: use setAlarmClock. An alarm-clock alarm is exempt
    from the exact-alarm permission, fires exactly even in Doze, and its
    activity PendingIntent is delivered by the system, so it brings
    MainActivity forward and onResume shows Time's up. It shows a small
    alarm-clock icon in the status bar while armed -- harmless on a TV.
    VERDICT (2026-11-20): setAlarmClock WORKS on the N5 Max (Android 9)
    but does NOT fire on the Xiaomi (Android TV 14 / MIUI defers or
    drops it). KEPT as a backup -- scheduleKidsAlarm still arms it, and
    the foreground service (KidsTimerService, v4.55, fixed v4.57) is now
    the primary timer on the Xiaomi. Full approach history lives in
    KidsTimerService.java. */
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class KidsTimerReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.bacem.launcher.KIDS_TIME_UP";

    public static void schedule(Context ctx, long delayMillis) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = activityPi(ctx);
            if (pi == null) return;
            long when = System.currentTimeMillis() + Math.max(0L, delayMillis);
            // v4.54: setAlarmClock is the reliable path -- it is exempt from
            // the SCHEDULE_EXACT_ALARM permission, fires exactly even in
            // Doze, and its activity PendingIntent brings MainActivity
            // forward. Fall back to setExactAndAllowWhileIdle, then to the
            // inexact setAndAllowWhileIdle, if setAlarmClock throws.
            try {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(when, pi);
                am.setAlarmClock(info, pi);
                return;
            } catch (Exception e) { /* fall through */ }
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } catch (Exception e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (Exception e) { /* ignore */ }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = activityPi(ctx);
            if (pi == null) return;
            am.cancel(pi);
            pi.cancel();
        } catch (Exception e) { /* ignore */ }
    }

    static PendingIntent activityPi(Context ctx) {
        try {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return PendingIntent.getActivity(ctx, 43, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } catch (Exception e) { return null; }
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        // Kept for manual / broadcast triggers. The alarm itself now
        // launches MainActivity directly (see schedule), so this path
        // is a fallback only.
        try {
            boolean kids = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
                    .getBoolean("kids_mode", false);
            if (!kids) return;
        } catch (Exception e) { return; }
        try {
            ctx.sendBroadcast(new Intent(PowerService.ACTION_GO_HOME));
        } catch (Exception e) { /* ignore */ }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(home);
        } catch (Exception e) { /* ignore */ }
        try {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
        } catch (Exception e) { /* ignore */ }
    }
}
