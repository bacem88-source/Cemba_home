package com.bacem.launcher;

/** Invisible trampoline: the published YouTube shortcut cards fire this, and it opens
 *  TizenTube Cobalt at the right page (with the .ab component as fallback). */
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
public class RedirectActivity extends Activity {
    private static final String[] PKGS = {
        "io.gh.reisxd.tizentube.cobalt", "io.gh.reisxd.tizentube.cobalt.ab" };
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Thread(() -> Publish.ensurePublished(getApplicationContext())).start();
        String url = getIntent().getStringExtra("target");
        if (url == null || url.isEmpty()) url = "https://www.youtube.com";
        boolean launched = false;
        for (String p : PKGS) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.setComponent(new ComponentName(p, "dev.cobalt.app.MainActivity"));
                i.putExtra("url", url);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                launched = true;
                break;
            } catch (Exception e) { /* next */ }
        }
        if (!launched) {
            for (String p : PKGS) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.setPackage(p);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    launched = true;
                    break;
                } catch (Exception e) { /* next */ }
            }
        }
        if (!launched) {
            Toast.makeText(this, "TizenTube Cobalt not installed", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
