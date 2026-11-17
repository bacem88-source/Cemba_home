package com.bacem.launcher;

/**
 * Poster loader for card rows: memory cache, loads off the UI thread.
 *
 * Two guards matter on a 2 GB box: the cache is size-based (4 MB, not a
 * count of entries -- a count let 64 full-size posters pile up), and decodes
 * are capped at 1024 px wide (cards are ~650 px on this box, so nothing
 * visible is lost, but a 1920 px source no longer decodes to an 8 MB bitmap).
 */
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class Img {
    static final int MAX_DECODE_W = 1024;
    // v3.93: poster cache 8 MB -> 4 MB (the box has only 0.5 GB free).
    static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024) {
        @Override protected int sizeOf(String k, Bitmap v) {
            return v.getRowBytes() * v.getHeight() / 1024;
        }
    };
    static final ExecutorService EX = Executors.newFixedThreadPool(3);
    // v3.89: heap cap in MB for this device (X96mini: 192). Default 256.
    static boolean lowHeap(Context ctx) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            return am != null && am.getMemoryClass() <= 192;
        } catch (Throwable t) { return false; }
    }
    public static void load(final Context ctx, final ImageView iv, final String uri) {
        if (uri == null || uri.length() == 0) return;
        iv.setTag(uri);
        Bitmap b = CACHE.get(uri);
        if (b != null) { iv.setImageBitmap(b); return; }
        EX.execute(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                try {
                    byte[] bytes = null;
                    InputStream in;
                    if (uri.startsWith("http")) {
                        HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
                        c.setConnectTimeout(6000);
                        c.setReadTimeout(6000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0");
                        in = c.getInputStream();
                    } else {
                        in = ctx.getContentResolver().openInputStream(Uri.parse(uri));
                    }
                    if (in != null) {
                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int n;
                        while ((n = in.read(chunk)) >= 0) buf.write(chunk, 0, n);
                        in.close();
                        bytes = buf.toByteArray();
                    }
                    if (bytes != null && bytes.length > 0) {
                        // v3.89: X96mini heap is capped at 192 MB -- a big
                        // poster decode threw OutOfMemoryError (an Error, so
                        // the old catch (Exception) did not catch it) and
                        // killed the app at Img.java:67. On a small-heap box
                        // decode smaller and in RGB_565 (half the bytes).
                        boolean lowMem = lowHeap(ctx);
                        int maxW = lowMem ? 512 : MAX_DECODE_W;
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        int sample = 1;
                        while (bounds.outWidth / sample > maxW) sample *= 2;
                        opts.inSampleSize = sample;
                        if (lowMem) opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                    }
                } catch (Throwable t) {
                    // OutOfMemoryError included: never let one bad poster
                    // kill the app -- free the cache and skip this poster.
                    try { CACHE.evictAll(); } catch (Throwable t2) { /* ignore */ }
                    try { System.gc(); } catch (Throwable t2) { /* ignore */ }
                    bmp = null;
                }
                if (bmp != null) {
                    CACHE.put(uri, bmp);
                    final Bitmap f = bmp;
                    iv.post(new Runnable() {
                        @Override public void run() {
                            if (uri.equals(iv.getTag())) iv.setImageBitmap(f);
                        }
                    });
                }
            }
        });
    }
}
