package com.bacem.launcher;

/** Publishes Cemba Home's own cards (Watch Later, History, Library, live weather) into the
 *  WatchNext store so they appear in Google TV Home's Continue watching row. Also tries to
 *  clean up the retired YT Cards app's rows. Runs on app open, on boot, and on a 30-min alarm. */
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;
public class Publish {
    static final String TAG = "CembaHome";
    static class Card {
        final String id, title, url, art;
        Card(String i, String t, String u, String a) { id = i; title = t; url = u; art = a; }
    }
    static final Card[] CARDS = {
        new Card("watchlater", "Watch Later", "https://www.youtube.com/playlist?list=WL", "watchlater"),
        new Card("history", "History", "https://www.youtube.com/feed/history", "history"),
        new Card("library", "Library", "https://www.youtube.com/feed/library", "library"),
    };
    // v3.88: X96mini (Android 7, phone Android, not Android TV) has no
    // TvContract$WatchNextPrograms class at all -- referencing it throws
    // NoClassDefFoundError (an Error, not an Exception, so the old
    // catch (Exception) did not catch it) and crashed the app on open.
    // Check first, with reflection, before touching TvContract at all.
    static boolean tvAvailable() {
        try {
            Class.forName("android.media.tv.TvContract$WatchNextPrograms");
            Class.forName("android.media.tv.TvContract$PreviewPrograms");
            return true;
        } catch (Throwable t) { return false; }
    }

    public static int ensurePublished(Context ctx) {
        if (!tvAvailable()) return 0;
        int ok = 0;
        try {
            ContentResolver cr = ctx.getContentResolver();
            Uri uri = TvContract.WatchNextPrograms.CONTENT_URI;
            try {
                int del = cr.delete(uri,
                    TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID + " IN (?,?,?,?,?)",
                    new String[] { "cemba_subs", "cemba_watchlater", "cemba_history", "cemba_library", "cemba_weather" });
                Log.d(TAG, "deleted old=" + del);
                try {
                    int delOld = cr.delete(uri,
                        TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID + " IN (?,?,?,?,?)",
                        new String[] { "ytcards_subs", "ytcards_watchlater", "ytcards_history", "ytcards_library", "ytcards_weather" });
                    Log.d(TAG, "deleted legacy=" + delOld);
                } catch (Exception e) { Log.d(TAG, "legacy delete failed: " + e); }
            } catch (Exception e) { Log.d(TAG, "delete failed: " + e); }
            long now = System.currentTimeMillis();
            for (Card c : CARDS) {
                Intent ri = new Intent(Intent.ACTION_VIEW);
                ri.setComponent(new ComponentName(ctx.getPackageName(),
                        ctx.getPackageName() + ".RedirectActivity"));
                ri.putExtra("target", c.url);
                String artUri = "content://" + ctx.getPackageName() + ".art/" + c.art;
                ContentValues v = new ContentValues();
                v.put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_MOVIE);
                v.put(TvContract.PreviewPrograms.COLUMN_TITLE, c.title);
                v.put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, "Open in TizenTube Cobalt");
                v.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, artUri);
                v.put(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI, artUri);
                v.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                        TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
                v.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, ri.toUri(Intent.URI_INTENT_SCHEME));
                v.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, "cemba_" + c.id);
                v.put(TvContract.PreviewPrograms.COLUMN_INTERACTION_TYPE,
                        TvContract.PreviewPrograms.INTERACTION_TYPE_VIEWS);
                v.put(TvContract.PreviewPrograms.COLUMN_INTERACTION_COUNT, 50);
                v.put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                        TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
                v.put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, now);
                v.put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 45000);
                v.put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, 100000);
                try {
                    Uri row = cr.insert(uri, v);
                    Log.d(TAG, "insert " + c.id + " -> " + row);
                    if (row != null) ok++;
                } catch (Exception e) { Log.d(TAG, "insert " + c.id + " failed: " + e); }
            }
            // weather card (live Kitchener weather, art generated on the box)
            try {
                Weather w = Weather.fetch();
                if (w != null) {
                    Weather.refreshArt(ctx, w);
                    Intent wIntent = new Intent();
                    wIntent.setComponent(new ComponentName(ctx.getPackageName(),
                            ctx.getPackageName() + ".WeatherActivity"));
                    ContentValues v = new ContentValues();
                    v.put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_MOVIE);
                    v.put(TvContract.PreviewPrograms.COLUMN_TITLE, "Kitchener " + w.temp + "\u00B0 " + w.label);
                    v.put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, "H " + w.hi + "\u00B0  L " + w.lo + "\u00B0 - tap for forecast");
                    String artUri = "content://" + ctx.getPackageName() + ".art/weather?v=" + (System.currentTimeMillis() / 1800000L);
                    v.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, artUri);
                    v.put(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI, artUri);
                    v.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
                    v.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, wIntent.toUri(Intent.URI_INTENT_SCHEME));
                    v.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, "cemba_weather");
                    v.put(TvContract.PreviewPrograms.COLUMN_INTERACTION_TYPE, TvContract.PreviewPrograms.INTERACTION_TYPE_VIEWS);
                    v.put(TvContract.PreviewPrograms.COLUMN_INTERACTION_COUNT, 60);
                    v.put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
                    v.put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, now + 60000);
                    v.put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 45000);
                    v.put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, 100000);
                    Uri row = cr.insert(uri, v);
                    Log.d(TAG, "insert weather -> " + row);
                    if (row != null) ok++;
                }
            } catch (Throwable t) { Log.d(TAG, "weather publish failed: " + t); }
        } catch (Throwable t) { Log.d(TAG, "publish failed: " + t); }
        Log.d(TAG, "published ok=" + ok);
        return ok;
    }
}
