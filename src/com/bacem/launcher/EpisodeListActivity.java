package com.bacem.launcher;

/** Episode list for a series season (v4.36): opened from the Live TV
 *  guide's Series category. Shows every episode of the season as a
 *  vertical list inside Cemba. Tapping an episode opens it in TizenTube
 *  Cobalt (YouTube videos cannot play in Cemba's own HLS player).
 *  Pressing Back in Cobalt returns here, to the episode list.
 *  DPAD only: Up/Down moves, OK plays, Back returns to the guide. */
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class EpisodeListActivity extends Activity {
    static final int BG = 0xFF0F2A44;
    static final int ACCENT = 0xFFC62828;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String season = getIntent().getStringExtra("season");
        String title = getIntent().getStringExtra("title");
        if (season == null) season = "";
        if (title == null) title = "Choufli Hal";
        Series.Ep[] eps = Series.episodesFor(season);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(48, 32, 48, 32);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextColor(Color.WHITE);
        head.setTextSize(30);
        head.setGravity(Gravity.LEFT);
        root.addView(head);

        TextView sub = new TextView(this);
        sub.setText(eps.length + " episodes - pick one to play in Cobalt");
        sub.setTextColor(0xFFB0BEC5);
        sub.setTextSize(18);
        sub.setPadding(0, 8, 0, 24);
        root.addView(sub);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv);
        setContentView(root);

        if (eps.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("No episodes for this season yet.");
            empty.setTextColor(Color.WHITE);
            empty.setTextSize(20);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < eps.length; i++) {
            final Series.Ep ep = eps[i];
            TextView row = new TextView(this);
            row.setText(ep.title);
            row.setTextColor(Color.WHITE);
            row.setTextSize(22);
            row.setPadding(24, 20, 24, 20);
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackgroundColor(hasFocus ? ACCENT : Color.TRANSPARENT);
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!tryCobalt("https://www.youtube.com/watch?v=" + ep.id))
                        Toast.makeText(EpisodeListActivity.this, "TizenTube Cobalt not installed", Toast.LENGTH_SHORT).show();
                }
            });
            list.addView(row);
            if (i == 0) row.requestFocus();
        }
    }

    boolean tryCobalt(String url) {
        String[] pkgs = { "io.gh.reisxd.tizentube.cobalt", "io.gh.reisxd.tizentube.cobalt.ab" };
        for (String pk : pkgs) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.setComponent(new ComponentName(pk, "dev.cobalt.app.MainActivity"));
                i.putExtra("url", url);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) { /* try next */ }
        }
        return false;
    }
}
