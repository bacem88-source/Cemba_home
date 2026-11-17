package com.bacem.launcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure decision logic for Cemba Home - no Android imports, so it can be
 * unit-tested on a plain JVM (see tests/LogicTest.java and tests/PerfTest.java;
 * the build runs both and stops if any scenario fails or a budget is blown).
 *
 * Everything here answers a small question the launcher asks constantly:
 * which row does this card belong in, what video does this link mean,
 * which picture matches this weather, which app comes first.
 */
public class Logic {

    // ---------- Continue watching classification ----------

    /**
     * Kids row = explicit children's signals only: a TV-Y rating, an
     * explicit kid/children/preschool/animation genre, or a kids source app.
     * v4.18: "anim" is back -- dropping it in v4.17 moved animated kids
     * titles (Pokemon, Sonic, Addams Family) OUT of the Kids row and into
     * the main rows, which is what he reported ("continue watching family
     * is still giving the kids list", "top picks is giving more about
     * kids"). "family" genre alone stays NOT kids (a family drama is not
     * a kids show). TV-G / G are general-audience, NOT kids.
     * v4.17: NO title/description keyword check any more (the old
     * KIDS_KW list) -- filtering by name was the wrong fix (his call:
     * "we need a final solution to differentiate between both"). A
     * title word matched the wrong thing (MASHA cooking show matched
     * "masha" for Masha and the Bear). Tubi titles are classified by
     * Tubi's own data instead (see Tubi kids IDs in MainActivity).
     */
    // v4.25: the kids titles HE named, as exact titles (normalised:
    // lowercased, accents stripped, so "Pok\u00e9mon" and "Pokemon"
    // match). This is NOT keyword filtering -- it is an exact match
    // against a definitive list of titles that are kids. It is a
    // backstop for Top picks: the PreviewPrograms cards there carry no
    // genre/rating and their links do not always expose a Tubi ID, so
    // neither the genre check nor the Tubi ID/title sets can catch
    // them, even though the same titles ARE caught in Continue
    // watching (WatchNext). The Tubi sets stay the primary signal.
    static final java.util.Set<String> KNOWN_KIDS_TITLES = new java.util.HashSet<String>(java.util.Arrays.asList(
        "pokemon: the first movie",
        "pokemon the series: sun & moon",
        "larva",
        "milo and chip",
        "the addams family 2",
        "sonic boom",
        "masha and the bear",
        "the magic school bus",
        "spongebob squarepants",
        "spongebob",
        "paw patrol",
        "the fish guys",
        "stay wild",
        "air buddies",
        "snow buddies",
        "space buddies",
        "santa buddies",
        "spooky buddies",
        "treasure buddies",
        "super buddies"
    ));

    static String normTitle(String t) {
        if (t == null) return "";
        String n = t.toLowerCase(java.util.Locale.US).trim();
        n = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n;
    }

    public static boolean isKnownKidsTitle(String title) {
        return KNOWN_KIDS_TITLES.contains(normTitle(title));
    }

    public static boolean isKids(String genre, String rating, String pkg) {
        String r = rating == null ? "" : rating.toUpperCase(Locale.US).replace('_', '-');
        if (r.contains("TV-Y")) return true;
        String g = genre == null ? "" : genre.toLowerCase(Locale.US);
        if (g.contains("kid") || g.contains("children") || g.contains("preschool") || g.contains("anim")) return true;
        if (pkg != null && (pkg.contains("kids") || pkg.contains("pbskids"))) return true;
        return false;
    }

    /** Same as isKids -- kept so callers do not change. The title and
     *  description are ignored on purpose (v4.17, see isKids). */
    public static boolean isKidsFull(String genre, String rating, String pkg, String title, String desc) {
        return isKids(genre, rating, pkg);
    }

    /** Kids timer (v3.87): time-based counting. The old tick-counted minutes
        died when MainActivity was destroyed in the background (e.g. while
        Jannah watched YouTube Kids), so the limit never fired. Now the
        used time is computed from the clock: stored minutes from finished
        sessions + elapsed minutes in the current session. */
    public static long kidsElapsedMin(long sessionStartMillis, long nowMillis) {
        if (sessionStartMillis <= 0 || nowMillis < sessionStartMillis) return 0;
        return (nowMillis - sessionStartMillis) / 60000L;
    }

    public static long kidsUsedTotal(long usedStoredMin, long sessionStartMillis, long nowMillis) {
        return usedStoredMin + kidsElapsedMin(sessionStartMillis, nowMillis);
    }

    public static long kidsRemainingMin(long limitMin, long usedStoredMin, long sessionStartMillis, long nowMillis) {
        long r = limitMin - kidsUsedTotal(usedStoredMin, sessionStartMillis, nowMillis);
        return r < 0 ? 0 : r;
    }

    /** Bedtime (v3.87): minutes until bedtime today. Returns 0 when bedtime
        is already reached (blocked now), -1 when no bedtime is set or the
        value is invalid. nowMin is minutes since midnight. */
    public static long bedtimeRemainingMin(String bedtime, int nowMin) {
        if (bedtime == null || bedtime.length() == 0) return -1;
        String[] parts = bedtime.split(":");
        if (parts.length != 2) return -1;
        int bed;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            bed = h * 60 + m;
        } catch (Exception e) { return -1; }
        long r = (long) bed - (long) nowMin;
        return r < 0 ? 0 : r;
    }

    /** Timer-up lockdown (v4.29): the PowerService (AccessibilityService)
        closes any app that opens while Kids mode is on and the time is up,
        so the remote YouTube / Netflix / Prime buttons cannot bypass the
        timer. This is the pure form of MainActivity.kidsCheck +
        bedtimeBlocked + kidsUsedToday -- same rules, no Android calls, so
        the service and the launcher can never disagree about "time is up".
        nowMin is minutes since midnight. Note: like bedtimeBlocked it does
        NOT range-check the hour (a bad "25:00" simply never matches the
        evening window, but the before-7-AM window still applies). */
    public static boolean kidsTimerUp(boolean kidsMode, int limitMin, int usedStoredMin, long sessionStartMillis, long nowMillis, String bedtime, int nowMin) {
        if (!kidsMode) return false;
        if (bedtime != null && bedtime.length() > 0) {
            String[] parts = bedtime.split(":");
            if (parts.length == 2) {
                try {
                    int bed = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                    if (nowMin >= bed || nowMin < 420) return true;
                } catch (Exception e) { /* bad bedtime: ignore */ }
            }
        }
        if (limitMin <= 0) return false;
        long used = usedStoredMin + kidsElapsedMin(sessionStartMillis, nowMillis);
        return used >= limitMin;
    }
    public static boolean isMusic(String genre, String pkg) {
        String g = genre == null ? "" : genre.toLowerCase(Locale.US);
        if (g.contains("music")) return true;
        if (pkg != null && (pkg.contains("music") || pkg.contains("spotify"))) return true;
        return false;
    }

    /** Fuller music test that also reads the title/description. His Watch Next rows
        rarely carry a genre, and his SmartTube song picks are Arabic titles or
        "Artist ft Artist - Song" shapes -- genre alone left the Music row empty.
        v3.68: SmartTube music videos are "Artist - Song" (e.g. "Fadel Chaker -
        Nam El Layl") with no music keyword in the title, so a SmartTube item
        whose title has " - " counts as music -- otherwise songs pollute Top
        picks, which is movies only.
        Speed: same as isKidsFull -- the keyword list is a static constant,
        not rebuilt on every card. */
    private static final String[] MUSIC_KW = { "music", "song", "songs", " remix", "dj ", "album", "concert",
        "playlist", "greatest hits", "top hits", " feat", " ft ", "official audio",
        "\u0627\u063a\u0627\u0646\u064a", "\u0623\u063a\u0627\u0646\u064a",
        "\u0645\u0648\u0633\u064a\u0642\u0649", "\u0637\u0631\u0628", "\u0631\u0627\u062f\u064a\u0648" };

    public static boolean isMusicFull(String genre, String pkg, String title, String desc) {
        if (isMusic(genre, pkg)) return true;
        String t = ((title == null ? "" : title) + " " + (desc == null ? "" : desc)).toLowerCase(Locale.US);
        if (pkg != null && pkg.contains("smarttube") && title != null && title.contains(" - ")) return true;
        for (String k : MUSIC_KW) if (t.contains(k)) return true;
        return false;
    }

    /** Weather ring color: always the theme accent (gold on his theme).
     *  v3.54: the temperature bands are gone -- the ring follows the theme,
     *  the fill fraction still comes from the temperature. */
    public static int wxRingColor(int temp) { return wxRingColorFor(temp, 0xFFE8C874); }

    /** Theme-aware ring color: the ring is the theme accent at any temperature. */
    public static int wxRingColorFor(int temp, int accent) {
        return accent;
    }

    /** Temperature mapped to ring fill: -10C nearly empty, 35C full. */
    public static float wxRingFrac(int temp) {
        float f = (temp + 10) / 45f;
        return Math.max(0.05f, Math.min(1f, f));
    }

    /** Weather ring diameter in dp for a widget size (0 Small, 1 Medium, 2 Large).
     *  v3.55: the ring box is this size too, so the ring and the centred
     *  temperature always share a centre -- Small used to put a 64dp ring
     *  top-left in a fixed 96dp box while the text was centred in the box. */
    public static int wxRingSize(int size) { return size == 0 ? 64 : size == 2 ? 110 : 96; }

    /** Which Continue watching row a card belongs in: kids first, then music, else films. */
    public static String rowFor(String genre, String rating, String pkg) {
        if (isKids(genre, rating, pkg)) return "kids";
        if (isMusic(genre, pkg)) return "music";
        return "films";
    }

    /** Dedupe key for Top picks rows (title + app). */
    public static String dedupeKey(String title, String pkg) {
        return title + "|" + pkg;
    }

    /** Dedupe key for store rows: the same title from the same app with the same intent is one card. */
    public static String dedupeKey(String title, String pkg, String intentUri) {
        return title + "|" + pkg + "|" + intentUri;
    }

    /** Dedupe key for Continue watching: the same title from the same app with the
     * same poster art is one card (Tubi publishes the same show three times with
     * blank art). Distinct posters survive, so real episodes are kept. */
    public static String watchDedupeKey(String title, String pkg, String poster) {
        String t = title == null ? "" : title.trim().toLowerCase(java.util.Locale.US);
        String p = pkg == null ? "" : pkg;
        String a = poster == null ? "" : poster;
        return t + "|" + p + "|" + a;
    }

    /** Junk Top picks: utility apps publishing status cards, not video
     * (Send Files to TV publishes "Transfer Service Active"). */
    public static boolean isJunkPick(String pkg, String title) {
        if ("com.yablio.sendfilestotv".equals(pkg)) return true;
        if (title != null && "Transfer Service Active".equalsIgnoreCase(title.trim())) return true;
        return false;
    }

    // ---------- YouTube ----------

    // Precompiled once: extractVideoId used to recompile all four patterns on
    // every call, so a row of hundreds of picks paid that cost hundreds of times.
    private static final Pattern VID_WATCH = Pattern.compile("watch\\?v=([a-zA-Z0-9_-]{11})");
    private static final Pattern VID_SHORT = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})");
    private static final Pattern VID_VND = Pattern.compile("vnd\\.youtube:(?://)?([a-zA-Z0-9_-]{11})");
    private static final Pattern VID_PARAM = Pattern.compile("[?&]video_id=([a-zA-Z0-9_-]{11})");

    /** Pulls the 11-char video ID out of a YouTube URL / intent text; null when there is none. */
    public static String extractVideoId(String s) {
        if (s == null) return null;
        Matcher m;
        m = VID_WATCH.matcher(s);
        if (m.find()) return m.group(1);
        m = VID_SHORT.matcher(s);
        if (m.find()) return m.group(1);
        m = VID_VND.matcher(s);
        if (m.find()) return m.group(1);
        m = VID_PARAM.matcher(s);
        if (m.find()) return m.group(1);
        return null;
    }

    /** v3.94: a card targets YouTube when its package is the YouTube app, or
        its intent text is a YouTube link (youtube.com / youtu.be), or it names
        the YouTube package. The last case is the one that slipped through:
        a YouTube Movies card whose intent targets the app by package
        ("intent:#Intent;...;package=com.google.android.youtube.tv;...;end")
        with no youtube.com URL in it, and the provider leaving the package
        column blank -- so a package-only or URL-only check missed it and OK
        opened the official YouTube app (his video: Meet the Parents in Top
        picks on v3.93). */
    public static boolean isYouTubeTarget(String intentUri, String pkg) {
        if ("com.google.android.youtube.tv".equals(pkg)) return true;
        if (intentUri == null) return false;
        String u = intentUri.toLowerCase(Locale.US);
        // v3.96: also "vnd.youtube" -- a YouTube card can carry a
        // vnd.youtube://VIDEO_ID intent, which has no youtube.com URL and
        // no package/component, so the earlier checks missed it and OK
        // opened the official YouTube app (his report on v3.95: Meet the
        // Parents still opened YouTube). The shell cannot read the TV
        // provider, so the exact intent is unconfirmed, but vnd.youtube is
        // the YouTube intent form the checks did not cover.
        return u.contains("youtube.com") || u.contains("youtu.be")
            || u.contains("vnd.youtube")
            || u.contains("com.google.android.youtube.tv");
    }

    // ---------- Weather ----------

    /** Scene key for an Open-Meteo weather code: clear, night, cloud, fog, rain, snow or storm. */
    public static String wxSceneKey(int code, int isDay) {
        if (code == 0 || code == 1) return isDay == 1 ? "clear" : "night";
        if (code == 2 || code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if (code >= 95 && code <= 99) return "storm";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        return "rain";
    }

    /** Short human label for an Open-Meteo weather code; "" for unknown codes. */
    // Icon for a weather code: same bands as the scenes, finer for clear/partly cloudy.
    public static String wxIconKey(int code, int isDay) {
        boolean day = isDay == 1;
        if (code == 0 || code == 1) return day ? "sun" : "moon";
        if (code == 2) return day ? "suncloud" : "mooncloud";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if (code >= 95 && code <= 99) return "storm";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "rain";
        return "cloud";
    }

    // "2026-10-20T07:38" -> "7:38 AM"
    public static String sunTimeFmt(String iso) {
        if (iso == null || iso.length() < 16) return "";
        try {
            int hh = Integer.parseInt(iso.substring(11, 13));
            String mm = iso.substring(14, 16);
            String ap = hh < 12 ? "AM" : "PM";
            int h12 = hh % 12;
            if (h12 == 0) h12 = 12;
            return h12 + ":" + mm + " " + ap;
        } catch (Exception e) { return ""; }
    }

    // Day length between two Open-Meteo ISO times -> "10 h 46 min"
    public static String dayLength(String riseIso, String setIso) {
        if (riseIso == null || setIso == null || riseIso.length() < 16 || setIso.length() < 16) return "";
        try {
            int r = Integer.parseInt(riseIso.substring(11, 13)) * 60 + Integer.parseInt(riseIso.substring(14, 16));
            int s = Integer.parseInt(setIso.substring(11, 13)) * 60 + Integer.parseInt(setIso.substring(14, 16));
            int diff = s - r;
            if (diff < 0) return "";
            return (diff / 60) + " h " + (diff % 60) + " min";
        } catch (Exception e) { return ""; }
    }

    // "12 min ago" / "3 h ago" / "2 d ago" for a news timestamp.
    public static String newsAge(long pubMs, long nowMs) {
        long min = (nowMs - pubMs) / 60000;
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        if (min < 1440) return (min / 60) + " h ago";
        return (min / 1440) + " d ago";
    }

    public static String wxLabel(int c) {
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
        if (c >= 95 && c <= 99) return "Thunderstorm";
        return "";
    }

    // ---------- App ordering ----------

    /**
     * App ordering (v3.91, speed): parse the saved order ONCE into a map.
     * The old compareOrder rebuilt "," + orderCsv + "," and scanned it twice
     * on EVERY comparison, so sorting N apps cost O(N log N) string rebuilds
     * on the UI thread (fillApps). Callers that sort must build this map once
     * per refresh and use the map-based compareOrder below -- two HashMap
     * lookups per comparison, no string work at all.
     */
    public static java.util.Map<String, Integer> orderMap(String orderCsv) {
        java.util.Map<String, Integer> pos = new java.util.HashMap<String, Integer>();
        if (orderCsv == null || orderCsv.length() == 0) return pos;
        String[] parts = orderCsv.split(",");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() > 0 && !pos.containsKey(p)) pos.put(p, Integer.valueOf(i));
        }
        return pos;
    }

    /** Compare two packages by saved order using a prebuilt map. Unknown apps
     *  sort after saved ones, equal among themselves. */
    public static int compareOrder(java.util.Map<String, Integer> pos, String pkgA, String pkgB) {
        Integer ia = pos == null ? null : pos.get(pkgA);
        Integer ib = pos == null ? null : pos.get(pkgB);
        int a = ia == null ? 1000000 : ia.intValue();
        int b = ib == null ? 1000000 : ib.intValue();
        return a - b;
    }

    /**
     * Compares two packages against the saved order (comma-separated, from
     * long-press > Move). Saved apps come first in saved order; unsaved apps
     * go after, equal among themselves. Delimiter-wrapped matching, so
     * "com.tubi" never matches inside "com.tubitv".
     * Single-comparison convenience only -- do NOT use inside a sort; build
     * orderMap once and use compareOrder(Map, ...) instead (see above).
     */
    public static int compareOrder(String orderCsv, String pkgA, String pkgB) {
        return compareOrder(orderMap(orderCsv), pkgA, pkgB);
    }
    // ---------- v3.47: languages, widget sizes/order, themes ----------

    /** Next language in the cycle: English -> French -> Arabic -> English. */
    public static String langNext(String lang) {
        if ("en".equals(lang)) return "fr";
        if ("fr".equals(lang)) return "ar";
        return "en";
    }

    /** Arabic is the only right-to-left language in the launcher. */
    public static boolean isRtl(String lang) { return "ar".equals(lang); }

    /** Launcher label for a language key (en/fr/ar). Unknown keys fall back to the key itself. */
    public static String tr(String lang, String key) {
        if (key == null) return "";
        String l = lang == null ? "en" : lang;
        if ("home".equals(key)) {
            if ("fr".equals(l)) return "Accueil";
            if ("ar".equals(l)) return "\u0627\u0644\u0631\u0626\u064a\u0633\u064a\u0629";
            return "Home";
        }
        if ("categories".equals(key)) {
            if ("fr".equals(l)) return "Cat\u00e9gories";
            if ("ar".equals(l)) return "\u0627\u0644\u0641\u0626\u0627\u062a";
            return "Categories";
        }
        if ("theme".equals(key)) {
            if ("fr".equals(l)) return "Th\u00e8me";
            if ("ar".equals(l)) return "\u0627\u0644\u0633\u0645\u0629";
            return "Theme";
        }
        if ("widgets".equals(key)) {
            if ("fr".equals(l)) return "Widgets";
            if ("ar".equals(l)) return "\u0627\u0644\u0648\u062f\u062c\u0627\u062a";
            return "Widgets";
        }
        if ("watching".equals(key)) {
            if ("fr".equals(l)) return "Regarder";
            if ("ar".equals(l)) return "\u0645\u0634\u0627\u0647\u062f\u0629";
            return "Watching";
        }
        if ("settings".equals(key)) {
            if ("fr".equals(l)) return "Param\u00e8tres";
            if ("ar".equals(l)) return "\u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a";
            return "Settings";
        }
        if ("language".equals(key)) {
            if ("fr".equals(l)) return "Langue";
            if ("ar".equals(l)) return "\u0627\u0644\u0644\u063a\u0629";
            return "Language";
        }
        if ("your_apps".equals(key)) {
            if ("fr".equals(l)) return "Vos applications";
            if ("ar".equals(l)) return "\u062a\u0637\u0628\u064a\u0642\u0627\u062a\u0643";
            return "Your apps";
        }
        if ("apps".equals(key)) {
            if ("fr".equals(l)) return "Applications";
            if ("ar".equals(l)) return "\u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a";
            return "Apps";
        }
        // v4.18: renamed to "Continue watching Family" (his ask) -- the
        // main row is the family's, the kids row follows right after it.
        if ("continue_watching".equals(key)) {
            if ("fr".equals(l)) return "Continuer \u00e0 regarder (Famille)";
            if ("ar".equals(l)) return "\u0645\u062a\u0627\u0628\u0639\u0629 \u0627\u0644\u0645\u0634\u0627\u0647\u062f\u0629 (\u0627\u0644\u0639\u0627\u0626\u0644\u0629)";
            return "Continue watching Family";
        }
        if ("top_picks".equals(key)) {
            if ("fr".equals(l)) return "S\u00e9lection pour vous";
            if ("ar".equals(l)) return "\u0645\u062e\u062a\u0627\u0631\u0627\u062a \u0644\u0643";
            return "Top picks for you";
        }
        if ("live_tv".equals(key)) {
            if ("fr".equals(l)) return "TV en direct";
            if ("ar".equals(l)) return "\u062a\u0644\u0641\u0632\u064a\u0648\u0646 \u0645\u0628\u0627\u0634\u0631";
            return "Live TV";
        }
        if ("music".equals(key)) {
            if ("fr".equals(l)) return "Musique";
            if ("ar".equals(l)) return "\u0645\u0648\u0633\u064a\u0642\u0649";
            return "Music";
        }
        // v4.18: renamed to "Continue watching Kids" (his ask) -- it now
        // sits right after Continue watching Family.
        if ("kids".equals(key)) {
            if ("fr".equals(l)) return "Continuer \u00e0 regarder (Enfants)";
            if ("ar".equals(l)) return "\u0645\u062a\u0627\u0628\u0639\u0629 \u0627\u0644\u0645\u0634\u0627\u0647\u062f\u0629 (\u0627\u0644\u0623\u0637\u0641\u0627\u0644)";
            return "Continue watching Kids";
        }
        if ("news".equals(key)) {
            if ("fr".equals(l)) return "Actualit\u00e9s";
            if ("ar".equals(l)) return "\u0627\u0644\u0623\u062e\u0628\u0627\u0631";
            return "News";
        }
        if ("shortcuts".equals(key)) {
            if ("fr".equals(l)) return "Raccourcis";
            if ("ar".equals(l)) return "\u0627\u062e\u062a\u0635\u0627\u0631\u0627\u062a";
            return "Shortcuts";
        }
        if ("weather".equals(key)) {
            if ("fr".equals(l)) return "M\u00c9T\u00c9O - KITCHENER";
            if ("ar".equals(l)) return "\u0637\u0642\u0633 \u0643\u064a\u062a\u0634\u0646\u0631";
            return "WEATHER - KITCHENER";
        }
        if ("sun".equals(key)) {
            if ("fr".equals(l)) return "SOLEIL - KITCHENER";
            if ("ar".equals(l)) return "\u0627\u0644\u0634\u0645\u0633";
            return "SUN - KITCHENER";
        }
        if ("box_care".equals(key)) {
            if ("fr".equals(l)) return "ENTRETIEN DU BO\u00ceTIER";
            if ("ar".equals(l)) return "\u0627\u0644\u0639\u0646\u0627\u064a\u0629 \u0628\u0627\u0644\u062c\u0647\u0627\u0632";
            return "BOX CARE";
        }
        if ("clear_ram".equals(key)) {
            if ("fr".equals(l)) return "Lib\u00e9rer la RAM";
            if ("ar".equals(l)) return "\u062a\u062d\u0631\u064a\u0631 \u0627\u0644\u0630\u0627\u0643\u0631\u0629";
            return "Clear RAM";
        }
        if ("speed_test".equals(key)) {
            if ("fr".equals(l)) return "Test de vitesse";
            if ("ar".equals(l)) return "\u0627\u062e\u062a\u0628\u0627\u0631 \u0627\u0644\u0633\u0631\u0639\u0629";
            return "Speed test";
        }
        if ("now_playing".equals(key)) {
            if ("fr".equals(l)) return "EN COURS DE LECTURE";
            if ("ar".equals(l)) return "\u0642\u064a\u062f \u0627\u0644\u062a\u0634\u063a\u064a\u0644";
            return "NOW PLAYING";
        }
        if ("morning".equals(key)) {
            if ("fr".equals(l)) return "BONJOUR";
            if ("ar".equals(l)) return "\u0635\u0628\u0627\u062d \u0627\u0644\u062e\u064a\u0631";
            return "GOOD MORNING";
        }
        if ("afternoon".equals(key)) {
            if ("fr".equals(l)) return "BON APR\u00c8S-MIDI";
            if ("ar".equals(l)) return "\u0637\u0627\u0628 \u064a\u0648\u0645\u0643";
            return "GOOD AFTERNOON";
        }
        if ("evening".equals(key)) {
            if ("fr".equals(l)) return "BONSOIR";
            if ("ar".equals(l)) return "\u0645\u0633\u0627\u0621 \u0627\u0644\u062e\u064a\u0631";
            return "GOOD EVENING";
        }
        return key;
    }

    /** Widget height in dp: 0 small (the new default), 1 medium (the old size), 2 large. */
    public static int widgetHeight(int size) {
        if (size == 0) return 130;
        if (size == 2) return 190;
        return 160;
    }

    public static String widgetSizeName(int size) {
        if (size == 0) return "Small";
        if (size == 2) return "Large";
        return "Medium";
    }

    /** Default widget order for the third line (the widgets row). */
    public static String widgetOrderDefault() { return "weather,sun,care,nowplaying"; }

    /** Default widget order for the Kids profile (v3.60): the Timer first
     *  so the child always sees the time left, then the Clock (time + day),
     *  then the Weather. No music bar in Kids. */
    public static String widgetOrderDefaultKids() { return "timer,clock,weather"; }

    /** Move one widget one step left (-1) or right (+1) inside a comma order; no-op at the ends. */
    public static String moveWidget(String orderCsv, String widget, int dir) {
        if (orderCsv == null || widget == null || dir == 0) return orderCsv == null ? "" : orderCsv;
        String[] parts = orderCsv.split(",");
        int i = -1;
        for (int k = 0; k < parts.length; k++) if (widget.equals(parts[k])) i = k;
        if (i < 0) return orderCsv;
        int j = i + dir;
        if (j < 0 || j >= parts.length) return orderCsv;
        String tmp = parts[i]; parts[i] = parts[j]; parts[j] = tmp;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < parts.length; k++) { if (k > 0) b.append(","); b.append(parts[k]); }
        return b.toString();
    }

    /** Continue watching cap per source app (v3.48, v4.13): YouTube gets 20
     *  so his longer history shows; every other app gets 12 so more apps
     *  show up in the row, while one loud app (Tubi) still cannot flood
     *  it and bury YouTube. */
    public static int watchCapFor(String pkg) {
        if ("com.google.android.youtube.tv".equals(pkg)) return 20;
        return 12;
    }

    /** SmartTube package candidates, newest first (v3.50): the current
     *  official stable installs as org.smarttube.stable (verified from the
     *  project's own stable link, v32.71) -- the old com.teamsmart package
     *  name is why v3.49 said "not installed" when SmartTube was on the box.
     *  The beta and the legacy package stay as fallbacks. */
    public static String[] youtubeMusicPkgs() {
        return new String[] { "org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv" };
    }

    /** v3.55: packages whose media notification carries the Now Playing title. */
    public static boolean isNowPlayingApp(String pkg) {
        if (pkg == null) return false;
        for (String p : youtubeMusicPkgs()) if (p.equals(pkg)) return true;
        if ("com.google.android.youtube.tv".equals(pkg)) return true;
        if ("com.spotify.tv.android".equals(pkg)) return true;
        if ("com.spotify.music".equals(pkg)) return true;
        return false;
    }

    /** Short app label for the Now Playing bar. */
    public static String nowPlayingAppLabel(String pkg) {
        if (pkg == null) return "YouTube";
        for (String p : youtubeMusicPkgs()) if (p.equals(pkg)) return "YouTube (SmartTube)";
        if ("com.google.android.youtube.tv".equals(pkg)) return "YouTube";
        if ("com.spotify.tv.android".equals(pkg) || "com.spotify.music".equals(pkg)) return "Spotify";
        return "YouTube";
    }

    /** Now Playing bar text: "App - Title", or "App - playing in background"
     *  when there is no title yet (listener off, or nothing posted). */
    public static String nowPlayingTitle(String appLabel, String title) {
        if (appLabel == null || appLabel.length() == 0) appLabel = "YouTube";
        if (title == null) title = "";
        title = title.trim();
        if (title.length() == 0) return appLabel + " - playing in background";
        return appLabel + " - " + title;
    }

    /** YouTube thumbnail for a Watch Next / Top picks card whose publisher
     *  gave no poster art (v3.50): derived from the video ID in its intent
     *  URI. The YouTube app publishes no poster in its Watch Next rows, so
     *  without this every Continue watching card stays on the flat fallback.
     *  Null when the intent carries no video ID (non-YouTube cards). */
    public static String ytThumbUrl(String intentUri) {
        String id = extractVideoId(intentUri);
        return id == null ? null : "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    }

    /** Live subline under a hero slogan (v3.49): the line that used to be
     *  painted into the hero picture now lives in the layout, once. */
    public static String heroSubline(String theme) {
        if ("beja".equals(theme)) return "Red and white \u00b7 Stade Boujemaa Kmiti";
        if ("arsenal".equals(theme)) return "Victoria Concordia Crescit";
        if ("psg".equals(theme)) return "Allez Paris Saint-Germain";
        if ("realmadrid".equals(theme)) return "Hala Madrid";
        if ("barca".equals(theme)) return "M\u00e9s que un club";
        return "";
    }

    /** Accent colour for a theme key (0 when the key is unknown - caller falls back to gold). */
    public static int themeAccent(String key) {
        if ("gold".equals(key)) return 0xFFE8C874;
        if ("classic".equals(key)) return 0xFFFFFFFF;
        if ("ocean".equals(key)) return 0xFF4DD0E1;
        if ("midnight".equals(key)) return 0xFF64B5F6;
        if ("crimson".equals(key)) return 0xFFEF5350;
        if ("emerald".equals(key)) return 0xFF66BB6A;
        if ("sunset".equals(key)) return 0xFFFFA726;
        if ("violet".equals(key)) return 0xFFAB47BC;
        if ("sport".equals(key)) return 0xFF00C853;
        if ("forest".equals(key)) return 0xFF2E7D32;
        if ("royal".equals(key)) return 0xFF7C4DFF;
        if ("coral".equals(key)) return 0xFFFF6E40;
        if ("esperance".equals(key)) return 0xFFFFD200;
        if ("africain".equals(key)) return 0xFFE30613;
        if ("beja".equals(key)) return 0xFFD50000;
        if ("arsenal".equals(key)) return 0xFFEF0107;
        if ("psg".equals(key)) return 0xFF004170;
        if ("realmadrid".equals(key)) return 0xFFFEBE10;
        if ("barca".equals(key)) return 0xFFA50044;
        if ("vicesunset".equals(key)) return 0xFFFF4081;
        if ("sky".equals(key)) return 0xFF29B6F6;
        if ("rose".equals(key)) return 0xFFF48FB1;
        if ("nightmosque".equals(key)) return 0xFF8AB4F8;
        if ("mosquesunset".equals(key)) return 0xFFFF9E57;
        if ("mosqueblue".equals(key)) return 0xFF7FB3FF;
        if ("mosquedesert".equals(key)) return 0xFFE8B04B;
        if ("eid".equals(key)) return 0xFFFFC107;
        if ("ramadan".equals(key)) return 0xFF7C4DFF;
        if ("mouled".equals(key)) return 0xFF2E7D32;
        if ("christmas".equals(key)) return 0xFFFFFFFF;
        if ("halloween".equals(key)) return 0xFFFF6D00;
        if ("eidadha".equals(key)) return 0xFFD84315;
        if ("hijri".equals(key)) return 0xFF283593;
        if ("ashura".equals(key)) return 0xFF37474F;
        if ("hajj".equals(key)) return 0xFF6D4C41;
        if ("newyear".equals(key)) return 0xFF00ACC1;
        if ("kids".equals(key)) return 0xFFEC407A;
        // v4.39: cartoon themes.
        if ("pokemon".equals(key)) return 0xFFF9A825;
        if ("conan".equals(key)) return 0xFFD32F2F;
        if ("spongebob".equals(key)) return 0xFFFFEB3B;
        if ("pawpatrol".equals(key)) return 0xFF1E88E5;
        return 0;
    }

    /** v3.55: how many rolling pictures a theme has -- 4 for the event themes,
     *  1 for the team / mosque themes (their hero, 3 more come later),
     *  0 for the plain colour themes (wallpaper / palette instead).
     *  v3.56: the plain themes have 4 pictures each now too -- every known
     *  theme rolls pictures; only an unknown key returns 0. */
    public static int themePicCount(String key) {
        if ("eid".equals(key) || "ramadan".equals(key) || "mouled".equals(key)
                || "christmas".equals(key) || "halloween".equals(key)
                || "eidadha".equals(key) || "hijri".equals(key) || "ashura".equals(key)
                || "hajj".equals(key) || "newyear".equals(key) || "kids".equals(key)) return 4;
        if ("gold".equals(key) || "classic".equals(key) || "ocean".equals(key)
                || "midnight".equals(key) || "crimson".equals(key) || "emerald".equals(key)
                || "sunset".equals(key) || "violet".equals(key) || "sport".equals(key)
                || "forest".equals(key) || "royal".equals(key) || "coral".equals(key)
                || "vicesunset".equals(key) || "sky".equals(key) || "rose".equals(key)) return 4;
        if ("esperance".equals(key) || "africain".equals(key) || "beja".equals(key)
                || "arsenal".equals(key) || "psg".equals(key) || "realmadrid".equals(key)
                || "barca".equals(key) || "nightmosque".equals(key) || "mosquesunset".equals(key)
                || "mosqueblue".equals(key) || "mosquedesert".equals(key)) return 1;
        return 0;
    }

    /** Club hero themes: the focus headline stays the club slogan instead of following the card. */
    public static boolean isHeroTheme(String key) {
        return "esperance".equals(key) || "africain".equals(key) || "beja".equals(key) || "arsenal".equals(key) || "psg".equals(key) || "realmadrid".equals(key) || "barca".equals(key);
    }
}
