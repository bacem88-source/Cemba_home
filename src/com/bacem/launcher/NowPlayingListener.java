package com.bacem.launcher;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** v3.55: reads the media notification of the app that is playing (SmartTube,
 *  YouTube, Spotify) and broadcasts its title so the Now Playing bar can show
 *  the real video / song title instead of "playing in background". The user
 *  grants Notification access once in Settings; without it the bar keeps the
 *  old app-name text and nothing else changes. */
public class NowPlayingListener extends NotificationListenerService {
    public static final String ACTION_TITLE = "com.bacem.launcher.NOW_PLAYING_TITLE";
    public static final String EXTRA_TITLE = "title";

    /** v3.57: when the service (re)connects -- e.g. right after the user grants
     *  Notification access, or after the app is updated -- a song that is
     *  already playing has its notification posted already, so
     *  onNotificationPosted never fires for it. Catch up on the notifications
     *  that are already up, so the bar shows the title without waiting for
     *  the next song. */
    @Override public void onListenerConnected() {
        try {
            StatusBarNotification[] act = getActiveNotifications();
            if (act != null) for (StatusBarNotification sbn : act) postTitle(sbn);
        } catch (Exception e) {}
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        postTitle(sbn);
    }

    void postTitle(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!Logic.isNowPlayingApp(pkg)) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle ex = n.extras;
        if (ex == null) return;
        // v3.57: the title is usually EXTRA_TITLE (MediaStyle), but some
        // players put it in the content text instead -- try both.
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        if (t == null || t.toString().trim().length() == 0) t = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (t == null || t.toString().trim().length() == 0) t = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String title = Logic.nowPlayingTitle(Logic.nowPlayingAppLabel(pkg), t == null ? "" : t.toString());
        if (title.length() == 0) return;
        Intent i = new Intent(ACTION_TITLE);
        i.putExtra(EXTRA_TITLE, title);
        sendBroadcast(i);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!Logic.isNowPlayingApp(pkg)) return;
        // The media notification went away -- fall back to the app name.
        Intent i = new Intent(ACTION_TITLE);
        i.putExtra(EXTRA_TITLE, Logic.nowPlayingTitle(Logic.nowPlayingAppLabel(pkg), ""));
        sendBroadcast(i);
    }
}
