package com.bacem.launcher;

/** On boot: re-publish the cards and re-arm the 30-min refresh alarm (Xiaomi may drop alarms). */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        final Context app = ctx.getApplicationContext();
        AlarmReceiver.schedule(app);
        new Thread(() -> Publish.ensurePublished(app)).start();
    }
}
