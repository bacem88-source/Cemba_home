package com.bacem.launcher;

/** v3.87: Kids timer alarm. The Handler tick in MainActivity dies when the
    activity is destroyed in the background (e.g. while Jannah watches
    YouTube Kids), so the daily limit never fired. This exact alarm fires
    even if MainActivity is dead: it brings the launcher forward, and
    onResume -> kidsCheck (time-based) shows Time's up. */
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
            Intent i = new Intent(ctx, KidsTimerReceiver.class);
            i.setAction(ACTION);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 43, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long when = System.currentTimeMillis() + Math.max(0L, delayMillis);
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
            Intent i = new Intent(ctx, KidsTimerReceiver.class);
            i.setAction(ACTION);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 43, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Exception e) { /* ignore */ }
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        // v4.11: only fire in Kids mode. The alarm can outlive a profile
        // switch -- he sets the limit in Kids, then opens Family and
        // Cobalt, and the alarm fires and pulls home, closing Cobalt for
        // no reason (his report 2026-11-14 18:20). If we are in Family
        // mode now, do nothing: no GO_HOME, no MainActivity start.
        try {
            boolean kids = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
                    .getBoolean("kids_mode", false);
            if (!kids) return;
        } catch (Exception e) { return; }
        // v3.95: pull her home first via Cemba Power (GLOBAL_ACTION_HOME) --
        // the system Home press, which works even while YouTube Kids is in
        // front. Starting the launcher activity alone did not bring Cemba
        // forward (his report: timer worked, YouTube Kids stayed open, no
        // return to the profile home). The broadcast is a no-op when Cemba
        // Power is off, so it is safe to send both.
        try {
            ctx.sendBroadcast(new Intent(PowerService.ACTION_GO_HOME));
        } catch (Exception e) { /* ignore */ }
        // v4.45: no Cemba Power fallback -- simulate a Home press with the
        // system HOME intent. Cemba is the default launcher, so this brings
        // it forward even while a video app is in front, same as the Home
        // button. No new permission needed. If the system blocks the plain
        // activity start below, this HOME intent is the one that pulls her
        // back to the Kids home where onResume shows Time's up.
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
