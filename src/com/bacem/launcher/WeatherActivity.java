package com.bacem.launcher;

/** Full-screen forecast page opened by tapping the weather card: now, next hours, whole week. */
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
public class WeatherActivity extends Activity {
    LinearLayout col;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(10, 14, 22));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(90, 60, 90, 60);
        sv.addView(col);
        setContentView(sv);
        addText("KITCHENER, ONTARIO", 22, Color.rgb(150, 165, 190));
        final TextView big = addText("...", 84, Color.WHITE);
        final TextView rest = addText("Loading forecast...", 29, Color.rgb(220, 228, 240));
        new Thread(() -> {
            String main = null;
            String detail = null;
            try {
                Weather w = Weather.fetch();
                if (w != null) {
                    main = w.temp + "\u00B0  " + w.label;
                    StringBuilder sb = new StringBuilder();
                    sb.append("Today:   H ").append(w.hi).append("\u00B0    L ").append(w.lo).append("\u00B0\n\n");
                    JSONObject j = new JSONObject(Weather.get("https://api.open-meteo.com/v1/forecast?latitude=43.4516&longitude=-80.4925&hourly=temperature_2m&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=America%2FToronto"));
                    JSONArray ht = j.getJSONObject("hourly").getJSONArray("time");
                    JSONArray hv = j.getJSONObject("hourly").getJSONArray("temperature_2m");
                    int start = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    sb.append("Next hours:\n");
                    for (int i = start; i < Math.min(start + 6, ht.length()); i++) {
                        sb.append(ht.getString(i).substring(11, 16)).append("    ")
                          .append((int) Math.round(hv.getDouble(i))).append("\u00B0\n");
                    }
                    JSONArray dt = j.getJSONObject("daily").getJSONArray("time");
                    JSONArray dmax = j.getJSONObject("daily").getJSONArray("temperature_2m_max");
                    JSONArray dmin = j.getJSONObject("daily").getJSONArray("temperature_2m_min");
                    JSONArray dc = j.getJSONObject("daily").getJSONArray("weather_code");
                    sb.append("\nThis week:\n");
                    SimpleDateFormat inf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    SimpleDateFormat outf = new SimpleDateFormat("EEE", Locale.getDefault());
                    for (int i = 0; i < Math.min(7, dt.length()); i++) {
                        Date dd = inf.parse(dt.getString(i));
                        sb.append(outf.format(dd)).append("    H ").append((int) Math.round(dmax.getDouble(i)))
                          .append("\u00B0    L ").append((int) Math.round(dmin.getDouble(i)))
                          .append("\u00B0    ").append(Weather.labelFor(dc.getInt(i))).append("\n");
                    }
                    detail = sb.toString();
                }
            } catch (Exception e) { detail = null; }
            final String m = main;
            final String d = detail;
            runOnUiThread(() -> {
                if (m != null) { big.setText(m); rest.setText(d); }
                else rest.setText("Could not load the forecast - check the box Wi-Fi and try again.");
            });
            new Thread(() -> Publish.ensurePublished(getApplicationContext())).start();
        }).start();
    }
    TextView addText(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        col.addView(t);
        return t;
    }
}
