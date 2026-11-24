package com.bacem.launcher;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cemba Home - a personal Google TV-style launcher for Bacem's Xiaomi TV Box S.
 *
 * Screen layout (top to bottom, one ScrollView):
 *   header (profile avatar, greeting, big clock + date, SSID / Settings pills, weather line)
 *   widgets row (weather photo card)
 *   Top picks for you   - from the TvContract PreviewPrograms store
 *   Your apps           - circles filled with each app's own artwork; long-press = Move / Pin menu
 *   Continue watching   - from the TvContract WatchNext store, split into three rows:
 *   Music / Kids continue watching
 *   Shortcuts           - Box Settings, Play Store, Google TV Home, Un-default, Wallpaper, Refresh cards,
 *                         Apps tab (open / hide / lock / uninstall), Search apps, Voice search
 *
 * Data feeds (all read live, nothing hard-coded):
 *   weather  - Open-Meteo for Kitchener, refreshed every 30 min (see Weather.java)
 *   picks    - PreviewPrograms published by YouTube / Tubi / Prime Video / Crave ...
 *   watching - WatchNextPrograms published by the box's apps; needs the one-time grant:
 *              pm grant com.bacem.launcher android.permission.READ_TV_LISTINGS
 *   Note: this app's OWN published cards (Watch Later / History / Library / weather, see
 *   Publish.java) are filtered OUT of Continue watching - that row is for real watching only.
 *
 * YouTube cards open TizenTube Cobalt DIRECTLY (title+channel search, or the exact video
 * when an ID is known) - no hop through the YouTube app or the Bridge.
 *
 * Prefs: "ui" (bgmode, wp, app_order).
 */
public class MainActivity extends Activity {
    static final String GRANT_CMD = "pm grant com.bacem.launcher android.permission.READ_TV_LISTINGS";
    LinearLayout picksRow, watchRow, musicRow, kidsRow, appsRow;
    TextView clockTv, weatherTv, dateTv, wifiTv, headlineTv, clubLineTv, sublineTv;
    TextView wxCardBig, wxCardSub, wxCardDays, wxCardMeta;
    ScrollView mainSv;
    View watchLabel, picksLabel, liveLabel, musicLabel, kidsLabel, newsLabel;
    LinearLayout catPanel, themePanel, searchPanel, searchResults, watchPanel, settingsPanel, appsPanel;
    // v4.53: Jannah's calendar panel (Kids only). Built lazily on first open,
    // like the theme panel -- see fillCalendarPanel().
    LinearLayout calendarPanel;
    boolean calendarPanelFilled = false;
    // v3.57: the theme picker's split layout -- the preview column is fixed,
    // the theme cards live in this scrollable column (themeCardsCol) so the
    // preview never scrolls away while browsing the themes.
    LinearLayout themeCardsCol;
    // Watching tab (v3.50): one section visible at a time, chips switch it.
    LinearLayout watchChipsRow;
    java.util.LinkedHashMap<String, LinearLayout> watchSecs = new java.util.LinkedHashMap<String, LinearLayout>();
    java.util.LinkedHashMap<String, TextView> watchChips = new java.util.LinkedHashMap<String, TextView>();
    java.util.HashMap<String, Integer> watchChipIds = new java.util.HashMap<String, Integer>();
    TextView kidsAppChip; // v3.94: YouTube Kids action chip (no-TV boxes only)
    String watchSection() { return getSharedPreferences("ui", MODE_PRIVATE).getString("watch_section", "picks"); }
    List<AppItem> lastApps = new ArrayList<AppItem>();
    // v4.16: the theme panel's 37 picture cards are built lazily on first
    // open (see navTheme) instead of at startup -- see fillThemePanel.
    boolean themePanelFilled = false;
    EditText searchEdit;
    android.app.Dialog timeUpDialog;
    Runnable kidsTickR;
    // v3.61: true while the home is in front. The Time's up dialog can only
    // be shown then -- showing it from the background (while she is inside
    // YouTube) would fail, so showTimeUp pulls her home first and onResume
    // shows the dialog once the home is back.
    boolean resumed = false;
    TextView navHome, navLive, navWatch, navTheme, navWidgets, navApps, navSettings, navCalendar;
    LinearLayout widgetsPanel, widgetsRow, homeCol;
    View nowPlayingBar, homeNowPlayingBar;
    TextView nowPlayingTitleTv, themePreviewTv;
    TextView homeNowPlayingTitleTv, panelNowPlayingTitleTv;
    ImageView themePreviewIv;
    ImageView avatarIv;
    String navMode = "home";
    int accent = 0xFFE8C874;
    int lastTemp = 12;
    // v3.51: last fetched weather/sun values -- a rebuilt widget card starts
    // from these instead of "..." / "--" (the fetch only runs every 30 min).
    String lastWxLab = "", lastWxMeta = "", lastSunrise = "", lastSunset = "", lastSunLen = "";
    int lastWxHi = 0, lastWxLo = 0;
    boolean lastWxOk = false;
    // v3.52: the YouTube mix has been started this launcher session.
    boolean ytMusicOpened = false;
    TextView wxCapTv, sunCapTv, careCapTv;
    RingView wxRingV, storageRingV;
    TextView storagePctTv, deviceTv;
    ImageView wxIconIv;
    TextView sunRiseTv, sunSetTv, sunLenTv, ramTv, storageTv;
    View ramFillV, ramRestV;
    LinearLayout newsRow, pills, scRow;
    long lastWx = 0;
    DynBg dynBg;
    FrameLayout rootF;
    FrameLayout root;
    // v3.93: ONE background view (was the photoA/photoB crossfade pair).
    // One 1280x720 RGB_565 picture is ~1.8 MB instead of two ARGB_8888
    // pictures at ~7.4 MB -- the pair was the biggest steady bitmap cost
    // on a 2 GB box.
    ImageView photo;
    // v3.64: Top picks backdrop. A full-screen poster of the focused movie,
    // like Google TV Home -- above the theme background, below the scrim
    // and the content, so the scrim still dims it for readability.
    ImageView pickBg;
    Runnable pickBgHideR;
    // v3.81: Top picks auto-advance, like Google TV Home's hero carousel.
    // Every 5s the highlight moves to the next movie by itself and the
    // backdrop follows (his ask: "it should move by itself like the google
    // tv picks .. each few seconds and next"). Manual navigation restarts
    // the 5s timer; leaving Top picks stops it.
    Runnable pickAutoR;
    int pickAutoIdx = -1;
    // v3.55: the slideshow rolls the active source -- his photos (PHOTOS) or
    // the active theme's own pictures. themeSlide is true while a theme's
    // pictures are rolling (the palette guard in photoTickR must not stop it).
    int[] slidePics = PHOTOS;
    // Rolling theme pictures: bundled drawable ids (slidePics above stays
    // for his PHOTOS slideshow).
    int[] slidePicFiles = null;
    int slideIdx = 0;
    boolean themeSlide = false;
    static final int[] PHOTOS = { R.drawable.photo1, R.drawable.photo2, R.drawable.photo3,
        R.drawable.photo4, R.drawable.photo5, R.drawable.photo6 };
    static final String[] BGNAMES = { "Photos", "Cinema", "Sunset", "Emerald", "Night City", "Graphite", "Crimson" };
    int bgMode() { return getSharedPreferences("ui", MODE_PRIVATE).getInt("bgmode", -1); }
    // Team themes carry their own background picture; 0 = no team background (wallpaper mode applies).
    // v3.43: hero pictures -- real players plus the club logo, MorphTV style
    // (the old crest-only pictures are kept in res but no longer used).
    // v3.55: themeBg() is gone -- setBaseWallpaper() now uses themePicIds(),
    // which returns the bundled drawable ids for the theme.
    // v3.93 reverted: theme pictures are bundled in the APK again (his ask
    // 2026-11-14 -- keep the theme pictures, movies use the internet).
    // Resolve a bg file name to its drawable id (0 = not bundled).
    int bgRes(String name) {
        if (name == null) return 0;
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); }
        catch (Throwable t) { return 0; }
    }

    // Hero (single-picture) file name for a theme key, or null.
    String heroPicName(String t) {
        if ("esperance".equals(t)) return "bg_esperance_hero";
        if ("africain".equals(t)) return "bg_africain_hero";
        if ("beja".equals(t)) return "bg_ob_hero";
        if ("arsenal".equals(t)) return "bg_arsenal_hero";
        if ("psg".equals(t)) return "bg_psg_hero";
        if ("realmadrid".equals(t)) return "bg_realmadrid_hero";
        if ("barca".equals(t)) return "bg_barca_hero";
        if ("nightmosque".equals(t)) return "bg_nightmosque";
        if ("mosquesunset".equals(t)) return "bg_mosque_sunset";
        if ("mosqueblue".equals(t)) return "bg_mosque_blue";
        if ("mosquedesert".equals(t)) return "bg_mosque_desert";
        // v4.39: cartoon themes -- one hero picture each, like team themes.
        if ("pokemon".equals(t)) return "bg_pokemon_hero";
        if ("conan".equals(t)) return "bg_conan_hero";
        if ("spongebob".equals(t)) return "bg_spongebob_hero";
        if ("pawpatrol".equals(t)) return "bg_pawpatrol_hero";
        return null;
    }

    // Background drawable for a given theme key (0 = colour theme, no picture).
    // First picture of a theme (picker thumbnail + single-picture themes);
    // 0 when the key has none.
    int themeBgRes(String t) {
        if (t == null) return 0;
        String h = heroPicName(t);
        if (h != null) return bgRes(h);
        int[] n = themePicIds(t);
        return n.length > 0 ? n[0] : 0;
    }

    // v3.55: rolling pictures per theme. Event themes have 4 each; team and
    // mosque themes have their one hero for now (3 more come later); plain
    // colour themes have none and fall back to the wallpaper / palette.
    // v3.56: the plain themes have 4 pictures each now too -- every theme
    // rolls pictures, none falls back to the wallpaper / palette any more.
    // Rolling picture ids for a theme key (4 for event and plain themes,
    // 1 hero for team and mosque themes, empty for unknown keys -- those
    // fall back to his photo slideshow / palette).
    int[] themePicIds(String t) {
        if (t == null) return new int[0];
        if ("eid".equals(t) || "ramadan".equals(t) || "mouled".equals(t)
                || "christmas".equals(t) || "halloween".equals(t) || "eidadha".equals(t)
                || "hijri".equals(t) || "ashura".equals(t) || "hajj".equals(t)
                || "newyear".equals(t) || "kids".equals(t)
                || "gold".equals(t) || "classic".equals(t) || "ocean".equals(t)
                || "midnight".equals(t) || "crimson".equals(t) || "emerald".equals(t)
                || "sunset".equals(t) || "violet".equals(t) || "sport".equals(t)
                || "forest".equals(t) || "royal".equals(t) || "coral".equals(t)
                || "vicesunset".equals(t) || "sky".equals(t) || "rose".equals(t)) {
            int[] ids = new int[4];
            int n = 0;
            for (int i = 1; i <= 4; i++) {
                int id = bgRes("bg_" + t + "_" + i);
                if (id != 0) ids[n++] = id;
            }
            if (n == 4) return ids;
            if (n == 0) return new int[0];
            int[] out = new int[n];
            System.arraycopy(ids, 0, out, 0, n);
            return out;
        }
        String h = heroPicName(t);
        if (h != null) {
            int id = bgRes(h);
            return id != 0 ? new int[] { id } : new int[0];
        }
        return new int[0];
    }

    // v3.47: language (prefs "lang": en/fr/ar) + widget prefs (third line).
    String lang() { return getSharedPreferences("ui", MODE_PRIVATE).getString("lang", "en"); }
    String t(String key) { return Logic.tr(lang(), key); }
    String langName() { if ("fr".equals(lang())) return "Fran\u00e7ais"; if ("ar".equals(lang())) return "\u0627\u0644\u0639\u0631\u0628\u064a\u0629"; return "English"; }
    // v3.60: widgets are per-profile now -- switching from Family to Kids
    // (or back) changes the widgets too. The Family profile keeps the
    // existing prefs; the Kids profile uses the same keys with a "_kids"
    // suffix, so each profile remembers its own order, hidden widgets and
    // sizes. The Kids default is Timer + Weather; Family stays as before.
    String wSuffix() { return kidsMode() ? "_kids" : ""; }
    int widgetSize() { return getSharedPreferences("ui", MODE_PRIVATE).getInt("widget_size" + wSuffix(), 0); }
    /** Per-widget size (v3.50): each widget keeps its own Small/Medium/Large
     *  under "widget_size_<key>"; unset widgets fall back to the old global
     *  size, so his current Small stays until he changes one. */
    int widgetSizeFor(String w) { return getSharedPreferences("ui", MODE_PRIVATE).getInt("widget_size_" + w + wSuffix(), widgetSize()); }
    void cycleWidgetSizeFor(final String w) { int n = (widgetSizeFor(w) + 1) % 3; getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("widget_size_" + w + wSuffix(), n).apply(); toast(w + " size: " + Logic.widgetSizeName(n)); fillWidgetsPanel(); }
    String widgetOrder() { String s = getSharedPreferences("ui", MODE_PRIVATE).getString("widget_order" + wSuffix(), ""); return s.length() == 0 ? (kidsMode() ? Logic.widgetOrderDefaultKids() : Logic.widgetOrderDefault()) : s; }
    boolean widgetOff(String w) { return csvSet("widget_off" + wSuffix()).contains(w); }
    void cycleLanguage() { getSharedPreferences("ui", MODE_PRIVATE).edit().putString("lang", Logic.langNext(lang())).apply(); toast("Language: " + langName()); recreate(); }
    void mediaKey(int code) { try { android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE); am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code)); } catch (Exception e) { toast("Nothing playing right now"); } }
    boolean hasPkg(String pkg) { try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; } }
    /** First SmartTube package actually installed on this box (v3.50): the
     *  current stable is org.smarttube.stable; older installs use the beta
     *  or the legacy com.teamsmart name. Null when SmartTube is absent. */
    String smartTubePkg() { for (String p : Logic.youtubeMusicPkgs()) if (hasPkg(p)) return p; return null; }
    // One title update for every Now Playing bar (home + Widgets panel).
    void setNowPlayingTitle(String s) {
        if (nowPlayingTitleTv != null) nowPlayingTitleTv.setText(s);
        if (homeNowPlayingTitleTv != null) homeNowPlayingTitleTv.setText(s);
        if (panelNowPlayingTitleTv != null) panelNowPlayingTitleTv.setText(s);
    }
    // v3.55: the Now Playing title listener -- the receiver registered in
    // onCreate that the NowPlayingListener broadcasts the real title to.
    android.content.BroadcastReceiver nowPlayingReceiver;
    // v3.58: the media-session route to the real title -- SmartTube does not
    // reliably post a media notification on this box, so the listener alone
    // never sees a title. Reading the active media session instead asks the
    // player directly what is playing, no notification needed.
    android.media.session.MediaSessionManager mediaSessionMgr;
    android.media.session.MediaSessionManager.OnActiveSessionsChangedListener sessionListener;
    void refreshNowPlayingFromSessions() {
        try {
            if (mediaSessionMgr == null) mediaSessionMgr = (android.media.session.MediaSessionManager) getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
            if (mediaSessionMgr == null) return;
            java.util.List<android.media.session.MediaController> cs = mediaSessionMgr.getActiveSessions(new android.content.ComponentName(this, NowPlayingListener.class));
            if (cs == null) return;
            for (android.media.session.MediaController c : cs) {
                if (c == null) continue;
                android.media.session.PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() != android.media.session.PlaybackState.STATE_PLAYING) continue;
                android.media.MediaMetadata md = c.getMetadata();
                if (md == null) continue;
                String title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.trim().length() == 0) continue;
                String pkg = c.getPackageName();
                setNowPlayingTitle(Logic.nowPlayingTitle(Logic.nowPlayingAppLabel(pkg), title));
                return;
            }
        } catch (Exception e) {}
    }
    /** True when the user granted Cemba Home Notification access, so the
     *  NowPlayingListener can read the media notification's title. */
    boolean isNowPlayingListenerEnabled() {
        try {
            String enabled = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return enabled != null && enabled.contains(getPackageName() + "/" + NowPlayingListener.class.getName());
        } catch (Exception e) { return false; }
    }
    /** v3.58: true when the user enabled Cemba Power in Accessibility settings,
     *  so the Power off row can open the power menu. */
    boolean isPowerServiceEnabled() {
        try {
            String enabled = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            return enabled != null && enabled.contains(getPackageName() + "/" + PowerService.class.getName());
        } catch (Exception e) { return false; }
    }
    // v3.64: Bridge toggle for the Kids profile. The TizenTube Bridge is an
    // Accessibility service (YouTubeClickInterceptorService); turning it off
    // lets the official YouTube app open normally in Kids, turning it back
    // on restores the Cobalt redirect in Family. Writing this setting needs
    // WRITE_SECURE_SETTINGS, granted once via ADB -- without it the toggle
    // is a no-op and the user is told the grant command.
    static final String BRIDGE_SVC = "com.whooslizi.tizenbridge/.YouTubeClickInterceptorService";
    // v3.70: true when the app can toggle the Bridge (WRITE_SECURE_SETTINGS
    // granted). A reinstall loses the pm grant, so this lets Settings show
    // the state instead of the toggle failing quietly.
    boolean isBridgeSwitchReady() {
        try {
            return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) { return false; }
    }
    void setBridgeEnabled(boolean on) {
        try {
            String cur = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            java.util.Set<String> set = new java.util.LinkedHashSet<String>();
            if (cur != null && cur.length() > 0) {
                for (String s : cur.split(":")) if (s.length() > 0) set.add(s);
            }
            boolean changed = on ? set.add(BRIDGE_SVC) : set.remove(BRIDGE_SVC);
            if (!changed) return;
            StringBuilder sb = new StringBuilder();
            for (String s : set) { if (sb.length() > 0) sb.append(":"); sb.append(s); }
            android.provider.Settings.Secure.putString(getContentResolver(), "enabled_accessibility_services", sb.toString());
        } catch (SecurityException se) {
            toast("Bridge switch needs one grant: pm grant com.bacem.launcher android.permission.WRITE_SECURE_SETTINGS");
        } catch (Exception e) { /* leave the Bridge as it is */ }
    }
    // v3.49: YouTube music goes to SmartTube, not Cobalt (his call) -- Cobalt
    // stops when you leave it, SmartTube keeps playing in a PiP window once
    // its Settings > General > Background playback > Picture in picture is on.
    // v3.58: the music widget's YouTube button opens SmartTube's History page
    // (his ask) instead of starting a "top hits music mix" search -- he picks
    // what to play from his own history.
    void openYouTubeMusic() {
        setNowPlayingTitle("YouTube (SmartTube) - playing in background");
        String pkg = smartTubePkg();
        if (pkg != null) {
            // v3.52: if a mix is already playing, just bring SmartTube to the
            // front -- firing the search again interrupts what is playing.
            if (ytMusicOpened) { launchPackage(pkg); return; }
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/feed/history"));
                i.setPackage(pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                ytMusicOpened = true;
                return;
            } catch (Exception e) { /* fall through to SmartTube home */ }
            launchPackage(pkg);
            return;
        }
        toast("SmartTube is not installed on this box");
    }

    // ---------- App hiding + PIN lock (v3.36) ----------
    // Sets are comma-separated strings in prefs, same style as app_order.
    Set<String> csvSet(String key) {
        String s = getSharedPreferences("ui", MODE_PRIVATE).getString(key, "");
        Set<String> out = new HashSet<String>();
        if (s.length() == 0) return out;
        for (String p : s.split(",")) if (p.length() > 0) out.add(p);
        return out;
    }
    String joinCsv(Set<String> set) {
        StringBuilder b = new StringBuilder();
        for (String p : set) { if (b.length() > 0) b.append(","); b.append(p); }
        return b.toString();
    }
    void saveCsvSet(String key, Set<String> set) {
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString(key, joinCsv(set)).apply();
    }
    boolean isHidden(String pkg) { return csvSet("hidden_apps").contains(pkg); }
    boolean isLocked(String pkg) { return csvSet("locked_apps").contains(pkg); }
    boolean pinSet() { return getSharedPreferences("ui", MODE_PRIVATE).getString("app_pin", "").length() == 4; }
    Handler handler = new Handler();
    float density;

    int dp(int v) { return Math.round(v * density); }

    // v3.53 responsive: the home is designed for a 960x540dp screen (1080p).
    // The real TV is measured once at startup (see onCreate) and the whole
    // home subtree is scaled by uiScale so it fits one screen on any TV.
    // Panels scroll, so they keep unscaled dp(); only the home uses hdp/hsp.
    float uiScale = 1f;

    int hdp(int v) { return Math.round(v * density * uiScale); }
    float hsp(float v) { return v * uiScale; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // v3.40: a launcher holds the screen while it is foreground. The box
        // was going dark right after Cemba came up -- a phone-side launch does
        // not reset the box's inactivity timer, so the timer could fire on
        // top of a healthy Cemba and look like Cemba killed the TV. This flag
        // keeps the display on while this window is up, and releases it as
        // soon as Cemba leaves the foreground.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // v3.39: launch safety net. If anything below throws, onCreate would
        // die before the first frame and the box would sit on a black screen
        // until it slept -- exactly the failure he saw. The catch below swaps
        // in a readable recovery screen instead, so a startup failure can
        // never again present as a black screen.
        try {
        // Speed (v3.91): startup keeps the main thread light. Everything
        // heavy -- the app list, Top picks, Continue watching, news, weather
        // -- runs on background threads and posts its rows back (see
        // loadPicks / loadWatch / loadApps). What stays on this thread is
        // view building only, plus the background pictures, which are
        // pre-downsized to 1280x720 in res/ so each decode is ~10 ms.
        density = getResources().getDisplayMetrics().density;
        { android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
          float sh = dm.heightPixels / dm.density, sw = dm.widthPixels / dm.density;
          uiScale = Math.max(0.7f, Math.min(1.5f, Math.min(sh / 540f, sw / 960f))); }
        root = new FrameLayout(this);
        dynBg = new DynBg(this);
        root.addView(dynBg, new FrameLayout.LayoutParams(-1, -1));
        rootF = root;
        // v3.93: one background view; the theme (or his photos) is applied
        // by setBaseWallpaper() just below.
        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        // v3.64: Top picks backdrop -- hidden until a Top pick is focused.
        pickBg = new ImageView(this);
        pickBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pickBg.setVisibility(View.GONE);
        // v3.81: never let the backdrop take input. It is a full-screen
        // ImageView; if it ever became clickable/focusable it would swallow
        // taps meant for the tabs and cards above it.
        pickBg.setClickable(false);
        pickBg.setFocusable(false);
        root.addView(pickBg, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0x40000000, 0x00000000, 0xB3000000 });
        scrim.setBackground(sg);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        setBaseWallpaper();

        ScrollView sv = new ScrollView(this);
        mainSv = sv;
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // v3.51: tighter top/bottom padding -- the home was ~48dp too tall
        // for the 540dp screen and the apps row sat below the fold.
        col.setPadding(hdp(56), hdp(12), hdp(56), hdp(12));
        sv.addView(col);
        root.addView(sv);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout hLeft = new LinearLayout(this);
        hLeft.setOrientation(LinearLayout.VERTICAL);
        TextView greet = new TextView(this);
        int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        greet.setText(hr < 12 ? t("morning") : hr < 18 ? t("afternoon") : t("evening"));
        greet.setTextColor(Color.rgb(190, 195, 205));
        greet.setShadowLayer(6, 0, 2, Color.BLACK);
        greet.setTextSize(hsp(15));
        try { greet.setLetterSpacing(0.25f); } catch (Exception e) { /* older tv */ }
        hLeft.addView(greet);
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.BOTTOM);
        clockTv = new TextView(this);
        clockTv.setTextColor(Color.WHITE);
        clockTv.setShadowLayer(8, 0, 2, Color.BLACK);
        clockTv.setTextSize(hsp(40));
        clockTv.setTypeface(clockTv.getTypeface(), android.graphics.Typeface.BOLD);
        clockRow.addView(clockTv);
        dateTv = new TextView(this);
        dateTv.setTextColor(Color.rgb(190, 195, 205));
        dateTv.setShadowLayer(6, 0, 2, Color.BLACK);
        dateTv.setTextSize(hsp(16));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = hdp(16);
        dlp.bottomMargin = hdp(9);
        clockRow.addView(dateTv, dlp);
        hLeft.addView(clockRow);
        avatarIv = new ImageView(this);
        avatarIv.setImageResource(R.drawable.avatar);
        applyProfileAvatar();
        avatarIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(hdp(56), hdp(56));
        avlp.rightMargin = hdp(18);
        avlp.topMargin = hdp(6);
        header.addView(avatarIv, avlp);
        avatarIv.setContentDescription("Account - tap to switch profile");
        focusableOvalAccent(avatarIv);
        // v3.60: tap the picture to switch profiles, like YouTube (his ask).
        // Family -> Kids switches straight away; Kids -> Family asks for the
        // PIN, so the kid cannot switch themself out. Same as the Profile row.
        avatarIv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleKidsMode(); }
        });
        header.addView(hLeft, new LinearLayout.LayoutParams(0, -2, 1f));
        // v3.27: the pills get their own full-width row under the header. Sharing the
        // header line with the clock squeezed the row: the Wi-Fi text wrapped and its
        // second line clipped (dot-only pill) and Settings got zero width.
        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.LEFT);
        wifiTv = pill("Wi-Fi");
        // v3.52: Wi-Fi + Settings sit slightly right of the nav pills.
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.leftMargin = hdp(24);
        wifiTv.setLayoutParams(wlp);
        wifiTv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) { toast("Wi-Fi settings not available"); }
            }
        });
        pills.addView(wifiTv);
        // v4.05 (his ask 2026-11-14): Bluetooth pill REMOVED from the
        // home header. Bluetooth is now only in Settings > Box (v4.02 row),
        // which is the way to open Bluetooth settings and turn it on.
        navSettings = navPill(t("settings"), new Runnable() { @Override public void run() { openSettingsPanel(); } });
        pills.addView(navSettings);
        col.addView(header);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = hdp(4);
        col.addView(pills, plp);
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String ssid = wm.getConnectionInfo().getSSID();
                if (ssid != null) {
                    ssid = ssid.replace("\"", "");
                    if (ssid.length() > 0 && !ssid.toLowerCase(Locale.getDefault()).contains("unknown")) {
                        android.text.SpannableString sstr = new android.text.SpannableString("\u25CF " + ssid);
                        sstr.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(96, 210, 130)), 0, 1, 0);
                        wifiTv.setText(sstr);
                    }
                }
            }
        } catch (Exception e) { /* keep "Wi-Fi" */ }
        if (wifiTv != null && "Wi-Fi".equals(String.valueOf(wifiTv.getText()))) {
            android.text.SpannableString dstr = new android.text.SpannableString("\u25CF Wi-Fi");
            dstr.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(96, 210, 130)), 0, 1, 0);
            wifiTv.setText(dstr);
        }

        // v3.42 hero themes: small club line above the headline (MorphTV style).
        clubLineTv = new TextView(this);
        clubLineTv.setTextColor(Color.rgb(190, 195, 205));
        clubLineTv.setShadowLayer(6, 0, 2, Color.BLACK);
        clubLineTv.setTextSize(hsp(15));
        try { clubLineTv.setLetterSpacing(0.25f); } catch (Exception e) { /* older tv */ }
        clubLineTv.setVisibility(View.GONE);
        LinearLayout.LayoutParams cllp = new LinearLayout.LayoutParams(-2, -2);
        cllp.topMargin = hdp(14);
        col.addView(clubLineTv, cllp);

        headlineTv = new TextView(this);
        headlineTv.setTextColor(Color.WHITE);
        headlineTv.setShadowLayer(8, 0, 2, Color.BLACK);
        headlineTv.setTextSize(hsp(38));
        headlineTv.setTypeface(headlineTv.getTypeface(), android.graphics.Typeface.BOLD);
        // v3.51: a long focused title ran off the right edge -- cut it with "..." instead.
        headlineTv.setSingleLine(true);
        headlineTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = hdp(6);
        headlineTv.setLayoutParams(hlp);
        col.addView(headlineTv);

        // v3.49 live subline: the line that used to be painted into the hero
        // picture (e.g. "Red and white - Stade Boujemaa Kmiti") now lives here.
        sublineTv = new TextView(this);
        sublineTv.setTextColor(Color.rgb(210, 215, 225));
        sublineTv.setShadowLayer(6, 0, 2, Color.BLACK);
        sublineTv.setTextSize(hsp(15));
        sublineTv.setVisibility(View.GONE);
        col.addView(sublineTv);

        // v3.50 (restored, his call): the widgets stay on home, exactly as they
        // were -- Weather, Sun, Box Care in one row, Now Playing under it.
        // The Widgets tab still manages them (move, resize, hide).
        homeCol = col;
        buildWidgetsRow();
        startClockTick();

        // v3.51: compact "Your apps" label (see the fit note above).
        TextView appsLab = new TextView(this);
        appsLab.setText(t("your_apps"));
        appsLab.setTextColor(Color.WHITE);
        appsLab.setTextSize(hsp(20));
        appsLab.setTypeface(appsLab.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.topMargin = hdp(8);
        alp.bottomMargin = hdp(6);
        col.addView(appsLab, alp);
        appsRow = newRow();
        col.addView(scrollOf(appsRow));

        // v3.50 Watching tab + Settings panel: created here so the content rows
        // below can be parented into the Watching sections.
        watchPanel = makePanel(t("watching"));
        buildWatchSections();
        settingsPanel = makePanel("Settings");
        appsPanel = makePanel(t("apps"));
        // v4.53: Jannah's calendar panel (Kids only) -- content is built
        // lazily on first open by fillCalendarPanel(), so startup stays fast.
        calendarPanel = makePanel(t("calendar"));

        // v3.50: the content rows live in the Watching tab, one section at a
        // time (chips switch it) -- home is one screen, no scrolling down.
        watchLabel = label(t("continue_watching"));
        watchSecs.get("watch").addView(watchLabel);
        watchRow = newRow();
        watchSecs.get("watch").addView(scrollOf(watchRow));

        picksLabel = label(t("top_picks"));
        watchSecs.get("picks").addView(picksLabel);
        picksRow = newRow();
        watchSecs.get("picks").addView(scrollOf(picksRow));

        // v3.67: the Watching > Live TV row is gone (his ask) -- the guide
        // now lives in the header Live TV tab. The old static YouTube cards
        // (Watania 1/2, El Hiwar, Attessia, Nessma, Hannibal) are in the
        // guide's categories instead. v4.16: the row's builders
        // (liveCard/liveCardBase/refreshLiveProvider and the provider
        // cache readers) were deleted -- they were unreachable since the
        // row went away, and the guide fetches its provider list itself.

        musicLabel = label(t("music"));
        watchSecs.get("music").addView(musicLabel);
        musicRow = newRow();
        watchSecs.get("music").addView(scrollOf(musicRow));

        kidsLabel = label(t("kids"));
        watchSecs.get("kids").addView(kidsLabel);
        kidsRow = newRow();
        watchSecs.get("kids").addView(scrollOf(kidsRow));


        newsLabel = label(t("news"));
        watchSecs.get("news").addView(newsLabel);
        newsRow = newRow();
        watchSecs.get("news").addView(scrollOf(newsRow));

        // v3.50: the old Shortcuts bar lives under Settings now; the row stays
        // only as the Kids-mode grown-ups exit (empty in normal mode).
        scRow = newRow();
        buildShortcuts();
        col.addView(scrollOf(scRow));

        // Top banner (v3.20): Home / Categories / Theme on a transparent strip, fixed
        // above the scrolling rows; Categories jumps to a row, Theme switches the look.
        catPanel = makePanel("Categories");
        catRow(catPanel, "Movies", "Top picks and new films", "picks");
        catRow(catPanel, "Series", "Continue watching", "watch");
        catRow(catPanel, "Music", "Songs from your apps", "music");
        catRow(catPanel, "Kids", "YouTube Kids and kids shows", "kids");
        catRow(catPanel, "News", "CBC, Radio-Canada and Mosaique FM", "news");
        // v3.57 split theme picker (his ask): the preview sits fixed on one
        // half of the page and the theme cards scroll on the other half, so
        // the preview never scrolls away while browsing the themes. Built
        // by hand instead of makePanel, because makePanel wraps everything
        // in one ScrollView -- the preview would scroll away with the list.
        // themePanel's parent is the wrap below, so setPanelVis still works.
        android.widget.FrameLayout themeWrap = new android.widget.FrameLayout(this);
        themeWrap.setBackgroundColor(0xD8000000);
        themeWrap.setVisibility(View.GONE);
        root.addView(themeWrap, new android.widget.FrameLayout.LayoutParams(-1, -1));
        themePanel = new LinearLayout(this);
        themePanel.setOrientation(LinearLayout.VERTICAL);
        themePanel.setPadding(dp(56), dp(90), dp(56), dp(40));
        themePanel.setBackgroundColor(0xD8000000);
        themePanel.setVisibility(View.GONE);
        themeWrap.addView(themePanel, new android.widget.FrameLayout.LayoutParams(-1, -1));
        TextView themeTitle = new TextView(this);
        themeTitle.setText(t("theme"));
        themeTitle.setTextColor(Color.WHITE);
        themeTitle.setTextSize(30);
        themeTitle.setTypeface(themeTitle.getTypeface(), android.graphics.Typeface.BOLD);
        themePanel.addView(themeTitle);
        LinearLayout themeSplit = new LinearLayout(this);
        themeSplit.setOrientation(LinearLayout.HORIZONTAL);
        themePanel.addView(themeSplit, new LinearLayout.LayoutParams(-1, 0, 1f));
        // Preview column -- fixed on one half, never scrolls.
        LinearLayout prevCol = new LinearLayout(this);
        prevCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(440), -1);
        prevLp.rightMargin = dp(24);
        themeSplit.addView(prevCol, prevLp);
        themePreviewIv = new ImageView(this);
        themePreviewIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        prevCol.addView(themePreviewIv, new LinearLayout.LayoutParams(-1, dp(248)));
        themePreviewTv = new TextView(this);
        themePreviewTv.setTextColor(Color.WHITE);
        themePreviewTv.setTextSize(17);
        themePreviewTv.setTypeface(themePreviewTv.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tpLp = new LinearLayout.LayoutParams(-1, -2);
        tpLp.topMargin = dp(12);
        themePreviewTv.setLayoutParams(tpLp);
        prevCol.addView(themePreviewTv);
        // Theme cards column -- the only part that scrolls.
        ScrollView themeCardsSv = new ScrollView(this);
        themeCardsSv.setFillViewport(true);
        themeSplit.addView(themeCardsSv, new LinearLayout.LayoutParams(0, -1, 1f));
        themeCardsCol = new LinearLayout(this);
        themeCardsCol.setOrientation(LinearLayout.VERTICAL);
        themeCardsSv.addView(themeCardsCol, new android.widget.FrameLayout.LayoutParams(-1, -2));
        widgetsPanel = makePanel(t("widgets"));
        searchPanel = makePanel("Search apps");
        searchEdit = new EditText(this);
        searchEdit.setHint("Search apps...");
        searchEdit.setTextColor(Color.WHITE);
        searchEdit.setHintTextColor(Color.rgb(180, 190, 205));
        searchEdit.setTextSize(20);
        searchEdit.setSingleLine(true);
        searchEdit.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable sgSearch = new GradientDrawable();
        sgSearch.setColor(0xB3141824);
        sgSearch.setCornerRadius(dp(12));
        sgSearch.setStroke(dp(1), 0x55FFFFFF);
        searchEdit.setBackground(sgSearch);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(560), -2);
        slp.topMargin = dp(12);
        searchEdit.setLayoutParams(slp);
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { runSearch(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchPanel.addView(searchEdit);
        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchPanel.addView(searchResults);
        // v3.21: the banner pills live in the header pills row, same line as Wi-Fi.
        navHome = navPill(t("home"), new Runnable() { @Override public void run() { showHome(); } });
        // v3.67: Live TV tab in the header, before Watching -- opens the
        // full-screen guide straight away (his ask: "live tv in home as a
        // tab before watching"). It is a launcher, not a mode, so refreshNav
        // leaves it white (never underlined as active). Red dot before the
        // label, like the guide's "Live TV" row (his ask: "live tv with
        // the red dot").
        navLive = navPill(t("live_tv"), new Runnable() { @Override public void run() {
            try { startActivity(new Intent(MainActivity.this, LiveTvActivity.class)); }
            catch (Exception e2) { toast("Guide not available"); }
        } });
        { android.text.SpannableString ss = new android.text.SpannableString("\u25CF " + t("live_tv"));
          ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFE62832), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          navLive.setText(ss); }
        navWatch = navPill(t("watching"), new Runnable() { @Override public void run() { showWatchPanel(); } });
        // v3.60: Categories pill removed (his ask) -- the Watching tab
        // already covers Movies / Series / Music / Kids / Live TV / News
        // with its chips, so the separate Categories pill was redundant.
        // The Categories panel itself stays in code, just unreached.
        // v4.16: build the theme cards lazily on first open -- decoding
        // all 37 theme pictures at startup held ~17 MB of thumbnails in
        // a hidden panel for the whole session.
        navTheme = navPill(t("theme"), new Runnable() { @Override public void run() { if (!themePanelFilled) { themePanelFilled = true; fillThemePanel(); } showPanel(themePanel); } });
        // v4.53: Calendar pill -- Kids only (see applyKidsMode). Opens
        // Jannah's week with today highlighted; Family never sees it.
        navCalendar = navPill(t("calendar"), new Runnable() { @Override public void run() { if (!calendarPanelFilled) { calendarPanelFilled = true; fillCalendarPanel(); } showPanel(calendarPanel); } });
        // v3.82: Widgets pill removed from the header (his ask) -- it now
        // lives under Settings > Box as a plain row, so the header stays
        // short and the widgets are one tap away in Settings.
        navApps = navPill(t("apps"), new Runnable() { @Override public void run() { openAppsPanel(); } });
        pills.addView(navHome, 0); pills.addView(navLive, 1); pills.addView(navWatch, 2); pills.addView(navTheme, 3); pills.addView(navCalendar, 4); pills.addView(navApps, 5);
        applyTheme(getSharedPreferences("ui", MODE_PRIVATE).getString("theme", "gold"), false);
        refreshNav();
        // v3.50: the pills stay in the home column (photo layout) -- they are
        // hidden with the home while a panel is open, Back brings them back.

        if (Logic.isRtl(lang())) root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);
        tickClock();
        fetchWeather();
        new Thread(new Runnable() {
            @Override public void run() { try { Publish.ensurePublished(getApplicationContext()); } catch (Throwable t) { /* no TV store on this box */ } }
        }).start();
        AlarmReceiver.schedule(getApplicationContext());
        // v3.55: the Now Playing title comes from the media notification --
        // register the receiver that the NowPlayingListener broadcasts to.
        try {
            nowPlayingReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context ctx, Intent intent) {
                    String s = intent.getStringExtra(NowPlayingListener.EXTRA_TITLE);
                    if (s != null && s.length() > 0) setNowPlayingTitle(s);
                }
            };
            registerReceiver(nowPlayingReceiver, new android.content.IntentFilter(NowPlayingListener.ACTION_TITLE));
        } catch (Exception e) {}
        // v3.58: also watch the active media sessions directly (see
        // refreshNowPlayingFromSessions) -- the notification route is a
        // backup; the session route asks the player what is playing.
        try {
            mediaSessionMgr = (android.media.session.MediaSessionManager) getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
            if (mediaSessionMgr != null) {
                sessionListener = new android.media.session.MediaSessionManager.OnActiveSessionsChangedListener() {
                    @Override public void onActiveSessionsChanged(java.util.List<android.media.session.MediaController> controllers) {
                        refreshNowPlayingFromSessions();
                    }
                };
                mediaSessionMgr.addOnActiveSessionsChangedListener(sessionListener, new android.content.ComponentName(this, NowPlayingListener.class));
                refreshNowPlayingFromSessions();
            }
        } catch (Exception e) {}
        } catch (Throwable t) { showStartupFallback(t); }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (nowPlayingReceiver != null) unregisterReceiver(nowPlayingReceiver); } catch (Exception e) {}
        try { if (mediaSessionMgr != null && sessionListener != null) mediaSessionMgr.removeOnActiveSessionsChangedListener(sessionListener); } catch (Exception e) {}
    }

    // v3.39 recovery screen: shown when startup throws, so the failure is
    // readable (and escapable) instead of a black screen. The error text is
    // the diagnosis -- a photo of it names the exact failing line.
    void showStartupFallback(Throwable t) {
        try {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundColor(Color.BLACK);
            box.setPadding(dp(48), dp(48), dp(48), dp(48));
            box.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(this);
            title.setText("Cemba Home couldn't start");
            title.setTextColor(Color.WHITE);
            title.setTextSize(28);
            box.addView(title);
            TextView err = new TextView(this);
            err.setText(String.valueOf(t));
            err.setTextColor(0xFFFF8A80);
            err.setTextSize(16);
            box.addView(err);
            TextView hint = new TextView(this);
            hint.setText("Take a photo of this screen and send it to Cemba -- it names the exact failure. Your setup is safe.");
            hint.setTextColor(Color.WHITE);
            hint.setTextSize(16);
            box.addView(hint);
            android.widget.Button home = new android.widget.Button(this);
            home.setText("Open Google Home");
            home.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchPackageRaw("com.google.android.apps.tv.launcherx"); }
            });
            box.addView(home);
            android.widget.Button retry = new android.widget.Button(this);
            retry.setText("Try again");
            retry.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { recreate(); }
            });
            box.addView(retry);
            setContentView(box);
        } catch (Throwable t2) {
            try { toast("Cemba Home couldn't start"); } catch (Exception e) {}
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        try { applyTabVisibility(); } catch (Throwable t) {}
        // v3.61: if her time ran out while she was inside another app, the
        // go-home already pulled her back -- now show the Time's up box.
        try { kidsCheck(); } catch (Throwable t) {}
        // v3.87: re-arm the Kids timer alarm on resume (it fires even if the
        // activity was dead while she watched YouTube Kids).
        try { scheduleKidsAlarm(); } catch (Throwable t) {}
        // v3.58: refresh the Now Playing title from the active media session
        // whenever the home comes back -- the song keeps playing while the
        // user is in SmartTube, and the bar should show its title on return.
        try { refreshNowPlayingFromSessions(); } catch (Throwable t) {}
        // v3.39: isolate each home loader -- one bad row must not kill resume.
        // Speed (v3.91): a resume is a full refresh by design (fresh cards
        // every time he comes home), but every query runs on its own
        // background thread -- loadWatch / loadPicks / loadApps / loadNews
        // -- and only the row rendering happens here. The caps bound the
        // work per refresh: 300 Top picks, 60 Watch Next.
        try { loadWatch(); } catch (Throwable t) {}
        try { loadPicks(); } catch (Throwable t) {}
        try { loadApps(); } catch (Throwable t) {}
        try { refreshBoxCare(); } catch (Throwable t) {}
        try { loadNews(); } catch (Throwable t) {}
        // v3.93: the background bitmaps were dropped in onStop -- re-apply
        // the theme picture (from the web-pack cache) now.
        try { setBaseWallpaper(); } catch (Throwable t) {}
        // v3.38: Kids mode is a persisted mode, not a one-time switch -- if the
        // activity restarts (reboot, sleep/wake) while it is on, re-assert the
        // kid-safe home and restart the minute tick, or the home would come
        // back full and unguarded and the limit/bedtime would never fire.
        // v4.54: re-assert in BOTH profiles, not just Kids. After a restart
        // in Family mode applyKidsMode() never ran, so the Calendar pill
        // (added visible by default) stayed visible in Family -- the
        // calendar must be Kids only. applyKidsMode() already sets every
        // pill from the current mode, so call it unconditionally; the
        // minute tick still only starts in Kids mode.
        // v4.55: also (re)start the Kids timer service on resume in Kids
        // mode, so it survives an activity restart while she is watching.
        try {
            applyKidsMode();
            if (kidsMode()) {
                startKidsTick();
                Intent svc = new Intent(this, KidsTimerService.class);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
                try {
                    startService(new Intent(this, KidsTimerService.class)
                        .setAction(KidsTimerService.ACTION_HIDE_OVERLAY));
                } catch (Throwable ignored) {}
                if (android.os.Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 901); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {}
        // v4.31: check GitHub once a day for a newer Cemba Home (see
        // checkForUpdates). Throttled by prefs so it does not hit the
        // network on every resume.
        try { maybeCheckForUpdates(); } catch (Throwable t) {}
        // v4.57: Jannah's birthday (Dec 29) -- pop the birthday message
        // once that day, the first time the home comes up.
        try { maybeShowBirthday(); } catch (Throwable t) {}
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        try {
            if (kidsMode()) {
                startService(new Intent(this, KidsTimerService.class)
                    .setAction(KidsTimerService.ACTION_HOME_PAUSED));
            }
        } catch (Throwable ignored) {}
    }

    // v3.93: when the home is no longer visible (he opened a movie app),
    // drop the heavy bitmaps so the box's low-memory killer does not pick
    // Cemba. The poster cache, the theme picture and the Top picks backdrop
    // are the big steady bitmaps; the rows reload in onResume.
    @Override protected void onStop() {
        super.onStop();
        try { stopSlideshow(); } catch (Throwable t) { /* ignore */ }
        try { Img.CACHE.evictAll(); } catch (Throwable t) { /* ignore */ }
        try { if (photo != null) { photo.animate().cancel(); photo.setImageDrawable(null); } } catch (Throwable t) { /* ignore */ }
        try { if (pickBg != null) { pickBg.animate().cancel(); pickBg.setImageDrawable(null); } } catch (Throwable t) { /* ignore */ }
        // v4.16: drop the cached background bitmap too (v3.93) -- it is
        // re-decoded once on the next resume.
        bgCacheBmp = null; bgCacheRes = 0;
    }

    /** v3.65: HOME must land on the home screen. With launchMode singleTask
     *  the system brings the existing MainActivity to the front and calls
     *  onNewIntent instead of creating a new one -- without this, pressing
     *  Home while inside a tab left the tab open and never went home. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        try { showHome(); } catch (Throwable t) {}
    }

    /** v3.61: pull the user back to the launcher home (his ask -- when the
     *  kid's time is up, stop everything, even if she is inside YouTube).
     *  Uses Cemba Power (accessibility) to press Home; if it is not enabled
     *  yet, brings the launcher itself to the front instead. */
    void goHome() {
        try {
            if (isPowerServiceEnabled()) {
                sendBroadcast(new Intent(PowerService.ACTION_GO_HOME));
                return;
            }
        } catch (Exception e) {}
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception e) {}
    }

    // ---------- v4.31: automatic updates from GitHub ----------
    // The source lives in a public GitHub repo (bacem88-source/Cemba_home).
    // version.json on the main branch carries the latest versionCode,
    // versionName, a download URL for the APK and release notes. The app
    // checks it once a day (and on demand from Settings > Box), and if a
    // newer version exists it offers to download and install it. Android
    // still asks the user to confirm the install (unknown-apps prompt),
    // so an update is never silent -- it is one tap.
    static final String UPDATE_URL = "https://raw.githubusercontent.com/bacem88-source/Cemba_home/main/version.json";
    static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    int installedVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    String installedVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return ""; }
    }

    String appVersionName() { return installedVersionName(); }

    void maybeCheckForUpdates() {
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        long last = p.getLong("update_last_check", 0);
        if (System.currentTimeMillis() - last < UPDATE_CHECK_INTERVAL_MS) return;
        p.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
        checkForUpdates(false);
    }

    void checkForUpdates(final boolean manual) {
        // v4.36: the first tap used to do nothing at all -- no dialog, no
        // toast. Two causes: (1) GitHub's raw CDN can serve a stale
        // version.json, so the check silently concluded "up to date";
        // (2) nothing told him the tap had registered while the fetch
        // was still running. Fix: show "Checking..." at once on a manual
        // check, and add a cache-buster (?t=now) so the fetch is always
        // fresh instead of a cached copy.
        if (manual) toast("Checking for updates...");
        final String freshUrl = UPDATE_URL + "?t=" + System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                int remoteCode = 0;
                String remoteName = "";
                String apkUrl = "";
                String notes = "";
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(freshUrl).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setRequestMethod("GET");
                    c.setRequestProperty("Accept", "application/json");
                    int code = c.getResponseCode();
                    if (code >= 400) throw new Exception("HTTP " + code);
                    String body = Tubi.readBody(c);
                    Object parsed = Iptv.parseJson(body);
                    if (!(parsed instanceof java.util.Map)) throw new Exception("bad version file");
                    java.util.Map<?, ?> m = (java.util.Map<?, ?>) parsed;
                    Object vc = m.get("versionCode");
                    if (vc instanceof Number) remoteCode = ((Number) vc).intValue();
                    else if (vc != null) remoteCode = Integer.parseInt(String.valueOf(vc).trim());
                    Object vn = m.get("versionName");
                    if (vn != null) remoteName = String.valueOf(vn);
                    Object au = m.get("apkUrl");
                    if (au != null) apkUrl = String.valueOf(au);
                    Object nt = m.get("notes");
                    if (nt != null) notes = String.valueOf(nt);
                } catch (Exception e) {
                    err = String.valueOf(e.getMessage());
                }
                final int rc = remoteCode;
                final String rn = remoteName;
                final String au2 = apkUrl;
                final String nt2 = notes;
                final String err2 = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (err2 != null) {
                            if (manual) toast("Could not check for updates");
                            return;
                        }
                        int installed = installedVersionCode();
                        if (rc > installed && au2.length() > 0) {
                            showUpdateDialog(rn, au2, nt2);
                        } else if (manual) {
                            toast("You are up to date - version " + installedVersionName());
                        }
                    }
                });
            }
        }).start();
    }

    void showUpdateDialog(final String remoteName, final String apkUrl, String notes) {
        String msg = "Version " + remoteName + " is available";
        if (notes != null && notes.length() > 0) msg += "\n\n" + notes;
        msg += "\n\nDownload and install it now?";
        new AlertDialog.Builder(this)
            .setTitle("Cemba Home update")
            .setMessage(msg)
            .setPositiveButton("Download & install", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    downloadAndInstall(apkUrl, remoteName);
                }
            })
            .setNegativeButton("Later", null)
            .show();
    }

    void downloadAndInstall(final String apkUrl, final String remoteName) {
        toast("Downloading update " + remoteName + "...");
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                java.io.File out = new java.io.File(getFilesDir(), "update.apk");
                try {
                    // v4.37: follow GitHub's redirect by hand. The release
                    // download URL answers 302 to the real file host, and
                    // relying on HttpURLConnection's automatic redirect
                    // left the N5 Max saving the redirect page instead of
                    // the APK, so the installer said "problem parsing the
                    // package". Follow up to 5 redirects manually.
                    String url = apkUrl;
                    java.io.InputStream in = null;
                    for (int hop = 0; hop < 6; hop++) {
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(60000);
                        c.setRequestMethod("GET");
                        c.setInstanceFollowRedirects(false);
                        int code = c.getResponseCode();
                        if (code >= 300 && code < 400) {
                            String loc = c.getHeaderField("Location");
                            if (loc == null || loc.length() == 0) throw new Exception("redirect without Location");
                            url = loc;
                            c.disconnect();
                            continue;
                        }
                        if (code >= 400) throw new Exception("HTTP " + code);
                        in = c.getInputStream();
                        break;
                    }
                    if (in == null) throw new Exception("no download");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.flush(); fos.close(); in.close();
                    // The file must be a real APK (a ZIP). If GitHub gave
                    // us an HTML page instead, refuse it here with a clear
                    // message rather than letting the installer fail with
                    // "problem parsing the package".
                    java.io.FileInputStream fin = new java.io.FileInputStream(out);
                    int b0 = fin.read();
                    int b1 = fin.read();
                    fin.close();
                    if (out.length() < 1000000 || b0 != 'P' || b1 != 'K') {
                        out.delete();
                        throw new Exception("bad file");
                    }
                } catch (Exception e) {
                    err = String.valueOf(e.getMessage());
                }
                final String err2 = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (err2 != null) { toast("Download failed"); return; }
                        try {
                            android.net.Uri uri = android.net.Uri.parse("content://com.bacem.launcher.art/update.apk");
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(uri, "application/vnd.android.package-archive");
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        } catch (Exception e) {
                            toast("Could not open installer - allow Install unknown apps for Cemba Home first");
                        }
                    }
                });
            }
        }).start();
    }

    @Override public void onBackPressed() {
        // v3.85: Back never leaves Cemba Home. After entering the Watching
        // tab and browsing Top picks one by one, pressing Back was exiting
        // to Google TV Home (his report: "i go back to that tab it goes
        // home google home"). The navMode flag alone could not be trusted
        // as the source of truth, so use the real visible state: if any
        // panel is showing, go to the Cemba home. If already on the Cemba
        // home, swallow the press -- Cemba IS the home, there is nowhere
        // to go back to, and exiting would hand the screen to Google Home
        // or to a background app like YouTube (his ask: "not put youtube
        // or any other app back"). The Google TV Home row in Settings >
        // Box stays the way to open Google Home on purpose.
        if (isAnyPanelVisible()) { showHome(); return; }
        if (!"home".equals(navMode)) { showHome(); return; }
        // Already on the Cemba home: do nothing.
    }

    boolean isAnyPanelVisible() {
        return (watchPanel != null && watchPanel.getVisibility() == View.VISIBLE)
            || (themePanel != null && themePanel.getVisibility() == View.VISIBLE)
            || (appsPanel != null && appsPanel.getVisibility() == View.VISIBLE)
            || (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE)
            || (catPanel != null && catPanel.getVisibility() == View.VISIBLE)
            || (widgetsPanel != null && widgetsPanel.getVisibility() == View.VISIBLE)
            || (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE);
    }

    TextView navPill(String s, final Runnable act) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(hsp(13));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(hdp(11), hdp(7), hdp(11), hdp(7));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x80000000);
        g.setCornerRadius(hdp(20));
        g.setStroke(hdp(1), 0x66FFFFFF);
        t.setBackground(g);
        t.setFocusable(true);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                // v4.13: a focused nav pill fills with the theme accent
                // (same UI as the Wi-Fi pill, but themed). On blur,
                // refreshNav restores the active tab's accent text.
                if (has) {
                    g.setColor(accent);
                    g.setStroke(hdp(1), accent);
                    t.setTextColor(accentText());
                } else {
                    g.setColor(0x80000000);
                    g.setStroke(hdp(1), 0x66FFFFFF);
                    refreshNav();
                }
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(110).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = hdp(6);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act.run(); }
        });
        return t;
    }

    void refreshNav() {
        setNavState(navHome, "home".equals(navMode));
        setNavState(navWatch, "watching".equals(navMode));
        setNavState(navTheme, "theme".equals(navMode));
        setNavState(navCalendar, "calendar".equals(navMode));
        setNavState(navApps, "apps".equals(navMode));
        setNavState(navSettings, "settings".equals(navMode));
        // v4.14: Live TV is a launcher, never active, but its text must
        // still come back to white -- the v4.13 navPill focus fill changes
        // its text color on focus, and refreshNav is what restores it on
        // blur. Without this line the "Live TV" text stayed dark after
        // being focused once. The red dot is a span, so it stays red.
        setNavState(navLive, false);
    }

    // v4.13: text color that reads on the theme accent (dark on a light
    // accent, white on a dark one).
    int accentText() {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return (0.299 * r + 0.587 * g + 0.114 * b) > 140 ? Color.rgb(18, 22, 30) : Color.WHITE;
    }

    void setNavState(TextView t, boolean on) {
        if (t == null) return;
        t.setTextColor(on ? accent : Color.WHITE);
        t.setPaintFlags(on ? (t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG)
                           : (t.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
    }

    // v3.41: a panel is an inner column inside a fullscreen dark ScrollView
    // wrapper (v3.36). Hiding only the column left the wrapper visible, so
    // five stacked 85%-black wrappers blacked the whole home from startup.
    // The wrapper must follow the column, always.
    void setPanelVis(View p, boolean vis) {
        if (p == null) return;
        p.setVisibility(vis ? View.VISIBLE : View.GONE);
        android.view.ViewParent par = p.getParent();
        if (par instanceof View) ((View) par).setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    void showHome() {
        navMode = "home";
        stopPickAuto();
        // v3.83: move focus to the Home pill. Going back to Home (or any
        // tab) was opening YouTube by itself because focus stayed on a
        // YouTube movie card in the now-hidden Watching panel, and the tab
        // tap leaked through to it (his report: "Go back the main tab top
        // picks or another tab opens youtube"). Focus now sits on a visible
        // tab pill, never on a hidden card.
        hidePickBg();
        if (mainSv != null) mainSv.setVisibility(View.VISIBLE);
        setPanelVis(catPanel, false);
        setPanelVis(themePanel, false);
        setPanelVis(calendarPanel, false);
        setPanelVis(widgetsPanel, false);
        setPanelVis(watchPanel, false);
        setPanelVis(settingsPanel, false);
        setPanelVis(appsPanel, false);
        setPanelVis(searchPanel, false);
        refreshNav();
        // v3.77: do NOT requestFocus() here. It made the apps take time to
        // appear when going Home (his report: "When i click home the apps
        // take time to appear") -- the focus search through the whole home
        // layout blocks the apps row from showing. Focus moves naturally
        // when he presses a direction key.
        // v3.50: rebuild the home widgets row so the values are fresh and the
        // update fields point back at the home cards (the Widgets panel builds
        // its own copies, which would otherwise steal the field refs).
        buildWidgetsRow();
        // v3.83: focus the Home pill (not the home ScrollView -- v3.77 showed
        // mainSv.requestFocus() slows the apps). A single pill takes focus
        // at once, so no hidden card keeps it.
        if (navHome != null) navHome.requestFocus();
    }

    void showPanel(final View p) {
        // v3.81: hide the Top picks backdrop on ANY panel switch. After Top
        // picks, tapping another tab (Home, Apps, Settings, or a Watching
        // sub-tab) was exiting to Google TV Home instead of switching (his
        // video 2026-11-13 14:47). The backdrop was still visible when the
        // panel changed, because showPanel never hid it -- only showHome and
        // showWatchSection(non-picks) did. Hide it here so it can never
        // interfere with a tab switch.
        hidePickBg();
        stopPickAuto();
        navMode = (p == catPanel) ? "cat" : (p == themePanel) ? "theme" : (p == calendarPanel) ? "calendar" : (p == widgetsPanel) ? "widgets" : (p == watchPanel) ? "watching" : (p == settingsPanel) ? "settings" : (p == appsPanel) ? "apps" : "search";
        if (mainSv != null) mainSv.setVisibility(View.GONE);
        setPanelVis(catPanel, p == catPanel);
        setPanelVis(themePanel, p == themePanel);
        setPanelVis(calendarPanel, p == calendarPanel);
        setPanelVis(widgetsPanel, p == widgetsPanel);
        setPanelVis(watchPanel, p == watchPanel);
        setPanelVis(settingsPanel, p == settingsPanel);
        setPanelVis(appsPanel, p == appsPanel);
        setPanelVis(searchPanel, p == searchPanel);
        refreshNav();
        // v3.84: focus the panel itself, not the nav pill. The pills live in
        // the home column (mainSv), which is GONE while a panel is open, so
        // focusing a pill here failed and focus stayed on a YouTube card in
        // the hidden Watching panel (his report, still happening in v3.83:
        // "after seeing the videos top picks and going back to the tab it
        // opens youtube"). The panel is visible and focusable, so it always
        // takes focus. Press Down to reach a card, OK to open it.
        p.requestFocus();
        // v3.72/v3.77: do NOT auto-focus a card in ANY panel. Focusing the
        // first card made the tab tap leak through to it and open YouTube /
        // SmartTube by itself (his report: "when i touch watching it opens
        // smart tube by itself" / "when i click on any tab it opens youtube
        // by itself"). v3.72 only skipped the watchPanel, but it happened
        // on every tab. The user now presses Down to reach a card and OK to
        // open it, like every other row. No auto-focus anywhere.
    }

    LinearLayout makePanel(String title) {
        // v3.36: panels are scrollable now (a ScrollView around the rows). Before
        // this, a panel was a plain full-screen column, so rows past the bottom
        // of the screen -- e.g. the last Theme rows -- could only be reached blind.
        // The scroll view is added to root here and carries the dark background;
        // the returned inner column keeps the old visibility/focus behaviour, so
        // showHome/showPanel and the row builders are unchanged.
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xD8000000);
        sv.setVisibility(View.GONE); // v3.41: wrapper follows the column (see setPanelVis)
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(56), dp(90), dp(56), dp(40));
        p.setBackgroundColor(0xD8000000);
        p.setVisibility(View.GONE);
        // v3.84: panels take focus themselves. showPanel used to focus the
        // nav pill for the panel, but the pills live in the home column
        // (mainSv), which is GONE while a panel is open -- so the focus
        // request failed and focus stayed on a YouTube card in the hidden
        // Watching panel, and the next tap opened YouTube by itself (his
        // report, still happening in v3.83). A focusable panel always takes
        // focus, so no hidden card keeps it.
        p.setFocusable(true);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(30);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        p.addView(t);
        sv.addView(p, new FrameLayout.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return p;
    }

    // v3.58: settings rows are smaller and tighter now (his ask -- the old
    // 20sp titles and 28sp section labels were big and the panel scrolled
    // too much). More settings fit on one screen, easier to read and move.
    void panelRow(LinearLayout p, String name, String sub, final Runnable act) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xB3141824);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), 0x55FFFFFF);
        r.setBackground(g);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        r.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Color.rgb(200, 208, 222));
        s.setTextSize(13);
        r.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        r.setLayoutParams(lp);
        r.setContentDescription(name);
        focusable(r, 1.0f);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act.run(); }
        });
        p.addView(r);
    }

    void catRow(LinearLayout p, final String name, String sub, final String section) {
        panelRow(p, name, sub, new Runnable() {
            @Override public void run() {
                runIfCatOk(name, new Runnable() {
                    @Override public void run() { showWatchPanel(); showWatchSection(section); }
                });
            }
        });
    }

    // v3.49 MorphTV-style picker: a small preview screen on top, small cards
    // in two columns; tapping a theme applies it at once, like MorphTV.
    // v3.53: picture themes first (his ask) -- teams, then mosques, then
    // the plain colour themes. Order inside each group is unchanged.
    static final String[][] THEMES = {
        {"Esp\u00e9rance", "esperance"}, {"Club Africain", "africain"}, {"Olympique de B\u00e9ja", "beja"},
        {"Arsenal", "arsenal"}, {"Paris Saint-Germain", "psg"}, {"Real Madrid", "realmadrid"}, {"FC Barcelona", "barca"},
        {"Night Mosque", "nightmosque"},
        {"Sunset Mosque", "mosquesunset"}, {"Blue Night Mosque", "mosqueblue"}, {"Desert Mosque", "mosquedesert"},
        {"Eid", "eid"}, {"Ramadan", "ramadan"}, {"Mouled", "mouled"},
        {"Christmas", "christmas"}, {"Halloween", "halloween"},
        {"Eid al-Adha", "eidadha"}, {"Islamic New Year", "hijri"}, {"Ashura", "ashura"},
        {"Hajj", "hajj"}, {"New Year", "newyear"},
        {"Kids", "kids"},
        {"Pok\u00e9mon", "pokemon"}, {"Detective Conan", "conan"},
        {"SpongeBob", "spongebob"}, {"PAW Patrol", "pawpatrol"},
        {"Gold", "gold"}, {"Classic", "classic"}, {"Ocean", "ocean"}, {"Midnight", "midnight"},
        {"Crimson", "crimson"}, {"Emerald", "emerald"}, {"Sunset", "sunset"}, {"Violet", "violet"},
        {"Sport", "sport"}, {"Forest", "forest"}, {"Royal", "royal"}, {"Coral", "coral"},
        {"Vice Sunset", "vicesunset"},
        {"Sky", "sky"}, {"Rose", "rose"},
    };
    static String themeDisplayName(String key) {
        for (String[] th : THEMES) if (th[1].equals(key)) return th[0];
        return key;
    }
    // v3.50: a stored theme that no longer exists (Al Ahly / Zamalek were
    // removed) falls back to Gold instead of rendering unthemed.
    boolean themeKnown(String k) { for (String[] th : THEMES) if (th[1].equals(k)) return true; return false; }
    // v4.40: the kids themes -- the only themes the Kids profile may
    // pick (his ask: the Kids Themes tab shows kids themes only).
    static boolean isKidsTheme(String key) {
        return "kids".equals(key) || "pokemon".equals(key) || "conan".equals(key)
                || "spongebob".equals(key) || "pawpatrol".equals(key);
    }
    void fillThemePanel() {
        // v3.57: the cards live in the scrollable column (themeCardsCol), one
        // card per row filling the column -- the preview column is fixed, so
        // two cards per row no longer fit. The preview itself stays put.
        // v4.40: in the Kids profile the picker shows only the kids themes.
        if (themeCardsCol == null) return;
        themeCardsCol.removeAllViews();
        for (int i = 0; i < THEMES.length; i++) {
            if (kidsMode() && !isKidsTheme(THEMES[i][1])) continue;
            View card = themeCard(THEMES[i][0], THEMES[i][1]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(10);
            card.setLayoutParams(lp);
            themeCardsCol.addView(card);
        }
        updateThemePreview(getSharedPreferences("ui", MODE_PRIVATE).getString("theme", "gold"));
    }
    View themeCard(String name, final String key) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xB3141824);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), 0x55FFFFFF);
        r.setBackground(g);
        // v3.50: every theme card shows its picture (his ask -- the old cards
        // were text-only). Hero themes show their photo; colour themes show
        // their accent colour.
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Picker thumbnail from the bundled theme picture; a theme with no
        // picture shows its accent colour instead.
        int bgRes = themeBgRes(key);
        boolean shown = false;
        if (bgRes != 0) {
            android.graphics.Bitmap tb = thumbFor(bgRes, 540);
            if (tb != null) { iv.setImageBitmap(tb); shown = true; }
        }
        if (!shown) iv.setBackgroundColor(Logic.themeAccent(key));
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(-1, dp(110));
        ivLp.bottomMargin = dp(8);
        r.addView(iv, ivLp);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        r.addView(t);
        TextView s = new TextView(this);
        s.setText("Tap to apply");
        s.setTextColor(Color.rgb(200, 208, 222));
        s.setTextSize(11);
        r.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(270), -2);
        lp.topMargin = dp(10);
        lp.rightMargin = dp(10);
        r.setLayoutParams(lp);
        r.setContentDescription(name);
        focusable(r);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { previewTheme(key); }
        });
        return r;
    }
    // Tap a theme = apply it at once (and save it), like MorphTV.
    void previewTheme(String key) {
        applyTheme(key, true);
        updateThemePreview(key);
    }
    void updateThemePreview(String key) {
        int bgRes = themeBgRes(key);
        if (themePreviewIv != null) {
            boolean shown = false;
            if (bgRes != 0) {
                android.graphics.Bitmap tb = thumbFor(bgRes, 1080);
                if (tb != null) { themePreviewIv.setImageBitmap(tb); themePreviewIv.setBackgroundColor(Color.TRANSPARENT); shown = true; }
            }
            if (!shown) { themePreviewIv.setImageDrawable(null); themePreviewIv.setBackgroundColor(Logic.themeAccent(key)); }
        }
        if (themePreviewTv != null) themePreviewTv.setText(themeDisplayName(key));
    }

    // v3.42: true while a hero club theme is on -- the focus headline then
    // stays the club slogan instead of following the focused card.
    boolean heroTheme() {
        String t = getSharedPreferences("ui", MODE_PRIVATE).getString("theme", "gold");
        return Logic.isHeroTheme(t);
    }

    void applyTheme(String name, boolean announce) {
        if (!themeKnown(name)) name = "gold";
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("theme", name).apply();
        if ("classic".equals(name)) {
            accent = Color.WHITE;
            setCapColors(Color.rgb(190, 215, 245), Color.rgb(255, 240, 210), Color.rgb(190, 220, 250));
        } else if ("ocean".equals(name)) {
            accent = 0xFF4DD0E1;
            setCapColors(0xFF4DD0E1, 0xFF4DD0E1, 0xFF4DD0E1);
        } else if ("midnight".equals(name)) {
            accent = 0xFF64B5F6;
            setCapColors(0xFF64B5F6, 0xFF64B5F6, 0xFF64B5F6);
        } else if ("crimson".equals(name)) {
            accent = 0xFFEF5350;
            setCapColors(0xFFEF5350, 0xFFEF5350, 0xFFEF5350);
        } else if ("emerald".equals(name)) {
            accent = 0xFF66BB6A;
            setCapColors(0xFF66BB6A, 0xFF66BB6A, 0xFF66BB6A);
        } else if ("sunset".equals(name)) {
            accent = 0xFFFFA726;
            setCapColors(0xFFFFA726, 0xFFFFA726, 0xFFFFA726);
        } else if ("violet".equals(name)) {
            accent = 0xFFAB47BC;
            setCapColors(0xFFAB47BC, 0xFFAB47BC, 0xFFAB47BC);
        } else if ("sport".equals(name)) {
            accent = 0xFF00C853;
            setCapColors(0xFF00C853, 0xFF00C853, 0xFF00C853);
        } else if ("forest".equals(name)) {
            accent = 0xFF2E7D32;
            setCapColors(0xFF2E7D32, 0xFF2E7D32, 0xFF2E7D32);
        } else if ("royal".equals(name)) {
            accent = 0xFF7C4DFF;
            setCapColors(0xFF7C4DFF, 0xFF7C4DFF, 0xFF7C4DFF);
        } else if ("coral".equals(name)) {
            accent = 0xFFFF6E40;
            setCapColors(0xFFFF6E40, 0xFFFF6E40, 0xFFFF6E40);
        } else if ("esperance".equals(name)) {
            // Esperance Sportive de Tunis: blood and gold; crest background via themeBg().
            accent = 0xFFFFD200;
            setCapColors(0xFFFFD200, 0xFFFFD200, 0xFFFFD200);
        } else if ("africain".equals(name)) {
            // Club Africain: red and white; crest background via themeBg().
            accent = 0xFFE30613;
            setCapColors(0xFFE30613, 0xFFE30613, 0xFFE30613);
        } else if ("beja".equals(name) || "arsenal".equals(name) || "psg".equals(name)
                || "realmadrid".equals(name) || "barca".equals(name)
                || "vicesunset".equals(name) || "sky".equals(name) || "rose".equals(name)
                || "nightmosque".equals(name) || "mosquesunset".equals(name)
                || "mosqueblue".equals(name) || "mosquedesert".equals(name)
                || "eid".equals(name) || "ramadan".equals(name) || "mouled".equals(name)
                || "christmas".equals(name) || "halloween".equals(name)) {
            accent = Logic.themeAccent(name);
            setCapColors(accent, accent, accent);
        } else {
            accent = 0xFFE8C874;
            setCapColors(0xFFE8C874, 0xFFE8C874, 0xFFE8C874);
        }
        if (wxRingV != null) wxRingV.setRing(Logic.wxRingColorFor(lastTemp, accent), Logic.wxRingFrac(lastTemp));
        if (storageRingV != null) storageRingV.setRing(accent, storageRingV.frac);
        setBaseWallpaper();
        refreshNav();
        // v4.13: recolor the Watching tab chips with the new theme accent.
        for (java.util.Map.Entry<String, TextView> e : watchChips.entrySet())
            setChip(e.getValue(), watchSection().equals(e.getKey()));
        // v3.42 hero themes (MorphTV style): club line + giant slogan headline.
        // Leaving a hero theme clears the slogan back to the focus headline.
        if ("esperance".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("Esp\u00e9rance Sportive de Tunis \u00b7 Tunis \u00b7 Founded in 1919"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Esp\u00e9rance Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("esperance"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else if ("africain".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("Club Africain \u00b7 Tunis \u00b7 Founded in 1920"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Club Africain Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("africain"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else if ("beja".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("Olympique de B\u00e9ja \u00b7 B\u00e9ja \u00b7 Founded in 1929"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Olympique Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("beja"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else if ("arsenal".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("Arsenal FC \u00b7 London \u00b7 Founded in 1886"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Arsenal Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("arsenal"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else if ("psg".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("Paris Saint-Germain \u00b7 Paris \u00b7 Founded in 1970"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Paris Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("psg"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else if ("realmadrid".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("Real Madrid CF \u00b7 Madrid \u00b7 Founded in 1902"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Real Madrid Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("realmadrid"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else if ("barca".equals(name)) {
            if (clubLineTv != null) { clubLineTv.setText("FC Barcelona \u00b7 Barcelona \u00b7 Founded in 1899"); clubLineTv.setVisibility(View.VISIBLE); }
            if (headlineTv != null) { headlineTv.setText("Barcelona Above All"); headlineTv.setTextSize(hsp(54)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); if (sublineTv != null) { String sl = Logic.heroSubline("barca"); sublineTv.setText(sl); sublineTv.setVisibility(sl.length() == 0 ? View.GONE : View.VISIBLE); } }
        } else {
            if (clubLineTv != null) clubLineTv.setVisibility(View.GONE);
            if (sublineTv != null) sublineTv.setVisibility(View.GONE);
            if (headlineTv != null) { headlineTv.setText(""); headlineTv.setTextSize(hsp(38)); headlineTv.getLayoutParams().height = -2; headlineTv.requestLayout(); }
        }
        if (themePreviewTv != null) updateThemePreview(name);
        if (announce) toast("Theme applied: " + themeDisplayName(name));
    }

    void setCapColors(int wx, int sun, int care) {
        if (wxCapTv != null) wxCapTv.setTextColor(wx);
        if (sunCapTv != null) sunCapTv.setTextColor(sun);
        if (careCapTv != null) careCapTv.setTextColor(care);
    }

    // v4.16: the clock formatters were rebuilt on every tick (header every
    // 20s, widget every 60s). Instance fields, so a language change (which
    // recreates the activity) rebuilds them with the new locale. All
    // callers run on the UI thread, so sharing them is safe.
    java.text.SimpleDateFormat fmtTime, fmtDate, fmtDateLong;
    java.text.SimpleDateFormat timeFmt() { if (fmtTime == null) fmtTime = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault()); return fmtTime; }
    java.text.SimpleDateFormat dateFmt() { if (fmtDate == null) fmtDate = new java.text.SimpleDateFormat("EEE, MMM d", Locale.getDefault()); return fmtDate; }
    java.text.SimpleDateFormat dateLongFmt() { if (fmtDateLong == null) fmtDateLong = new java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault()); return fmtDateLong; }

    // Header clock + date, re-ticking every 20s; also triggers the 30-min weather refresh.
    void tickClock() {
        Date now = new Date();
        clockTv.setText(timeFmt().format(now));
        if (dateTv != null) dateTv.setText(dateFmt().format(now).toUpperCase(Locale.getDefault()));
        if (System.currentTimeMillis() - lastWx > 1800000) fetchWeather();
        handler.postDelayed(new Runnable() {
            @Override public void run() { tickClock(); }
        }, 20000);
    }


    // v3.90: X96mini heap is 192 MB. fillThemePanel() decoded all 37 theme
    // pictures at full 1920x1080 (8 MB each, ~300 MB) at startup and killed
    // the app with OutOfMemoryError -- the log's "no stack trace available"
    // crash, heap so full even the finalizer could not allocate 8 bytes.
    // Thumbnails decode small, in RGB_565 (no alpha needed for photos).
    android.graphics.Bitmap thumbFor(int resId, int targetW) {
        try {
            android.graphics.BitmapFactory.Options b = new android.graphics.BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeResource(getResources(), resId, b);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            int s = 1;
            while (b.outWidth / s > targetW) s *= 2;
            o.inSampleSize = s;
            o.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeResource(getResources(), resId, o);
        } catch (Throwable t) { return null; }
    }
    // Background photo: always RGB_565 now (was full-colour ARGB_8888 on a
    // normal box, RGB_565 only on a small-heap box) -- one 1280x720 picture
    // is ~1.8 MB instead of ~3.7 MB.
    // v4.16: cache the decoded background bitmap for its resource id.
    // setBaseWallpaper re-decoded the same picture on every call while
    // resumed (Widgets panel open, theme preview tap) even when the
    // picture had not changed. The slideshow still decodes each new
    // picture once (a different id each advance). The cache holds the
    // same bitmap the ImageView already shows, so it adds no retained
    // memory; onStop drops it with the other bitmaps (v3.93).
    int bgCacheRes = 0;
    android.graphics.Bitmap bgCacheBmp = null;
    void setPhoto(android.widget.ImageView iv, int resId) {
        if (iv == null) return;
        android.graphics.Bitmap bmp;
        if (resId == bgCacheRes && bgCacheBmp != null) bmp = bgCacheBmp;
        else {
            bmp = thumbFor(resId, 1920);
            if (bmp != null) { bgCacheRes = resId; bgCacheBmp = bmp; }
        }
        if (bmp != null) { iv.setImageBitmap(bmp); iv.setBackgroundColor(Color.TRANSPARENT); return; }
        iv.setImageResource(resId);
    }

    // ---------- Background: photo slideshow with Ken Burns drift, or a color palette ----------
    // v3.55: a theme with rolling pictures rolls its own set (event themes
    // have 4 each); a single-picture theme shows it statically; a plain
    // colour theme falls back to the photo slideshow or a palette, as before.
    void setBaseWallpaper() {
        String t = getSharedPreferences("ui", MODE_PRIVATE).getString("theme", "gold");
        int[] pics = themePicIds(t);
        if (pics.length == 1) {
            // Single-picture theme: static background, slideshow and palettes stand aside.
            themeSlide = false;
            stopSlideshow();
            if (dynBg != null) dynBg.setVisibility(View.GONE);
            showThemePic(pics[0]);
            return;
        }
        if (pics.length > 1) {
            // Rolling theme pictures: same Ken Burns drift as the photo
            // slideshow, through this theme's own pictures.
            themeSlide = true;
            slidePicFiles = pics;
            slideIdx = 0;
            if (dynBg != null) dynBg.setVisibility(View.GONE);
            if (photo != null) {
                showThemePic(pics[0]);
                startSlideshow();
            }
            return;
        }
        // No theme pictures: photo slideshow or palette, as before.
        themeSlide = false;
        slidePics = PHOTOS;
        int m = bgMode();
        if (m < 0) {
            if (dynBg != null) dynBg.setVisibility(View.GONE);
            if (photo != null) {
                photo.animate().cancel();
                photo.setScaleX(1.0f); photo.setScaleY(1.0f); photo.setAlpha(1f);
                setPhoto(photo, PHOTOS[slideIdx % PHOTOS.length]);
                photo.setVisibility(View.VISIBLE);
                startSlideshow();
            }
        } else {
            stopSlideshow();
            if (photo != null) photo.setVisibility(View.GONE);
            if (dynBg != null) {
                dynBg.setVisibility(View.VISIBLE);
                dynBg.setPalette(m);
            }
        }
    }

    // Show one bundled theme picture (decoded RGB_565 by setPhoto -- the
    // single background view, no crossfade).
    void showThemePic(int res) {
        if (photo == null) return;
        photo.animate().cancel();
        photo.setScaleX(1.0f); photo.setScaleY(1.0f); photo.setAlpha(1f);
        setPhoto(photo, res);
        photo.setVisibility(View.VISIBLE);
    }

    // v3.64: Top picks backdrop. Focused movie's poster fills the screen
    // (its own art, loaded at full size -- not the small card image).
    // Leaving Top picks hides it and the theme background shows again.
    // v4.09 TEST (user hypothesis 2026-11-14): the Top picks backdrop
    // poster is what kills Cemba for RAM on the Xiaomi box -- loading a
    // full-screen poster when visiting Top picks spikes memory and the
    // system kills Cemba, landing on Google Home. NO_PICK_BG turns the
    // backdrop (and its 5s auto-advance) OFF entirely for this test
    // build. Revert by setting it to false.
    static final boolean NO_PICK_BG = false;

    // v4.10: safe parent walk. getParent() returns a ViewParent, and
    // at the root of the hierarchy it is a ViewRootImpl, NOT a View.
    // Casting it to (View) threw ClassCastException and crashed the app
    // (FATAL EXCEPTION in MainActivity$21.run at line 1568, confirmed
    // on the N5 Max logcat 2026-11-14 18:10). When focus leaves Top
    // picks, hidePickBgSoon walks up to the root and crashed -- that is
    // what killed Cemba and dropped the box to Google Home. This returns
    // null at the root instead of crashing. Same bug in startPickAuto
    // and fillSettingsPanel, fixed the same way.
    static View parentView(View v) {
        if (v == null) return null;
        android.view.ViewParent par = v.getParent();
        return (par instanceof View) ? (View) par : null;
    }

    void showPickBg(String posterUri) {
        if (NO_PICK_BG) return;
        if (pickBg == null) return;
        if (pickBgHideR != null) handler.removeCallbacks(pickBgHideR);
        if (posterUri == null || posterUri.length() == 0) { hidePickBg(); return; }
        Img.load(this, pickBg, posterUri);
        pickBg.setVisibility(View.VISIBLE);
        // v3.75: NO bringToFront() at all. The bringToFront() calls were the
        // bug -- they put the poster above the panel, so it showed as a big
        // picture in front covering everything (his report: "i quickly saw
        // it [as background] and than on front big picture"). The natural
        // z-order is already right: pickBg was added after the theme
        // background and before the panels, so it sits behind the rows. The
        // only thing hiding it was the panel's dark background, which is
        // made transparent here so the poster shows through as the tab
        // background.
        if (watchPanel != null) {
            watchPanel.setBackgroundColor(0x00000000);
            View wp = parentView(watchPanel);
            if (wp != null) wp.setBackgroundColor(0x00000000);
        }
    }

    void hidePickBg() {
        if (pickBg == null) return;
        if (pickBgHideR != null) handler.removeCallbacks(pickBgHideR);
        pickBg.setVisibility(View.GONE);
        pickBg.setImageDrawable(null);
        // v3.74: restore the Watching panel dark background when the
        // backdrop hides, so the tab is readable over the theme again.
        if (watchPanel != null) {
            watchPanel.setBackgroundColor(0xD8000000);
            View wp = parentView(watchPanel);
            if (wp != null) wp.setBackgroundColor(0xD8000000);
        }
    }

    void hidePickBgSoon() {
        if (pickBg == null) return;
        if (pickBgHideR == null) pickBgHideR = new Runnable() {
            @Override public void run() {
                // Only hide if focus really left the Top picks row (moving to
                // the next pick cancels this via showPickBg).
                View f = getCurrentFocus();
                boolean stillInPicks = false;
                if (f != null && picksRow != null) {
                    View p = f;
                    while (p != null) {
                        if (p == picksRow) { stillInPicks = true; break; }
                        p = parentView(p);
                    }
                }
                if (!stillInPicks) hidePickBg();
            }
        };
        handler.removeCallbacks(pickBgHideR);
        handler.postDelayed(pickBgHideR, 150);
    }

    void kenBurns(ImageView v) {
        if (v == null) return;
        v.setScaleX(1.0f);
        v.setScaleY(1.0f);
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(13500).start();
    }

    final Runnable photoTickR = new Runnable() {
        @Override public void run() {
            // A theme's own pictures roll even when a palette bgMode is set
            // (the theme overrides it); the photo slideshow stops for palettes.
            if (!themeSlide && bgMode() >= 0) return;
            advancePhoto();
            handler.postDelayed(this, 12000);
        }
    };

    void startSlideshow() {
        handler.removeCallbacks(photoTickR);
        kenBurns(photo);
        handler.postDelayed(photoTickR, 12000);
    }

    void stopSlideshow() { handler.removeCallbacks(photoTickR); }

    void advancePhoto() {
        if (photo == null) return;
        // v3.93: one view now -- fade out, swap the picture, fade back in
        // with the Ken Burns drift. The tick is removed in onStop, but a
        // fade already in flight can still land after pause, so bail out
        // when the activity is not resumed (it would re-decode a picture
        // onStop just dropped).
        final ImageView v = photo;
        final int nextIdx;
        if (themeSlide) {
            if (slidePicFiles == null || slidePicFiles.length == 0) return;
            nextIdx = (slideIdx + 1) % slidePicFiles.length;
        } else {
            if (slidePics == null || slidePics.length == 0) return;
            nextIdx = (slideIdx + 1) % slidePics.length;
        }
        v.animate().alpha(0f).setDuration(700).withEndAction(new Runnable() {
            @Override public void run() {
                if (photo != v || !resumed) return;
                if (themeSlide) {
                    showThemePic(slidePicFiles[nextIdx]);
                } else {
                    setPhoto(v, slidePics[nextIdx]);
                }
                slideIdx = nextIdx;
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
                v.setAlpha(1f);
                kenBurns(v);
            }
        }).start();
    }

    // ---------- Weather (header line + widget card; data in Weather.java) ----------
    static String wxLabel(int c) { return Logic.wxLabel(c); }

    void fetchWeather() {
        lastWx = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                String text = null;
                String daysLine = "";
                int ft = 0, fhi = 0, flo = 0;
                String flab = "";
                int fhum = -1; double fwind = Double.NaN; int fpres = -1;
                String fmeta = "";
                int iconRes = R.drawable.wx_ic_sun;
                String sunRiseText = "", sunSetText = "", sunLenText = "";
                try {
                    URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=43.4516&longitude=-80.4925&current=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m,surface_pressure&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=America%2FToronto");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject j = new JSONObject(sb.toString());
                    JSONObject cur = j.getJSONObject("current");
                    int t = (int) Math.round(cur.getDouble("temperature_2m"));
                    String lab = wxLabel(cur.getInt("weather_code"));
                    JSONObject d = j.getJSONObject("daily");
                    int hi = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
                    int lo = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
                    ft = t; fhi = hi; flo = lo; flab = lab;
                    fhum = cur.optInt("relative_humidity_2m", -1);
                    fwind = cur.optDouble("wind_speed_10m", Double.NaN);
                    double pr = cur.optDouble("surface_pressure", -1);
                    fpres = pr < 0 ? -1 : (int) Math.round(pr);
                    fmeta = (fhum >= 0 ? "Humidity " + fhum + "%   " : "")
                        + (!Double.isNaN(fwind) ? "Wind " + String.format(Locale.US, "%.1f", fwind) + " km/h   " : "")
                        + (fpres >= 0 ? "Pressure " + fpres + " hPa" : "");
                    iconRes = wxIconFor(cur.getInt("weather_code"), cur.optInt("is_day", 1));
                    try {
                        org.json.JSONArray times = d.getJSONArray("time");
                        StringBuilder db = new StringBuilder();
                        for (int k = 1; k < Math.min(4, times.length()); k++) {
                            java.util.Date dd = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(times.getString(k));
                            String dn = new java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(dd);
                            int h2 = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(k));
                            int l2 = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(k));
                            if (db.length() > 0) db.append("    ");
                            db.append(dn).append(" ").append(h2).append("\u00B0/").append(l2).append("\u00B0");
                        }
                        daysLine = db.toString();
                    } catch (Exception e) { daysLine = ""; }
                    text = "Kitchener  " + t + "\u00B0 " + lab + "   H " + hi + "\u00B0  L " + lo + "\u00B0";
                    try {
                        String rise = d.getJSONArray("sunrise").getString(0);
                        String set = d.getJSONArray("sunset").getString(0);
                        sunRiseText = "Sunrise  " + Logic.sunTimeFmt(rise);
                        sunSetText = "Sunset  " + Logic.sunTimeFmt(set);
                        String dl = Logic.dayLength(rise, set);
                        sunLenText = dl.length() > 0 ? "Day length  " + dl : "";
                    } catch (Exception e) { /* sun lines keep their last values */ }
                } catch (Exception e) { text = null; }
                final String f = text;
                final String fdays = daysLine;
                final int fft = ft, ffhi = fhi, fflo = flo;
                final String fflab = flab;
                final String fmetaF = fmeta;
                final int ficon = iconRes;
                final String fsunrise = sunRiseText, fsunset = sunSetText, fsunlen = sunLenText;
                if (f != null) runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (weatherTv != null) weatherTv.setText(f);
                        if (wxCardBig != null) wxCardBig.setText(fft + "\u00B0");
                        if (wxCardSub != null) wxCardSub.setText(fflab);
                        if (wxCardDays != null) wxCardDays.setText("H " + ffhi + "\u00B0   L " + fflo + "\u00B0");
                        if (wxCardMeta != null) wxCardMeta.setText(fmetaF);
                        lastTemp = fft;
                        // v3.51: remember the last values so a rebuilt card
                        // starts from them instead of "..." / "--".
                        lastWxLab = fflab; lastWxHi = ffhi; lastWxLo = fflo;
                        lastWxMeta = fmetaF; lastSunrise = fsunrise;
                        lastSunset = fsunset; lastSunLen = fsunlen; lastWxOk = true;
                        if (wxRingV != null) wxRingV.setRing(Logic.wxRingColorFor(fft, accent), Logic.wxRingFrac(fft));
                        if (wxIconIv != null) wxIconIv.setImageResource(ficon);
                        if (sunRiseTv != null && fsunrise.length() > 0) sunRiseTv.setText(fsunrise);
                        if (sunSetTv != null && fsunset.length() > 0) sunSetTv.setText(fsunset);
                        if (sunLenTv != null) sunLenTv.setText(fsunlen);
                    }
                });
            }
        }).start();
    }

    int wxIconFor(int code, int isDay) {
        String k = Logic.wxIconKey(code, isDay);
        if ("moon".equals(k)) return R.drawable.wx_ic_moon;
        if ("suncloud".equals(k)) return R.drawable.wx_ic_suncloud;
        if ("mooncloud".equals(k)) return R.drawable.wx_ic_mooncloud;
        if ("cloud".equals(k)) return R.drawable.wx_ic_cloud;
        if ("fog".equals(k)) return R.drawable.wx_ic_fog;
        if ("rain".equals(k)) return R.drawable.wx_ic_rain;
        if ("snow".equals(k)) return R.drawable.wx_ic_snow;
        if ("storm".equals(k)) return R.drawable.wx_ic_storm;
        return R.drawable.wx_ic_sun;
    }

    // ---------- Widget cards (widgets row under the header) ----------
    // PLUI-style glowing ring gauge (v3.19): a track circle plus a colored arc.
    // The weather ring's color follows the temperature (Logic.wxRingColor); the
    // Box Care ring shows storage used in gold.
    static class RingView extends View {
        int color = 0xFFE8C874; float frac = 0.5f;
        public RingView(android.content.Context ctx) { super(ctx); }
        void setRing(int c, float f) { color = c; frac = Math.max(0.02f, Math.min(1f, f)); invalidate(); }
        float dpPx(float v) { return v * getResources().getDisplayMetrics().density; }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            super.onDraw(cv);
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - dpPx(6);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setStrokeWidth(dpPx(5));
            p.setColor(0x55FFFFFF);
            cv.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(dpPx(7));
            p.setColor(color);
            p.setAlpha(90);
            cv.drawArc(cx - r, cy - r, cx + r, cy + r, -90, 360 * frac, false, p);
            p.setAlpha(255);
            cv.drawArc(cx - r, cy - r, cx + r, cy + r, -90, 360 * frac, false, p);
        }
    }

    View weatherCard(int size) {
        FrameLayout card = new FrameLayout(this);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), hdp(16));
            }
        });
        card.setClipToOutline(true);
        View shade = new View(this);
        shade.setBackgroundColor(0x690E121A);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        // PLUI climate style (v3.19): the temperature sits inside a glowing ring on
        // the left; condition, H/L and the humidity/wind/pressure line stack right.
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(hdp(14), hdp(12), hdp(14), hdp(10));
        body.setGravity(Gravity.CENTER_VERTICAL);
        // v3.55: the ring box is the ring's own size and the ring is centred in
        // it -- on Small the 64dp ring used to sit top-left in a fixed 96dp box
        // while the temperature was centred in the box, so the 6\u00B0 landed at the
        // bottom-right of the ring (N5 Max photo).
        int ring = hdp(Logic.wxRingSize(size));
        FrameLayout ringBox = new FrameLayout(this);
        wxRingV = new RingView(this);
        ringBox.addView(wxRingV, new FrameLayout.LayoutParams(ring, ring, Gravity.CENTER));
        wxCardBig = new TextView(this);
        wxCardBig.setText("...");
        wxCardBig.setTextColor(Color.WHITE);
        wxCardBig.setTextSize(hsp(size == 0 ? 20 : 26));
        wxCardBig.setTypeface(wxCardBig.getTypeface(), android.graphics.Typeface.BOLD);
        wxCardBig.setShadowLayer(8, 0, 2, Color.BLACK);
        wxCardBig.setGravity(Gravity.CENTER);
        ringBox.addView(wxCardBig, new FrameLayout.LayoutParams(-1, -1));
        body.addView(ringBox, new LinearLayout.LayoutParams(ring, ring));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = hdp(12);
        texts.setLayoutParams(tlp);
        TextView cap = new TextView(this);
        wxCapTv = cap;
        cap.setText(t("weather"));
        // v3.51: "WEATHER - KITCHENER" wrapped to two lines in English.
        cap.setSingleLine(true);
        cap.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cap.setTextColor(Color.rgb(232, 200, 116));
        cap.setTextSize(hsp(13));
        cap.setShadowLayer(6, 0, 2, Color.BLACK);
        texts.addView(cap);
        wxCardSub = new TextView(this);
        wxCardSub.setTextColor(Color.rgb(225, 225, 230));
        wxCardSub.setTextSize(hsp(15));
        wxCardSub.setShadowLayer(6, 0, 2, Color.BLACK);
        texts.addView(wxCardSub);
        wxCardDays = new TextView(this);
        wxCardDays.setTextColor(Color.rgb(190, 215, 245));
        wxCardDays.setTextSize(hsp(14));
        wxCardDays.setShadowLayer(6, 0, 2, Color.BLACK);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(-2, -2);
        dlp2.topMargin = hdp(4);
        texts.addView(wxCardDays, dlp2);
        wxCardMeta = new TextView(this);
        wxCardMeta.setTextColor(Color.rgb(190, 215, 245));
        wxCardMeta.setTextSize(hsp(12));
        wxCardMeta.setShadowLayer(6, 0, 2, Color.BLACK);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = hdp(6);
        texts.addView(wxCardMeta, mlp);
        // v3.51: start from the last fetched values, not "..." -- the home row
        // is rebuilt when he comes back home, and the fetch only runs every
        // 30 min, so a fresh card would sit empty until then.
        if (lastWxOk) {
            wxCardBig.setText(lastTemp + "\u00B0");
            wxCardSub.setText(lastWxLab);
            wxCardDays.setText("H " + lastWxHi + "\u00B0   L " + lastWxLo + "\u00B0");
            wxCardMeta.setText(lastWxMeta);
            wxRingV.setRing(Logic.wxRingColorFor(lastTemp, accent), Logic.wxRingFrac(lastTemp));
        }
        body.addView(texts);
        card.addView(body, new FrameLayout.LayoutParams(-1, -1));
        // v3.50: the condition icon stays off Small -- at 260dp it sat on top
        // of the "METEO - KITCHENER" cap (his photo). The condition is already
        // written out as text ("Fog"), so Small loses nothing.
        if (size != 0) {
            wxIconIv = new ImageView(this);
            wxIconIv.setImageResource(R.drawable.wx_ic_sun);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(hdp(34), hdp(34),
                Gravity.TOP | Gravity.RIGHT);
            ilp.topMargin = hdp(10); ilp.rightMargin = hdp(10);
            card.addView(wxIconIv, ilp);
        } else { wxIconIv = null; }
        card.setContentDescription("Kitchener weather forecast");
        focusable(card);
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { cycleWidgetSizeFor("weather"); return true; } });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setClassName("com.bacem.launcher", "com.bacem.launcher.WeatherActivity");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) { fetchWeather(); }
            }
        });
        int wh = Logic.widgetHeight(size);
        int ww = size == 0 ? 260 : size == 2 ? 360 : 320;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(hdp(ww), hdp(wh));
        lp.rightMargin = hdp(12);
        card.setLayoutParams(lp);
        return card;
    }

    // Widget card base: flat translucent dark glass, rounded corners (v3.21:
    // the photo inside is gone -- the wallpaper shows through).
    FrameLayout photoCard(int wDp, int hDp, int rightMargin) {
        FrameLayout card = new FrameLayout(this);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), hdp(16));
            }
        });
        card.setClipToOutline(true);
        // Flat translucent dark glass; the wallpaper shows through.
        View shade = new View(this);
        shade.setBackgroundColor(0x690E121A);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(hdp(wDp), hdp(hDp));
        lp.rightMargin = hdp(rightMargin);
        card.setLayoutParams(lp);
        return card;
    }

    TextView cardCap(String s, int argb) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(argb);
        t.setTextSize(hsp(12));
        t.setShadowLayer(6, 0, 2, Color.BLACK);
        return t;
    }

    TextView cardLine(int size, int argb) {
        TextView t = new TextView(this);
        t.setTextColor(argb);
        t.setTextSize(hsp(size));
        t.setShadowLayer(6, 0, 2, Color.BLACK);
        return t;
    }

    // One icon + text line inside a widget card (the sun card's sunrise/sunset rows).
    LinearLayout iconLine(int iconRes, TextView tv) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        r.addView(iv, new LinearLayout.LayoutParams(hdp(16), hdp(16)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = hdp(7);
        r.addView(tv, tlp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = hdp(8);
        r.setLayoutParams(lp);
        return r;
    }

    // Sun card: sunrise, sunset and day length for Kitchener (from the weather feed).
    View sunCard(int size) {
        FrameLayout card = photoCard(size == 0 ? 150 : size == 2 ? 210 : 180, Logic.widgetHeight(size), 12);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(hdp(14), hdp(10), hdp(12), hdp(8));
        sunCapTv = cardCap(t("sun"), Color.rgb(232, 200, 116));
        texts.addView(sunCapTv);
        sunRiseTv = cardLine(14, Color.WHITE);
        sunRiseTv.setText("Sunrise  --");
        texts.addView(iconLine(R.drawable.wx_ic_sun, sunRiseTv));
        sunSetTv = cardLine(14, Color.WHITE);
        sunSetTv.setText("Sunset  --");
        texts.addView(iconLine(R.drawable.wx_ic_moon, sunSetTv));
        sunLenTv = cardLine(12, Color.rgb(225, 232, 240));
        sunLenTv.setText("");
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = hdp(9);
        texts.addView(sunLenTv, llp);
        // v3.51: start from the last fetched values (see weatherCard).
        if (lastSunrise.length() > 0) sunRiseTv.setText(lastSunrise);
        if (lastSunset.length() > 0) sunSetTv.setText(lastSunset);
        if (lastSunLen.length() > 0) sunLenTv.setText(lastSunLen);
        card.addView(texts, new FrameLayout.LayoutParams(-1, -1));
        card.setContentDescription("Sun - sunrise and sunset");
        card.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        focusable(card);
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { cycleWidgetSizeFor("sun"); return true; } });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setClassName("com.bacem.launcher", "com.bacem.launcher.WeatherActivity");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) { fetchWeather(); }
            }
        });
        return card;
    }

    TextView careBtn(String label, boolean primary, final Runnable action) {
        final TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(hsp(12));
        if (primary) b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? Color.rgb(30, 110, 200) : 0xB3141824);
        g.setCornerRadius(hdp(9));
        if (!primary) g.setStroke(hdp(1), 0x78FFFFFF);
        b.setBackground(g);
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                // v4.13: a focused button fills with the theme accent
                // (same UI as the Wi-Fi pill, but themed). On blur the
                // chip returns to its active/inactive look.
                if (v instanceof TextView) chipLook((TextView) v);
                v.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(110).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, hdp(25), 1f);
        lp.rightMargin = hdp(6);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return b;
    }

    LinearLayout btnRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = hdp(4);
        r.setLayoutParams(lp);
        return r;
    }

    // Box care card: live RAM/storage readouts + the care actions.
    View boxCareCard(int size) {
        // 160dp fits the full content (cap + device + RAM + bar + storage + one
        // button row) with slack at larger text. 150dp clipped the second button
        // row (v3.30 lesson); 185dp left the box looking empty (v3.32). The 50dp
        // ring ends at 58dp, the RAM bar starts ~68dp, so they never cross (v3.34).
        FrameLayout card = photoCard(size == 0 ? 240 : size == 2 ? 340 : 300, Logic.widgetHeight(size), 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(hdp(14), hdp(9), hdp(12), hdp(7));
        careCapTv = cardCap(t("box_care"), Color.rgb(232, 200, 116));
        texts.addView(careCapTv);
        deviceTv = cardLine(12, Color.rgb(190, 215, 245));
        deviceTv.setText(android.os.Build.MODEL + "  \u00B7  Android " + android.os.Build.VERSION.RELEASE);
        LinearLayout.LayoutParams dvp = new LinearLayout.LayoutParams(-2, -2);
        dvp.topMargin = hdp(3);
        texts.addView(deviceTv, dvp);
        ramTv = cardLine(13, Color.WHITE);
        ramTv.setText("RAM free  --");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = hdp(4);
        texts.addView(ramTv, rlp);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, hdp(6));
        blp.topMargin = hdp(3);
        bar.setLayoutParams(blp);
        ramFillV = new View(this);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(accent);
        fg.setCornerRadius(hdp(3));
        ramFillV.setBackground(fg);
        ramFillV.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 50f));
        ramRestV = new View(this);
        ramRestV.setBackgroundColor(0x55FFFFFF);
        ramRestV.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 50f));
        bar.addView(ramFillV);
        bar.addView(ramRestV);
        texts.addView(bar);
        storageTv = cardLine(13, Color.WHITE);
        storageTv.setText("Storage free  --");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = hdp(3);
        texts.addView(storageTv, slp);
        // v3.31: both actions on one row (his ask) -- each button takes half the row
        LinearLayout b1 = btnRow();
        b1.addView(careBtn(t("clear_ram"), true, new Runnable() {
            @Override public void run() { clearRam(); }
        }));
        final TextView[] speedBtn = new TextView[1];
        speedBtn[0] = careBtn(t("speed_test"), false, new Runnable() {
            @Override public void run() { speedTest(speedBtn[0]); }
        });
        b1.addView(speedBtn[0]);
        texts.addView(b1);
        card.addView(texts, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout ringBox = new FrameLayout(this);
        storageRingV = new RingView(this);
        ringBox.addView(storageRingV, new FrameLayout.LayoutParams(hdp(50), hdp(50)));
        storagePctTv = new TextView(this);
        storagePctTv.setText("--%");
        storagePctTv.setTextColor(Color.WHITE);
        storagePctTv.setTextSize(hsp(12));
        storagePctTv.setTypeface(storagePctTv.getTypeface(), android.graphics.Typeface.BOLD);
        storagePctTv.setGravity(Gravity.CENTER);
        ringBox.addView(storagePctTv, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams rlp2 = new FrameLayout.LayoutParams(hdp(50), hdp(50), Gravity.TOP | Gravity.RIGHT);
        rlp2.topMargin = hdp(8); rlp2.rightMargin = hdp(12);
        card.addView(ringBox, rlp2);
        card.setContentDescription("Box care");
        card.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        focusable(card);
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { cycleWidgetSizeFor("care"); return true; } });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearRam(); }
        });
        return card;
    }

    // ---------- Timer widget (v3.60): the Kids profile's countdown. ----------
    // Shows the time left today (daily limit, else time until bedtime) and
    // rings when it runs out. It is the first widget in the Kids profile.
    TextView timerTv, timerSubTv;

    View timerCard(int size) {
        FrameLayout card = photoCard(size == 0 ? 240 : size == 2 ? 340 : 300, Logic.widgetHeight(size), 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(hdp(14), hdp(9), hdp(12), hdp(7));
        texts.addView(cardCap("TIME LEFT", Color.rgb(232, 200, 116)));
        timerTv = new TextView(this);
        timerTv.setText("--");
        timerTv.setTextColor(Color.WHITE);
        timerTv.setTextSize(hsp(size == 0 ? 26 : 32));
        timerTv.setTypeface(timerTv.getTypeface(), android.graphics.Typeface.BOLD);
        timerTv.setShadowLayer(8, 0, 2, Color.BLACK);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = hdp(6);
        texts.addView(timerTv, tlp);
        timerSubTv = cardLine(13, Color.rgb(225, 225, 230));
        timerSubTv.setText("");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = hdp(4);
        texts.addView(timerSubTv, slp);
        card.addView(texts, new FrameLayout.LayoutParams(-1, -1));
        card.setContentDescription("Timer - tap to change the daily limit (PIN)");
        card.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        focusable(card);
        // v3.60: tap the Timer to edit the time (his ask). PIN-gated, so the
        // kid cannot extend their own time -- a grown-up enters the PIN,
        // then the Daily limit editor opens.
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pinPrompt(new Runnable() {
                    @Override public void run() { kidsLimitDialog(true); }
                });
            }
        });
        updateTimerWidget();
        return card;
    }

    /** Minutes left for the Kids profile today. -1 when no limit and no bedtime. */
    int kidsRemainingMin() {
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        int limit = p.getInt("kids_limit_min", 0);
        if (limit > 0) return Math.max(0, limit - kidsUsedToday());
        String bt = p.getString("kids_bedtime", "");
        if (bt.length() > 0) {
            String[] parts = bt.split(":");
            if (parts.length == 2) {
                try {
                    int bed = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
                    int rem = bed - now;
                    if (rem < 0) rem += 24 * 60;
                    return rem;
                } catch (Exception e) {}
            }
        }
        return -1;
    }

    void updateTimerWidget() {
        if (timerTv == null) return;
        int rem = kidsRemainingMin();
        if (rem < 0) {
            timerTv.setText("--");
            if (timerSubTv != null) timerSubTv.setText("No daily limit set");
        } else if (rem == 0) {
            timerTv.setText("Time's up");
            if (timerSubTv != null) timerSubTv.setText("Ask a grown-up");
        } else {
            int h = rem / 60, m = rem % 60;
            timerTv.setText(h > 0 ? (h + " h " + m + " min") : (m + " min"));
            if (timerSubTv != null) {
                int limit = getSharedPreferences("ui", MODE_PRIVATE).getInt("kids_limit_min", 0);
                timerSubTv.setText(limit > 0 ? "left today" : "until bedtime");
            }
        }
    }

    /** Rings the default alarm sound once (v3.60): the Timer rings when the
     *  kid's time is up, so the end is heard, not just seen in a dialog.
     *  v3.95: also sound on the MUSIC stream. On a TV box the alarm stream
     *  is often silent (volume 0, or not routed to the soundbar), so the
     *  alarm ringtone alone was not heard (his report: timer worked, no
     *  sound). The music stream is the one YouTube Kids plays on and is
     *  always audible -- a 3-second tone so it is heard over the video. */
    void ringTimer() {
        try {
            android.net.Uri u = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, u);
            if (r != null) r.play();
        } catch (Exception e) {}
        try {
            android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);
            tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000);
        } catch (Exception e) {}
    }

    // ---------- Clock widget (v3.60): a kid-friendly clock. ----------
    // Big time, then the day and date, plus School day / Weekend -- so
    // Jannah can read the time and know if it is a school day. It is in
    // the Kids default widgets (Timer, Clock, Weather) and can be added to
    // Family from the Widgets panel too. Refreshed every minute.
    TextView clockWgtTv, clockWgtDateTv;

    View clockCard(int size) {
        FrameLayout card = photoCard(size == 0 ? 240 : size == 2 ? 340 : 300, Logic.widgetHeight(size), 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(hdp(14), hdp(9), hdp(12), hdp(7));
        texts.addView(cardCap("TIME", Color.rgb(232, 200, 116)));
        clockWgtTv = new TextView(this);
        clockWgtTv.setText("--:--");
        clockWgtTv.setTextColor(Color.WHITE);
        clockWgtTv.setTextSize(hsp(size == 0 ? 26 : 32));
        clockWgtTv.setTypeface(clockWgtTv.getTypeface(), android.graphics.Typeface.BOLD);
        clockWgtTv.setShadowLayer(8, 0, 2, Color.BLACK);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = hdp(6);
        texts.addView(clockWgtTv, tlp);
        clockWgtDateTv = cardLine(13, Color.rgb(225, 225, 230));
        clockWgtDateTv.setText("");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = hdp(4);
        texts.addView(clockWgtDateTv, slp);
        card.addView(texts, new FrameLayout.LayoutParams(-1, -1));
        card.setContentDescription("Clock");
        card.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        focusable(card);
        updateClockWidget();
        return card;
    }

    void updateClockWidget() {
        if (clockWgtTv == null) return;
        java.util.Date now = new Date();
        clockWgtTv.setText(timeFmt().format(now));
        if (clockWgtDateTv != null) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            int dow = c.get(java.util.Calendar.DAY_OF_WEEK);
            String kind = (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) ? "Weekend" : "School day";
            clockWgtDateTv.setText(dateLongFmt().format(now) + "  \u00B7  " + kind);
        }
    }

    Runnable clockTickR;
    void startClockTick() {
        if (clockTickR == null) clockTickR = new Runnable() {
            @Override public void run() { updateClockWidget(); handler.postDelayed(clockTickR, 60000); }
        };
        handler.removeCallbacks(clockTickR);
        handler.postDelayed(clockTickR, 60000);
        updateClockWidget();
    }

    // v3.47: rebuilds the third line from prefs (order, hidden set, size).
    // Now Playing bar, exactly as it was on home (v3.50, his call): one
    // horizontal row -- Prev / Pause / Next talk to whichever app is actually
    // playing (Spotify or SmartTube) through media keys, so the music keeps
    // playing while he browses. The title names the app he started; a live
    // track name would need the notification-listener permission, which this
    // launcher does not ask for.
    View nowPlayingBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(hdp(16), hdp(10), hdp(16), hdp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xB3141824);
        g.setCornerRadius(hdp(16));
        g.setStroke(hdp(1), 0xFFFF0000);
        bar.setBackground(g);
        TextView cap = new TextView(this);
        cap.setText(t("now_playing"));
        cap.setTextColor(0xFFFF0000);
        cap.setTextSize(hsp(12));
        cap.setTypeface(cap.getTypeface(), android.graphics.Typeface.BOLD);
        bar.addView(cap);
        nowPlayingTitleTv = new TextView(this);
        nowPlayingTitleTv.setText("YouTube - tap to start");
        nowPlayingTitleTv.setTextColor(Color.WHITE);
        nowPlayingTitleTv.setTextSize(hsp(15));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = hdp(14);
        nowPlayingTitleTv.setLayoutParams(tlp);
        bar.addView(nowPlayingTitleTv);
        final TextView prevBtn = miniBtn("Prev", new Runnable() { @Override public void run() { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); } });
        bar.addView(prevBtn);
        bar.addView(miniBtn("Pause", new Runnable() { @Override public void run() { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); } }));
        bar.addView(miniBtn("Next", new Runnable() { @Override public void run() { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); } }));
        bar.addView(miniBtn("YouTube", new Runnable() { @Override public void run() { openYouTubeMusic(); } }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, hdp(56));
        lp.topMargin = hdp(6);
        bar.setLayoutParams(lp);
        bar.setContentDescription("Now playing - YouTube");
        // v3.51: OK on the bar itself starts YouTube (SmartTube) -- pressing OK
        // while the bar is focused did nothing before. Right on the bar moves
        // into the buttons (focus could skip them otherwise); Left on Prev
        // comes back to the bar.
        bar.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openYouTubeMusic(); } });
        bar.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { prevBtn.requestFocus(); return true; }
                return false;
            }
        });
        prevBtn.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { bar.requestFocus(); return true; }
                return false;
            }
        });
        focusable(bar);
        return bar;
    }

    TextView miniBtn(String label, final Runnable act) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(hsp(14));
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(hdp(12), hdp(8), hdp(12), hdp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xB3141824);
        g.setCornerRadius(hdp(12));
        g.setStroke(hdp(1), 0x78FFFFFF);
        b.setBackground(g);
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(110).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = hdp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { act.run(); } });
        return b;
    }

    // v4.14: a tab chip that switches on focus, not just on OK -- the
    // Watching tabs were needing an OK press to access. Focus moves to the
    // tab and the section follows at once; OK still works too. Focus on a
    // chip that is already the current section just re-runs showWatchSection
    // (idempotent) and requestFocus on a focused view is a no-op, so there
    // is no loop.
    TextView miniBtnFocus(String label, final Runnable act) {
        TextView b = miniBtn(label, act);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (v instanceof TextView) chipLook((TextView) v);
                v.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(110).start();
                if (has) act.run();
            }
        });
        return b;
    }

    // v3.50 Widgets panel: the widgets themselves live here now (his ask) --
    // home is clean. Each widget shows its live card, then control rows to
    // move it up/down, change its own size, or hide it. Hidden widgets show
    // an OFF row so they can come back. Caps/rings are recolored at the end
    // (applyTheme touches the live cards through the field refs).
    void openWidgetsPanel() { showPanel(widgetsPanel); fillWidgetsPanel(); fetchWeather(); try { refreshBoxCare(); } catch (Throwable t) {} }

    void fillWidgetsPanel() {
        while (widgetsPanel.getChildCount() > 1) widgetsPanel.removeViewAt(1);
        final String[] names = {"weather", "sun", "care", "nowplaying", "timer", "clock"};
        final String[] labels = {"Weather", "Sun", "Box Care", "Now Playing", "Timer", "Clock"};
        for (int i = 0; i < names.length; i++) {
            final String w = names[i];
            final String label = labels[i];
            if (widgetOff(w)) {
                panelRow(widgetsPanel, label + ": OFF", "Tap to show it in Widgets again",
                    new Runnable() { @Override public void run() { Set<String> o = csvSet("widget_off" + wSuffix()); o.remove(w); saveCsvSet("widget_off" + wSuffix(), o); toast(label + " shown"); fillWidgetsPanel(); } });
                continue;
            }
            if ("weather".equals(w)) widgetsPanel.addView(weatherCard(widgetSizeFor(w)));
            else if ("sun".equals(w)) widgetsPanel.addView(sunCard(widgetSizeFor(w)));
            else if ("care".equals(w)) widgetsPanel.addView(boxCareCard(widgetSizeFor(w)));
            else if ("timer".equals(w)) widgetsPanel.addView(timerCard(widgetSizeFor(w)));
            else if ("clock".equals(w)) widgetsPanel.addView(clockCard(widgetSizeFor(w)));
            else if ("nowplaying".equals(w)) {
                nowPlayingBar = nowPlayingBar();
                panelNowPlayingTitleTv = nowPlayingTitleTv;
                nowPlayingBar.setLayoutParams(new LinearLayout.LayoutParams(dp(1000), dp(56)));
                widgetsPanel.addView(nowPlayingBar);
            }
            panelRow(widgetsPanel, label + " size: " + Logic.widgetSizeName(widgetSizeFor(w)),
                "Tap to change Small / Medium / Large for this widget",
                new Runnable() { @Override public void run() { cycleWidgetSizeFor(w); } });
            panelRow(widgetsPanel, "Move " + label + " up",
                "Moves it one step up in Widgets",
                new Runnable() { @Override public void run() { getSharedPreferences("ui", MODE_PRIVATE).edit().putString("widget_order" + wSuffix(), Logic.moveWidget(widgetOrder(), w, -1)).apply(); toast(label + " moved up"); fillWidgetsPanel(); } });
            panelRow(widgetsPanel, "Move " + label + " down",
                "Moves it one step down in Widgets",
                new Runnable() { @Override public void run() { getSharedPreferences("ui", MODE_PRIVATE).edit().putString("widget_order" + wSuffix(), Logic.moveWidget(widgetOrder(), w, 1)).apply(); toast(label + " moved down"); fillWidgetsPanel(); } });
            panelRow(widgetsPanel, "Hide " + label,
                "Removes it from Widgets - tap its OFF row to bring it back",
                new Runnable() { @Override public void run() { Set<String> o = csvSet("widget_off" + wSuffix()); o.add(w); saveCsvSet("widget_off" + wSuffix(), o); toast(label + " hidden"); fillWidgetsPanel(); } });
        }
        panelRow(widgetsPanel, "Reset widgets", "Back to the default widgets for this profile - all Small",
            new Runnable() { @Override public void run() { SharedPreferences.Editor e = getSharedPreferences("ui", MODE_PRIVATE).edit(); e.putString("widget_order" + wSuffix(), kidsMode() ? Logic.widgetOrderDefaultKids() : Logic.widgetOrderDefault()); e.putString("widget_off" + wSuffix(), ""); e.putInt("widget_size" + wSuffix(), 0); e.remove("widget_size_weather" + wSuffix()); e.remove("widget_size_sun" + wSuffix()); e.remove("widget_size_care" + wSuffix()); e.remove("widget_size_timer" + wSuffix()); e.remove("widget_size_clock" + wSuffix()); e.apply(); toast("Widgets reset"); fillWidgetsPanel(); } });
        applyTheme(getSharedPreferences("ui", MODE_PRIVATE).getString("theme", "gold"), false);
    }

    // v3.50 (restored, his call): the widgets live on home again, exactly as
    // they were -- the Weather / Sun / Box Care row, then the Now Playing bar.
    // Idempotent: it rebuilds the row from the current order/sizes, so it can
    // be called again on showHome to refresh the values and to point the
    // update fields back at the home cards (the Widgets panel builds its own
    // copies, which would otherwise steal the field refs). The Now Playing
    // title is preserved across rebuilds.
    void buildWidgetsRow() {
        if (homeCol == null || sublineTv == null) return;
        String np = (homeNowPlayingTitleTv != null) ? homeNowPlayingTitleTv.getText().toString() : null;
        if (widgetsRow != null && widgetsRow.getParent() instanceof ViewGroup) {
            ViewGroup wrap = (ViewGroup) widgetsRow.getParent();
            if (wrap.getParent() instanceof ViewGroup) ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        if (homeNowPlayingBar != null && homeNowPlayingBar.getParent() instanceof ViewGroup)
            ((ViewGroup) homeNowPlayingBar.getParent()).removeView(homeNowPlayingBar);
        widgetsRow = newRow();
        for (String w : widgetOrder().split(",")) {
            if (widgetOff(w)) continue;
            int sz = widgetSizeFor(w);
            if ("weather".equals(w)) widgetsRow.addView(weatherCard(sz));
            else if ("sun".equals(w)) widgetsRow.addView(sunCard(sz));
            else if ("care".equals(w)) widgetsRow.addView(boxCareCard(sz));
            else if ("timer".equals(w)) widgetsRow.addView(timerCard(sz));
            else if ("clock".equals(w)) widgetsRow.addView(clockCard(sz));
        }
        int idx = homeCol.indexOfChild(sublineTv) + 1;
        homeCol.addView(scrollOf(widgetsRow), idx);
        // v3.60: the Now Playing bar only shows when this profile has it.
        // The Kids default order is Timer + Clock + Weather, so the music bar
        // is gone from Jannah's home; Family keeps it (it is in the Family
        // order). Hiding it in the Widgets panel hides it here too.
        homeNowPlayingBar = nowPlayingBar();
        homeNowPlayingTitleTv = nowPlayingTitleTv;
        if (np != null) homeNowPlayingTitleTv.setText(np);
        boolean showNp = false;
        for (String w : widgetOrder().split(",")) if ("nowplaying".equals(w)) showNp = true;
        if (showNp && !widgetOff("nowplaying")) homeCol.addView(homeNowPlayingBar, idx + 1);
        // v3.51: fill the Box Care readouts right away (live local read, no
        // network) so the rebuilt card never shows "RAM free --".
        try { refreshBoxCare(); } catch (Throwable t) {}
    }

    // ---------- v3.50 Watching tab ----------
    void buildWatchSections() {
        // v3.67: "live" removed from the Watching tab (his ask) -- the Live
        // TV guide now lives in the header Live TV tab, so the old Live TV
        // row here was redundant.
        // v4.18: Continue watching Kids sits right after Continue
        // watching Family (his ask) -- order is now picks, family,
        // kids, music, news.
        String[] keys = {"picks", "watch", "kids", "music", "news"};
        String[] labels = {t("top_picks"), t("continue_watching"), t("kids"), t("music"), t("news")};
        watchChipsRow = newRow();
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            TextView chip = miniBtnFocus(labels[i], new Runnable() { @Override public void run() { showWatchSection(k); } });
            // v3.86: stable IDs so a row card can point its Up key at its
            // own tab chip. Going back up from a Top picks card to the
            // "Top picks" title/chip was exiting to Google TV Home (his
            // report 2026-11-13 16:42) -- with no explicit Up target the
            // focus search could leave the panel. Now Up from a card lands
            // on its section's chip, inside Cemba, every time.
            int cid = View.generateViewId();
            chip.setId(cid);
            watchChipIds.put(k, cid);
            watchChips.put(k, chip);
            watchChipsRow.addView(chip);
        }
        // v3.94: a "YouTube Kids" tab for boxes without a TV provider. It is
        // an action, not a section -- tapping it opens the YouTube Kids app
        // directly (a standalone app on the N5 Max). It only shows when the
        // TV tabs are hidden (see applyTabVisibility).
        kidsAppChip = miniBtn("YouTube Kids", new Runnable() { @Override public void run() { openYouTubeKids(); } });
        kidsAppChip.setId(View.generateViewId());
        watchChipsRow.addView(kidsAppChip);
        watchPanel.addView(scrollOf(watchChipsRow));
        for (String k : keys) {
            LinearLayout sec = new LinearLayout(this);
            sec.setOrientation(LinearLayout.VERTICAL);
            watchSecs.put(k, sec);
            watchPanel.addView(sec);
        }
        showWatchSection(watchSection());
        applyTabVisibility();
    }

    // v3.94: per-box tabs. Boxes without a TV provider (N5 Max, X96mini --
    // phone Android, no TvContract) can never fill Top picks / Continue
    // watching / Music / Kids, so those tabs are hidden there and a
    // "YouTube Kids" tab takes their place. The Xiaomi keeps everything.
    // Auto (default) decides from Publish.tvAvailable(); Settings > Box >
    // Tabs can force either way.
    boolean simpleTabs() {
        String m = getSharedPreferences("ui", MODE_PRIVATE).getString("tabs_mode", "auto");
        if ("full".equals(m)) return false;
        if ("simple".equals(m)) return true;
        return !Publish.tvAvailable();
    }

    // v4.00: Top picks is NOT a TV tab any more -- it is the app row
    // (Tubi / Pluto TV / Netfly cards), it needs no TV provider, so it
    // shows on every box, including the X96mini and N5 Max.
    // v4.15: Continue watching and Music show on no-provider boxes too --
    // their fallback content needs no provider (the YouTube history card
    // and the static Cobalt music cards). Only Kids still hides there.
    boolean isTvTabHidden(String k) {
        return simpleTabs() && "kids".equals(k);
    }

    void applyTabVisibility() {
        if (watchChipsRow == null) return;
        boolean simple = simpleTabs();
        // v4.15: hide per tab, not blanket -- Continue watching and Music
        // show on no-provider boxes now (fallback content), only Kids hides.
        for (String k : new String[]{"watch", "music", "kids"}) {
            boolean hidden = isTvTabHidden(k);
            TextView chip = watchChips.get(k);
            if (chip != null) chip.setVisibility(hidden ? View.GONE : View.VISIBLE);
            LinearLayout sec = watchSecs.get(k);
            if (sec != null) sec.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
        TextView picksChip = watchChips.get("picks");
        if (picksChip != null) picksChip.setVisibility(View.VISIBLE);
        TextView newsChip = watchChips.get("news");
        if (newsChip != null) newsChip.setVisibility(View.VISIBLE);
        if (kidsAppChip != null) kidsAppChip.setVisibility(simple ? View.VISIBLE : View.GONE);
        // v4.42: Kids mode -- the Watching panel shows only Continue
        // watching Kids (his ask: only kids content in the Kids profile;
        // his photo 2026-11-17 showed all 5 chips with Top picks empty).
        // Top picks, Continue watching Family, Music and News hide, so
        // Jannah never sees a family section or an empty Top picks. The
        // Kids section itself is left alone -- it is the only one with
        // content in Kids mode. On a box without a TV provider the Kids
        // tab is already hidden (see isTvTabHidden) and the YouTube Kids
        // chip takes its place, so the move-to-Kids only runs when the
        // Kids tab is available.
        if (kidsMode()) {
            for (String k : new String[]{"picks", "watch", "music", "news"}) {
                TextView chip = watchChips.get(k);
                if (chip != null) chip.setVisibility(View.GONE);
                LinearLayout sec = watchSecs.get(k);
                if (sec != null) sec.setVisibility(View.GONE);
            }
            if (!"kids".equals(watchSection()) && !isTvTabHidden("kids")) showWatchSection("kids");
        }
        // If the section we are on just got hidden, move to News (always
        // visible). Only then -- never yank focus on a plain resume.
        if (simple && isTvTabHidden(watchSection())) showWatchSection("news");
    }

    void openYouTubeKids() {
        try {
            Intent li = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.youtube.kids");
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); return; }
            toast("YouTube Kids is not installed on this box");
        } catch (Exception e) { toast("YouTube Kids is not installed on this box"); }
    }

    String tabsModeLabel() {
        String m = getSharedPreferences("ui", MODE_PRIVATE).getString("tabs_mode", "auto");
        if ("full".equals(m)) return "Show all tabs";
        if ("simple".equals(m)) return "Hide TV tabs (Kids)";
        return Publish.tvAvailable() ? "Auto - all tabs (this box has TV listings)"
            : "Auto - Kids tab hidden (no TV listings on this box)";
    }

    void showWatchSection(String k) {
        // v4.42: Kids mode shows only Continue watching Kids -- any other
        // section redirects to it (unless the Kids tab is hidden on this
        // box, in which case the TV-tab check below takes over).
        if (kidsMode() && !"kids".equals(k) && !isTvTabHidden("kids")) k = "kids";
        // v3.94: never show a TV tab that is hidden on this box -- fall back to News.
        if (isTvTabHidden(k)) k = "news";
        if (!watchSecs.containsKey(k)) k = "picks";
        // v3.64: leaving Top picks restores the theme background.
        if (!"picks".equals(k)) hidePickBg();
        // v4.16: only write when the section actually changed. Since v4.14
        // the tabs switch on focus, so moving across the chips wrote the
        // pref to disk on every focus move.
        if (!k.equals(watchSection())) getSharedPreferences("ui", MODE_PRIVATE).edit().putString("watch_section", k).apply();
        for (java.util.Map.Entry<String, LinearLayout> e : watchSecs.entrySet()) {
            boolean vis = k.equals(e.getKey());
            // v4.42: Kids mode -- never show a non-kids section, even if
            // it is the selected one. On a box without a TV provider the
            // Kids tab itself is hidden, so every section stays hidden
            // and the YouTube Kids chip is the only action.
            if (kidsMode() && !"kids".equals(e.getKey())) vis = false;
            e.getValue().setVisibility(vis ? View.VISIBLE : View.GONE);
        }
        for (java.util.Map.Entry<String, TextView> e : watchChips.entrySet())
            setChip(e.getValue(), k.equals(e.getKey()));
        // v3.79: move focus to the selected tab chip. Tapping a tab (e.g.
        // News) was opening a card from the previous section (e.g. a Tubi
        // movie in Top picks) because the focus stayed on that hidden card
        // (his report: "When i clicked on news tab... it opened tubi").
        // Focus now sits on the tab itself, so OK switches sections instead
        // of opening an app.
        TextView chip = watchChips.get(k);
        if (chip != null) chip.requestFocus();
        // v3.81: Top picks auto-advance. Start it ONLY when the user
        // manually focuses a movie card (in the pickTile focus listener),
        // not on entering Top picks. Starting it on entry was opening
        // YouTube by itself when he went back to the tab (his report:
        // "it opened youtube when i went back to the tab pick" -- he did
        // not press OK). Leaving Top picks stops it.
        if (!"picks".equals(k)) {
            stopPickAuto();
        }
    }

    /** v3.91: restore focus after a row rebuild. Every onResume rebuilds the
        Watching rows (removeAllViews + fresh cards), and that DESTROYS the
        card the user had focused when he left for a movie app. Android then
        leaves focus nowhere -- or on a card in a rebuilt row -- so the next
        OK press, meant for the Top picks tab, opens whatever card focus
        landed on (his report: "when i go to top picks and come back to
        the tab it goes to ... youtube"). This only acts when focus is
        actually lost (no focused view, or one detached from the window)
        AND the Watching panel is showing, so it never yanks focus while
        he is happily browsing. The safe anchor is the current section's
        chip: OK on a chip switches sections, it never opens an app. */
    void restoreWatchFocusIfLost() {
        if (watchPanel == null || watchPanel.getVisibility() != View.VISIBLE) return;
        View f = getCurrentFocus();
        if (f != null && f.getWindowToken() != null) return;
        TextView chip = watchChips.get(watchSection());
        if (chip != null) chip.requestFocus();
    }

    void startPickAuto() {
        if (NO_PICK_BG) return;
        stopPickAuto();
        if (pickAutoR == null) pickAutoR = new Runnable() {
            @Override public void run() {
                // v3.84: cycle the row that holds the focused card, not
                // always Top picks. Focusing a Kids Continue watching card
                // starts the carousel for the Kids row; focusing a Top picks
                // card starts it for Top picks.
                View f = getCurrentFocus();
                LinearLayout row = null;
                if (f != null) {
                    View p = f;
                    while (p != null) {
                        if (p == picksRow || p == kidsRow || p == watchRow || p == musicRow) { row = (LinearLayout) p; break; }
                        p = parentView(p);
                    }
                }
                if (row == null) row = picksRow;
                if (row == null || row.getChildCount() < 2) return;
                pickAutoIdx = (pickAutoIdx + 1) % row.getChildCount();
                Object tag = row.getChildAt(pickAutoIdx).getTag();
                if (tag instanceof String) showPickBg((String) tag);
                handler.postDelayed(pickAutoR, 5000);
            }
        };
        handler.postDelayed(pickAutoR, 5000);
    }

    void stopPickAuto() {
        if (pickAutoR != null) handler.removeCallbacks(pickAutoR);
    }

    // v4.53: Jannah's calendar (Kids only). One card per day, Monday first,
    // today highlighted with the theme accent. Weekly stickers repeat every
    // week (Logic.calendarActivity); 2027 Ontario holidays are listed below
    // so a holiday day is tagged (Logic.holiday2027). No network, all baked in.
    // v4.56: editable Kids calendar, port of tablet v1.13. One anchor
    // date; Week shows the anchor's week (Monday first), Month shows
    // the anchor's month. < > moves a week / a month; Today resets to
    // today. PD days + L'Harmonie holidays get a purple "Pas d'ecole"
    // badge (activity still shows underneath). Tapping a day asks for
    // the grown-up PIN, then opens the edit sheet: change the activity
    // text, add an event with a picture (9 to pick from), Save or
    // Delete. Events live in SharedPreferences under evt_yyyy-MM-dd.
    int calMode = 0; // 0 = Week, 1 = Month
    int calAnchorYear = 0, calAnchorMonth = 0, calAnchorDay = 0;
    String editSheetPic = "";
    // v4.59: how many days the edit applies to (1 = just the tapped
    // day, N = this day and the next N-1 days). Set by the stepper in
    // the edit sheet; reset to 1 each time the sheet opens.
    int editSheetDays = 1;
    View calFirstDay = null; // first day card/cell of the current view, for focus restore after refresh

    void calInitAnchor() {
        if (calAnchorYear == 0) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            calAnchorYear = c.get(java.util.Calendar.YEAR);
            calAnchorMonth = c.get(java.util.Calendar.MONTH) + 1;
            calAnchorDay = c.get(java.util.Calendar.DAY_OF_MONTH);
        }
    }

    void calResetToToday() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        calAnchorYear = c.get(java.util.Calendar.YEAR);
        calAnchorMonth = c.get(java.util.Calendar.MONTH) + 1;
        calAnchorDay = c.get(java.util.Calendar.DAY_OF_MONTH);
        refreshCalendar();
    }

    void calMove(int delta) {
        calInitAnchor();
        if (calMode == 0) {
            int[] d = Logic.addDays(calAnchorYear, calAnchorMonth, calAnchorDay, delta * 7);
            calAnchorYear = d[0]; calAnchorMonth = d[1]; calAnchorDay = d[2];
        } else {
            int[] m = Logic.addMonths(calAnchorYear, calAnchorMonth, delta);
            calAnchorYear = m[0]; calAnchorMonth = m[1];
            int max = Logic.daysInMonth(calAnchorYear, calAnchorMonth);
            if (calAnchorDay > max) calAnchorDay = max;
        }
        refreshCalendar();
    }

    void refreshCalendar() {
        if (calendarPanel == null) return;
        calendarPanelFilled = false;
        calendarPanel.removeAllViews();
        fillCalendarPanel();
        calendarPanelFilled = true;
        // v4.57: removeAllViews destroyed the focused view, so focus is gone
        // and the remote would stop working. Put it on the first day so the
        // user can keep moving with the arrows (only when the panel is open;
        // refresh is only ever called from inside the calendar, never from
        // the Calendar pill tap, so this cannot leak a tap into a day edit).
        if (calendarPanel.getVisibility() == View.VISIBLE && calFirstDay != null) calFirstDay.requestFocus();
    }

    // v4.57: on Jannah's birthday (Dec 29) pop a birthday message once that
    // day, the first time the home comes up -- in either profile, so whoever
    // opens the box sees it. The flag is per-day, so it shows again next
    // year but never twice in one day.
    void maybeShowBirthday() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int y = c.get(java.util.Calendar.YEAR);
        int m = c.get(java.util.Calendar.MONTH) + 1;
        int d = c.get(java.util.Calendar.DAY_OF_MONTH);
        if (!Logic.isJannahBirthday(m, d)) return;
        String key = "birthday_shown_" + Logic.eventDateKey(y, m, d);
        android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        if (p.getBoolean(key, false)) return;
        p.edit().putBoolean(key, true).apply();
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Joyeux anniversaire !")
                    .setMessage("Joyeux anniversaire, Jannah !")
                    .setIcon(R.drawable.cal_cake)
                    .setPositiveButton("Merci", null)
                    .setCancelable(true)
                    .show();
        } catch (Exception e) { /* ignore */ }
    }

    int calPicRes(String key) {
        if (key == null) return 0;
        if (key.equals("swim")) return R.drawable.cal_swim;
        if (key.equals("teacher")) return R.drawable.cal_teacher;
        if (key.equals("art")) return R.drawable.cal_art;
        if (key.equals("pizza")) return R.drawable.cal_pizza;
        if (key.equals("family")) return R.drawable.cal_family;
        if (key.equals("popsicle")) return R.drawable.cal_popsicle;
        if (key.equals("holiday")) return R.drawable.cal_holiday;
        if (key.equals("cake")) return R.drawable.cal_cake;
        if (key.equals("school")) return R.drawable.cal_school;
        if (key.equals("spring")) return R.drawable.cal_spring;
        if (key.equals("autumn")) return R.drawable.cal_autumn;
        if (key.equals("summer")) return R.drawable.cal_summer;
        return 0;
    }

    String calPicLabel(String key) {
        if (key == null) return "";
        if (key.equals("swim")) return "Piscine";
        if (key.equals("teacher")) return "Madame Jack";
        if (key.equals("art")) return "Art";
        if (key.equals("pizza")) return "Pizza";
        if (key.equals("family")) return "Famille";
        if (key.equals("popsicle")) return "Popsicle";
        if (key.equals("holiday")) return "F\u00eate";
        if (key.equals("cake")) return "G\u00e2teau";
        if (key.equals("school")) return "\u00c9cole";
        if (key.equals("spring")) return "Printemps";
        if (key.equals("autumn")) return "Automne";
        if (key.equals("summer")) return "\u00c9t\u00e9";
        return "";
    }

    // v4.53: picture for each calendar day, by activity.
    int calendarPicRes(int dow) {
        if (dow == 2) return R.drawable.cal_swim;
        if (dow == 3 || dow == 4) return R.drawable.cal_teacher;
        if (dow == 5) return R.drawable.cal_art;
        if (dow == 6) return R.drawable.cal_pizza;
        if (dow == 7) return R.drawable.cal_family;
        return R.drawable.cal_popsicle; // Sunday
    }

    void fillCalendarPanel() {
        if (calendarPanel == null) return;
        calFirstDay = null;
        // v4.53: pink, not black -- Jannah's calendar is a bright pink
        // panel with white day cards (today is bright pink), dark text.
        // The panel and its ScrollView both default to near-black, so
        // paint both pink; the title (child 0, set by makePanel) goes
        // dark plum so it reads on the light background.
        try {
            calendarPanel.setBackgroundColor(0xFFFFD1E3);
            android.view.View parent = (android.view.View) calendarPanel.getParent();
            if (parent != null) parent.setBackgroundColor(0xFFFFD1E3);
            android.view.View titleV = calendarPanel.getChildAt(0);
            if (titleV instanceof TextView) ((TextView) titleV).setTextColor(0xFF5A143C);
        } catch (Exception e) { /* ignore */ }
        calInitAnchor();
        java.util.Calendar now = java.util.Calendar.getInstance();
        final int todayY = now.get(java.util.Calendar.YEAR);
        final int todayM = now.get(java.util.Calendar.MONTH) + 1;
        final int todayD = now.get(java.util.Calendar.DAY_OF_MONTH);
        final int todayDow = now.get(java.util.Calendar.DAY_OF_WEEK);
        // Nav row: <  label  >  Today  Semaine|Mois.
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, hdp(6), 0, hdp(12));
        calendarPanel.addView(nav);
        TextView prev = calNavBtn("<");
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { calMove(-1); } });
        nav.addView(prev);
        TextView navLabel = new TextView(this);
        if (calMode == 0) {
            int[] mon = Logic.weekMonday(calAnchorYear, calAnchorMonth, calAnchorDay);
            int[] sun = Logic.addDays(mon[0], mon[1], mon[2], 6);
            navLabel.setText("Semaine  " + Logic.weekRangeText(mon[0], mon[1], mon[2], sun[0], sun[1], sun[2]));
        } else {
            navLabel.setText(Logic.monthNameFr(calAnchorMonth) + " " + calAnchorYear);
        }
        navLabel.setTextColor(0xFF5A143C);
        navLabel.setTextSize(hsp(20));
        navLabel.setTypeface(navLabel.getTypeface(), android.graphics.Typeface.BOLD);
        navLabel.setPadding(hdp(16), 0, hdp(16), 0);
        navLabel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        nav.addView(navLabel);
        TextView next = calNavBtn(">");
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { calMove(1); } });
        nav.addView(next);
        TextView todayBtn = calNavBtn("Aujourd'hui");
        todayBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { calResetToToday(); } });
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(-2, -2);
        tbLp.leftMargin = hdp(16);
        todayBtn.setLayoutParams(tbLp);
        nav.addView(todayBtn);
        TextView modeBtn = calNavBtn(calMode == 0 ? "Mois" : "Semaine");
        modeBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { calMode = 1 - calMode; refreshCalendar(); } });
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(-2, -2);
        mbLp.leftMargin = hdp(12);
        modeBtn.setLayoutParams(mbLp);
        nav.addView(modeBtn);
        // v4.57: the panel itself takes focus when it opens (showPanel), and
        // a focused container's children are NOT reachable by D-pad -- they
        // sit inside the panel's rect, so Android's focus search never picks
        // them (same trap the Apps panel hit, v4.34). Catch Down on the panel
        // and send it to the first nav button ("<"); from there Right walks
        // the nav row and Down drops into the day grid. Up stays put.
        final TextView firstNav = prev;
        calendarPanel.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    firstNav.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
                return false;
            }
        });
        // Hint: tap a day to edit (grown-ups only).
        TextView hint = new TextView(this);
        hint.setText("Touchez un jour pour le modifier \u2014 adultes seulement (PIN requis)");
        hint.setTextColor(0xFF783C64);
        hint.setTextSize(hsp(14));
        hint.setPadding(0, 0, 0, hdp(10));
        calendarPanel.addView(hint);
        if (calMode == 0) fillCalendarWeek(todayY, todayM, todayD);
        else fillCalendarMonth(todayY, todayM, todayD);
        TextView hol = new TextView(this);
        hol.setText("2027 : 1 Jan New Year's Day  \u00b7  15 Feb Family Day  \u00b7  26 Mar Good Friday  \u00b7  24 May Victoria Day  \u00b7  1 Jul Canada Day  \u00b7  6 Sep Labour Day  \u00b7  11 Oct Thanksgiving  \u00b7  25 Dec Christmas Day  \u00b7  26 Dec Boxing Day");
        hol.setTextColor(0xFF783C64);
        hol.setTextSize(hsp(13));
        hol.setPadding(0, hdp(10), 0, 0);
        calendarPanel.addView(hol);
    }

    // v4.57: focus visuals for the calendar nav buttons -- focused is
    // brighter pink with a white ring, so the remote position is visible.
    GradientDrawable calNavBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(focused ? 0xFFFF5090 : 0xFFC2185B);
        g.setCornerRadius(hdp(20));
        if (focused) g.setStroke(hdp(3), Color.WHITE);
        return g;
    }

    // v4.57: focus visuals for a day card/cell -- focused gets a thick
    // orange ring (and a light orange tint unless it is today, which stays
    // bright pink), so the remote position is visible.
    GradientDrawable calDayBg(boolean isToday, boolean focused, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(isToday ? 0xFFFF78B4 : (focused ? 0xFFFFF3E0 : Color.WHITE));
        g.setCornerRadius(radius);
        g.setStroke(focused ? hdp(4) : hdp(2), focused ? 0xFFFF6D00 : (isToday ? 0xFFFF5090 : 0xFFCCCCCC));
        return g;
    }

    TextView calNavBtn(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(hsp(18));
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(hdp(18), hdp(8), hdp(18), hdp(8));
        b.setBackground(calNavBg(false));
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(calNavBg(hasFocus));
            }
        });
        return b;
    }

    /** Week view: the anchor's week, Monday first, 7 cards in 2 rows. */
    void fillCalendarWeek(final int todayY, final int todayM, final int todayD) {
        int[] mon = Logic.weekMonday(calAnchorYear, calAnchorMonth, calAnchorDay);
        int y = mon[0], m = mon[1], d = mon[2];
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, hdp(12));
            calendarPanel.addView(row);
            int count = (r == 0) ? 4 : 3;
            for (int i = 0; i < count; i++) {
                int[] cur = Logic.addDays(y, m, d, r * 4 + i);
                final int cy = cur[0], cm = cur[1], cd = cur[2];
                final int dow = Logic.dayOfWeek(cy, cm, cd);
                final boolean isToday = (cy == todayY && cm == todayM && cd == todayD);
                final String dateKey = Logic.eventDateKey(cy, cm, cd);
                android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
                final String savedEvent = p.getString(Logic.eventTextKey(dateKey), "");
                final String savedPic = p.getString(Logic.eventPicKey(dateKey), "");
                final String savedAct = p.getString(Logic.eventActivityKey(dateKey), "");
                final String actText = savedAct.length() > 0 ? savedAct : Logic.calendarActivity(dow);
                final String badge = Logic.noSchoolBadge(cy, cm, cd);
                // v4.57: the day's event -- a saved event wins; otherwise
                // Jannah's birthday (Dec 29) shows as a built-in event.
                final String displayEvent = savedEvent.length() > 0 ? savedEvent
                        : (Logic.isJannahBirthday(cm, cd) ? Logic.birthdayText() : "");
                String picKey = savedEvent.length() > 0 && Logic.isPictureKey(savedPic) ? savedPic
                        : (Logic.isJannahBirthday(cm, cd) ? "cake" : Logic.calendarPictureForDate(cy, cm, cd));
                final int picRes = calPicRes(picKey);
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(0, 0, 0, hdp(12));
                card.setBackground(calDayBg(isToday, false, hdp(14)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(250), 1f);
                lp.rightMargin = hdp(12);
                card.setLayoutParams(lp);
                card.setFocusable(true);
                card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override public void onFocusChange(View v, boolean hasFocus) {
                        v.setBackground(calDayBg(isToday, hasFocus, hdp(14)));
                    }
                });
                card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { promptEditDay(cy, cm, cd); } });
                if (calFirstDay == null) calFirstDay = card;
                if (picRes != 0) {
                    android.widget.ImageView pic = new android.widget.ImageView(this);
                    pic.setImageResource(picRes);
                    pic.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(110));
                    pic.setLayoutParams(pp);
                    try {
                        android.graphics.drawable.GradientDrawable pg = new android.graphics.drawable.GradientDrawable();
                        pg.setCornerRadii(new float[]{hdp(14), hdp(14), hdp(14), hdp(14), 0, 0, 0, 0});
                        pic.setBackground(pg);
                        pic.setClipToOutline(true);
                    } catch (Exception e) { /* ignore */ }
                    card.addView(pic);
                }
                TextView dn = new TextView(this);
                dn.setText(Logic.calendarDayName(dow) + " " + cd + (isToday ? "  \u25CF" : ""));
                dn.setTextColor(0xFF3C1428);
                dn.setTextSize(hsp(19));
                dn.setTypeface(dn.getTypeface(), android.graphics.Typeface.BOLD);
                dn.setPadding(hdp(14), hdp(10), hdp(14), 0);
                card.addView(dn);
                if (badge != null) {
                    TextView b = new TextView(this);
                    b.setText(badge);
                    b.setTextColor(Color.WHITE);
                    b.setTextSize(hsp(13));
                    b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
                    b.setPadding(hdp(10), hdp(4), hdp(10), hdp(4));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xFF783CB4);
                    bg.setCornerRadius(hdp(10));
                    b.setBackground(bg);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
                    blp.leftMargin = hdp(14);
                    blp.topMargin = hdp(6);
                    b.setLayoutParams(blp);
                    card.addView(b);
                }
                TextView act = new TextView(this);
                act.setText(actText);
                act.setTextColor(0xFF5A3C50);
                act.setTextSize(hsp(15));
                act.setPadding(hdp(14), hdp(8), hdp(14), 0);
                card.addView(act);
                if (displayEvent.length() > 0) {
                    TextView ev = new TextView(this);
                    ev.setText(displayEvent);
                    ev.setTextColor(0xFFC2185B);
                    ev.setTextSize(hsp(15));
                    ev.setTypeface(ev.getTypeface(), android.graphics.Typeface.BOLD);
                    ev.setPadding(hdp(14), hdp(4), hdp(14), 0);
                    card.addView(ev);
                }
                row.addView(card);
            }
        }
    }

    /** Month view: Mon..Sun header + a grid of day cells. */
    void fillCalendarMonth(final int todayY, final int todayM, final int todayD) {
        String[] heads = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};
        LinearLayout hrow = new LinearLayout(this);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        hrow.setPadding(0, 0, 0, hdp(6));
        calendarPanel.addView(hrow);
        for (int i = 0; i < 7; i++) {
            TextView h = new TextView(this);
            h.setText(heads[i]);
            h.setTextColor(0xFF5A143C);
            h.setTextSize(hsp(16));
            h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
            h.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
            h.setLayoutParams(hp);
            hrow.addView(h);
        }
        int offset = Logic.firstDayMondayOffset(calAnchorYear, calAnchorMonth);
        int days = Logic.daysInMonth(calAnchorYear, calAnchorMonth);
        int rows = Logic.monthGridRows(calAnchorYear, calAnchorMonth);
        android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, hdp(8));
            calendarPanel.addView(row);
            for (int c = 0; c < 7; c++) {
                int cell = r * 7 + c;
                int dayNum = cell - offset + 1;
                LinearLayout cellV = new LinearLayout(this);
                cellV.setOrientation(LinearLayout.VERTICAL);
                cellV.setPadding(hdp(10), hdp(8), hdp(10), hdp(8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(170), 1f);
                lp.rightMargin = hdp(8);
                cellV.setLayoutParams(lp);
                if (dayNum < 1 || dayNum > days) {
                    cellV.setBackgroundColor(Color.TRANSPARENT);
                    row.addView(cellV);
                    continue;
                }
                final int cy = calAnchorYear, cm = calAnchorMonth, cd = dayNum;
                final int dow = Logic.dayOfWeek(cy, cm, cd);
                final boolean isToday = (cy == todayY && cm == todayM && cd == todayD);
                final String dateKey = Logic.eventDateKey(cy, cm, cd);
                final String savedEvent = p.getString(Logic.eventTextKey(dateKey), "");
                final String savedPic = p.getString(Logic.eventPicKey(dateKey), "");
                final String savedAct = p.getString(Logic.eventActivityKey(dateKey), "");
                final String badge = Logic.noSchoolBadge(cy, cm, cd);
                cellV.setBackground(calDayBg(isToday, false, hdp(12)));
                cellV.setFocusable(true);
                cellV.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override public void onFocusChange(View v, boolean hasFocus) {
                        v.setBackground(calDayBg(isToday, hasFocus, hdp(12)));
                    }
                });
                cellV.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { promptEditDay(cy, cm, cd); } });
                if (calFirstDay == null) calFirstDay = cellV;
                // v4.57: the month cells now show the day's picture, like the
                // week cards -- custom event picture if one was picked, else
                // Jannah's birthday cake on Dec 29, else the agreed activity
                // picture for that weekday / holiday.
                final String displayEvent = savedEvent.length() > 0 ? savedEvent
                        : (Logic.isJannahBirthday(cm, cd) ? Logic.birthdayText() : "");
                String picKey = savedEvent.length() > 0 && Logic.isPictureKey(savedPic) ? savedPic
                        : (Logic.isJannahBirthday(cm, cd) ? "cake" : Logic.calendarPictureForDate(cy, cm, cd));
                final int picRes = calPicRes(picKey);
                if (picRes != 0) {
                    android.widget.ImageView pic = new android.widget.ImageView(this);
                    pic.setImageResource(picRes);
                    pic.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(48));
                    pic.setLayoutParams(pp);
                    cellV.addView(pic);
                }
                TextView num = new TextView(this);
                num.setText(String.valueOf(cd));
                num.setTextColor(0xFF3C1428);
                num.setTextSize(hsp(18));
                num.setTypeface(num.getTypeface(), android.graphics.Typeface.BOLD);
                cellV.addView(num);
                TextView act = new TextView(this);
                act.setText(savedAct.length() > 0 ? savedAct : Logic.calendarShortActivity(dow));
                act.setTextColor(0xFF5A3C50);
                act.setTextSize(hsp(13));
                cellV.addView(act);
                if (displayEvent.length() > 0) {
                    TextView ev = new TextView(this);
                    ev.setText(displayEvent);
                    ev.setTextColor(0xFFC2185B);
                    ev.setTextSize(hsp(13));
                    ev.setTypeface(ev.getTypeface(), android.graphics.Typeface.BOLD);
                    cellV.addView(ev);
                }
                if (badge != null) {
                    TextView b = new TextView(this);
                    b.setText("Pas d'\u00e9cole");
                    b.setTextColor(Color.WHITE);
                    b.setTextSize(hsp(11));
                    b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
                    b.setPadding(hdp(8), hdp(2), hdp(8), hdp(2));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xFF783CB4);
                    bg.setCornerRadius(hdp(8));
                    b.setBackground(bg);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
                    blp.topMargin = hdp(4);
                    b.setLayoutParams(blp);
                    cellV.addView(b);
                }
                row.addView(cellV);
            }
        }
    }

    /** v4.56: a tap on a day first asks for the grown-up PIN. */
    void promptEditDay(final int year, final int month, final int day) {
        if (!pinSet()) {
            toast("D\u00e9finissez d'abord un PIN (R\u00e9glages > PIN)");
            return;
        }
        pinPrompt(new Runnable() { @Override public void run() { showEditDay(year, month, day); } });
    }

    /** v4.56: the edit sheet for one date. */
    void showEditDay(final int year, final int month, final int day) {
        final String dateKey = Logic.eventDateKey(year, month, day);
        final int dow = Logic.dayOfWeek(year, month, day);
        final String defaultActivity = Logic.calendarActivity(dow);
        android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        final String savedEvent = p.getString(Logic.eventTextKey(dateKey), "");
        final String savedPic = p.getString(Logic.eventPicKey(dateKey), "");
        final String savedAct = p.getString(Logic.eventActivityKey(dateKey), "");
        editSheetPic = Logic.isPictureKey(savedPic) ? savedPic : Logic.calendarPictureForDate(year, month, day);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView dateTv = new TextView(this);
        dateTv.setText(Logic.calendarDayName(dow) + " " + day + "/" + month + "/" + year);
        dateTv.setTextSize(24);
        dateTv.setTypeface(dateTv.getTypeface(), android.graphics.Typeface.BOLD);
        dateTv.setTextColor(0xFF3C1428);
        col.addView(dateTv);
        final String badge = Logic.noSchoolBadge(year, month, day);
        if (badge != null) {
            TextView b = new TextView(this);
            b.setText(badge);
            b.setTextSize(16);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
            b.setTextColor(Color.WHITE);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF783CB4);
            bg.setCornerRadius(dp(10));
            b.setBackground(bg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = dp(10);
            b.setLayoutParams(blp);
            col.addView(b);
        }
        TextView actLabel = new TextView(this);
        actLabel.setText("Activit\u00e9");
        actLabel.setTextSize(18);
        actLabel.setTypeface(actLabel.getTypeface(), android.graphics.Typeface.BOLD);
        actLabel.setTextColor(0xFF3C1428);
        actLabel.setPadding(0, dp(14), 0, 0);
        col.addView(actLabel);
        final EditText actIn = new EditText(this);
        actIn.setText(savedAct.length() > 0 ? savedAct : defaultActivity);
        actIn.setTextSize(18);
        actIn.setTextColor(0xFF3C1428);
        col.addView(actIn);
        TextView evLabel = new TextView(this);
        evLabel.setText("\u00c9v\u00e9nement (facultatif)");
        evLabel.setTextSize(18);
        evLabel.setTypeface(evLabel.getTypeface(), android.graphics.Typeface.BOLD);
        evLabel.setTextColor(0xFF3C1428);
        evLabel.setPadding(0, dp(14), 0, 0);
        col.addView(evLabel);
        final EditText evIn = new EditText(this);
        evIn.setText(savedEvent);
        evIn.setHint("Ex : Anniversaire de Jannah");
        evIn.setTextSize(18);
        evIn.setTextColor(0xFF3C1428);
        col.addView(evIn);
        TextView picLabel = new TextView(this);
        picLabel.setText("Image");
        picLabel.setTextSize(18);
        picLabel.setTypeface(picLabel.getTypeface(), android.graphics.Typeface.BOLD);
        picLabel.setTextColor(0xFF3C1428);
        picLabel.setPadding(0, dp(14), 0, 0);
        col.addView(picLabel);
        final LinearLayout pickerBox = new LinearLayout(this);
        pickerBox.setOrientation(LinearLayout.VERTICAL);
        buildCalPicker(pickerBox);
        col.addView(pickerBox);
        // v4.59: apply the event + picture to one day or to the upcoming
        // days (a range). The stepper changes editSheetDays (1-60); the
        // activity override still applies only to the tapped day, so the
        // daily routine is not overwritten for the whole range. Saving
        // with an empty event clears the event + picture for the range.
        editSheetDays = 1;
        TextView daysLabel = new TextView(this);
        daysLabel.setText("Appliquer \u00e0");
        daysLabel.setTextSize(18);
        daysLabel.setTypeface(daysLabel.getTypeface(), android.graphics.Typeface.BOLD);
        daysLabel.setTextColor(0xFF3C1428);
        daysLabel.setPadding(0, dp(14), 0, 0);
        col.addView(daysLabel);
        LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        daysRow.setPadding(0, dp(6), 0, 0);
        final Button daysMinus = new Button(this);
        daysMinus.setText("\u2212");
        daysMinus.setTextSize(20);
        final TextView daysTv = new TextView(this);
        daysTv.setText("1 jour");
        daysTv.setTextSize(18);
        daysTv.setTextColor(0xFF3C1428);
        daysTv.setPadding(dp(16), dp(8), dp(16), dp(8));
        final Button daysPlus = new Button(this);
        daysPlus.setText("+");
        daysPlus.setTextSize(20);
        daysMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (editSheetDays > 1) { editSheetDays--; daysTv.setText(editSheetDays == 1 ? "1 jour" : (editSheetDays + " jours")); }
            }
        });
        daysPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (editSheetDays < 60) { editSheetDays++; daysTv.setText(editSheetDays + " jours"); }
            }
        });
        daysRow.addView(daysMinus);
        daysRow.addView(daysTv);
        daysRow.addView(daysPlus);
        col.addView(daysRow);
        new AlertDialog.Builder(this)
                .setTitle("Modifier le jour")
                .setView(col)
                .setPositiveButton("Enregistrer", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String act = actIn.getText().toString().trim();
                        String ev = evIn.getText().toString().trim();
                        android.content.SharedPreferences.Editor e = getSharedPreferences("ui", MODE_PRIVATE).edit();
                        // v4.59: apply to one day or to the upcoming days.
                        // The event + picture go on every day of the range;
                        // the activity override stays on the tapped day only
                        // (the daily routine must not be overwritten for a
                        // whole range). An empty event clears the event +
                        // picture for the whole range.
                        int days = editSheetDays < 1 ? 1 : editSheetDays;
                        for (int i = 0; i < days; i++) {
                            int[] dt = Logic.addDays(year, month, day, i);
                            String key = Logic.eventDateKey(dt[0], dt[1], dt[2]);
                            if (i == 0) {
                                if (act.length() == 0 || act.equals(defaultActivity)) {
                                    e.remove(Logic.eventActivityKey(key));
                                } else {
                                    e.putString(Logic.eventActivityKey(key), act);
                                }
                            }
                            if (ev.length() == 0) {
                                e.remove(Logic.eventTextKey(key));
                                e.remove(Logic.eventPicKey(key));
                            } else {
                                e.putString(Logic.eventTextKey(key), ev);
                                e.putString(Logic.eventPicKey(key), editSheetPic);
                            }
                        }
                        e.apply();
                        refreshCalendar();
                        toast(days == 1 ? "Enregistr\u00e9" : ("Enregistr\u00e9 pour " + days + " jours"));
                    }
                })
                .setNeutralButton("Supprimer", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        getSharedPreferences("ui", MODE_PRIVATE).edit()
                                .remove(Logic.eventTextKey(dateKey))
                                .remove(Logic.eventPicKey(dateKey))
                                .remove(Logic.eventActivityKey(dateKey))
                                .apply();
                        refreshCalendar();
                        toast("Supprim\u00e9 \u2014 jour normal");
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** v4.56: fill the picker box with the 9 picture choices (2 rows). */
    void buildCalPicker(final LinearLayout box) {
        box.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < Logic.PICTURE_KEYS.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER);
                box.addView(row);
            }
            final String key = Logic.PICTURE_KEYS[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(key.equals(editSheetPic) ? 0xFFFFC98A : Color.WHITE);
            bg.setCornerRadius(hdp(10));
            bg.setStroke(hdp(2), key.equals(editSheetPic) ? 0xFFFF9800 : 0xFFCCCCCC);
            cell.setBackground(bg);
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT);
            cellLp.leftMargin = dp(6);
            cellLp.rightMargin = dp(6);
            cellLp.topMargin = dp(8);
            cell.setLayoutParams(cellLp);
            cell.setFocusable(true);
            android.widget.ImageView iv = new android.widget.ImageView(this);
            int res = calPicRes(key);
            if (res != 0) iv.setImageResource(res);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            cell.addView(iv, new LinearLayout.LayoutParams(dp(80), dp(80)));
            TextView lb = new TextView(this);
            lb.setText(calPicLabel(key));
            lb.setTextSize(14);
            lb.setTextColor(0xFF3C1428);
            lb.setGravity(android.view.Gravity.CENTER);
            cell.addView(lb);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    editSheetPic = key;
                    buildCalPicker(box);
                }
            });
            if (row != null) row.addView(cell);
        }
    }

    void showWatchPanel() { showPanel(watchPanel); showWatchSection(watchSection()); }

    // v4.13: one look for a tab chip -- active and/or focused fills with
    // the theme accent (the active chip was a fixed green before, which
    // never matched the theme); inactive and unfocused stays dark.
    void chipLook(TextView chip) {
        boolean on = Boolean.TRUE.equals(chip.getTag());
        boolean foc = chip.isFocused();
        boolean hot = on || foc;
        GradientDrawable g = new GradientDrawable();
        g.setColor(hot ? accent : 0xB3141824);
        g.setCornerRadius(hdp(12));
        g.setStroke(hdp(1), hot ? accent : 0x78FFFFFF);
        chip.setBackground(g);
        chip.setTextColor(hot ? accentText() : Color.WHITE);
    }

    void setChip(TextView chip, boolean on) {
        chip.setTag(on ? Boolean.TRUE : Boolean.FALSE);
        chipLook(chip);
    }

    // ---------- v3.50 Settings panel ----------
    // v3.62: Settings is a two-pane Android TV Settings-style UI (his ask).
    // Left nav = Launcher / Kids / My IPTV / Box; right = the rows for the
    // selected section. The nav stays fixed; only the right side scrolls.
    // The nav is built once (focus survives refreshes); the right side is
    // refilled per section.
    int settingsCat = 0;
    LinearLayout settingsNavCol;
    LinearLayout settingsContentCol;
    android.widget.ScrollView settingsContentSv;
    View[] settingsNavRows = new View[4];
    LinearLayout settingsSplit;

    void fillSettingsPanel() {
        // Remember whether focus was in the right pane, so a refresh from a
        // row action can put it back on the first row there.
        View f = getCurrentFocus();
        boolean wasInContent = false;
        if (f != null && settingsContentSv != null) {
            View v = f;
            while (v != null) { if (v == settingsContentSv) { wasInContent = true; break; } v = parentView(v); }
        }
        if (settingsSplit == null) {
            while (settingsPanel.getChildCount() > 1) settingsPanel.removeViewAt(1);
            settingsSplit = new LinearLayout(this);
            settingsSplit.setOrientation(LinearLayout.HORIZONTAL);
            // Height: fill the screen below the title, so the outer panel
            // never needs to scroll -- only the right side scrolls.
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenHdp = (int) (dm.heightPixels / dm.density);
            int splitH = dp(Math.max(260, screenHdp - 90 - 40 - 60));
            settingsSplit.setLayoutParams(new LinearLayout.LayoutParams(-1, splitH));
            // Left nav -- fixed width, the four sections.
            settingsNavCol = new LinearLayout(this);
            settingsNavCol.setOrientation(LinearLayout.VERTICAL);
            settingsNavCol.setLayoutParams(new LinearLayout.LayoutParams(dp(300), -1));
            settingsNavCol.setPadding(0, dp(8), dp(24), 0);
            settingsNavRow("Launcher", 0);
            settingsNavRow("Kids", 1);
            settingsNavRow("My IPTV", 2);
            settingsNavRow("Box", 3);
            // Right content -- scrolls on its own.
            settingsContentSv = new android.widget.ScrollView(this);
            settingsContentSv.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
            settingsContentSv.setVerticalScrollBarEnabled(false);
            settingsContentCol = new LinearLayout(this);
            settingsContentCol.setOrientation(LinearLayout.VERTICAL);
            settingsContentSv.addView(settingsContentCol, new android.widget.FrameLayout.LayoutParams(-1, -2));
            settingsSplit.addView(settingsNavCol);
            settingsSplit.addView(settingsContentSv);
            settingsPanel.addView(settingsSplit);
        }
        updateSettingsNav();
        fillSettingsContent();
        if (wasInContent && settingsContentCol.getChildCount() > 0) {
            settingsContentCol.getChildAt(0).requestFocus();
        }
    }

    void updateSettingsNav() {
        for (int i = 0; i < settingsNavRows.length; i++) {
            View r = settingsNavRows[i];
            if (r == null) continue;
            boolean sel = (i == settingsCat);
            TextView t = (TextView) ((LinearLayout) r).getChildAt(0);
            t.setTextColor(sel ? Color.WHITE : Color.rgb(170, 178, 190));
            GradientDrawable g = new GradientDrawable();
            g.setColor(sel ? 0x33FFFFFF : 0x00000000);
            g.setCornerRadius(dp(10));
            r.setBackground(g);
        }
    }

    void settingsNavRow(String title, final int cat) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        r.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        r.setLayoutParams(lp);
        r.setFocusable(true);
        r.setFocusableInTouchMode(true);
        r.setContentDescription(title);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) { settingsCat = cat; updateSettingsNav(); fillSettingsContent(); }
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                settingsCat = cat; updateSettingsNav(); fillSettingsContent();
                if (settingsContentCol.getChildCount() > 0) settingsContentCol.getChildAt(0).requestFocus();
            }
        });
        settingsNavCol.addView(r);
        settingsNavRows[cat] = r;
    }

    // Android TV Settings-style row: title left, current value right, a
    // subtle light background when focused (no gold border, no scaling).
    void settingsRow(LinearLayout parent, String title, String value, final Runnable act) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(16), dp(13), dp(16), dp(13));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        r.addView(t, tlp);
        TextView s = new TextView(this);
        s.setText(value);
        s.setTextColor(Color.rgb(176, 184, 198));
        s.setTextSize(15);
        s.setGravity(android.view.Gravity.END);
        s.setMaxLines(2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        r.addView(s, slp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        r.setLayoutParams(lp);
        r.setFocusable(true);
        r.setFocusableInTouchMode(true);
        r.setContentDescription(title);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(hasFocus ? 0x33FFFFFF : 0x00000000);
                g.setCornerRadius(dp(10));
                v.setBackground(g);
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act.run(); }
        });
        parent.addView(r);
    }

    void fillSettingsContent() {
        if (settingsContentCol == null) return;
        settingsContentCol.removeAllViews();
        if (settingsCat == 0) {
            // Launcher
            settingsRow(settingsContentCol, "Backup setup",
                "Saves order, theme, background, hidden and locked apps on this box",
                new Runnable() { @Override public void run() { backupSetup(); } });
            settingsRow(settingsContentCol, "Restore setup",
                "Brings back the last backup saved on this box",
                new Runnable() { @Override public void run() { restoreSetup(); } });
            settingsRow(settingsContentCol, "Language: " + langName(), "English, French, Arabic",
                new Runnable() { @Override public void run() { cycleLanguage(); fillSettingsPanel(); } });
            settingsRow(settingsContentCol, "Profile", kidsMode() ? "Kids - allowed apps only, tap to switch to Family (PIN)" : "Family - all apps, tap to switch to Kids",
                new Runnable() { @Override public void run() { toggleKidsMode(); fillSettingsPanel(); } });
            settingsRow(settingsContentCol, "Wallpaper", "Background: " + BGNAMES[bgMode() + 1],
                new Runnable() { @Override public void run() { int n = (bgMode() + 2) % 7 - 1; getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("bgmode", n).apply(); setBaseWallpaper(); toast("Wallpaper: " + BGNAMES[bgMode() + 1]); fillSettingsPanel(); } });
            settingsRow(settingsContentCol, "Refresh cards", "Republish the launcher cards",
                new Runnable() { @Override public void run() { new Thread(new Runnable() { @Override public void run() { Publish.ensurePublished(getApplicationContext()); } }).start(); toast("Cards refreshed"); } });
            settingsRow(settingsContentCol, "Make Cemba default", "Set Cemba Home as the default launcher",
                new Runnable() { @Override public void run() { unDefault(); } });
            settingsRow(settingsContentCol, "Search apps", "Find an app by name",
                new Runnable() { @Override public void run() { showPanel(searchPanel); searchEdit.setText(""); searchResults.removeAllViews(); } });
            settingsRow(settingsContentCol, "Voice search", "System Assistant voice search",
                new Runnable() { @Override public void run() { try { startActivity(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e1) { try { startActivity(new Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e2) { toast("Voice search isn't available on this box"); } } } });
            settingsRow(settingsContentCol, "Show song title", isNowPlayingListenerEnabled() ? "On - the Now Playing bar shows the real title" : "Off - tap to allow Notification access",
                new Runnable() { @Override public void run() { try { startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); toast("Turn on Cemba Home, then come back"); } catch (Exception e) { toast("Notification settings not available on this box"); } } });
            // v4.37: Check for updates lives under Launcher now, not Box --
            // it updates Cemba Home itself, it is not a box setting.
            settingsRow(settingsContentCol, "Check for updates", "Version " + appVersionName() + " - tap to check GitHub for a newer Cemba Home",
                new Runnable() { @Override public void run() { checkForUpdates(true); } });
        } else if (settingsCat == 1) {
            // Kids
            settingsRow(settingsContentCol, "Kids apps",
                csvSet("kids_apps").size() + " apps allowed in Kids mode",
                new Runnable() { @Override public void run() { kidsAppsDialog(); } });
            final int lim = getSharedPreferences("ui", MODE_PRIVATE).getInt("kids_limit_min", 0);
            settingsRow(settingsContentCol, "Daily limit",
                "Used today: " + kidsUsedToday() + " min - limit: " + (lim == 0 ? "none" : (lim + " min")),
                new Runnable() { @Override public void run() { kidsLimitDialog(); } });
            // v4.29: reset the used minutes to zero -- a fresh day without
            // changing the limit or waiting for tomorrow. PIN-protected so
            // Jannah cannot reset it herself.
            settingsRow(settingsContentCol, "Reset timer",
                "Used today: " + kidsUsedToday() + " min - tap to reset to 0",
                new Runnable() { @Override public void run() { resetKidsTimer(); } });
            String bt = getSharedPreferences("ui", MODE_PRIVATE).getString("kids_bedtime", "");
            settingsRow(settingsContentCol, "Bedtime",
                bt.length() == 0 ? "No bedtime set" : ("Bedtime: " + bt + " - blocked until 7:00 AM"),
                new Runnable() { @Override public void run() { kidsBedtimeDialog(); } });
            // v3.70 (moved to Kids in v3.82, his ask): Bridge switch status.
            // The Kids-profile YouTube fix (Bridge off in Kids, on in Family)
            // needs WRITE_SECURE_SETTINGS, granted once by pm grant. A
            // reinstall loses the grant, so the toggle then fails quietly and
            // YouTube in Kids goes to Google Home. This row shows whether the
            // switch is ready; tapping it shows the command to run when it
            // is not.
            settingsRow(settingsContentCol, "Kids YouTube switch", isBridgeSwitchReady() ? "Ready - Bridge turns off in Kids, on in Family" : "Not ready - tap for the one-time grant command",
                new Runnable() { @Override public void run() { if (isBridgeSwitchReady()) toast("Bridge switch is ready"); else toast("Run in TellyScope Shell: pm grant com.bacem.launcher android.permission.WRITE_SECURE_SETTINGS"); } });
            settingsRow(settingsContentCol, "Time-up app blocker",
                canDrawOverlaysSafe() ? "Ready - closes YouTube & apps when time is up" : "Not granted - tap to allow Display over other apps",
                new Runnable() { @Override public void run() { requestOverlayPermission(); } });
            // The PIN sits under the Kids items here (his call): it is what
            // keeps Jannah in Kids, locks apps, and stops her extending time.
            settingsRow(settingsContentCol, pinSet() ? "App PIN: set" : "App PIN: not set",
                pinSet() ? "Change the PIN or remove it" : "Set a 4-digit PIN to lock apps",
                new Runnable() { @Override public void run() { pinSetupDialog(); } });
            if (pinSet()) settingsRow(settingsContentCol, "Remove app PIN",
                "Unlocks every locked app",
                new Runnable() { @Override public void run() { pinRemoveDialog(); } });
        } else if (settingsCat == 2) {
            // My IPTV
            settingsRow(settingsContentCol, "Provider: " + ("".equals(iptvType()) ? "Not set" : "xtream".equals(iptvType()) ? "Xtream" : "Playlist link (m3u)"),
                "Your subscription -- tap to switch type",
                new Runnable() { @Override public void run() { iptvPut("iptv_type", "xtream".equals(iptvType()) ? "m3u" : "xtream"); toast("Provider: " + ("xtream".equals(iptvType()) ? "Xtream" : "Playlist link (m3u)")); fillSettingsPanel(); } });
            if ("xtream".equals(iptvType())) {
                settingsRow(settingsContentCol, "Server", iptvPref("iptv_server").length() > 0 ? iptvPref("iptv_server") : "Tap to enter the server address",
                    new Runnable() { @Override public void run() { iptvTextDialog("Server", "like example.com:8080", "iptv_server", false); } });
                settingsRow(settingsContentCol, "Username", iptvPref("iptv_user").length() > 0 ? iptvPref("iptv_user") : "Tap to enter your username",
                    new Runnable() { @Override public void run() { iptvTextDialog("Username", "Your provider username", "iptv_user", false); } });
                settingsRow(settingsContentCol, "Password", iptvPref("iptv_pass").length() > 0 ? "Set -- tap to change" : "Tap to enter your password",
                    new Runnable() { @Override public void run() { iptvTextDialog("Password", "Your provider password", "iptv_pass", true); } });
            }
            if ("m3u".equals(iptvType())) {
                settingsRow(settingsContentCol, "Playlist link", iptvPref("iptv_m3u").length() > 0 ? "Set -- tap to change" : "Tap to paste the m3u link",
                    new Runnable() { @Override public void run() { iptvTextDialog("Playlist link", "http://.../playlist.m3u", "iptv_m3u", false); } });
            }
            if (iptvConfigured()) {
                settingsRow(settingsContentCol, "Test connection", "Check the provider answers",
                    new Runnable() { @Override public void run() { testIptvConnection(); } });
                settingsRow(settingsContentCol, "Remove provider", "Clear the saved provider details",
                    new Runnable() { @Override public void run() { getSharedPreferences("ui", MODE_PRIVATE).edit().remove("iptv_type").remove("iptv_server").remove("iptv_user").remove("iptv_pass").remove("iptv_m3u").apply(); toast("Provider removed"); fillSettingsPanel(); } });
            }
        } else {
            // Box
            // v3.82: Widgets lives here now (his ask) -- moved from the home
            // header to Settings > Box, first row so it is easy to find.
            // Tapping it opens the same widgets panel as before.
            settingsRow(settingsContentCol, "Widgets", "Move, resize or hide the home widgets",
                new Runnable() { @Override public void run() { openWidgetsPanel(); } });
            settingsRow(settingsContentCol, "Tabs", tabsModeLabel(),
                new Runnable() { @Override public void run() {
                    final String[] modes = {"Auto (decide by box)", "Show all tabs", "Hide TV tabs"};
                    final String[] vals = {"auto", "full", "simple"};
                    String cur = getSharedPreferences("ui", MODE_PRIVATE).getString("tabs_mode", "auto");
                    int checked = 0;
                    if ("full".equals(cur)) checked = 1;
                    else if ("simple".equals(cur)) checked = 2;
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Watching tabs")
                        .setSingleChoiceItems(modes, checked, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                getSharedPreferences("ui", MODE_PRIVATE).edit().putString("tabs_mode", vals[which]).apply();
                                d.dismiss();
                                applyTabVisibility();
                                fillSettingsPanel();
                                toast("Tabs: " + modes[which]);
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
                } });
            settingsRow(settingsContentCol, "Restart box", isPowerServiceEnabled() ? "Opens the power menu - pick Restart there" : "Off - tap to enable Cemba Power first",
                new Runnable() { @Override public void run() {
                    if (isPowerServiceEnabled()) {
                        try { sendBroadcast(new Intent(PowerService.ACTION_POWER_DIALOG)); } catch (Exception e) { toast("Power menu not available"); }
                    } else {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); toast("Turn on Cemba Power, then tap Restart box again"); } catch (Exception e) { toast("Accessibility settings not available on this box"); }
                    }
                } });
            settingsRow(settingsContentCol, "Power off", isPowerServiceEnabled() ? "Opens the power menu - pick Power off" : "Off - tap to enable Cemba Power first",
                new Runnable() { @Override public void run() {
                    if (isPowerServiceEnabled()) {
                        try { sendBroadcast(new Intent(PowerService.ACTION_POWER_DIALOG)); } catch (Exception e) { toast("Power menu not available"); }
                    } else {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); toast("Turn on Cemba Power, then tap Power off again"); } catch (Exception e) { toast("Accessibility settings not available on this box"); }
                    }
                } });
            settingsRow(settingsContentCol, "Sleep", isPowerServiceEnabled() ? "Puts the box to sleep - wake with the remote" : "Off - tap to enable Cemba Power first",
                new Runnable() { @Override public void run() {
                    if (isPowerServiceEnabled()) {
                        try { sendBroadcast(new Intent(PowerService.ACTION_SLEEP)); } catch (Exception e) { toast("Sleep not available"); }
                    } else {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); toast("Turn on Cemba Power, then tap Sleep again"); } catch (Exception e) { toast("Accessibility settings not available on this box"); }
                    }
                } });
            // v4.02 (his ask): Bluetooth in Settings. The pill next to Wi-Fi
            // could not open the Bluetooth page on this box ("settings not
            // available") and Bluetooth is off, so there was no way in Cemba
            // to turn it on. This row opens the Bluetooth settings (falling
            // back to the main Settings page, like the pill). Hidden on boxes
            // with no Bluetooth hardware at all (X96mini) -- getDefaultAdapter
            // is null there, and it needs no Bluetooth permission to check.
            if (android.bluetooth.BluetoothAdapter.getDefaultAdapter() != null) {
                boolean btOn = false;
                try { btOn = android.provider.Settings.Global.getInt(getContentResolver(), "bluetooth_on", 0) == 1; } catch (Exception e) {}
                settingsRow(settingsContentCol, "Bluetooth", btOn ? "On - tap to open Bluetooth settings" : "Off - tap to open Bluetooth settings and turn it on",
                    new Runnable() { @Override public void run() {
                        try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                        catch (Exception e1) {
                            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                            catch (Exception e2) { toast("Bluetooth settings not available"); }
                        }
                    } });
            }
            settingsRow(settingsContentCol, "Wireless debugging", "Wireless ADB - for Bugjaeger and TellyScope",
                new Runnable() { @Override public void run() { try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("Developer options not available on this box"); } } });
            settingsRow(settingsContentCol, "Box Settings", "The box's own system settings",
                new Runnable() { @Override public void run() { try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("Settings not available"); } } });
            settingsRow(settingsContentCol, "Play Store", "Get and update apps",
                new Runnable() { @Override public void run() { launchPackage("com.android.vending"); } });
            settingsRow(settingsContentCol, "Google TV Home", "The box's original home screen",
                new Runnable() { @Override public void run() { launchPackage("com.google.android.apps.tv.launcherx"); } });
        }
    }

    // ---------- v3.54 My IPTV: the user's own provider (Xtream or m3u) ----------
    // Details are typed on the box and stored in its own prefs, like the app
    // PIN -- they never leave the box.
    String iptvType() { return getSharedPreferences("ui", MODE_PRIVATE).getString("iptv_type", ""); }
    String iptvPref(String k) { return getSharedPreferences("ui", MODE_PRIVATE).getString(k, ""); }
    void iptvPut(String k, String v) { getSharedPreferences("ui", MODE_PRIVATE).edit().putString(k, v).apply(); }
    boolean iptvConfigured() {
        if ("xtream".equals(iptvType()))
            return iptvPref("iptv_server").length() > 0 && iptvPref("iptv_user").length() > 0 && iptvPref("iptv_pass").length() > 0;
        if ("m3u".equals(iptvType())) return iptvPref("iptv_m3u").length() > 0;
        return false;
    }

    void iptvTextDialog(final String title, String hint, final String key, boolean isPassword) {
        final EditText in = new EditText(this);
        in.setHint(hint);
        if (isPassword) in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        in.setText(iptvPref(key));
        new AlertDialog.Builder(this).setTitle(title).setView(in)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String v = in.getText().toString().trim();
                    iptvPut(key, v);
                    toast(v.length() == 0 ? title + " cleared" : title + " saved");
                    fillSettingsPanel();
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    void testIptvConnection() {
        toast("Testing connection...");
        new Thread(new Runnable() {
            @Override public void run() {
                String msg2;
                try {
                    if ("xtream".equals(iptvType())) {
                        String cats = Iptv.fetchText(Iptv.xtreamApiUrl(iptvPref("iptv_server"), iptvPref("iptv_user"), iptvPref("iptv_pass"), "get_live_categories"), 10000);
                        int n = Iptv.parseLiveCategories(cats).size();
                        msg2 = n > 0 ? "Connected -- " + n + " categories" : "Connected, but no categories -- check the login";
                    } else {
                        String text = Iptv.fetchText(iptvPref("iptv_m3u"), 15000);
                        int n = Iptv.parseM3u(text).size();
                        msg2 = n > 0 ? "Connected -- " + n + " channels" : "Connected, but no channels found";
                    }
                } catch (Exception e) {
                    String m = e.getMessage() == null ? "" : e.getMessage();
                    if (m.contains("HTTP 401") || m.contains("HTTP 403")) msg2 = "Wrong username or password";
                    else if (m.contains("Unable to resolve") || m.contains("UnknownHost")) msg2 = "Server not found -- check the address";
                    else msg2 = "Connection failed -- check the address and Wi-Fi";
                }
                final String out = msg2;
                runOnUiThread(new Runnable() { @Override public void run() { toast(out); } });
            }
        }).start();
    }

    void openSettingsPanel() {
        showPanel(settingsPanel); fillSettingsPanel();
        // Focus the selected section in the left nav, like the box Settings.
        settingsPanel.post(new Runnable() { @Override public void run() {
            if (settingsNavRows != null && settingsCat >= 0 && settingsCat < settingsNavRows.length && settingsNavRows[settingsCat] != null) settingsNavRows[settingsCat].requestFocus();
        } });
    }

    // v3.52: the Apps tab -- every app as a row, Play Store first.
    void openAppsPanel() {
        showPanel(appsPanel); fillAppsPanel();
        // v4.34: the panel itself takes focus when the tab opens
        // (showPanel requests focus on it), so the FIRST Down press
        // lands on the panel, not on a circle -- and the panel has no
        // grid key handler, so Android's default search jumps straight
        // to the last app. Catch Down on the panel and send it to the
        // first app instead; Up on the panel stays put.
        appsPanel.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    View first = firstCircleIn(appsPanel);
                    if (first != null) { first.requestFocus(); return true; }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
                return false;
            }
        });
    }

    // v4.34: first app circle in a panel grid (child 0 is the title).
    View firstCircleIn(View panel) {
        try {
            android.view.ViewGroup pg = (android.view.ViewGroup) panel;
            for (int r = 1; r < pg.getChildCount(); r++) {
                View rr = pg.getChildAt(r);
                if (!(rr instanceof LinearLayout)) continue;
                LinearLayout rl = (LinearLayout) rr;
                if (rl.getChildCount() > 0) {
                    View cc = rl.getChildAt(0);
                    if (cc instanceof LinearLayout) {
                        LinearLayout cl = (LinearLayout) cc;
                        if (cl.getChildCount() > 0 && cl.getChildAt(0) instanceof FrameLayout)
                            return cl.getChildAt(0);
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }
    void fillAppsPanel() {
        while (appsPanel.getChildCount() > 1) appsPanel.removeViewAt(1);
        // v4.16: reuse the list loadApps already queried on a background
        // thread instead of querying the PackageManager again on the UI
        // thread every time the Apps tab opens. Falls back to a fresh
        // query if the home row has never loaded (first seconds after
        // startup).
        List<AppItem> all = lastApps.isEmpty() ? queryApps() : lastApps;
        Set<String> hidden = csvSet("hidden_apps");
        Set<String> kids = kidsMode() ? csvSet("kids_apps") : null;
        // Play Store first (the shortcut he asked for).
        List<AppItem> ordered = new ArrayList<AppItem>();
        AppItem store = null;
        for (AppItem ai : all) if ("com.android.vending".equals(ai.comp.getPackageName())) { store = ai; break; }
        if (store != null) ordered.add(store);
        else {
            try {
                Intent li = getPackageManager().getLaunchIntentForPackage("com.android.vending");
                if (li != null && li.getComponent() != null) {
                    AppItem s = new AppItem();
                    s.label = "Play Store"; s.comp = li.getComponent(); s.ri = null;
                    ordered.add(s);
                }
            } catch (Exception e) {}
        }
        for (AppItem ai : all) {
            if (store != null && ai == store) continue;
            if (hidden.contains(ai.comp.getPackageName())) continue;
            if (kids != null && !kids.contains(ai.comp.getPackageName())) continue;
            ordered.add(ai);
        }
        // v3.52: a grid, not a list -- 7 circles per row (his call).
        LinearLayout row = null;
        int n = 0;
        for (AppItem ai : ordered) {
            if (n % 7 == 0) {
                row = newRow();
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.topMargin = dp(14);
                appsPanel.addView(row, rlp);
            }
            row.addView(appCircle(ai, false));
            n++;
        }
    }
    String gb(long bytes) { return String.format(Locale.US, "%.1f GB", bytes / 1.0e9); }

    void refreshBoxCare() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (ramTv != null) ramTv.setText("RAM free  " + gb(mi.availMem) + " / " + gb(mi.totalMem));
            if (ramFillV != null && ramRestV != null) {
                int freePct = (int) (100 * mi.availMem / Math.max(1L, mi.totalMem));
                ((LinearLayout.LayoutParams) ramFillV.getLayoutParams()).weight = Math.max(1, freePct);
                ((LinearLayout.LayoutParams) ramRestV.getLayoutParams()).weight = Math.max(1, 100 - freePct);
                ramFillV.requestLayout();
                ramRestV.requestLayout();
            }
            android.os.StatFs sf = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            long free = sf.getAvailableBlocksLong() * sf.getBlockSizeLong();
            long total = sf.getBlockCountLong() * sf.getBlockSizeLong();
            if (storageTv != null) storageTv.setText("Storage free  " + gb(free));
            if (total > 0) {
                int usedPct = (int) Math.round(100.0 * (total - free) / total);
                if (storageRingV != null) storageRingV.setRing(accent, usedPct / 100f);
                if (storagePctTv != null) storagePctTv.setText(usedPct + "%");
            }
        } catch (Exception e) { /* readouts keep their last values */ }
    }

    void clearRam() {
        final android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        final long before = mi.availMem;
        toast("Clearing RAM...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    for (android.app.ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
                        if (p.pkgList == null) continue;
                        for (String pkg : p.pkgList) {
                            if (!pkg.equals(getPackageName())) am.killBackgroundProcesses(pkg);
                        }
                    }
                    Thread.sleep(700);
                } catch (Exception e) { /* best effort */ }
                am.getMemoryInfo(mi);
                final long freed = Math.max(0, mi.availMem - before);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        refreshBoxCare();
                        toast(freed > 1048576 ? "RAM cleared - freed about " + (freed / 1048576) + " MB" : "RAM cleared");
                    }
                });
            }
        }).start();
    }

    void speedTest(final TextView btn) {
        btn.setText("Testing...");
        new Thread(new Runnable() {
            @Override public void run() {
                long bytes = 0;
                long t0 = System.currentTimeMillis();
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new URL("https://speed.cloudflare.com/__down?bytes=4000000").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(20000);
                    java.io.InputStream in = c.getInputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) bytes += n;
                    in.close();
                } catch (Exception e) { bytes = -1; }
                final long took = Math.max(1, System.currentTimeMillis() - t0);
                final long got = bytes;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (got > 0) {
                            double mbps = got * 8.0 / (took / 1000.0) / 1000000.0;
                            btn.setText(String.format(Locale.US, "%.0f Mbps", mbps));
                            toast("Wi-Fi download: " + String.format(Locale.US, "%.0f Mbps", mbps));
                        } else {
                            btn.setText("Test failed");
                        }
                        handler.postDelayed(new Runnable() {
                            @Override public void run() { btn.setText("Speed test"); }
                        }, 5000);
                    }
                });
            }
        }).start();
    }

    // ---- News row (last on the home): CBC + Radio-Canada headlines ----
    long lastNews = 0;

    // v4.16: publish time for a news item -- element [5] when present
    // (parsed once in fetchFeed), else parse the date string directly.
    long newsMs(String[] it) {
        if (it.length > 5) { try { return Long.parseLong(it[5]); } catch (Exception e) { return 0; } }
        return parsePubMs(it[3]);
    }

    void loadNews() {
        if (newsRow == null) return;
        if (System.currentTimeMillis() - lastNews < 600000) return;
        lastNews = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                final java.util.List<String[]> items = new java.util.ArrayList<String[]>();
                fetchFeed(items, "https://www.cbc.ca/webfeed/rss/rss-topstories", "CBC NEWS", 3);
                fetchFeed(items, "https://ici.radio-canada.ca/rss/4159", "RADIO-CANADA", 3);
                fetchFeed(items, "https://www.mosaiquefm.net/fr/rss", "MOSAIQUE FM", 3);
                fetchFeed(items, "https://www.mosaiquefm.net/rss", "MOSAIQUE FM (AR)", 3);
                java.util.Collections.sort(items, new java.util.Comparator<String[]>() {
                    @Override public int compare(String[] a, String[] b) {
                        return Long.compare(newsMs(b), newsMs(a));
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (newsRow == null) return;
                        newsRow.removeAllViews();
                        if (items.isEmpty()) {
                            TextView t = new TextView(MainActivity.this);
                            t.setText("News could not load - check the connection.");
                            t.setTextColor(Color.rgb(170, 170, 180));
                            t.setTextSize(17);
                            newsRow.addView(t);
                            return;
                        }
                        long now = System.currentTimeMillis();
                        for (String[] it : items) {
                            long ms = newsMs(it);
                            String age = ms > 0 ? Logic.newsAge(ms, now) : "";
                            newsRow.addView(newsTile(it[0], it[1], age, it[2], it.length > 4 ? it[4] : null));
                        }
                    }
                });
            }
        }).start();
    }

    long parsePubMs(String s) {
        if (s == null || s.length() == 0) return 0;
        String[] fmts = { "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz" };
        for (String f : fmts) {
            try {
                return new java.text.SimpleDateFormat(f, Locale.ENGLISH).parse(s.trim()).getTime();
            } catch (Exception e) { /* try next */ }
        }
        return 0;
    }

    // v4.16: the image-src pattern was compiled on every feed item without
    // an image (up to 12 compiles per news refresh).
    static final java.util.regex.Pattern IMG_SRC_PAT = java.util.regex.Pattern.compile("src=\"([^\"]+)\"");
    void fetchFeed(java.util.List<String[]> out, String url, String src, int max) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser();
            p.setInput(c.getInputStream(), null);
            boolean inItem = false;
            String tag = null, title = null, link = null, date = null, img = null, desc = null;
            int count = 0;
            int ev = p.getEventType();
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && count < max) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("item".equals(tag)) { inItem = true; title = null; link = null; date = null; img = null; desc = null; }
                    if (inItem && img == null && ("thumbnail".equals(tag) || "content".equals(tag) || "enclosure".equals(tag))) {
                        String u = p.getAttributeValue(null, "url");
                        if (u == null) u = p.getAttributeValue(null, "href");
                        if (u != null && (u.contains(".jpg") || u.contains(".jpeg") || u.contains(".png") || u.contains(".webp"))) img = u;
                    }
                } else if (ev == org.xmlpull.v1.XmlPullParser.TEXT && inItem && tag != null) {
                    String t = p.getText();
                    if ("title".equals(tag)) title = t;
                    else if ("link".equals(tag)) link = t;
                    else if ("pubDate".equals(tag)) date = t;
                    else if ("description".equals(tag)) desc = t;
                } else if (ev == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    if ("item".equals(p.getName())) {
                        if (title != null && link != null) {
                            if (img == null && desc != null) {
                                java.util.regex.Matcher mm = IMG_SRC_PAT.matcher(desc);
                                if (mm.find()) img = mm.group(1);
                            }
                            // v4.16: the parsed publish time rides along as element [5]
                            // (parsed once here) -- the sort and the render loop used
                            // to re-parse the date string on every comparison/item.
                            out.add(new String[] { src, title.trim(), link.trim(), date == null ? "" : date.trim(), img == null ? "" : img, String.valueOf(parsePubMs(date == null ? "" : date.trim())) });
                            count++;
                        }
                        inItem = false;
                    }
                    tag = null;
                }
                ev = p.next();
            }
        } catch (Exception e) { /* a feed being down just means fewer cards */ }
    }

    View newsTile(String source, String title, String age, final String link, String img) {
        // His ask (2026-10-22): the card background is the story's own photo when the
        // feed carries one, under a dark scrim so the headline stays readable;
        // photo-less stories keep the plain dark card.
        FrameLayout card = new FrameLayout(this);
        if (img != null && img.length() > 0) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            Img.load(this, iv, img);
            View scrim = new View(this);
            scrim.setBackgroundColor(0xA6000000);
            card.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackground(tileBg());
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView s = new TextView(this);
        s.setText(source);
        s.setTextColor(Color.rgb(240, 130, 115));
        s.setTextSize(11);
        texts.addView(s);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(t);
        TextView a = new TextView(this);
        a.setText(age);
        a.setTextColor(Color.rgb(170, 185, 205));
        a.setTextSize(11);
        texts.addView(a);
        card.addView(texts, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), dp(104));
        lp.rightMargin = dp(14);
        card.setLayoutParams(lp);
        card.setContentDescription(title);
        focusable(card);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link));
                    i.setPackage("com.phoenix.tvbro");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e2) { toast("No browser available for news"); }
                }
            }
        });
        return card;
    }

    TextView pill(String s) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(hsp(14));
        t.setPadding(hdp(11), hdp(7), hdp(11), hdp(7));
        final GradientDrawable g = new GradientDrawable();
        g.setColor(0xA60E121C);
        g.setCornerRadius(hdp(20));
        g.setStroke(hdp(1), 0x6EFFFFFF);
        t.setBackground(g);
        t.setFocusable(true);
        // Approved design (2026-10-21): on focus the pill fills white with dark text and grows a touch.
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) {
                    // v4.13: the Wi-Fi pill fills with the theme accent
                    // now, not fixed white.
                    g.setColor(accent);
                    g.setStroke(hdp(1), accent);
                    t.setTextColor(accentText());
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110).start();
                } else {
                    g.setColor(0xA60E121C);
                    g.setStroke(hdp(1), 0x6EFFFFFF);
                    t.setTextColor(Color.WHITE);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                }
                if (has) {
                    CharSequence cd = v.getContentDescription();
                    if (cd != null && headlineTv != null && !heroTheme()) headlineTv.setText(cd);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = hdp(10);
        t.setLayoutParams(lp);
        return t;
    }

    // v3.58: section labels are smaller too (28sp was shouting) -- a compact
    // header, not a second title.
    TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    HorizontalScrollView scrollOf(LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(row);
        return h;
    }

    GradientDrawable tileBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(34, 34, 40));
        g.setCornerRadius(dp(16));
        return g;
    }

    GradientDrawable borderFg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(3), accent);
        return g;
    }

    void focusable(final View v) { focusable(v, 1.13f); }

    // v3.59: panel rows are full-width now and must NOT grow on focus.
    // The old fixed 560dp width overflowed narrower screens (the N5 Max
    // on the bedroom Samsung cut the rows off on the right, and the
    // 1.13x focus scale pushed the focused row off both edges). Rows now
    // fill the panel (MATCH_PARENT) and only get the gold border on
    // focus, no scale -- a full-width row has nowhere to grow into.
    void focusable(final View v, final float scale) {
        v.setFocusable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).setDuration(120).start();
                view.setForeground(has ? borderFg() : null);
                if (has) {
                    // Focus only grows the card a little and updates the headline.
                    // The background NEVER follows focus - it stays his photos.
                    CharSequence cd = view.getContentDescription();
                    if (cd != null && headlineTv != null && !heroTheme()) headlineTv.setText(cd);
                }
            }
        });
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    void launchPackageRaw(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { toast("App not found: " + pkg); return; }
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast("Could not open " + pkg); }
    }

    // PIN gate (v3.36): every app launch goes through here or through the
    // circle click below, so a locked app cannot be opened from any row.
    // Unlocked apps and no-PIN setups pass straight through.
    void launchPackage(String pkg) {
        final String p = pkg;
        // v4.12: in Family mode, the YouTube app circle opens Cobalt
        // directly instead of the official YouTube app. The TizenTube
        // Bridge used to do this redirect, but a broken Bridge
        // intercepts the launch and sends it to Google Home instead
        // (2026-11-14). Routing inside Cemba removes that dependency.
        // Kids mode still opens the official app (Bridge off there).
        if (!kidsMode() && "com.google.android.youtube.tv".equals(p)) {
            runIfPinOk(p, new Runnable() {
                @Override public void run() {
                    // v4.13: resume Cobalt where it was instead of opening
                    // YouTube home (which lost the video). Falls back to
                    // the official app if Cobalt is not installed.
                    if (!bringCobaltToFront()) launchPackageRaw(p);
                }
            });
            return;
        }
        runIfPinOk(p, new Runnable() {
            @Override public void run() { launchPackageRaw(p); }
        });
    }

    // Generic PIN prompt shared by app locks, category locks and the Kids-mode exit.
    void pinPrompt(final Runnable onOk) {
        final EditText in = new EditText(this);
        in.setHint("4-digit PIN");
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });
        new AlertDialog.Builder(this)
            .setTitle("Enter PIN")
            .setView(in)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String pin = getSharedPreferences("ui", MODE_PRIVATE).getString("app_pin", "");
                    if (in.getText().toString().equals(pin)) onOk.run();
                    else toast("Wrong PIN");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    void runIfPinOk(final String pkg, final Runnable doLaunch) {
        if (!pinSet() || !isLocked(pkg)) { doLaunch.run(); return; }
        pinPrompt(doLaunch);
    }

    boolean catLocked(String name) { return csvSet("locked_cats").contains(name); }

    void runIfCatOk(final String name, final Runnable doJump) {
        if (!pinSet() || !catLocked(name)) { doJump.run(); return; }
        pinPrompt(doJump);
    }

    boolean tryLaunch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (Exception e) { return false; }
    }

    void manageAppDialog(final String label, final String pkg) {
        final boolean hid = isHidden(pkg);
        final boolean loc = isLocked(pkg);
        java.util.ArrayList<String> items = new java.util.ArrayList<String>();
        items.add("Open app");
        items.add(hid ? "Show app" : "Hide app");
        items.add(loc ? "Unlock app" : (pinSet() ? "Lock app" : "Lock app (set a PIN first)"));
        // v3.60: uninstall from the Apps tab (his ask -- it did not let him
        // uninstall apps). Not offered for Cemba Home itself.
        final boolean canUninstall = !pkg.equals(getPackageName());
        if (canUninstall) items.add("Uninstall app");
        new AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) { showHome(); launchPackage(pkg); return; }
                    if (which == 1) {
                        Set<String> s = csvSet("hidden_apps");
                        if (hid) s.remove(pkg); else s.add(pkg);
                        saveCsvSet("hidden_apps", s);
                        loadApps();
                        fillAppsPanel();
                        toast(hid ? (label + " shown") : (label + " hidden"));
                        return;
                    }
                    if (which == 2) {
                        if (loc) {
                            Set<String> s = csvSet("locked_apps");
                            s.remove(pkg); saveCsvSet("locked_apps", s);
                            fillAppsPanel();
                            toast(label + " unlocked");
                        } else if (pinSet()) {
                            Set<String> s = csvSet("locked_apps");
                            s.add(pkg); saveCsvSet("locked_apps", s);
                            fillAppsPanel();
                            toast(label + " locked");
                        } else {
                            pinSetupDialog();
                        }
                        return;
                    }
                    if (which == 3 && canUninstall) {
                        // Opens the system uninstall confirmation -- the user
                        // confirms there. A launcher cannot uninstall
                        // silently; the system dialog is the safeguard.
                        // System apps cannot be fully uninstalled -- the
                        // system offers to remove updates / disable instead.
                        // v3.62: ACTION_DELETE showed no confirmation on his
                        // box, so use ACTION_UNINSTALL_PACKAGE (the modern
                        // uninstall action) first, with ACTION_DELETE as a
                        // fallback. If neither opens, say so plainly.
                        boolean opened = false;
                        try {
                            Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, android.net.Uri.parse("package:" + pkg));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                            opened = true;
                        } catch (Exception e1) {
                            try {
                                Intent i = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + pkg));
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(i);
                                opened = true;
                            } catch (Exception e2) {}
                        }
                        if (!opened) toast("Couldn't open the uninstaller - remove it from Settings, Apps");
                        return;
                    }
                }
            })
            .show();
    }

    EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });
        return e;
    }

    void pinSetupDialog() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        final EditText p1 = pinField("New 4-digit PIN");
        final EditText p2 = pinField("Repeat PIN");
        v.addView(p1);
        v.addView(p2);
        new AlertDialog.Builder(this).setTitle("App PIN").setView(v)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String a = p1.getText().toString(), b = p2.getText().toString();
                    if (a.length() == 4 && a.equals(b)) {
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("app_pin", a).apply();
                        fillSettingsPanel();
                        toast("App PIN set");
                    } else toast("PINs must match and be 4 digits");
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    void pinRemoveDialog() {
        new AlertDialog.Builder(this).setTitle("Remove app PIN?")
            .setMessage("Every locked app will open without a PIN.")
            .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    SharedPreferences.Editor e = getSharedPreferences("ui", MODE_PRIVATE).edit();
                    e.putString("app_pin", "");
                    e.putString("locked_apps", "");
                    e.apply();
                    fillSettingsPanel();
                    toast("App PIN removed");
                }
            })
            .setNegativeButton("Keep PIN", null).show();
    }

    void backupSetup() {
        try {
            SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
            JSONObject o = new JSONObject();
            o.put("app_order", p.getString("app_order", ""));
            o.put("theme", p.getString("theme", "gold"));
            o.put("bgmode", p.getInt("bgmode", -1));
            o.put("hidden", new JSONArray(new ArrayList<String>(csvSet("hidden_apps"))));
            o.put("locked", new JSONArray(new ArrayList<String>(csvSet("locked_apps"))));
            // The PIN itself is never written to the backup file.
            File f = new File(getExternalFilesDir(null), "CembaHome-backup.json");
            FileOutputStream out = new FileOutputStream(f);
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
            toast("Setup backed up on this box");
        } catch (Exception e) { toast("Backup failed"); }
    }

    void restoreSetup() {
        try {
            File f = new File(getExternalFilesDir(null), "CembaHome-backup.json");
            if (!f.exists()) { toast("No backup found on this box"); return; }
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf); in.close();
            JSONObject o = new JSONObject(new String(buf, 0, n, "UTF-8"));
            Set<String> hid = new HashSet<String>();
            JSONArray ha = o.optJSONArray("hidden");
            if (ha != null) for (int i = 0; i < ha.length(); i++) hid.add(ha.optString(i));
            Set<String> loc = new HashSet<String>();
            JSONArray la = o.optJSONArray("locked");
            if (la != null) for (int i = 0; i < la.length(); i++) loc.add(la.optString(i));
            SharedPreferences.Editor e = getSharedPreferences("ui", MODE_PRIVATE).edit();
            e.putString("app_order", o.optString("app_order", ""));
            e.putString("theme", o.optString("theme", "gold"));
            e.putInt("bgmode", o.optInt("bgmode", -1));
            e.putString("hidden_apps", joinCsv(hid));
            e.putString("locked_apps", joinCsv(loc));
            e.apply();
            applyTheme(o.optString("theme", "gold"), false);
            loadApps();
            fillSettingsPanel();
            toast("Setup restored");
        } catch (Exception e) { toast("Restore failed"); }
    }

    void runSearch(String q) {
        searchResults.removeAllViews();
        String needle = q.toLowerCase(Locale.getDefault()).trim();
        if (needle.length() == 0) return;
        Set<String> hidden = csvSet("hidden_apps");
        Set<String> kids = kidsMode() ? csvSet("kids_apps") : null;
        boolean any = false;
        for (AppItem ai : lastApps) {
            if (hidden.contains(ai.comp.getPackageName())) continue;
            if (kids != null && !kids.contains(ai.comp.getPackageName())) continue;
            if (!ai.label.toLowerCase(Locale.getDefault()).contains(needle)) continue;
            any = true;
            final String pkg = ai.comp.getPackageName();
            panelRow(searchResults, ai.label, "Open " + ai.label, new Runnable() {
                @Override public void run() { showHome(); launchPackage(pkg); }
            });
        }
        if (!any) {
            TextView t = new TextView(this);
            t.setText("No apps found");
            t.setTextColor(Color.rgb(200, 208, 222));
            t.setTextSize(16);
            searchResults.addView(t);
        }
    }

    // ---------- Shortcuts row (v3.37: rebuilt on Kids-mode toggles) ----------
    // In Kids mode the row holds only the grown-ups exit chip, so a kid cannot
    // reach Settings, the Play Store or the other shortcuts.
    void buildShortcuts() {
        // v3.50: the Shortcuts bar moved under Settings (his ask) -- home keeps
        // only the Kids-mode grown-ups exit (empty in normal mode).
        scRow.removeAllViews();
        if (kidsMode()) {
            scRow.addView(textTile("Grown-ups", new Runnable() {
                @Override public void run() { exitKidsModePin(); }
            }));
        }
    }


    // ---------- Kids mode: allow-list + daily limit + bedtime (v3.37) ----------
    // Time is counted while Kids mode is on (one tick per minute), not per app:
    // per-app foreground tracking would need UsageStats access, which this
    // launcher deliberately does not ask for.
    boolean kidsMode() { return getSharedPreferences("ui", MODE_PRIVATE).getBoolean("kids_mode", false); }

    boolean canDrawOverlaysSafe() {
        try {
            return android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        } catch (Throwable t) { return false; }
    }

    void requestOverlayPermission() {
        if (canDrawOverlaysSafe()) {
            toast("Time-up app blocker is ready");
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            toast("Run in TellyScope Shell: appops set com.bacem.launcher SYSTEM_ALERT_WINDOW allow");
        }
    }

    void toggleKidsMode() {
        if (kidsMode()) { exitKidsModePin(); return; }
        if (!pinSet()) {
            toast("Set an app PIN first, so kids cannot switch Kids mode off");
            pinSetupDialog();
            return;
        }
        if (bedtimeBlocked()) { toast("Past bedtime - Kids mode stays off"); return; }
        if (csvSet("kids_apps").isEmpty())
            toast("No kids apps chosen yet - add some in Settings, Kids");
        setKidsMode(true);
        if (!canDrawOverlaysSafe()) toast("Kids mode on - allow 'Display over other apps' in Settings > Kids so apps close when time is up");
        else toast("Kids mode on");
    }

    void exitKidsModePin() {
        if (!pinSet()) { setKidsMode(false); return; }
        pinPrompt(new Runnable() {
            @Override public void run() { setKidsMode(false); toast("Kids mode off"); }
        });
    }

    // v3.60: profiles -- Family and Kids. The Kids profile is Kids mode made
    // explicit: its own avatar picture and its own theme, so the home looks
    // different the moment you switch. Family gets his photo and whatever
    // theme he picked; Kids gets the lion cub avatar and the Kids theme.
    void applyProfileAvatar() {
        if (avatarIv == null) return;
        try { avatarIv.setImageResource(kidsMode() ? R.drawable.avatar_kids : R.drawable.avatar); } catch (Exception e) {}
    }

    void setKidsMode(boolean on) {
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        // v3.60: theme follows the profile. Entering Kids saves the Family
        // theme and applies the Kids theme; leaving Kids restores the saved
        // Family theme. The Kids theme is "kids" (Bluey / Pokemon / hero).
        if (on && !kidsMode()) {
            p.edit().putString("family_theme", p.getString("theme", "gold")).apply();
            applyTheme("kids", false);
        } else if (!on && kidsMode()) {
            applyTheme(p.getString("family_theme", "gold"), false);
        }
        p.edit().putBoolean("kids_mode", on).commit();
        // v3.87: time-based Kids timer. Record the session start when
        // entering Kids mode; when leaving, commit the elapsed minutes to
        // kids_used_min. kidsUsedToday() adds the live session elapsed on
        // top, so the count is right even if the tick missed minutes while
        // another app (YouTube Kids) was in front or the activity died.
        if (on) {
            p.edit().putLong("kids_session_start", System.currentTimeMillis()).commit();
        } else {
            long start = p.getLong("kids_session_start", 0);
            long elapsed = Logic.kidsElapsedMin(start, System.currentTimeMillis());
            if (elapsed > 0) {
                int used = p.getInt("kids_used_min", 0);
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                if (!today.equals(p.getString("kids_used_date", ""))) used = 0;
                p.edit().putString("kids_used_date", today).putInt("kids_used_min", used + (int) elapsed).commit();
            }
            p.edit().putLong("kids_session_start", 0).commit();
            KidsTimerReceiver.cancel(this);
        }
        // v3.64: Bridge follows the profile. Kids gets the official YouTube
        // (Bridge off, no redirect to Cobalt); Family gets Cobalt back.
        setBridgeEnabled(!on);
        hidePickBg();
        if (timeUpDialog != null) {
            try { timeUpDialog.dismiss(); } catch (Exception e) {}
            timeUpDialog = null;
        }
        if (on) { startKidsTick(); scheduleKidsAlarm(); }
        else handler.removeCallbacks(kidsTickR);
        // v4.55: Kids timer foreground service -- the AlarmManager path
        // works on the N5 Max but never fires on the Xiaomi box (MIUI /
        // Android TV 14 defers the alarm, and Android TV has no "Battery
        // unrestricted" setting). The service stays alive while she
        // watches YouTube and brings the home forward when time is up.
        // v4.57: the service FAILED on the Xiaomi at first (no "Kids time"
        // notification -- it never started). Root cause: the manifest
        // declared foregroundServiceType="dataSync" but was missing the
        // FOREGROUND_SERVICE_DATA_SYNC permission, which Android 14
        // requires for that type. Fixed by adding that permission. The
        // setAlarmClock alarm (scheduleKidsAlarm) is still armed as a
        // backup. Full approach history lives in KidsTimerService.java.
        try {
            Intent svc = new Intent(this, KidsTimerService.class);
            if (on) {
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
            } else {
                stopService(svc);
            }
        } catch (Exception e) { /* ignore */ }
        applyKidsMode();
        applyProfileAvatar();
    }

    void startKidsTick() {
        if (kidsTickR == null) kidsTickR = new Runnable() {
            @Override public void run() { kidsTick(); }
        };
        handler.removeCallbacks(kidsTickR);
        handler.postDelayed(kidsTickR, 60000);
        kidsCheck();
    }

    void applyKidsMode() {
        boolean kids = kidsMode();
        // v4.41: the Kids profile gets its own tabs (his ask, requested
        // before): Live TV, Continue watching, Theme. Before, the whole
        // pills row was hidden in Kids mode, so none of those tabs could
        // be seen or tapped. Now only the grown-ups pills hide (Home,
        // Apps, Wi-Fi, Settings); the 3 kids tabs stay. Back from any
        // panel still returns to the Kids home, so the Home pill is not
        // needed. Focus lands on Live TV when entering Kids mode.
        setPillVis(navHome, !kids);
        setPillVis(navLive, true);
        setPillVis(navWatch, true);
        setPillVis(navTheme, true);
        // v4.53: Calendar is Kids only -- Jannah's week. Family never sees it.
        setPillVis(navCalendar, kids);
        setPillVis(navApps, !kids);
        setPillVis(wifiTv, !kids);
        setPillVis(navSettings, !kids);
        showHome();
        if (kids && navLive != null) navLive.requestFocus();
        setSection(watchLabel, watchRow, !kids);
        setSection(picksLabel, picksRow, !kids);
        setSection(musicLabel, musicRow, !kids);
        setSection(newsLabel, newsRow, !kids);
        // v4.42: re-filter the Watching chips on profile switch, so the
        // Kids panel shows only Continue watching Kids right away.
        try { applyTabVisibility(); } catch (Throwable t) {}
        buildShortcuts();
        loadApps();
    }

    void setPillVis(View v, boolean vis) {
        if (v != null) v.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    void setSection(View label, View row, boolean vis) {
        int v = vis ? View.VISIBLE : View.GONE;
        if (label != null) label.setVisibility(v);
        if (row != null) row.setVisibility(v);
    }

    void kidsTick() {
        // v3.87: no longer counts minutes by ticks -- the count is time-based
        // (kidsUsedToday), so missed ticks while another app is in front do
        // not lose minutes. The tick just refreshes the widget and checks.
        updateTimerWidget();
        kidsCheck();
        if (kidsMode()) handler.postDelayed(kidsTickR, 60000);
    }

    /** v3.87: schedule the exact alarm that fires when the daily limit is
        reached OR bedtime arrives, even if MainActivity is dead in the
        background. The alarm brings the launcher forward; onResume ->
        kidsCheck shows Time's up. Whichever comes first wins.
        v4.54: the alarm uses setAlarmClock (see KidsTimerReceiver) --
        WORKS on the N5 Max, does NOT fire on the Xiaomi (MIUI / Android
        TV 14). KEPT as a backup: the foreground service (KidsTimerService,
        v4.55, fixed v4.57) is the primary timer on the Xiaomi, and this
        alarm is still armed alongside it so either path can pull the home
        forward. Full approach history lives in KidsTimerService.java. */
    void scheduleKidsAlarm() {
        try {
            if (!kidsMode()) { KidsTimerReceiver.cancel(this); return; }
            long best = -1;
            int limit = getSharedPreferences("ui", MODE_PRIVATE).getInt("kids_limit_min", 0);
            if (limit > 0) {
                best = Logic.kidsRemainingMin(limit, kidsUsedStored(), kidsSessionStart(), System.currentTimeMillis());
            }
            String bt = getSharedPreferences("ui", MODE_PRIVATE).getString("kids_bedtime", "");
            java.util.Calendar c = java.util.Calendar.getInstance();
            int nowMin = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
            long bedRem = Logic.bedtimeRemainingMin(bt, nowMin);
            if (bedRem >= 0 && (best < 0 || bedRem < best)) best = bedRem;
            if (best < 0) { KidsTimerReceiver.cancel(this); return; }
            if (best <= 0) { kidsCheck(); return; }
            KidsTimerReceiver.schedule(this, best * 60000L);
        } catch (Exception e) { /* ignore */ }
    }

    long kidsSessionStart() {
        return getSharedPreferences("ui", MODE_PRIVATE).getLong("kids_session_start", 0);
    }

    int kidsUsedStored() {
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        if (!today.equals(p.getString("kids_used_date", ""))) return 0;
        return p.getInt("kids_used_min", 0);
    }

    void kidsCheck() {
        if (!kidsMode()) return;
        if (bedtimeBlocked()) { showTimeUp("Past bedtime"); return; }
        int limit = getSharedPreferences("ui", MODE_PRIVATE).getInt("kids_limit_min", 0);
        if (limit > 0 && kidsUsedToday() >= limit) showTimeUp("Daily time is up");
    }

    int kidsUsedToday() {
        // v3.87: time-based -- stored minutes from finished sessions today
        // + elapsed minutes in the live Kids session. Accurate even if the
        // tick missed minutes while YouTube Kids was in front.
        long used = kidsUsedStored();
        if (kidsMode()) used += Logic.kidsElapsedMin(kidsSessionStart(), System.currentTimeMillis());
        return (int) Math.min(used, Integer.MAX_VALUE);
    }

    boolean bedtimeBlocked() {
        String bt = getSharedPreferences("ui", MODE_PRIVATE).getString("kids_bedtime", "");
        if (bt.length() == 0) return false;
        String[] parts = bt.split(":");
        if (parts.length != 2) return false;
        int bed;
        try { bed = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]); }
        catch (Exception e) { return false; }
        // Blocked from bedtime until 7:00 in the morning.
        java.util.Calendar c = java.util.Calendar.getInstance();
        int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        return now >= bed || now < 420;
    }

    void showTimeUp(String reason) {
        if (timeUpDialog != null) return;
        ringTimer();
        updateTimerWidget();
        // v3.61: stop everything -- pull her out of whatever she is watching
        // (YouTube, Cobalt, ...) back to the launcher home, so the video
        // stops. If the home is not in front yet, the dialog waits: onResume
        // calls kidsCheck again and shows it once the home is back.
        goHome();
        if (!resumed) return;
        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle("Time's up")
            .setMessage(reason + ". Ask a grown-up to continue.")
            .setPositiveButton("Grown-ups", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dg, int w) { exitKidsModePin(); }
            })
            .setCancelable(false)
            .create();
        timeUpDialog = d;
        d.show();
    }

    void kidsAppsDialog() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppItem> all = queryApps();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        final Set<String> chosen = csvSet("kids_apps");
                        final String[] names = new String[all.size()];
                        final boolean[] checked = new boolean[all.size()];
                        for (int i = 0; i < all.size(); i++) {
                            names[i] = all.get(i).label;
                            checked[i] = chosen.contains(all.get(i).comp.getPackageName());
                        }
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Kids apps")
                            .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override public void onClick(DialogInterface d, int which, boolean isChecked) {
                                    checked[which] = isChecked;
                                }
                            })
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    Set<String> s = new HashSet<String>();
                                    for (int i = 0; i < all.size(); i++)
                                        if (checked[i]) s.add(all.get(i).comp.getPackageName());
                                    saveCsvSet("kids_apps", s);
                                    fillSettingsPanel();
                                    toast("Kids apps saved");
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    void kidsLimitDialog() { kidsLimitDialog(false); }

    // v4.29: reset the Kids timer -- used minutes back to zero, a fresh
    // day without changing the limit or waiting for tomorrow. PIN-
    // protected so Jannah cannot reset it herself. The session clock is
    // restarted too, or the live session elapsed would count the old
    // minutes straight back in.
    void resetKidsTimer() {
        if (!pinSet()) { doResetKidsTimer(); return; }
        pinPrompt(new Runnable() {
            @Override public void run() { doResetKidsTimer(); }
        });
    }

    void doResetKidsTimer() {
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        p.edit().putString("kids_used_date", today).putInt("kids_used_min", 0).putLong("kids_session_start", System.currentTimeMillis()).commit();
        if (kidsMode()) { scheduleKidsAlarm(); kidsCheck(); }
        updateTimerWidget();
        toast("Timer reset - fresh day");
        fillSettingsPanel();
    }

    void kidsLimitDialog(final boolean fromTimer) {
        final EditText in = new EditText(this);
        in.setHint("Minutes per day (0 = no limit)");
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        int cur = getSharedPreferences("ui", MODE_PRIVATE).getInt("kids_limit_min", 0);
        in.setText(String.valueOf(cur));
        new AlertDialog.Builder(this).setTitle("Daily limit").setView(in)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    int v;
                    try { v = Integer.parseInt(in.getText().toString().trim()); }
                    catch (Exception e) { toast("Enter a number of minutes"); return; }
                    if (v < 0) v = 0;
                    getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("kids_limit_min", v).commit();
                    updateTimerWidget();
                    scheduleKidsAlarm();
                    if (fromTimer) { buildWidgetsRow(); }
                    else fillSettingsPanel();
                    toast(v == 0 ? "No daily limit" : ("Daily limit: " + v + " min"));
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    void kidsBedtimeDialog() {
        final EditText in = new EditText(this);
        in.setHint("Bedtime like 21:00 (empty = none)");
        String cur = getSharedPreferences("ui", MODE_PRIVATE).getString("kids_bedtime", "");
        in.setText(cur);
        new AlertDialog.Builder(this).setTitle("Bedtime").setView(in)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String v = in.getText().toString().trim();
                    if (v.length() == 0) {
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("kids_bedtime", "").commit();
                        fillSettingsPanel();
                        toast("No bedtime");
                        return;
                    }
                    String[] parts = v.split(":");
                    try {
                        int h = Integer.parseInt(parts[0]), m = Integer.parseInt(parts[1]);
                        if (parts.length != 2 || h < 0 || h > 23 || m < 0 || m > 59) throw new Exception();
                    } catch (Exception e) { toast("Use a time like 21:00"); return; }
                    getSharedPreferences("ui", MODE_PRIVATE).edit().putString("kids_bedtime", v).commit();
                    fillSettingsPanel();
                    scheduleKidsAlarm();
                    toast("Bedtime: " + v);
                }
            })
            .setNegativeButton("Cancel", null).show();
    }







    // Currently uncalled (Buttons chip removed in v3.34) -- kept deliberately:
    // this holds the tvQuickActions/Button Mapper fallback chain for his
    // standing Netflix-button wish, so the chip can come back in one step.
    // Quick action for changing what the remote's buttons do (his standing Netflix-button
    // wish): opens a TV button mapper if one is installed -- tvQuickActions (Pro then
    // free) or Button Mapper -- and otherwise lands on Accessibility settings, where a
    // mapper's service gets switched on, with a toast naming where to get the app.
    void openButtons() {
        if (tryLaunch("dev.vodik7.tvquickactions")) return;
        if (tryLaunch("dev.vodik7.tvquickactions.free")) return;
        if (tryLaunch("flar2.homebutton")) return;
        toast("No button mapper installed - get tvQuickActions from the Play Store");
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e2) { /* no settings route on this box */ }
        }
    }

    // ---------- Continue watching (Watch Next store) ----------
    static class WatchItem {
        long id; String title, poster, intentUri, pkg, genre, rating, desc;
        long pos, dur;
        // v4.16: memoized kids/music class (0 = not classified yet; bit 1
        // = kids, bit 2 = music, 4 = neither). The inputs never change
        // after the card is built, so classify once and reuse.
        int cls;
    }

    // v4.16: classify a card once (see WatchItem.cls). A card used to be
    // classified 3-5 times per refresh -- fillPicks partition, movie
    // filter, tile fallback icon -- each pass re-running the full keyword
    // scan (lowercase + ~70 contains) over title/description.
    int classify(WatchItem wi) {
        if (wi.cls == 0) {
            int c = 0;
            // v4.17: a Tubi title is kids iff Tubi says so (its ID is in
            // the kids set) -- this is what catches Pokemon / Sonic /
            // Addams Family, whose WatchNext listing carries no usable
            // rating or genre. No title words are used anywhere.
            // v4.25: the curated exact-title list (titles he named) is
            // checked too -- it catches the Top picks PreviewPrograms
            // cards (Larva, Milo and Chip) that carry no genre/rating
            // and whose links expose no Tubi ID.
            if (isTubiKids(wi) || Logic.isKnownKidsTitle(wi.title) || Logic.isKidsFull(wi.genre, wi.rating, wi.pkg, wi.title, wi.desc)) c |= 1;
            if (Logic.isMusicFull(wi.genre, wi.pkg, wi.title, wi.desc)) c |= 2;
            wi.cls = (c == 0) ? 4 : c;
        }
        return wi.cls;
    }

    // v4.17: Tubi ID from a WatchNext / Top picks intent URI.
    // Tubi links look like intent://tubitv.com/movies/{id}#Intent;...
    // or https://tubitv.com/movies/{id}. Returns "" when there is none.
    static String tubiIdFromUri(String uri) {
        if (uri == null) return "";
        String key = "tubitv.com/movies/";
        int i = uri.indexOf(key);
        if (i < 0) return "";
        int j = i + key.length();
        StringBuilder sb = new StringBuilder();
        while (j < uri.length()) {
            char ch = uri.charAt(j);
            if (ch == '#' || ch == '/' || ch == '?' || ch == '&') break;
            sb.append(ch);
            j++;
        }
        return sb.toString();
    }

    // v4.17: load the persisted Tubi kids IDs once (lazy -- classify may
    // run before any Tubi fetch, and the disk cache is what makes the
    // IDs available on that first classification).
    void loadTubiKidsIds() {
        if (tubiKidsLoaded) return;
        tubiKidsLoaded = true;
        try {
            Tubi.Store st = tubiStore();
            long at = st.getLong("kids_ids_at", 0);
            if (System.currentTimeMillis() - at < Tubi.TITLES_TTL_MS) {
                tubiKidsIds.addAll(Tubi.idsFromString(st.getString("kids_ids", "")));
            }
        } catch (Throwable t) { /* no cached kids IDs yet */ }
    }

    // v4.22: load the persisted Tubi kids titles once (lazy).
    void loadTubiKidsTitles() {
        if (tubiKidsTitlesLoaded) return;
        tubiKidsTitlesLoaded = true;
        try {
            Tubi.Store st = tubiStore();
            long at = st.getLong("kids_titles_at", 0);
            if (System.currentTimeMillis() - at < Tubi.TITLES_TTL_MS) {
                tubiKidsTitles.addAll(Tubi.titlesSetFromJson(st.getString("kids_titles", "")));
            }
        } catch (Throwable t) { /* no cached kids titles yet */ }
    }

    // v4.17: true iff this card is a Tubi title Tubi itself lists as kids.
    // v4.22: two keys -- the Tubi ID in the kids-ID set, OR the exact
    // title in the kids-titles set (for WatchNext items whose intent URI
    // carries no parseable ID).
    boolean isTubiKids(WatchItem wi) {
        if (wi == null || wi.pkg == null) return false;
        if (!wi.pkg.equals("com.tubitv") && !wi.pkg.equals("com.tvtv.tubi.app")) return false;
        loadTubiKidsIds();
        String id = tubiIdFromUri(wi.intentUri);
        if (id.length() > 0 && tubiKidsIds.contains(id)) return true;
        loadTubiKidsTitles();
        String t = wi.title == null ? "" : wi.title.toLowerCase(java.util.Locale.US).trim();
        return t.length() > 0 && tubiKidsTitles.contains(t);
    }

    // v4.20: true iff this card is a Tubi title (by package or by its
    // tubitv.com deep link).
    boolean isTubiCard(WatchItem wi) {
        if (wi == null) return false;
        if (wi.pkg != null && (wi.pkg.equals("com.tubitv") || wi.pkg.equals("com.tvtv.tubi.app"))) return true;
        return wi.intentUri != null && wi.intentUri.contains("tubitv.com/movies/");
    }

    // v4.20: open a Tubi card in the Tubi app, directly. The deep link
    // (intent://tubitv.com/movies/{id}) is an IMPLICIT intent, and on
    // the N5 Max the installed Tubi build (com.tvtv.tubi.app) does not
    // claim tubitv.com links, so Android showed an "Open with" chooser
    // (SmartTube / TizenTube Bridge / YouTube -- but NOT Tubi) and the
    // movie would not open at all (his report: "I can't open movie from
    // tubi in the 5max", Spider-Man: Brand New Day). Pinning the intent
    // to the installed Tubi package skips the chooser and goes straight
    // to Tubi. If Tubi refuses the deep link even explicitly, fall back
    // to opening the Tubi app itself; if Tubi is not installed, fall
    // back to the browser page.
    void openTubiCard(WatchItem wi) {
        String tp = tubiPkg();
        if (tp.length() == 0) {
            try {
                Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) { toast("Tubi is not installed on this box"); }
            return;
        }
        try {
            Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
            i.setPackage(tp);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            // Tubi would not take the deep link -- open the app itself.
            launchPackageRaw(tp);
        }
    }

    // Kids = explicit children's signals only (TV-Y rating, kid/children/preschool genre, kids apps).
    // TV-G / G are general-audience, NOT kids - treating them as kids hid YouTube shows here.
    boolean isKids(WatchItem wi) { return (classify(wi) & 1) != 0; }

    // Music row: music genre, or a music source app (YouTube Music, Spotify, ...).
    boolean isMusic(WatchItem wi) { return (classify(wi) & 2) != 0; }

    // ---------- Top picks row (PreviewPrograms store) ----------
    void loadPicks() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<WatchItem> items = new ArrayList<WatchItem>();
                // v3.88: X96mini has no TvContract class -- skip, row stays empty.
                if (!Publish.tvAvailable()) { runOnUiThread(new Runnable() { @Override public void run() { fillPicks(items); } }); return; }
                try {
                    ContentResolver cr = getContentResolver();
                    String[] proj = { TvContract.PreviewPrograms._ID,
                        TvContract.PreviewPrograms.COLUMN_TITLE,
                        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                        TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                        TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                        TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                        "genre",
                        TvContract.PreviewPrograms.COLUMN_CONTENT_RATING };
                    Cursor c = cr.query(TvContract.PreviewPrograms.CONTENT_URI, proj, null, null,
                        TvContract.PreviewPrograms._ID + " DESC");
                    if (c != null) {
                        Set<String> seen = new HashSet<String>();
                        // v3.71: cap raised 100 -> 300. YouTube floods the
                        // store with so many items that Tubi / Pluto were cut
                        // off by the 100 cap and never reached Top picks
                        // (his report: "we need to make sure other apps
                        // share"). The fair-share in fillPicks (3 per app
                        // first) then gives every app a slot.
                        while (c.moveToNext() && items.size() < 300) {
                            String intentUri = c.getString(3);
                            if (intentUri == null || intentUri.startsWith("browsehere")) continue;
                            String t = c.getString(1);
                            if (t == null || t.length() == 0) continue;
                            String pkg = null;
                            try { pkg = c.getString(4); } catch (Exception e) { pkg = null; }
                            if (!seen.add(Logic.dedupeKey(t, pkg))) continue;
                            WatchItem wi = new WatchItem();
                            wi.id = c.getLong(0);
                            wi.title = t;
                            wi.poster = c.getString(2);
                            wi.intentUri = intentUri;
                            wi.pkg = pkg;
                            try { wi.desc = c.getString(5); } catch (Exception e) { wi.desc = null; }
                            try { wi.genre = c.getString(6); } catch (Exception e) { wi.genre = null; }
                            try { wi.rating = c.getString(7); } catch (Exception e) { wi.rating = null; }
                            items.add(wi);
                        }
                        c.close();
                    }
                } catch (Throwable t) { /* row stays empty */ }
                runOnUiThread(new Runnable() {
                    @Override public void run() { fillPicks(items); }
                });
            }
        }).start();
    }


    void fillPicks(List<WatchItem> items) {
        // v3.28: drop utility status cards (Send Files to TV's "Transfer Service Active")
        List<WatchItem> kept = new ArrayList<WatchItem>();
        for (WatchItem wi : items) if (!Logic.isJunkPick(wi.pkg, wi.title)) kept.add(wi);
        items = kept;
        // v3.64: music harvest stays -- it feeds the Music tab (the songs Top
        // picks used to show). Kids movies harvest feeds the Kids section.
        synchronized (picksMusic) {
            picksMusic.clear();
            for (final WatchItem wi : items) if (isMusic(wi)) picksMusic.add(wi);
        }
        synchronized (picksKids) {
            picksKids.clear();
            for (final WatchItem wi : items) if (isKids(wi) && !isMusic(wi)) picksKids.add(wi);
        }
        renderMusicExtras();
        // v4.28: Top picks no longer sends its kids titles to the Kids
        // Continue watching row -- they now render in the Top picks row
        // itself, after the family titles (see below).
        // v4.01: the movie cards are back. The app row (v3.98) was too
        // plain -- he wants to browse movies with their posters. The cards
        // now pass a STRICT package allow-list: only Tubi (com.tubitv),
        // Pluto TV (tv.pluto.android) and Netfly (xyz.netfly), by exact
        // package match. Anything else -- the official YouTube app,
        // SmartTube, or a BLANK package -- is excluded. That is how Meet
        // the Parents slipped through before: its package column was blank,
        // so the old exclusions (which only removed named YouTube /
        // SmartTube packages) never caught it. A blank package can never be
        // a movie app, so it is dropped here.
        // On a box with no TV provider (X96mini, N5 Max) there are no cards
        // to harvest, so Top picks tries Tubi's web API first (v4.08) and
        // the app row shows meanwhile (and stays if the fetch fails), so
        // Top picks is never empty there.
        picksRow.removeAllViews();
        if (!Publish.tvAvailable()) { showTubiPicksOrApps(); }
        else {
            List<WatchItem> movies = new ArrayList<WatchItem>();
            for (WatchItem wi : items) {
                if (isMusic(wi)) continue;
                if (isKids(wi)) continue;
                // v4.23: no picture, no card -- he does not want videos
                // without a picture in Top picks.
                if (!hasArt(wi)) continue;
                String pk = wi.pkg;
                // v4.05: Netfly now ships as org.drive_hunter (verified
                // from the v3.0.3 and v3.0.5 APKs) as well as the older
                // xyz.netfly, so both packages are allowed.
                if (!"com.tubitv".equals(pk) && !"tv.pluto.android".equals(pk) && !"xyz.netfly".equals(pk) && !"org.drive_hunter".equals(pk)) continue;
                movies.add(wi);
            }
            if (movies.isEmpty()) {
                // v4.08: no movie cards harvested -- on the N5 Max (its
                // TvContract classes exist, so tvAvailable() says true,
                // but it has NO TV provider at all) and on a provider
                // box before Tubi / Pluto / Netfly have shared. Top
                // picks tries Tubi's web API first (real movie cards,
                // no provider needed); the app row shows meanwhile and
                // stays if the fetch fails.
                showTubiPicksOrApps();
            } else {
                tubiFallbackShown = false;
                // v4.24: the provider has movies, so the Tubi API fetch
                // never runs from the fallback -- but the kids sets (which
                // classify THESE provider cards) come from that fetch.
                // Refresh them in the background when stale, without
                // touching the provider cards on screen.
                refreshTubiKidsSetsIfStale();
                List<WatchItem> shown = new ArrayList<WatchItem>();
                java.util.Map<String, Integer> perSrc = new java.util.HashMap<String, Integer>();
                for (WatchItem wi : movies) {
                    String k = wi.pkg == null ? "" : wi.pkg;
                    Integer n = perSrc.get(k);
                    if (n != null && n.intValue() >= 3) continue;
                    perSrc.put(k, n == null ? 1 : n.intValue() + 1);
                    shown.add(wi);
                    if (shown.size() >= 30) break;
                }
                if (shown.size() < 30) {
                    java.util.Set<WatchItem> inShown = new java.util.HashSet<WatchItem>(shown);
                    for (WatchItem wi : movies) {
                        if (shown.size() >= 30) break;
                        if (inShown.contains(wi)) continue;
                        shown.add(wi);
                    }
                }
                for (final WatchItem wi : shown) {
                    View t = pickTile(wi, true);
                    Integer upId = watchChipIds.get("picks");
                    if (upId != null) t.setNextFocusUpId(upId);
                    picksRow.addView(t);
                }
                // v4.28: Top picks holds EVERYTHING -- family first, then
                // kids second, in the same row. The kids titles (picksKids)
                // render here, after the family movies, with the same
                // picture + app allow-list.
                synchronized (picksKids) {
                    java.util.Map<String, Integer> perSrcK = new java.util.HashMap<String, Integer>();
                    int kidsShown = 0;
                    for (final WatchItem wi : picksKids) {
                        if (!hasArt(wi)) continue;
                        String pk = wi.pkg;
                        if (!"com.tubitv".equals(pk) && !"tv.pluto.android".equals(pk) && !"xyz.netfly".equals(pk) && !"org.drive_hunter".equals(pk)) continue;
                        String k = pk == null ? "" : pk;
                        Integer n = perSrcK.get(k);
                        if (n != null && n.intValue() >= 3) continue;
                        perSrcK.put(k, n == null ? 1 : n.intValue() + 1);
                        View t = pickTile(wi, true);
                        Integer upId = watchChipIds.get("picks");
                        if (upId != null) t.setNextFocusUpId(upId);
                        picksRow.addView(t);
                        if (++kidsShown >= 30) break;
                    }
                }
            }
        }

        // v3.91: the row was just rebuilt -- if the focused card was
        // destroyed, put focus back on the section chip, not on a card.
        restoreWatchFocusIfLost();
    }

    // ---------- Tubi Top picks (v4.08) ----------
    // Real movie cards from Tubi's web API -- no TV provider needed, so
    // the N5 Max and X96mini get Top picks too. The fetch runs on a
    // background thread; the app row shows meanwhile (never blank), and
    // a late or failed fetch leaves the app row in place. The token and
    // the fetched titles are cached in the "tubi" preferences (6 hours
    // for titles, the token until 5 minutes before its expiry).

    Tubi.Store tubiStore() {
        final android.content.SharedPreferences sp = getSharedPreferences("tubi", MODE_PRIVATE);
        return new Tubi.Store() {
            public String getString(String k, String d) { try { return sp.getString(k, d); } catch (Throwable t) { return d; } }
            public void putString(String k, String v) { try { sp.edit().putString(k, v).apply(); } catch (Throwable t) { } }
            public long getLong(String k, long d) { try { return sp.getLong(k, d); } catch (Throwable t) { return d; } }
            public void putLong(String k, long v) { try { sp.edit().putLong(k, v).apply(); } catch (Throwable t) { } }
        };
    }

    /** The installed Tubi package (com.tubitv on a TV box,
     *  com.tvtv.tubi.app on the N5 Max), or "" when Tubi is not
     *  installed -- then the card shows no source label and the tap
     *  falls back to the Tubi web page in the browser. */
    String tubiPkg() {
        if (hasPkg("com.tubitv")) return "com.tubitv";
        if (hasPkg("com.tvtv.tubi.app")) return "com.tvtv.tubi.app";
        // v4.26: the N5 Max Tubi build has a different package name, so
        // the two hardcoded names miss it and openTubiCard falls back
        // to the implicit deep link (the "Open with" chooser). Scan the
        // installed apps for any package containing "tubi" instead.
        try {
            for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                if (ai != null && ai.packageName != null && ai.packageName.toLowerCase(java.util.Locale.US).contains("tubi")) return ai.packageName;
            }
        } catch (Exception e) {}
        return "";
    }

    /** Tubi titles as Top picks cards. The 16:9 landscape art fits the
     *  248x140 tile and the backdrop; the genre (tags) lets the existing
     *  kids/music split classify each card. */
    List<WatchItem> tubiToWatchItems(List<Tubi.Title> ts) {
        List<WatchItem> out = new ArrayList<WatchItem>();
        String pkg = tubiPkg();
        for (Tubi.Title t : ts) {
            if (t == null || t.id.length() == 0 || t.title.length() == 0) continue;
            WatchItem wi = new WatchItem();
            try { wi.id = Long.parseLong(t.id); } catch (Exception e) { wi.id = 0; }
            wi.title = t.title;
            wi.poster = Tubi.artFor(t);
            wi.intentUri = Tubi.movieIntentUri(t.id);
            wi.pkg = pkg;
            wi.genre = t.genre;
            wi.desc = t.year > 0 ? String.valueOf(t.year) : "";
            out.add(wi);
        }
        return out;
    }

    /** Render Tubi cards in the Top picks row. Kids titles go to the
     *  Kids row, the same split as the provider path. */
    void renderTubiPicks(List<WatchItem> wis) {
        tubiFallbackShown = false;
        picksRow.removeAllViews();
        // v4.28: Top picks holds EVERYTHING -- family first, then kids
        // second, in the same row. Kids titles render here, after the
        // family ones, instead of going to the Kids Continue watching row.
        List<WatchItem> kids = new ArrayList<WatchItem>();
        for (WatchItem wi : wis) {
            // v4.23: no picture, no card (same as the provider path).
            if (!hasArt(wi)) continue;
            if (isKids(wi)) kids.add(wi);
            else {
                View t = pickTile(wi, true);
                Integer upId = watchChipIds.get("picks");
                if (upId != null) t.setNextFocusUpId(upId);
                picksRow.addView(t);
            }
        }
        for (final WatchItem wi : kids) {
            View t = pickTile(wi, true);
            Integer upId = watchChipIds.get("picks");
            if (upId != null) t.setNextFocusUpId(upId);
            picksRow.addView(t);
        }
        restoreWatchFocusIfLost();
    }

    /** Top picks when there are no provider movie cards: Tubi cards when
     *  the cache is fresh, else the app row now and a background refresh
     *  that swaps the Tubi cards in when it lands. */
    void showTubiPicksOrApps() {
        tubiFallbackShown = true;
        if (tubiCache != null && System.currentTimeMillis() - tubiCacheAt < Tubi.TITLES_TTL_MS) {
            renderTubiPicks(tubiCache);
            return;
        }
        try {
            Tubi.Store st = tubiStore();
            String cj = st.getString("titles_json", "");
            long cat = st.getLong("titles_at", 0);
            if (cj.length() > 0 && System.currentTimeMillis() - cat < Tubi.TITLES_TTL_MS) {
                List<Tubi.Title> ts = Tubi.titlesFromJson(cj);
                if (!ts.isEmpty()) {
                    // v4.24: the cache now holds series too (for the kids
                    // sets) -- the CARDS stay movies-only.
                    List<Tubi.Title> mv = new ArrayList<Tubi.Title>();
                    for (Tubi.Title t : ts) if (t != null && t.isMovie()) mv.add(t);
                    tubiCache = tubiToWatchItems(mv);
                    tubiCacheAt = cat;
                    // v4.17: kids IDs from the same cache (regular titles
                    // whose Tubi genre says kids) plus the persisted
                    // kids-mode set, so WatchNext Tubi cards classify
                    // correctly even before the next fetch.
                    // v4.22: same for the exact kids titles.
                    tubiKidsIds.addAll(Tubi.kidsIds(ts));
                    tubiKidsTitles.addAll(Tubi.kidsTitles(ts));
                    loadTubiKidsIds();
                    loadTubiKidsTitles();
                    renderTubiPicks(tubiCache);
                    return;
                }
            }
        } catch (Throwable t) { /* fall through to the app row + fetch */ }
        renderPicksApps();
        refreshTubiPicks();
    }

    // v4.24: on a provider box the Tubi fetch never runs (the provider
    // has movies, so the fallback never triggers), which left the kids
    // sets empty and isTubiKids false for every provider card. Refresh
    // the sets in the background when the persisted set is stale (>6h)
    // or empty. Throttled by the TTL + the tubiFetching guard.
    void refreshTubiKidsSetsIfStale() {
        try {
            Tubi.Store st = tubiStore();
            long at = st.getLong("kids_ids_at", 0);
            if (System.currentTimeMillis() - at < Tubi.TITLES_TTL_MS) return;
            if (tubiFetching) return;
            refreshTubiPicks();
        } catch (Throwable t) { /* best effort only */ }
    }

    void refreshTubiPicks() {
        if (tubiFetching) return;
        tubiFetching = true;
        final int gen = ++tubiGen;
        final Tubi.Store st = tubiStore();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    // v4.24: fetch ALL titles (movies + series) -- the kids
                    // sets need the series too (Larva, Milo and Chip,
                    // Pokemon the Series), and the regular movies-only
                    // fetch excluded them. The Top picks CARDS stay movies
                    // only (filtered below), so the cards do not change.
                    final List<Tubi.Title> ts = Tubi.fetchHomescreenTitles(st, false, false);
                    if (ts == null || ts.isEmpty()) return;
                    // v4.24: movies-only subset, for the cards cache.
                    final List<Tubi.Title> moviesOnly = new ArrayList<Tubi.Title>();
                    for (Tubi.Title t : ts) if (t != null && t.isMovie()) moviesOnly.add(t);
                    // v4.17: kids set = every ID from Tubi's kids-mode
                    // homescreen (kids by definition) plus regular titles
                    // whose own Tubi genre says kids. Best effort -- a
                    // kids-mode failure must not lose the regular titles.
                    // v4.24: kids-mode is fetched ONCE and feeds both the
                    // ID set and the title set (it was fetched twice).
                    final java.util.Set<String> kids = new java.util.HashSet<String>(Tubi.kidsIds(ts));
                    final java.util.Set<String> kidTitles = new java.util.HashSet<String>(Tubi.kidsTitles(ts));
                    try {
                        List<Tubi.Title> kts = Tubi.fetchKidsTitles(st);
                        if (kts != null) for (Tubi.Title kt : kts) {
                            if (kt == null) continue;
                            if (kt.id.length() > 0) kids.add(kt.id);
                            if (kt.title.length() > 0) kidTitles.add(kt.title.toLowerCase(java.util.Locale.US).trim());
                        }
                    } catch (Throwable t) { /* kids-mode fetch failed, keep the genre/row set */ }
                    try {
                        st.putString("titles_json", Tubi.titlesToJson(ts));
                        st.putLong("titles_at", System.currentTimeMillis());
                        // v4.21: persist the UNION (existing set + new
                        // kids), not just this fetch -- the home page
                        // rotates, so replacing dropped IDs Cemba had
                        // already learned (Pokemon, Addams, Sonic).
                        java.util.Set<String> union = new java.util.HashSet<String>(tubiKidsIds);
                        union.addAll(kids);
                        union.addAll(Tubi.idsFromString(st.getString("kids_ids", "")));
                        st.putString("kids_ids", Tubi.idsToString(union));
                        st.putLong("kids_ids_at", System.currentTimeMillis());
                        java.util.Set<String> unionT = new java.util.HashSet<String>(tubiKidsTitles);
                        unionT.addAll(kidTitles);
                        unionT.addAll(Tubi.titlesSetFromJson(st.getString("kids_titles", "")));
                        st.putString("kids_titles", Tubi.titlesSetToJson(unionT));
                        st.putLong("kids_titles_at", System.currentTimeMillis());
                    } catch (Throwable t) { }
                    runOnUiThread(new Runnable() { @Override public void run() {
                        if (gen != tubiGen) return;
                        // v4.24: ALWAYS update the in-memory kids sets and
                        // re-classify, even on a provider box (where
                        // tubiFallbackShown is false). The old early return
                        // on !tubiFallbackShown meant the sets never updated
                        // on the Xiaomi box, so isTubiKids stayed false and
                        // Pokemon / Addams / Sonic never left the main rows.
                        // v4.21: UNION, never replace -- the set only grows.
                        tubiKidsIds.addAll(kids);
                        tubiKidsLoaded = true;
                        tubiKidsTitles.addAll(kidTitles);
                        tubiKidsTitlesLoaded = true;
                        if (tubiFallbackShown) {
                            tubiCache = tubiToWatchItems(moviesOnly);
                            tubiCacheAt = System.currentTimeMillis();
                            renderTubiPicks(tubiCache);
                        }
                        // v4.17: the new kids set can move Tubi WatchNext
                        // cards (Pokemon / Sonic / Addams) from the main
                        // row to the Kids row -- re-run both loads so they
                        // re-classify with the fresh set.
                        loadWatch();
                        loadPicks();
                    }});
                } catch (Throwable t) {
                    // Any failure leaves the app row in place.
                } finally { tubiFetching = false; }
            }
        }).start();
    }

    /** v3.98: the Top picks app row -- one card per installed movie app
        (Tubi, Pluto TV, Netfly). Tap it and the app opens to browse its
        movies. No PreviewPrograms harvest, no posters, no backdrop, so
        there is nothing here that can open YouTube or push the box out of
        RAM. Apps that are not installed are simply not shown. */
    void renderPicksApps() {
        // v4.04: the N5 Max list. He ran pm list packages -3 on the N5
        // Max (192.168.1.66) and MANY movie apps are installed there -- my
        // short list missed most of them, so the row showed nothing. Two
        // Tubi packages exist: com.tubitv (APKMirror) and com.tvtv.tubi.app
        // (the one actually on the N5 Max), so both are checked. Added the
        // N5 Max movie/TV apps: Yacine TV Pro, Ostora, Tunisie Live TV,
        // Morph TV, EgyPlayer. Only installed apps are shown, as before,
        // so this is safe on every box.
        String[][] apps = {
            { "com.tubitv", "Tubi" },
            { "com.tvtv.tubi.app", "Tubi" },
            { "tv.pluto.android", "Pluto TV" },
            { "xyz.netfly", "Netfly" },
            { "org.drive_hunter", "Netfly" },
            { "com.stremio.one", "Stremio" },
            { "ca.cbc.android.cbctv", "CBC Gem" },
            { "ver3.ycntivi.off", "Yacine TV" },
            { "com.t4w.ostora516", "Ostora" },
            { "com.linkinapp.tunisielivetv", "Tunisie Live TV" },
            { "local.tvmorph.tv", "Morph TV" },
            { "com.playeregy.egyplayerapp", "EgyPlayer" },
        };
        boolean any = false;
        for (String[] a : apps) {
            if (!hasPkg(a[0])) continue;
            any = true;
            View t = appTile(a[0], a[1]);
            Integer upId = watchChipIds.get("picks");
            if (upId != null) t.setNextFocusUpId(upId);
            picksRow.addView(t);
        }
        if (!any) {
            TextView t = new TextView(this);
            t.setText("No movie apps installed.");
            t.setTextColor(Color.rgb(170, 170, 180));
            t.setTextSize(20);
            picksRow.addView(t);
        }
    }

    /** One Top picks app card: the app icon above its name. Tap it and the
        app opens (through launchPackage, so a PIN-locked app still asks
        for the PIN). No poster, no backdrop. */
    View appTile(final String pkg, final String fallbackLabel) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(tileBg());
        String label = fallbackLabel;
        android.graphics.drawable.Drawable icon = null;
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            icon = pm.getApplicationIcon(pkg);
        } catch (Exception e) { /* keep the fallback label, no icon */ }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(64), dp(64));
            ip.bottomMargin = dp(8);
            box.addView(iv, ip);
        }
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(20);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);
        tile.addView(box, new FrameLayout.LayoutParams(-1, -1));
        tile.setContentDescription(label);
        focusable(tile);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchPackage(pkg); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(248), dp(140));
        lp.rightMargin = dp(18);
        tile.setLayoutParams(lp);
        return tile;
    }

    /** v3.92: a card is a YouTube card when its package is the YouTube app
        OR its intent is a YouTube link. Publishers sometimes leave the
        package blank or put a plain https://youtube.com URL in the intent,
        so a package-only check missed them and OK opened the official
        YouTube app instead of Cobalt. */
    boolean isYouTubeCard(WatchItem wi) {
        if (wi == null) return false;
        // v3.94: string check (package column, YouTube link, or the package
        // name inside the intent text) plus the parsed intent's own
        // package/component. An intent can target the YouTube app by package
        // with no youtube.com URL in it, and the provider often leaves the
        // package column blank -- that is how Meet the Parents slipped
        // through on v3.93 and OK opened the official YouTube app.
        if (Logic.isYouTubeTarget(wi.intentUri, wi.pkg)) return true;
        if (wi.intentUri == null) return false;
        try {
            Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
            String pr = i.getPackage();
            if (pr == null && i.getComponent() != null) pr = i.getComponent().getPackageName();
            if ("com.google.android.youtube.tv".equals(pr)) return true;
        } catch (Exception e) { /* not a parseable intent */ }
        return false;
    }

    /** v3.92: open a YouTube card in Cobalt -- by video ID when the intent
        carries one, otherwise a title (+channel) search. */
    void openYouTubeCard(WatchItem wi) {
        String id = extractVideoId(wi.intentUri);
        if (id != null) { openCobaltUrl("https://www.youtube.com/watch?v=" + id); return; }
        String q = wi.title;
        if (wi.desc != null && wi.desc.length() > 0 && wi.desc.length() <= 40
                && !wi.desc.contains("\u2022")) q = q + " " + wi.desc;
        openCobaltSearch(q);
    }

    // Pulls a YouTube video ID out of a pick's intent/URL text (same patterns as the Bridge).
    String extractVideoId(String s) { return Logic.extractVideoId(s); }

    /** v3.97: true when this card's intent would actually open the official
        YouTube app. An implicit intent (a URL or a vnd.youtube link with no
        package) has no package/component in its text, so the string checks
        miss it -- but the system still resolves it to the YouTube app. Ask
        the PackageManager what would really open, and route that to Cobalt.
        This is the catch-all for the Top picks card that kept opening
        YouTube on v3.94-v3.96. */
    boolean resolvesToYouTube(WatchItem wi) {
        if (wi == null || wi.intentUri == null) return false;
        try {
            Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
            // v4.06: was MATCH_DEFAULT_ONLY -- that only matches activities
            // declaring CATEGORY_DEFAULT, so a YouTube card whose intent
            // is a content:// URI or a custom YouTube action (no
            // youtube.com URL, no package in the text) resolved to nothing
            // and fell through to startActivity, which opened the official
            // YouTube app even on v4.05 in Family mode. Flag 0 matches ALL
            // activities, so it catches YouTube in any intent form.
            android.content.pm.ResolveInfo ri = getPackageManager().resolveActivity(i, 0);
            return ri != null && ri.activityInfo != null
                && "com.google.android.youtube.tv".equals(ri.activityInfo.packageName);
        } catch (Exception e) { return false; }
    }

    // v4.16: app-label cache. pickTile/watchTile asked the PackageManager
    // for the app label (getApplicationInfo + getApplicationLabel = 2 IPC
    // calls) on EVERY tile, every refresh -- up to ~130 tiles for a
    // handful of distinct packages. Labels do not change while the app
    // runs, so cache them per package (a few entries, bounded).
    java.util.Map<String, String> appLabelCache = new java.util.HashMap<String, String>();
    String appLabel(String pkg) {
        if (pkg == null) return null;
        String s = appLabelCache.get(pkg);
        if (s != null) return s;
        try { s = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))); }
        catch (Exception e) { s = pkg; }
        appLabelCache.put(pkg, s);
        return s;
    }

    View pickTile(final WatchItem wi) { return pickTile(wi, false); }

    View pickTile(final WatchItem wi, final boolean bgOnFocus) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(tileBg());
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        // v4.16: label from the cache (see appLabel) -- was 2 PackageManager
        // IPC calls per tile, every refresh.
        String src = appLabel(wi.pkg);
        if (src != null && src.length() > 0) {
            TextView sv2 = new TextView(this);
            sv2.setText(src);
            sv2.setTextColor(Color.rgb(170, 200, 235));
            sv2.setTextSize(12);
            sv2.setShadowLayer(6, 0, 2, Color.BLACK);
            info.addView(sv2);
        }
        TextView tv = new TextView(this);
        tv.setText(wi.title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(17);
        tv.setShadowLayer(6, 0, 2, Color.BLACK);
        tv.setMaxLines(2);
        info.addView(tv);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tlp.leftMargin = dp(12);
        tlp.rightMargin = dp(12);
        tlp.bottomMargin = dp(10);
        tile.addView(info, tlp);
        // v3.24: music/kids Watch Next rows often publish no poster art -- without
        // this the tile was blank. Fallback stays unless a real poster loads.
        iv.setImageResource(isKids(wi) ? R.drawable.tile_kids
            : isMusic(wi) ? R.drawable.tile_music : R.drawable.tile_default);
        // v4.16: artFor once per tile (was computed twice -- poster load
        // and tile tag -- and it re-derives the YouTube thumbnail URL).
        String art = artFor(wi);
        Img.load(this, iv, art);
        tile.setContentDescription(wi.title);
        // v3.81: store the poster URI on the tile so the auto-advance can
        // cycle the backdrop without moving focus.
        tile.setTag(art);
        // v3.68: Top picks tiles need BOTH the focus visuals (gold border +
        // scale + headline, from focusable) AND the backdrop. The old code
        // called focusable(tile) then OVERWROTE its listener with the
        // backdrop-only one, so the tiles lost their border/scale -- focus
        // moved down to them but the user could not see it, and the
        // backdrop never showed. Now one combined listener does both.
        tile.setFocusable(true);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.animate().scaleX(has ? 1.13f : 1f).scaleY(has ? 1.13f : 1f).setDuration(120).start();
                view.setForeground(has ? borderFg() : null);
                if (has) {
                    CharSequence cd = view.getContentDescription();
                    if (cd != null && headlineTv != null && !heroTheme()) headlineTv.setText(cd);
                }
                if (bgOnFocus) {
                    if (has) {
                        showPickBg(artFor(wi));
                        // v3.81: manual focus restarts the 5s auto timer, so
                        // the backdrop does not change right after he picks
                        // a movie by hand.
                        startPickAuto();
                    }
                    else hidePickBgSoon();
                }
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    noteOpened(wi); // v4.19: remember it for Continue watching
                    // v3.92: route ANY YouTube card to Cobalt, not just ones
                    // whose package is the YouTube app. Some publishers
                    // (YouTube Movies, kids videos) leave the package
                    // blank or put a plain https://youtube.com URL in the
                    // intent, so the package check alone missed them and
                    // OK on the card opened the official YouTube app
                    // (his video: Meet the Parents in Top picks, and a
                    // kids card, both opened YouTube). His rule: YouTube
                    // always means TizenTube Cobalt.
                    // v3.92/v3.97: route a YouTube card to Cobalt, but only
                    // outside Kids mode. In Kids mode the Bridge is off so
                    // YouTube must open in the official app / YouTube Kids,
                    // not Cobalt; routing it to Cobalt broke YouTube Kids
                    // (his report after v3.96). "YouTube means Cobalt" is
                    // the Family profile rule.
                    if (!kidsMode() && isYouTubeCard(wi)) { openYouTubeCard(wi); return; }
                    // v3.97: resolve the intent -- an implicit intent can
                    // resolve to the YouTube app at launch time even when
                    // its text names no package and no youtube.com URL,
                    // which is why Meet the Parents still opened YouTube
                    // on v3.96. If it resolves to the official YouTube
                    // app, open it in Cobalt instead.
                    if (!kidsMode() && resolvesToYouTube(wi)) { openYouTubeCard(wi); return; }
                    // v4.45: Kids mode -- same as the Watching tile: a
                    // YouTube card opens in the official YouTube app
                    // with the package set explicitly, so the Bridge
                    // cannot grab the URL and send it to Cobalt.
                    if (kidsMode() && (isYouTubeCard(wi) || resolvesToYouTube(wi))) { openYouTubeVideo(wi.intentUri); return; }
                    // v4.20: Tubi cards open in the Tubi app directly --
                    // the implicit deep link showed an "Open with" chooser
                    // on the N5 Max (no Tubi in it) and the movie would
                    // not open at all.
                    if (isTubiCard(wi)) { openTubiCard(wi); return; }
                    Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) { toast("Couldn't open this pick"); }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(248), dp(140));
        lp.rightMargin = dp(18);
        tile.setLayoutParams(lp);
        return tile;
    }

    // ---------- Continue watching rows (WatchNext store) ----------
    // Music row is fed from two sources (Watch Next in fillWatch, song picks in
    // fillPicks); both render through musicSeen so a song never appears twice.
    final List<WatchItem> picksMusic = new ArrayList<WatchItem>();
    final java.util.Set<String> musicSeen = new java.util.HashSet<String>();
    boolean musicStaticsAdded = false;
    // v3.64: best kids movies from PreviewPrograms, for the Kids section.
    final List<WatchItem> picksKids = new ArrayList<WatchItem>();
    // v4.08: Tubi Top picks -- real movie cards from Tubi's web API on
    // boxes with no TV provider (or when no provider cards harvested).
    // tubiCache holds converted WatchItems, tubiCacheAt their time;
    // tubiFetching guards one in-flight fetch; tubiGen drops stale
    // callbacks; tubiFallbackShown is true while the row shows the app
    // row because there were no cards, so a late fetch may replace it.
    List<WatchItem> tubiCache = null; long tubiCacheAt = 0;
    boolean tubiFetching = false; int tubiGen = 0; boolean tubiFallbackShown = false;
    // v4.17: Tubi kids IDs -- the set of Tubi content IDs Tubi itself
    // says are kids (kids-mode homescreen + kids-genre titles). A
    // WatchNext item from Tubi is kids iff its Tubi ID is in here.
    // Persisted in the "tubi" prefs (kids_ids / kids_ids_at, same TTL
    // as the titles) so it is ready before any fetch completes.
    final java.util.Set<String> tubiKidsIds = new java.util.HashSet<String>();
    boolean tubiKidsLoaded = false;
    // v4.22: exact lowercased TITLES Tubi itself lists as kids -- the
    // second key for a WatchNext item whose intent URI carries no
    // parseable Tubi ID (the Tubi app's WatchNext intents do not always
    // expose the tubitv.com/movies/{id} link). This is an EXACT match
    // against titles Tubi classified as kids, not keyword filtering.
    // Persisted in the "tubi" prefs (kids_titles), unioned like the IDs.
    final java.util.Set<String> tubiKidsTitles = new java.util.HashSet<String>();
    boolean tubiKidsTitlesLoaded = false;

    // v4.19: Cemba's own "recently opened" list -- what he tapped in
    // Cemba itself (a Top picks Tubi card, a Continue watching card...).
    // This is how watching shows up in Continue watching on a box with
    // NO TV provider (N5 Max): Tubi has nowhere to publish its Watch
    // Next there, and Cemba cannot read inside the Tubi app, so Cemba
    // remembers its own opens instead. Stored in the "recent" prefs
    // (items_json), most recent first, capped at 20, deduped by
    // intentUri. Only Cemba opens are recorded -- if he opens the Tubi
    // app directly and watches there, Cemba cannot see it.
    static final int RECENT_MAX = 20;

    void noteOpened(WatchItem wi) {
        if (wi == null || wi.intentUri == null || wi.intentUri.length() == 0) return;
        if (wi.title == null || wi.title.trim().isEmpty()) return;
        try {
            List<WatchItem> rec = loadRecent();
            List<WatchItem> out = new ArrayList<WatchItem>();
            out.add(wi);
            for (WatchItem r : rec) {
                if (out.size() >= RECENT_MAX) break;
                if (r.intentUri != null && r.intentUri.equals(wi.intentUri)) continue;
                out.add(r);
            }
            getSharedPreferences("recent", MODE_PRIVATE).edit()
                .putString("items_json", recentToJson(out)).apply();
        } catch (Throwable t) { /* recents are best-effort */ }
    }

    List<WatchItem> loadRecent() {
        List<WatchItem> out = new ArrayList<WatchItem>();
        try {
            String j = getSharedPreferences("recent", MODE_PRIVATE).getString("items_json", "");
            if (j != null && j.length() > 0) out = recentFromJson(j);
        } catch (Throwable t) { /* no recents yet */ }
        return out;
    }

    static String recentToJson(List<WatchItem> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (WatchItem wi : items) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"title\":").append(Tubi.jsonStr(wi.title));
            sb.append(",\"poster\":").append(Tubi.jsonStr(wi.poster));
            sb.append(",\"intentUri\":").append(Tubi.jsonStr(wi.intentUri));
            sb.append(",\"pkg\":").append(Tubi.jsonStr(wi.pkg));
            sb.append(",\"genre\":").append(Tubi.jsonStr(wi.genre));
            sb.append(",\"rating\":").append(Tubi.jsonStr(wi.rating));
            sb.append(",\"desc\":").append(Tubi.jsonStr(wi.desc));
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static List<WatchItem> recentFromJson(String json) {
        List<WatchItem> out = new ArrayList<WatchItem>();
        Object v = Iptv.parseJson(json);
        if (!(v instanceof List)) return out;
        for (Object o : (List<Object>) v) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            WatchItem wi = new WatchItem();
            wi.title = jstr(m, "title");
            wi.poster = jstr(m, "poster");
            wi.intentUri = jstr(m, "intentUri");
            wi.pkg = jstr(m, "pkg");
            wi.genre = jstr(m, "genre");
            wi.rating = jstr(m, "rating");
            wi.desc = jstr(m, "desc");
            if (wi.title.length() == 0 || wi.intentUri.length() == 0) continue;
            out.add(wi);
        }
        return out;
    }

    static String jstr(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v instanceof String) ? (String) v : "";
    }

    void loadWatch() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<WatchItem> items = new ArrayList<WatchItem>();
                final boolean[] denied = { false };
                // v3.88: X96mini has no TvContract class -- skip, row stays empty.
                if (!Publish.tvAvailable()) { runOnUiThread(new Runnable() { @Override public void run() { fillWatch(items, false); } }); return; }
                try {
                    ContentResolver cr = getContentResolver();
                    String[] proj = { TvContract.WatchNextPrograms._ID,
                        TvContract.PreviewPrograms.COLUMN_TITLE,
                        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                        TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                        TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                        "genre",
                        TvContract.PreviewPrograms.COLUMN_CONTENT_RATING,
                        TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
                        TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                        TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION };
                    Cursor c = cr.query(TvContract.WatchNextPrograms.CONTENT_URI, proj, null, null,
                        TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC");
                    if (c != null) {
                        Set<String> seen = new HashSet<String>();
                        while (c.moveToNext() && items.size() < 100) {
                            String intentUri = c.getString(3);
                            if (intentUri == null || intentUri.startsWith("browsehere")) continue;
                            String t = c.getString(1);
                            if (t == null) t = "";
                            String pkg = null;
                            try { pkg = c.getString(4); } catch (Exception e) { pkg = null; }
                            if (!seen.add(Logic.dedupeKey(t, pkg, intentUri))) continue;
                            if ("com.bacem.launcher".equals(pkg) || "com.bacem.ytshort.cards".equals(pkg)) continue; // own shortcut/weather cards stay out of Continue watching
                            WatchItem wi = new WatchItem();
                            wi.id = c.getLong(0);
                            wi.title = t;
                            wi.poster = c.getString(2);
                            wi.intentUri = intentUri;
                            wi.pkg = pkg;
                            try { wi.genre = c.getString(5); } catch (Exception e) { wi.genre = null; }
                            try { wi.rating = c.getString(6); } catch (Exception e) { wi.rating = null; }
                            try { wi.dur = c.getLong(7); } catch (Exception e) { wi.dur = 0; }
                            try { wi.pos = c.getLong(8); } catch (Exception e) { wi.pos = 0; }
                            try { wi.desc = c.getString(9); } catch (Exception e) { wi.desc = null; }
                            if (wi.title == null || wi.title.trim().isEmpty()) continue;
                            items.add(wi);
                        }
                        c.close();
                    }
                } catch (SecurityException e) { denied[0] = true; }
                catch (Throwable t) { /* row stays empty */ }
                // v4.19: merge Cemba's own recents in front (most recent
                // first), deduped against the provider items by intent
                // URI. On a no-provider box this IS the Continue watching
                // content; on a provider box it adds what he opened from
                // Cemba that the provider has not published yet.
                try {
                    List<WatchItem> rec = loadRecent();
                    if (!rec.isEmpty()) {
                        java.util.Set<String> seenUris = new java.util.HashSet<String>();
                        for (WatchItem pi : items) if (pi.intentUri != null) seenUris.add(pi.intentUri);
                        List<WatchItem> merged = new ArrayList<WatchItem>();
                        for (WatchItem r : rec) {
                            if (r.intentUri != null && seenUris.add(r.intentUri)) merged.add(r);
                        }
                        merged.addAll(items);
                        items.clear();
                        items.addAll(merged);
                    }
                } catch (Throwable t) { /* recents are best-effort */ }
                runOnUiThread(new Runnable() {
                    @Override public void run() { fillWatch(items, denied[0]); }
                });
            }
        }).start();
    }

    // Static colored card in the Live TV card shape, with a caller-supplied tag and
    // action (Kids entrance cards, Music fallback searches).
    View staticCard(String name, String sub, String tag, int color, int artRes, final Runnable act) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        card.setBackground(g);
        if (artRes != 0) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageResource(artRes);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            // v3.28: photo backgrounds need a fade behind the bottom texts
            View scrim = new View(this);
            GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x00000000, 0xAA000000 });
            scrim.setBackground(sg);
            card.addView(scrim, new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM));
        }
        TextView tg = new TextView(this);
        tg.setText(tag);
        tg.setTextColor(Color.rgb(255, 205, 120));
        tg.setTextSize(12);
        tg.setTypeface(tg.getTypeface(), android.graphics.Typeface.BOLD);
        FrameLayout.LayoutParams tgl = new FrameLayout.LayoutParams(-2, -2);
        tgl.gravity = Gravity.TOP | Gravity.LEFT;
        tgl.leftMargin = dp(12); tgl.topMargin = dp(10);
        card.addView(tg, tgl);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setMaxLines(1);
        texts.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Color.rgb(215, 220, 232));
        s.setTextSize(12);
        s.setMaxLines(1);
        texts.addView(s);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2);
        tlp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        tlp.leftMargin = dp(12); tlp.bottomMargin = dp(10); tlp.rightMargin = dp(8);
        card.addView(texts, tlp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(248), dp(140));
        lp.rightMargin = dp(16);
        card.setLayoutParams(lp);
        card.setContentDescription(name);
        focusable(card);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act.run(); }
        });
        return card;
    }

    // Kids row (his choice, 2026-10-22): the kids profile's history is sealed inside
    // Google's profiles and can never feed a launcher, so the row leads with Cobalt
    // cards into the kids side; store items an app tags as kids still append after.
    void addKidsCards() {
        // v4.13: the "YouTube Kids" card is gone (his ask -- its KIDS tag
        // overlapped the subtitle). Kids Songs, Cartoons and Learn & Play stay.
        kidsRow.addView(staticCard("Kids Songs", "Sing-along favourites", "SING", 0xFF6A1B9A, R.drawable.tile_kids, new Runnable() {
            @Override public void run() { openCobaltSearch("kids songs nursery rhymes"); }}));
        kidsRow.addView(staticCard("Cartoons", "Fun cartoons for kids", "FUN", 0xFF1565C0, R.drawable.tile_kids, new Runnable() {
            @Override public void run() { openCobaltSearch("cartoons for kids"); }}));
        kidsRow.addView(staticCard("Learn & Play", "Letters, numbers, colours", "LEARN", 0xFF2E7D32, R.drawable.tile_kids, new Runnable() {
            @Override public void run() { openCobaltSearch("learning videos for kids"); }}));
        // v4.35: Conan + Pokemon search shortcuts in the Kids watching
        // tab (his ask). Both open a search for the Arabic-dubbed
        // episodes; he will see later how to improve them (e.g. direct
        // channel links). v4.39 (his summary 2026-11-17): Family profile
        // keeps Cobalt as is; Kids profile opens the normal YouTube app
        // (openKidsSearch) -- the YouTube app there is signed into
        // Jannah's Kids profile.
        kidsRow.addView(staticCard("Conan", "Detective Conan in Arabic", "CONAN", 0xFFD32F2F, R.drawable.tile_kids, new Runnable() {
            @Override public void run() { openKidsSearch("\u0627\u0644\u0645\u062d\u0642\u0642 \u0643\u0648\u0646\u0627\u0646 \u0645\u062f\u0628\u0644\u062c"); }}));
        kidsRow.addView(staticCard("Pokemon", "Pokemon in Arabic", "POKEMON", 0xFFF9A825, R.drawable.tile_kids, new Runnable() {
            @Override public void run() { openKidsSearch("\u0628\u0648\u0643\u064a\u0645\u0648\u0646 \u0645\u062f\u0628\u0644\u062c"); }}));
    }

    // Adds song picks to the Music row (deduped through musicSeen); if the row would
    // otherwise stay a dead placeholder, fills it with Cobalt music search cards.
    void renderMusicExtras() {
        synchronized (picksMusic) {
            for (final WatchItem wi : picksMusic) {
                String key = ((wi.title == null ? "" : wi.title) + "|" + (wi.pkg == null ? "" : wi.pkg)).toLowerCase(Locale.US);
                if (musicSeen.add(key)) musicRow.addView(pickTile(wi));
            }
        }
        if (musicRow.getChildCount() == 0 && !musicStaticsAdded) {
            musicStaticsAdded = true;
            musicRow.addView(staticCard("Arabic Hits Mix", "Search in Cobalt", "MIX", 0xFFEF6C00, R.drawable.mx_arabic, new Runnable() {
                @Override public void run() { openCobaltSearch("Arabic songs mix"); }}));
            musicRow.addView(staticCard("Tunisian Music", "Search in Cobalt", "MIX", 0xFFEF6C00, R.drawable.mx_tunisian, new Runnable() {
                @Override public void run() { openCobaltSearch("Tunisian music mix"); }}));
            musicRow.addView(staticCard("Top Hits Mix", "Search in Cobalt", "MIX", 0xFFEF6C00, R.drawable.mx_tophits, new Runnable() {
                @Override public void run() { openCobaltSearch("top hits music mix"); }}));
        }
    }

    // v3.64: best kids movies from PreviewPrograms, for the Kids section.
    // Same pattern as renderMusicExtras: called from fillPicks and from
    // fillWatch, so the movies survive whichever load finishes last.
    void fillWatch(List<WatchItem> items, boolean denied) {
        watchRow.removeAllViews();
        musicRow.removeAllViews();
        kidsRow.removeAllViews();
        musicSeen.clear();
        musicStaticsAdded = false;
        if (denied) {
            View t = textTile("Continue watching needs one permission - tap here", new Runnable() {
                @Override public void run() { toast("In Bugjaeger shell run: " + GRANT_CMD); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(520), dp(110));
            lp.rightMargin = dp(18);
            t.setLayoutParams(lp);
            watchRow.addView(t);
            return;
        }
        List<WatchItem> main = new ArrayList<WatchItem>();
        List<WatchItem> music = new ArrayList<WatchItem>();
        List<WatchItem> kids = new ArrayList<WatchItem>();
        for (WatchItem w2 : items) {
            // v4.23: no picture, no card -- the Tubi WatchNext items
            // (Pokemon, Addams Family 2, Sonic Boom) arrive with no
            // poster art and render as blank dark tiles. He does not want
            // videos without a picture, in any row. Music keeps its cards
            // (a song shows the music tile, which is fine for audio).
            if (isKids(w2)) { if (hasArt(w2)) kids.add(w2); }
            else if (isMusic(w2)) music.add(w2);
            else { if (hasArt(w2)) main.add(w2); }
        }
        // v4.13: resume first -- videos started but not finished (pos > 0)
        // lead the row, then the rest in recency order. YouTube history
        // stays, but Continue watching now reads as resume, not history.
        // v4.23: YouTube leads -- his YouTube history is the source he
        // asked the Family row to start from, so YouTube cards come
        // before other apps' cards (resume-first inside each group).
        List<WatchItem> ytResume = new ArrayList<WatchItem>();
        List<WatchItem> ytRest = new ArrayList<WatchItem>();
        List<WatchItem> resume = new ArrayList<WatchItem>();
        List<WatchItem> rest = new ArrayList<WatchItem>();
        for (WatchItem w2 : main) {
            boolean yt = "com.google.android.youtube.tv".equals(w2.pkg);
            if (w2.pos > 0) { if (yt) ytResume.add(w2); else resume.add(w2); }
            else { if (yt) ytRest.add(w2); else rest.add(w2); }
        }
        main.clear(); main.addAll(ytResume); main.addAll(ytRest); main.addAll(resume); main.addAll(rest);
        if (main.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Nothing here yet - watch something in any app and it will show up here.");
            t.setTextColor(Color.rgb(170, 170, 180));
            t.setTextSize(20);
            watchRow.addView(t);
        }
        // v3.29: his YouTube profile's own continue watching -- the store only
        // carries what the official app publishes, so his profile history in
        // Cobalt stands in as well.
        // v4.23: the Family row ALWAYS starts with the YouTube history card
        // (he asked for it first), not only when no YouTube card is present.
        watchRow.addView(staticCard("YouTube history", "Your profile, in Cobalt", "YOUTUBE", 0xFFC62828, R.drawable.tile_default, new Runnable() {
            @Override public void run() { openCobaltUrl("https://www.youtube.com/feed/history"); }
        }));
        java.util.Map<String, Integer> perSrc = new java.util.HashMap<String, Integer>();
        java.util.Set<String> seenWatch = new java.util.HashSet<String>();
        for (final WatchItem wi : main) {
            // v3.28: Tubi publishes the same show three times with blank art --
            // identical cards collapse to one (distinct posters still survive).
            if (!seenWatch.add(Logic.watchDedupeKey(wi.title, wi.pkg, wi.poster))) continue;
            // One loud app (Tubi) flooded this row and buried his YouTube series:
            // cap each source so every app keeps a fair share of the row.
            String k = wi.pkg == null ? "" : wi.pkg;
            Integer n = perSrc.get(k);
            if (n != null && n.intValue() >= Logic.watchCapFor(wi.pkg)) continue;
            perSrc.put(k, n == null ? 1 : n.intValue() + 1);
            watchRow.addView(watchTile(wi));
        }
        for (final WatchItem wi : music) {
            String key = ((wi.title == null ? "" : wi.title) + "|" + (wi.pkg == null ? "" : wi.pkg)).toLowerCase(Locale.US);
            if (musicSeen.add(key)) musicRow.addView(watchTile(wi));
        }
        renderMusicExtras();
        addKidsCards();
        // v4.28: Kids Continue watching no longer starts with the Top picks
        // (reverses v3.84). It is the real continue watching now: only the
        // Watch Next kids items from Tubi, Netfly and the other apps that
        // publish them. The Top picks kids titles live in the Top picks
        // row instead (family first, kids second).
        for (final WatchItem wi : kids) {
            if (!seenWatch.add(Logic.watchDedupeKey(wi.title, wi.pkg, wi.poster))) continue;
            View t = watchTile(wi, true);
            Integer upId = watchChipIds.get("kids");
            if (upId != null) t.setNextFocusUpId(upId);
            kidsRow.addView(t);
        }
        // v3.91: same as fillPicks -- the rows were just rebuilt, so if the
        // focused card was destroyed, put focus back on the section chip.
        restoreWatchFocusIfLost();
    }

    // Android will not let an app revoke its own Home role - open the system Home picker
    // so he can hand Home back to Google TV in one tap.
    void unDefault() {
        // RoleManager's Home picker silently no-ops on his Google TV build (his
        // 2026-10-22 report: tap -> no popup, no text, default unchanged -- the
        // request "succeeds" so the old fallbacks never fired). Cemba IS already
        // the Home role holder (his 2026-10-23 transcript), yet Home still opens
        // Google TV Home -- so the tile lands on Google TV Home's own app page,
        // where the fix lives: Disable (remote-only, re-enable from Cemba) or
        // Open by default > Clear defaults, then Home and pick Cemba Home.
        toast("Opening Google TV Home - tap Disable to make Cemba Home your default");
        try {
            Intent d = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            d.setData(android.net.Uri.parse("package:com.google.android.apps.tv.launcherx"));
            d.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(d);
            android.widget.Toast.makeText(this,
                "Tap Disable - that makes Cemba Home your default. (Google comes back via Cemba > Box Settings > Apps > Google TV Home > Enable)",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        } catch (Exception e) { /* fall through to Home settings */ }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e2) { toast("Settings > Apps > Google TV Home > Disable"); }
    }

    // ---------- Cobalt fast path: open TizenTube Cobalt directly, skipping YouTube + Bridge ----------
    // YouTube search URL in Cobalt, for channels whose direct /live link is not confirmed yet.
    static String ytSearch(String q) {
        return "https://www.youtube.com/results?search_query=" + android.net.Uri.encode(q);
    }

    void openCobaltSearch(String query) {
        openCobaltUrl(ytSearch(query));
    }

    // v4.39: open a YouTube search in the official YouTube app, not
    // Cobalt. Used by the Kids Conan/Pokemon cards when the profile is
    // Kids: in Kids mode the Bridge is off and the YouTube app is signed
    // into Jannah's Kids profile, so the search opens in normal YouTube
    // there. Tries the YouTube TV app first (Xiaomi), then the phone
    // YouTube app (N5 Max), then a plain intent as a fallback.
    void openYouTubeSearch(String query) {
        try {
            String url = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8");
            String[] pkgs = { "com.google.android.youtube.tv", "com.google.android.youtube" };
            for (String pk : pkgs) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    i.setPackage(pk);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    return;
                } catch (Exception e) { /* try next */ }
            }
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("YouTube is not installed on this box");
        }
    }

    // v4.45: open a YouTube video in the official YouTube app, package
    // set explicitly so the Bridge cannot grab the URL and send it to
    // Cobalt. Used by Kids-mode cards (Watching + Top picks): in Kids
    // mode the raw WatchNext intent is a youtube.com URL and the Bridge
    // intercepts it even with the Bridge "off", so the card opened
    // TizenTube. Same shape as openYouTubeSearch, which works.
    void openYouTubeVideo(String intentUri) {
        String id = extractVideoId(intentUri);
        String url = id != null ? "https://www.youtube.com/watch?v=" + id : null;
        if (url == null) {
            try {
                Intent i = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
                i.setPackage("com.google.android.youtube.tv");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception e) { /* fall through */ }
            toast("Couldn't open this video");
            return;
        }
        String[] pkgs = { "com.google.android.youtube.tv", "com.google.android.youtube" };
        for (String pk : pkgs) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                i.setPackage(pk);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception e) { /* try next */ }
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { toast("YouTube is not installed on this box"); }
    }

    // v4.39: Kids-aware search -- normal YouTube app in Kids mode,
    // Cobalt in Family mode (kept as is there).
    void openKidsSearch(String query) {
        if (kidsMode()) openYouTubeSearch(query);
        else openCobaltSearch(query);
    }


    // v4.13: bring Cobalt to front WITHOUT a URL, so a video left playing
    // resumes where it was instead of restarting. When Cobalt is already
    // running, Android brings the existing task forward and Cobalt keeps
    // its page; when it is not running, it starts on YouTube home. This
    // is what "go back to YouTube" should do -- openCobaltUrl always
    // navigates to a new page, which is why the video was lost.
    boolean bringCobaltToFront() {
        String[] pkgs = { "io.gh.reisxd.tizentube.cobalt", "io.gh.reisxd.tizentube.cobalt.ab" };
        for (String pk : pkgs) {
            try {
                Intent i = new Intent();
                i.setComponent(new android.content.ComponentName(pk, "dev.cobalt.app.MainActivity"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) { /* try next */ }
        }
        return false;
    }

    // v4.44 Back fix: a YouTube watch URL opens in the YouTube TV
    // format (Cobalt's own base is youtube.com/tv) with a fresh
    // Cobalt, so the video is the FIRST page in its history and one
    // Back press exits to Cemba. Non-video URLs keep the old launch.
    static String cobaltTvUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf("v=");
        if (q < 0) return null;
        int end = url.indexOf('&', q);
        String id = end < 0 ? url.substring(q + 2) : url.substring(q + 2, end);
        if (id.length() == 0) return null;
        return "https://www.youtube.com/tv#/watch?v=" + id;
    }

    boolean tryCobalt(String url) {
        String[] pkgs = { "io.gh.reisxd.tizentube.cobalt", "io.gh.reisxd.tizentube.cobalt.ab" };
        String tv = cobaltTvUrl(url);
        String open = tv != null ? tv : url;
        for (String pk : pkgs) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(open));
                i.setComponent(new android.content.ComponentName(pk, "dev.cobalt.app.MainActivity"));
                i.putExtra("url", open);
                // bringCobaltToFront is untouched -- it must keep the
                // playing video.
                if (tv != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                } else {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                startActivity(i);
                return true;
            } catch (Exception e) { /* try next */ }
        }
        return false;
    }

    void openCobaltUrl(String url) {
        if (!tryCobalt(url)) toast("TizenTube Cobalt not installed");
    }

    View watchTile(final WatchItem wi) { return watchTile(wi, false); }

    // v3.84: Kids Continue watching gets the backdrop too (his ask: "Kids
    // continue watching should have the background as well"). bgOnFocus
    // tiles show the poster as the tab background on focus, like Top picks.
    View watchTile(final WatchItem wi, final boolean bgOnFocus) {
            FrameLayout tile = new FrameLayout(this);
            tile.setBackground(tileBg());
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            // v4.16: label from the cache (see appLabel) -- was 2 PackageManager
            // IPC calls per tile, every refresh.
            String src = appLabel(wi.pkg);
            if (src != null && src.length() > 0) {
                TextView sv2 = new TextView(this);
                sv2.setText(src);
                sv2.setTextColor(Color.rgb(170, 200, 235));
                sv2.setTextSize(12);
                sv2.setShadowLayer(6, 0, 2, Color.BLACK);
                info.addView(sv2);
            }
            TextView tv = new TextView(this);
            tv.setText(wi.title);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(17);
            tv.setShadowLayer(6, 0, 2, Color.BLACK);
            tv.setMaxLines(2);
            info.addView(tv);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            tlp.leftMargin = dp(12); tlp.rightMargin = dp(12); tlp.bottomMargin = dp(9);
            tile.addView(info, tlp);
            if (wi.dur > 0 && wi.pos > 0) {
                View track = new View(this);
                track.setBackgroundColor(0x55FFFFFF);
                tile.addView(track, new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM));
                View fill = new View(this);
                fill.setBackgroundColor(Color.rgb(229, 9, 20));
                int fw = (int) (dp(248) * Math.min(1.0, (double) wi.pos / (double) wi.dur));
                tile.addView(fill, new FrameLayout.LayoutParams(fw, dp(4), Gravity.BOTTOM));
            }
            // v3.28: same fallback as pickTile -- Continue watching rows often
            // publish no poster (Tubi's blank cards). Fallback stays unless a
            // real poster loads.
            iv.setImageResource(isKids(wi) ? R.drawable.tile_kids
                : isMusic(wi) ? R.drawable.tile_music : R.drawable.tile_default);
            // v4.16: artFor once per tile (was computed twice -- poster load
            // and tile tag).
            String art = artFor(wi);
            Img.load(this, iv, art);
            tile.setContentDescription(wi.title);
            // v3.84: store the poster URI so the auto-advance can cycle it.
            tile.setTag(art);
            if (bgOnFocus) {
                // Combined listener (same shape as pickTile): scale + border
                // + headline AND the backdrop, so Kids Continue watching
                // behaves like Top picks.
                tile.setFocusable(true);
                tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override public void onFocusChange(View view, boolean has) {
                        view.animate().scaleX(has ? 1.13f : 1f).scaleY(has ? 1.13f : 1f).setDuration(120).start();
                        view.setForeground(has ? borderFg() : null);
                        if (has) {
                            CharSequence cd = view.getContentDescription();
                            if (cd != null && headlineTv != null && !heroTheme()) headlineTv.setText(cd);
                            showPickBg(artFor(wi));
                            startPickAuto();
                        } else hidePickBgSoon();
                    }
                });
            } else {
                focusable(tile);
            }
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        noteOpened(wi); // v4.19: remember it for Continue watching
                        Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
                        String pkg = i.getPackage();
                        if (pkg == null && i.getComponent() != null) pkg = i.getComponent().getPackageName();
                        // v3.92: add the YouTube-link check too -- a plain
                        // https://youtube.com URL parses to an ACTION_VIEW
                        // with no package at all, so the package check above
                        // missed it and it opened the official YouTube app.
                        // v3.92/v3.97: route a YouTube card to Cobalt, but only
                        // outside Kids mode (Kids must open the official
                        // YouTube / YouTube Kids app, not Cobalt).
                        if (!kidsMode() && ("com.google.android.youtube.tv".equals(pkg) || isYouTubeCard(wi) || resolvesToYouTube(wi))) {
                            openYouTubeCard(wi);
                            return;
                        }
                        // v4.45: Kids mode -- a YouTube card must open in
                        // the official YouTube app, not Cobalt. The raw
                        // WatchNext intent is a youtube.com URL and the
                        // Bridge grabs it even with the Bridge "off" in
                        // Kids, so the Watching tab kept opening
                        // TizenTube. Set the package explicitly instead.
                        if (kidsMode() && ("com.google.android.youtube.tv".equals(pkg) || isYouTubeCard(wi) || resolvesToYouTube(wi))) {
                            openYouTubeVideo(wi.intentUri);
                            return;
                        }
                        // v4.20: Tubi cards open in the Tubi app directly
                        // (see openTubiCard -- the implicit deep link
                        // showed an "Open with" chooser on the N5 Max).
                        if (isTubiCard(wi)) { openTubiCard(wi); return; }
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) { toast("Couldn't open this card"); }
                }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                if (Publish.tvAvailable()) getContentResolver().delete(TvContract.WatchNextPrograms.CONTENT_URI,
                                    TvContract.WatchNextPrograms._ID + "=?",
                                    new String[] { String.valueOf(wi.id) });
                            } catch (Throwable t) { /* ignore */ }
                            runOnUiThread(new Runnable() {
                                @Override public void run() { toast("Removed: " + wi.title); loadWatch(); }
                            });
                        }
                    }).start();
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(248), dp(140));
            lp.rightMargin = dp(18);
            tile.setLayoutParams(lp);
            return tile;
    }

    /** Card art (v3.50): the publisher's poster when it has one; otherwise the
     *  YouTube thumbnail derived from the video ID in the intent URI. The
     *  YouTube app publishes no poster in its Watch Next rows, so without the
     *  second step every Continue watching card stays on the flat fallback. */
    String artFor(WatchItem wi) {
        if (wi.poster != null && wi.poster.length() > 0) return wi.poster;
        return Logic.ytThumbUrl(wi.intentUri);
    }

    // v4.23: true iff this card has a real picture to show. A card with
    // no poster art renders as a blank dark tile with just a play button
    // and a title -- he said plainly he does not want videos without a
    // picture. Top picks drops such cards instead of showing them blank.
    boolean hasArt(WatchItem wi) {
        String a = artFor(wi);
        return a != null && a.length() > 0;
    }

    // ---------- Apps ----------
    static class AppItem {
        ResolveInfo ri; String label; ComponentName comp;
    }

    List<AppItem> queryApps() {
        PackageManager pm = getPackageManager();
        LinkedHashMap<String, AppItem> map = new LinkedHashMap<String, AppItem>();
        int[] cats = { 0, 1 };
        for (int k : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(k == 0 ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                // v3.23: one circle per app. Some apps (YTV) declare two launcher
                // activities -- a TV one and a phone one -- with the same name, which
                // used to show as two identical circles. The leanback query runs
                // first, so the TV entry wins and the phone entry is skipped.
                if (map.containsKey(pkg)) continue;
                ComponentName comp = new ComponentName(pkg, ri.activityInfo.name);
                AppItem ai = new AppItem();
                ai.ri = ri;
                ai.label = String.valueOf(ri.loadLabel(pm));
                ai.comp = comp;
                map.put(pkg, ai);
            }
        }
        List<AppItem> all = new ArrayList<AppItem>(map.values());
        Collections.sort(all, new Comparator<AppItem>() {
            @Override public int compare(AppItem a, AppItem b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return all;
    }



    // ---------- Apps: circles row + reorder ----------
    void loadApps() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppItem> all = queryApps();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        // v3.39: a bad app entry must not kill the home.
                        try { fillApps(all); } catch (Throwable t) {}
                    }
                });
            }
        }).start();
    }


    GradientDrawable borderOvalFg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(3), Color.WHITE);
        return g;
    }

    // v3.54: the header photo is a circle, so its focus frame is a circle
    // in the theme gold -- the rectangular border cut across its top.
    GradientDrawable borderOvalAccentFg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(3), accent);
        return g;
    }

    void focusableOval(final View v) {
        v.setFocusable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
                view.setForeground(has ? borderOvalFg() : null);
                if (has) {
                    CharSequence cd = view.getContentDescription();
                    if (cd != null && headlineTv != null && !heroTheme()) headlineTv.setText(cd);
                }
            }
        });
    }

    void focusableOvalAccent(final View v) {
        v.setFocusable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
                view.setForeground(has ? borderOvalAccentFg() : null);
                if (has) {
                    CharSequence cd = view.getContentDescription();
                    if (cd != null && headlineTv != null && !heroTheme()) headlineTv.setText(cd);
                }
            }
        });
    }

    static final int[] CIRCLE_COLORS = { 0xFF37474F, 0xFF4A6572, 0xFF5D4037, 0xFF455A64,
        0xFF6A4C93, 0xFF00695C, 0xFF283593, 0xFF795548 };

    // v4.33: flat index of a circle inside the Apps-panel grid, and the
    // circle +/-step positions away (step 7 = one row). Returns null when
    // the step lands outside the grid (first/last row) -- the caller then
    // stays put instead of letting the focus search jump to the end.
    View gridNeighbour(View circle, int step) {
        try {
            View cell = (View) circle.getParent();
            View row = (View) cell.getParent();
            android.view.ViewGroup panel = (android.view.ViewGroup) row.getParent();
            // Collect the circles in panel order (skip child 0 = title).
            java.util.List<View> circles = new java.util.ArrayList<View>();
            for (int r = 1; r < panel.getChildCount(); r++) {
                View rr = panel.getChildAt(r);
                if (!(rr instanceof LinearLayout)) continue;
                LinearLayout rl = (LinearLayout) rr;
                for (int c = 0; c < rl.getChildCount(); c++) {
                    View cc = rl.getChildAt(c);
                    if (!(cc instanceof LinearLayout)) continue;
                    LinearLayout cl = (LinearLayout) cc;
                    if (cl.getChildCount() > 0 && cl.getChildAt(0) instanceof FrameLayout)
                        circles.add(cl.getChildAt(0));
                }
            }
            int idx = circles.indexOf(circle);
            if (idx < 0) return null;
            int target = idx + step;
            if (target < 0) return null;
            if (target >= circles.size()) {
                // Down past the last row: land on the last app, not past it.
                if (step > 0 && idx < circles.size() - 1) return circles.get(circles.size() - 1);
                return null;
            }
            return circles.get(target);
        } catch (Exception e) { return null; }
    }

    View appCircle(final AppItem ai) { return appCircle(ai, true); }
    // The grid in the Apps tab reuses the same cell but without the long-press
    // Move (Move only works on the home row -- it reorders appsRow).
    View appCircle(final AppItem ai, final boolean movable) {
        final LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        final FrameLayout circle = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int hc = Math.abs(ai.comp.getPackageName().hashCode());
        g.setColor(CIRCLE_COLORS[hc % CIRCLE_COLORS.length]);
        circle.setBackground(g);
        Drawable icon = null; // v3.62: icon packs removed -- always the app's own icon
        if (icon == null) {
            try { icon = ai.ri.loadIcon(getPackageManager()); } catch (Exception e) { icon = null; }
        }
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            iv.setClipToOutline(true);
            circle.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView letter = new TextView(this);
            letter.setText(ai.label.length() > 0 ? ai.label.substring(0, 1).toUpperCase(Locale.getDefault()) : "?");
            letter.setTextColor(Color.WHITE);
            letter.setTextSize(hsp(30));
            letter.setGravity(Gravity.CENTER);
            circle.addView(letter, new FrameLayout.LayoutParams(-1, -1));
        }
        Drawable banner = null;
        try { banner = ai.ri.activityInfo.loadBanner(getPackageManager()); } catch (Exception e) { banner = null; }
        // No banner tag on app tiles: focusing an app must NOT put its logo art
        // into the background. The background stays his photos, always.
        circle.setContentDescription(ai.label);
        focusableOval(circle);
        // v4.33: Apps-tab grid navigation. The panel is a grid (7 per row)
        // inside a ScrollView, and Android's default focus search jumps
        // DOWN straight to the last app instead of the row below. So in
        // the Apps panel (movable=false) DOWN/UP move exactly one row
        // (+/-7) and stay put at the first/last row. The home row
        // (movable=true) is a single horizontal row, so it keeps the
        // default behaviour (DOWN leaves the row for the section below).
        if (!movable) circle.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN && keyCode != KeyEvent.KEYCODE_DPAD_UP) return false;
                View target = gridNeighbour(v, keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 7 : -7);
                if (target != null) { target.requestFocus(); return true; }
                return true; // first/last row: stay, never jump to the end
            }
        });
        circle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (movingPkg != null) return;
                final String pkg = ai.comp.getPackageName();
                // v3.60: in the Apps tab (movable=false) a tap manages the app
                // -- Open / Hide / Lock / Uninstall -- instead of launching
                // it (his ask). The home row still launches on tap.
                if (!movable) { manageAppDialog(ai.label, pkg); return; }
                runIfPinOk(pkg, new Runnable() {
                    @Override public void run() {
                        Intent i = new Intent(Intent.ACTION_MAIN);
                        i.setComponent(ai.comp);
                        i.addCategory(Intent.CATEGORY_LAUNCHER);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { startActivity(i); }
                        catch (Exception e) { launchPackageRaw(pkg); }
                    }
                });
            }
        });
        if (movable) circle.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                startMove(cell, circle, ai.label);
                return true;
            }
        });
        cell.addView(circle, new LinearLayout.LayoutParams(hdp(68), hdp(68)));
        TextView lab = new TextView(this);
        lab.setText(ai.label);
        lab.setTextColor(Color.rgb(232, 232, 236));
        lab.setTextSize(hsp(14));
        lab.setGravity(Gravity.CENTER);
        lab.setMaxLines(1);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(hdp(90), -2);
        llp.topMargin = hdp(7);
        cell.addView(lab, llp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.rightMargin = hdp(16);
        cell.setLayoutParams(clp);
        cell.setTag(ai.comp.getPackageName());
        return cell;
    }

    String movingPkg = null;

    // Move mode for one app circle: Left/Right slide it along the row, OK/Back drops it
    // and the new order is saved (ui prefs "app_order") and reapplied on every fillApps().
    void startMove(final View cellView, final View circleView, final String label) {
        movingPkg = (String) cellView.getTag();
        toast("Move " + label + ":  \u25c0 \u25b6  to slide, OK to drop");
        // The long-press that opens Move mode is still held when this listener attaches;
        // its repeats would read as an instant "OK to drop" and kill the mode before any
        // sliding (his 2026-10-21 report: "Move appears, arrows don't work"). So a drop
        // only counts after the button has been RELEASED once (armed) and pressed fresh.
        final boolean[] armed = { false };
        circleView.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (movingPkg == null) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) { armed[0] = true; return true; }
                    if (e.getAction() == KeyEvent.ACTION_DOWN && armed[0]) {
                        saveAppOrder(); movingPkg = null; circleView.setOnKeyListener(null); toast("Order saved");
                    }
                    return true;
                }
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    int idx = appsRow.indexOfChild(cellView);
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && idx > 0) {
                        appsRow.removeViewAt(idx); appsRow.addView(cellView, idx - 1);
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && idx >= 0 && idx < appsRow.getChildCount() - 1) {
                        appsRow.removeViewAt(idx); appsRow.addView(cellView, idx + 1);
                    }
                    circleView.requestFocus();
                }
                return true;
            }
        });
        circleView.requestFocus();
    }

    void saveAppOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            Object tg = appsRow.getChildAt(i).getTag();
            if (tg != null) { if (sb.length() > 0) sb.append(","); sb.append(tg.toString()); }
        }
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("app_order", sb.toString()).apply();
    }


    // v4.16: signature of the last apps row built. fillApps runs on every
    // onResume (via loadApps) and rebuilt ~50 circles -- icon loads +
    // ~300 views -- even when nothing had changed.
    String lastAppsSig = null;

    void fillApps(List<AppItem> all) {
        // Your apps as circles, in his saved order first (long-press a circle to move it)
        lastApps = new ArrayList<AppItem>(all);
        // v4.16: skip the rebuild when the row would render identically.
        // The signature covers everything the row shows: the app list
        // (package + label), the saved order, the hidden set, the Kids
        // set and the profile. Any change in those rebuilds as before.
        // (An icon-only app update without a label change keeps the old
        // icon until the next rebuild trigger; labels and installs do
        // trigger one.)
        StringBuilder sig = new StringBuilder();
        for (AppItem ai : all) sig.append(ai.comp.getPackageName()).append('|').append(ai.label).append(';');
        sig.append('#').append(getSharedPreferences("ui", MODE_PRIVATE).getString("app_order", ""));
        sig.append('#').append(getSharedPreferences("ui", MODE_PRIVATE).getString("hidden_apps", ""));
        sig.append('#').append(kidsMode());
        if (kidsMode()) sig.append(getSharedPreferences("ui", MODE_PRIVATE).getString("kids_apps", ""));
        String s = sig.toString();
        if (s.equals(lastAppsSig)) return;
        lastAppsSig = s;
        appsRow.removeAllViews();
        final String ord = getSharedPreferences("ui", MODE_PRIVATE).getString("app_order", "");
        Set<String> hidden = csvSet("hidden_apps");
        List<AppItem> sorted = new ArrayList<AppItem>(all);
        // v3.91 (speed): the saved order is parsed ONCE into a map, then the
        // sort is two HashMap lookups per comparison. The old call rebuilt
        // "," + orderCsv + "," and scanned it twice on every comparison.
        final java.util.Map<String, Integer> ordPos = Logic.orderMap(ord);
        java.util.Collections.sort(sorted, new java.util.Comparator<AppItem>() {
            @Override public int compare(AppItem a, AppItem b) {
                return Logic.compareOrder(ordPos, a.comp.getPackageName(), b.comp.getPackageName());
            }
        });
        Set<String> kids = kidsMode() ? csvSet("kids_apps") : null;
        for (AppItem ai : sorted)
            if (!hidden.contains(ai.comp.getPackageName())
                && (kids == null || kids.contains(ai.comp.getPackageName()))) appsRow.addView(appCircle(ai));
    }

    // ---------- Shortcut tiles ----------
    View textTile(String text, final Runnable action) {
        FrameLayout tile = new FrameLayout(this);
        // Slim glass chip (v3.14, his "smaller and nicer"): matches the header pills'
        // design language instead of the old 220x88 block with the shared tileBg.
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0xA60E121C);
        chip.setCornerRadius(dp(14));
        chip.setStroke(dp(1), 0x55FFFFFF);
        tile.setBackground(chip);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tile.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        tile.setContentDescription(text);
        focusable(tile);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), dp(62));
        lp.rightMargin = dp(14);
        tile.setLayoutParams(lp);
        return tile;
    }
}
