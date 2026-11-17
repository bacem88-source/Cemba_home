package com.bacem.launcher;

/** 30-min tick: refresh the weather art and re-publish the cards. */
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class AlarmReceiver extends BroadcastReceiver {
    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, AlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 42, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 1800000L,
                    1800000L, pi);
        } catch (Exception e) { /* ignore */ }
    }
    @Override public void onReceive(Context ctx, Intent intent) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> Publish.ensurePublished(app)).start();
    }
}
