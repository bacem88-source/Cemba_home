package com.bacem.launcher;

import java.util.List;
import java.util.Map;

/**
 * Scenario tests for Iptv (pure JVM, no Android, no network).
 * Run by build_launcher.sh on every build; the build stops on failure.
 * Run manually: javac -d out Iptv.java IptvTest.java && java -cp out com.bacem.launcher.IptvTest
 */
public class IptvTest {
    static int pass = 0, fail = 0;

    static void eq(String name, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name + "  expected=" + expected + "  actual=" + actual); }
    }

    public static void main(String[] args) {
        // ---------- server normalisation ----------
        eq("Bare host gets http", Iptv.normalizeServer("example.com:8080"), "http://example.com:8080");
        eq("Trailing slash stripped", Iptv.normalizeServer("https://x.com/"), "https://x.com");
        eq("Full API URL cut to host", Iptv.normalizeServer("http://x.com:8080/player_api.php?username=a"),
            "http://x.com:8080");
        eq("Empty server stays empty", Iptv.normalizeServer("  "), "");
        eq("Null server stays empty", Iptv.normalizeServer(null), "");

        // ---------- URLs ----------
        eq("Xtream API URL",
            Iptv.xtreamApiUrl("example.com:8080", "user1", "p@ss", "get_live_streams"),
            "http://example.com:8080/player_api.php?username=user1&password=p%40ss&action=get_live_streams");
        eq("Xtream stream URL is HLS",
            Iptv.xtreamStreamUrl("https://x.com", "u", "p", 123),
            "https://x.com/live/u/p/123.m3u8");

        // ---------- Xtream categories ----------
        String catsJson = "[{\"category_id\":\"1\",\"category_name\":\"Sports\"},"
            + "{\"category_id\":\"2\",\"category_name\":\"News\"},"
            + "{\"category_id\":\"3\",\"category_name\":\"\"}]";
        Map<String, String> cats = Iptv.parseLiveCategories(catsJson);
        eq("Two categories parsed", cats.size(), 2);
        eq("Sports is id 1", cats.get("1"), "Sports");

        // ---------- Xtream streams ----------
        String streamsJson = "["
            + "{\"stream_id\":10,\"name\":\"beIN Sports 1\",\"category_id\":\"1\",\"stream_icon\":\"http://x/i.png\"},"
            + "{\"stream_id\":11,\"name\":\"Nessma\",\"category_id\":\"9\",\"stream_icon\":\"\"},"
            + "{\"stream_id\":12,\"name\":\"\",\"category_id\":\"1\"},"
            + "{\"name\":\"No id\",\"category_id\":\"1\"}"
            + "]";
        List<Iptv.Chan> chans = Iptv.parseLiveStreams(streamsJson, cats, "example.com", "u", "p");
        eq("Only valid streams kept", chans.size(), 2);
        eq("First stream name", chans.get(0).name, "beIN Sports 1");
        eq("First stream category name", chans.get(0).sub, "Sports");
        eq("First stream URL", chans.get(0).url, "http://example.com/live/u/p/10.m3u8");
        eq("First stream icon", chans.get(0).icon, "http://x/i.png");
        eq("Unknown category is blank", chans.get(1).sub, "");

        // ---------- JSON robustness ----------
        eq("Bad JSON returns null", Iptv.parseJson("{oops"), null);
        eq("Escaped quote in name", Iptv.parseJson("[{\"name\":\"A \\\"B\\\"\"}]") != null, true);
        Object parsed = Iptv.parseJson("[{\"name\":\"Caf\\u00e9\"}]");
        eq("Unicode escape decodes",
            parsed instanceof java.util.List
                ? Iptv.jstr((java.util.Map<String, Object>) ((java.util.List<Object>) parsed).get(0), "name")
                : "", "Caf\u00e9");
        eq("Empty categories on garbage", Iptv.parseLiveCategories("not json").size(), 0);
        eq("Empty streams on garbage", Iptv.parseLiveStreams("not json", cats, "x", "u", "p").size(), 0);

        // ---------- m3u ----------
        String m3u = "#EXTM3U\n"
            + "#EXTINF:-1 tvg-id=\"beins1\" tvg-name=\"beIN Sports 1\" tvg-logo=\"http://x/logo.png\" group-title=\"Sports\",beIN Sports 1\n"
            + "http://x.com/live/u/p/10.m3u8\n"
            + "#EXTINF:-1 group-title=\"News\",\u0627\u0644\u062c\u0632\u064a\u0631\u0629\n"
            + "http://x.com/live/u/p/20.m3u8\n"
            + "http://x.com/no-extinf.m3u8\n";
        List<Iptv.Chan> m = Iptv.parseM3u(m3u);
        eq("Two m3u channels parsed", m.size(), 2);
        eq("m3u first name", m.get(0).name, "beIN Sports 1");
        eq("m3u first group", m.get(0).sub, "Sports");
        eq("m3u first logo", m.get(0).icon, "http://x/logo.png");
        eq("m3u first URL", m.get(0).url, "http://x.com/live/u/p/10.m3u8");
        eq("m3u Arabic name kept", m.get(1).name, "\u0627\u0644\u062c\u0632\u064a\u0631\u0629");

        // ---------- initials ----------
        eq("Initials of two words", Iptv.initials("beIN Sports 1"), "BS");
        eq("Initials of one word", Iptv.initials("Nessma"), "N");
        eq("Initials of empty name", Iptv.initials(""), "?");

        System.out.println("----");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
