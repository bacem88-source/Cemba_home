package com.bacem.launcher;

/** Open-Meteo client for Kitchener (43.4516, -80.4925, America/Toronto): current conditions,
 *  hourly + 7-day forecast for the weather page, and the scene-photo art for the weather card. */
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
public class Weather {
    public int temp, hi, lo, code, isDay = 1, hum = -1, pres = -1;
    public double wind = Double.NaN;
    public String label = "";
    static final String API = "https://api.open-meteo.com/v1/forecast?latitude=43.4516&longitude=-80.4925&current=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m,surface_pressure&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=America%2FToronto";
    public static String labelFor(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mainly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Overcast";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorm";
        return "";
    }
    public static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }
    public static Weather fetch() {
        try {
            JSONObject j = new JSONObject(get(API));
            JSONObject cur = j.getJSONObject("current");
            Weather w = new Weather();
            w.temp = (int) Math.round(cur.getDouble("temperature_2m"));
            w.code = cur.getInt("weather_code");
            try { w.isDay = cur.getInt("is_day"); } catch (Exception e) { w.isDay = 1; }
            w.label = labelFor(w.code);
            try { w.hum = cur.getInt("relative_humidity_2m"); } catch (Exception e) { }
            try { w.wind = cur.getDouble("wind_speed_10m"); } catch (Exception e) { }
            try { w.pres = (int) Math.round(cur.getDouble("surface_pressure")); } catch (Exception e) { }
            JSONObject d = j.getJSONObject("daily");
            w.hi = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
            w.lo = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
            return w;
        } catch (Exception e) { return null; }
    }
    public static int sceneFor(int code, int isDay) {
        if (code == 0 || code == 1) return isDay == 1 ? R.drawable.wx_clear : R.drawable.wx_night;
        if (code == 2 || code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 95) return R.drawable.wx_storm;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        return R.drawable.wx_rain;
    }
    public static File pngFile(Context ctx) { return new File(ctx.getFilesDir(), "weather.png"); }
    public static void refreshArt(Context ctx, Weather w) {
        try {
            Bitmap bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Bitmap scene = null;
            try { scene = android.graphics.BitmapFactory.decodeResource(ctx.getResources(), sceneFor(w.code, w.isDay)); }
            catch (Throwable t) { scene = null; }
            if (scene != null) {
                float sc2 = Math.max(320f / scene.getWidth(), 180f / scene.getHeight());
                int sw = Math.round(scene.getWidth() * sc2), sh = Math.round(scene.getHeight() * sc2);
                Bitmap scaled = Bitmap.createScaledBitmap(scene, sw, sh, true);
                c.drawBitmap(scaled, (320 - sw) / 2, (180 - sh) / 2, null);
                Paint sh3 = new Paint();
                sh3.setShader(new LinearGradient(0, 0, 0, 180, 0x40000000, 0xA6000000, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, 320, 180, sh3);
            } else {
                Paint bg = new Paint();
                bg.setShader(new LinearGradient(0, 0, 320, 180, Color.rgb(18, 32, 58), Color.rgb(8, 12, 22), Shader.TileMode.CLAMP));
                c.drawRect(0, 0, 320, 180, bg);
            }
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setShadowLayer(4, 0, 1, Color.BLACK);
            p.setColor(Color.rgb(160, 180, 205));
            p.setTextSize(16);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("WEATHER - KITCHENER", 18, 30, p);
            p.setColor(Color.WHITE);
            p.setTextSize(82);
            c.drawText(w.temp + "\u00B0", 16, 112, p);
            p.setTextSize(26);
            c.drawText(w.label, 20, 150, p);
            p.setColor(Color.rgb(190, 200, 215));
            p.setTextSize(20);
            String hl = "H " + w.hi + "\u00B0   L " + w.lo + "\u00B0";
            c.drawText(hl, 320 - p.measureText(hl) - 16, 150, p);
            FileOutputStream out = new FileOutputStream(pngFile(ctx));
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
        } catch (Exception e) { /* keep old art */ }
    }
}
