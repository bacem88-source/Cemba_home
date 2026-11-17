package com.bacem.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Season list for a series (v4.38): opened from the Live TV Series
 *  category when a series title is tapped. Shows the series' seasons
 *  (S1, S2, ...); tapping one opens the episode list for that season. */
public class SeasonListActivity extends Activity {
    static final int BG = 0xFF0F2A44;
    static final int ACCENT = 0xFFC62828;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String series = getIntent().getStringExtra("series");
        String title = getIntent().getStringExtra("title");
        if (series == null) series = "choufli";
        if (title == null) title = "Choufli Hal";
        final String seriesF = series;
        final String titleF = title;
        String[][] seasons = Series.seasonsFor(series);

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
        sub.setText(seasons.length + " seasons - pick one");
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

        for (int i = 0; i < seasons.length; i++) {
            final String seasonKey = seasons[i][0];
            final String seasonLabel = seasons[i][1];
            TextView row = new TextView(this);
            row.setText(seasonLabel);
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
                    Intent in = new Intent(SeasonListActivity.this, EpisodeListActivity.class);
                    in.putExtra("series", seriesF);
                    in.putExtra("season", seasonKey);
                    in.putExtra("title", titleF + " - " + seasonLabel);
                    startActivity(in);
                }
            });
            list.addView(row);
            if (i == 0) row.requestFocus();
        }
    }
}
