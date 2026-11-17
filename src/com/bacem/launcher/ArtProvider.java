package com.bacem.launcher;

/** Serves the bundled card posters (and the generated weather art) to the TV provider
 *  so the published cards can show pictures. */
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
public class ArtProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        // v4.31: serve the downloaded update APK to the system installer.
        // The APK is downloaded to the app's files dir by downloadAndInstall
        // and opened as content://com.bacem.launcher.art/update.apk.
        if ("update.apk".equals(name)) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                final ParcelFileDescriptor.AutoCloseOutputStream out =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                final java.io.File f = new java.io.File(getContext().getFilesDir(), "update.apk");
                new Thread(() -> {
                    try {
                        java.io.FileInputStream in = new java.io.FileInputStream(f);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                        in.close();
                    } catch (Exception e) { /* ignore */ }
                    try { out.close(); } catch (Exception e) { /* ignore */ }
                }).start();
                return pipe[0];
            } catch (IOException e) {
                throw new FileNotFoundException(String.valueOf(e.getMessage()));
            }
        }
        if ("weather".equals(name)) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                final ParcelFileDescriptor.AutoCloseOutputStream out =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                final java.io.File f = Weather.pngFile(getContext());
                new Thread(() -> {
                    try {
                        java.io.FileInputStream in = new java.io.FileInputStream(f);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                        in.close();
                    } catch (Exception e) { /* ignore */ }
                    try { out.close(); } catch (Exception e) { /* ignore */ }
                }).start();
                return pipe[0];
            } catch (IOException e) {
                throw new FileNotFoundException(String.valueOf(e.getMessage()));
            }
        }
        final int resId;
        if ("watchlater".equals(name)) resId = R.drawable.card_watchlater;
        else if ("history".equals(name)) resId = R.drawable.card_history;
        else if ("library".equals(name)) resId = R.drawable.card_library;
        else throw new FileNotFoundException("unknown art: " + uri);
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor.AutoCloseOutputStream out =
                    new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
            final Bitmap bmp = BitmapFactory.decodeResource(getContext().getResources(), resId);
            new Thread(() -> {
                try { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); out.flush(); }
                catch (Exception e) { /* ignore */ }
                try { out.close(); } catch (Exception e) { /* ignore */ }
            }).start();
            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException(String.valueOf(e.getMessage()));
        }
    }
    @Override public String getType(Uri uri) {
        if ("update.apk".equals(uri.getLastPathSegment())) return "application/vnd.android.package-archive";
        return "image/png";
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
