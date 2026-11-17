package com.bacem.launcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario tests for Tubi (pure JVM, no Android, no network).
 * The golden vectors below come from a live verified run (2026-11-14):
 * the signing_key / token handshake against
 * account.production-public.tubi.io and the homescreen response.
 * Run by build_launcher.sh on every build; the build stops on failure.
 * Run manually: javac -d out Logic.java Iptv.java Tubi.java TubiTest.java
 *   && java -cp out com.bacem.launcher.TubiTest
 */
public class TubiTest {
    static int pass = 0, fail = 0;

    static void eq(String name, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name + "  expected=" + expected + "  actual=" + actual); }
    }

    static void isTrue(String name, boolean v) { eq(name, v, true); }

    /** In-memory Store for the token-cache tests. */
    static class MemStore implements Tubi.Store {
        Map<String, String> s = new HashMap<String, String>();
        Map<String, Long> l = new HashMap<String, Long>();
        public String getString(String k, String d) { String v = s.get(k); return v == null ? d : v; }
        public void putString(String k, String v) { s.put(k, v); }
        public long getLong(String k, long d) { Long v = l.get(k); return v == null ? d : v; }
        public void putLong(String k, long v) { l.put(k, v); }
    }

    public static void main(String[] args) throws Exception {
        // ---------- PKCE challenge (live golden vector) ----------
        eq("Challenge matches the live run",
            Tubi.codeChallenge("94428c4e8a32c69bdb83ede7876ab917"),
            "HQg41MB7AyEq0QDTr1Y0yr8n2fYRz8YHGfblfkLBSrI=");
        String verifier = Tubi.newVerifier();
        eq("Verifier is 32 lowercase hex", verifier.length(), 32);
        isTrue("Verifier is hex", verifier.matches("[0-9a-f]{32}"));

        // ---------- base64 round-trip ----------
        byte[] raw = new byte[] { 0, 1, 2, (byte) 0xFF, 0x10, 0x20, 0x30, 0x40, 0x50 };
        byte[] back = Tubi.base64Decode(Tubi.base64Encode(raw, true));
        eq("Base64url decoded length", back.length, raw.length);
        boolean same = true;
        for (int i = 0; i < raw.length; i++) if (back[i] != raw[i]) same = false;
        isTrue("Base64url bytes identical", same);
        eq("Signing key decodes to 32 bytes",
            Tubi.base64Decode("LRBu/9OvFIO786SghmRzNg4ErmUYEB0qUwbeLNOeWG0=").length, 32);

        // ---------- HMAC signing (live golden vector) ----------
        String[] signed = Tubi.signTokenRequest(
            "94428c4e8a32c69bdb83ede7876ab917",
            "56d180f9-39bd-4667-8b53-a8bb1298af9f",
            "11111111-2222-3333-4444-555555555555",
            "LRBu/9OvFIO786SghmRzNg4ErmUYEB0qUwbeLNOeWG0=",
            1794693600000L);
        eq("Signed body key order",
            signed[0],
            "{\"verifier\":\"94428c4e8a32c69bdb83ede7876ab917\",\"id\":\"56d180f9-39bd-4667-8b53-a8bb1298af9f\",\"platform\":\"web\",\"device_id\":\"11111111-2222-3333-4444-555555555555\"}");
        isTrue("Signed query carries the live signature",
            signed[1].contains("X-Tubi-Signature=eb51debdd82f659eebc648f2ad0f6839fba775248eb042a2398f43f1cc71501d"));
        isTrue("Signed query carries the stamp",
            signed[1].contains("X-Tubi-Date=20261114T220000Z"));
        isTrue("Signed query carries the algorithm",
            signed[1].contains("X-Tubi-Algorithm=TUBI-HMAC-SHA256"));

        // ---------- token cache ----------
        MemStore st = new MemStore();
        st.putString("access_token", "tok123");
        st.putLong("expires_at", System.currentTimeMillis() + 3600 * 1000);
        Tubi.Token tok = Tubi.anonymousToken(st);
        eq("Cached token reused without network", tok.accessToken, "tok123");
        MemStore st2 = new MemStore();
        Tubi.Token expiring = new Tubi.Token();
        expiring.accessToken = "old";
        expiring.expiresAtMs = System.currentTimeMillis() + 60 * 1000;
        isTrue("Token inside the 5-minute margin counts as expired",
            !expiring.valid(System.currentTimeMillis()));

        // ---------- homescreen parsing ----------
        String homescreen = "{"
            + "\"contents\":{"
            + "\"1\":{\"id\":\"1\",\"title\":\"Alpha\",\"year\":2020,\"detailed_type\":\"movie\",\"type\":\"v\","
            + "\"tags\":[\"Action\"],\"posterarts\":[\"http://x/p1\"],\"landscape_images\":[\"http://x/l1\"]},"
            + "\"2\":{\"id\":\"2\",\"title\":\"Beta\",\"year\":2021,\"detailed_type\":\"movie\",\"type\":\"v\","
            + "\"tags\":[\"Comedy\"],\"posterarts\":[\"http://x/p2\"],\"landscape_images\":[]},"
            + "\"3\":{\"id\":\"3\",\"title\":\"Gamma Show\",\"year\":2019,\"detailed_type\":\"series\",\"type\":\"s\","
            + "\"tags\":[\"Drama\"],\"posterarts\":[\"http://x/p3\"],\"landscape_images\":[\"http://x/l3\"]},"
            + "\"4\":{\"id\":\"4\",\"title\":\"Delta\",\"year\":2022,\"detailed_type\":\"movie\",\"type\":\"v\","
            + "\"tags\":[\"Kids & Family\",\"Animation\"],\"posterarts\":[],\"landscape_images\":[\"http://x/l4\"]},"
            + "\"5\":{\"id\":\"5\",\"title\":\"Epsilon\",\"year\":2023,\"detailed_type\":\"movie\",\"type\":\"v\","
            + "\"tags\":[],\"posterarts\":[\"http://x/p5\"],\"landscape_images\":[\"http://x/l5\"]},"
            + "\"6\":{\"id\":\"6\",\"title\":\"Zeta\",\"year\":2024,\"detailed_type\":\"movie\",\"type\":\"v\","
            + "\"tags\":[],\"posterarts\":[\"http://x/p6\"],\"landscape_images\":[\"http://x/l6\"]},"
            + "\"7\":{\"id\":\"7\",\"title\":\"Eta\",\"year\":2024,\"detailed_type\":\"movie\",\"type\":\"v\","
            + "\"tags\":[],\"posterarts\":[\"http://x/p7\"],\"landscape_images\":[\"http://x/l7\"]}"
            + "},"
            + "\"containers\":["
            + "{\"id\":\"featured\",\"title\":\"Featured\",\"children\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"]},"
            + "{\"id\":\"most_popular\",\"title\":\"Most Popular\",\"children\":[\"1\",\"5\",\"2\"]},"
            + "{\"id\":\"rec\",\"title\":\"Recommended\",\"children\":[\"6\",\"7\"]},"
            + "{\"id\":\"cw\",\"title\":\"Continue Watching\",\"children\":[\"1\"]},"
            + "{\"id\":\"live\",\"title\":\"Live Events\",\"children\":[\"2\"]}"
            + "]}";
        List<Tubi.Title> titles = Tubi.parseHomescreen(homescreen);
        eq("Series and personal/live rows excluded", titles.size(), 6);
        eq("First is Alpha", titles.get(0).title, "Alpha");
        eq("Delta kept (kids tag, caller splits it)", titles.get(2).title, "Delta");
        eq("Epsilon present", titles.get(3).title, "Epsilon");
        eq("Zeta present", titles.get(4).title, "Zeta");
        eq("Eta present", titles.get(5).title, "Eta");
        isTrue("No duplicate Alpha", titles.size() == 6);
        eq("Alpha art is the landscape", Tubi.artFor(titles.get(0)), "http://x/l1");
        eq("Beta art falls back to the poster", Tubi.artFor(titles.get(1)), "http://x/p2");
        eq("Delta art is the landscape", Tubi.artFor(titles.get(2)), "http://x/l4");
        eq("Delta genre joins the tags", titles.get(2).genre, "Kids & Family, Animation");
        eq("Garbage returns nothing", Tubi.parseHomescreen("not json").size(), 0);
        eq("Empty object returns nothing", Tubi.parseHomescreen("{}").size(), 0);

        // ---------- disk cache round-trip ----------
        String cj = Tubi.titlesToJson(titles);
        List<Tubi.Title> back2 = Tubi.titlesFromJson(cj);
        eq("Round-trip keeps every title", back2.size(), titles.size());
        eq("Round-trip keeps the art", Tubi.artFor(back2.get(0)), "http://x/l1");
        eq("Round-trip keeps the genre", back2.get(2).genre, "Kids & Family, Animation");
        eq("Round-trip keeps the year", back2.get(0).year, 2020);
        eq("Bad cache returns nothing", Tubi.titlesFromJson("{{{").size(), 0);

        // ---------- v4.17: kids IDs (Tubi's own genre, no title words) ----------
        List<Tubi.Title> kts = new java.util.ArrayList<Tubi.Title>();
        Tubi.Title k1 = new Tubi.Title(); k1.id = "101"; k1.title = "Pokemon the Series"; k1.genre = "Kids, Animation";
        Tubi.Title k2 = new Tubi.Title(); k2.id = "102"; k2.title = "The Addams Family 2"; k2.genre = "Family, Animation";
        Tubi.Title k3 = new Tubi.Title(); k3.id = "103"; k3.title = "Some Drama"; k3.genre = "Drama";
        Tubi.Title k4 = new Tubi.Title(); k4.id = "104"; k4.title = "Sonic"; k4.genre = "Kids";
        kts.add(k1); kts.add(k2); kts.add(k3); kts.add(k4);
        java.util.Set<String> kids = Tubi.kidsIds(kts);
        eq("Kids genre title is a kids ID", kids.contains("101"), true);
        eq("Family+Animation genre is a kids ID (v4.18, anim is back)", kids.contains("102"), true);
        eq("Drama is not a kids ID", kids.contains("103"), false);
        eq("Kids genre title is a kids ID (Sonic)", kids.contains("104"), true);
        eq("Kids IDs round-trip", Tubi.idsFromString(Tubi.idsToString(kids)), kids);
        // v4.22: a title in a kids row is kids even when its genre tags
        // are blank (Pokemon / Larva / Milo and Chip in Top picks).
        Tubi.Title k5 = new Tubi.Title(); k5.id = "105"; k5.title = "Pokemon: The First Movie"; k5.genre = ""; k5.row = "Kids Movies";
        Tubi.Title k6 = new Tubi.Title(); k6.id = "106"; k6.title = "Some Action"; k6.genre = "Action"; k6.row = "Trending Now";
        java.util.List<Tubi.Title> rts = new java.util.ArrayList<Tubi.Title>();
        rts.add(k5); rts.add(k6);
        java.util.Set<String> rk = Tubi.kidsIds(rts);
        eq("Kids-row title is a kids ID even with blank genre", rk.contains("105"), true);
        eq("Trending-row title is not a kids ID", rk.contains("106"), false);
        // v4.22: kidsTitles gives the exact lowercased titles (second key
        // for WatchNext items whose intent URI has no parseable ID).
        java.util.Set<String> kt = Tubi.kidsTitles(kts);
        eq("Kids-genre title is a kids title", kt.contains("pokemon the series"), true);
        eq("Drama title is not a kids title", kt.contains("some drama"), false);
        eq("Kids titles round-trip", Tubi.titlesSetFromJson(Tubi.titlesSetToJson(kt)), kt);
        // v4.23: parseHomescreen with moviesOnly=false keeps series (the
        // kids catalog is mostly series); the default stays movies-only.
        String sj = "{\"contents\":{\"a\":{\"id\":\"201\",\"title\":\"Pokemon the Series\",\"detailed_type\":\"series\",\"type\":\"s\",\"tags\":[\"Kids\"]},\"b\":{\"id\":\"202\",\"title\":\"Some Movie\",\"detailed_type\":\"movie\",\"type\":\"v\",\"tags\":[\"Action\"]}},\"containers\":[{\"title\":\"Kids\",\"id\":\"r1\",\"children\":[\"a\",\"b\"]}]}";
        eq("Movies-only parse drops the series", Tubi.parseHomescreen(sj).size(), 1);
        eq("All-titles parse keeps the series", Tubi.parseHomescreen(sj, false).size(), 2);
        java.util.Set<String> sk = Tubi.kidsIds(Tubi.parseHomescreen(sj, false));
        eq("Series in a kids row is a kids ID", sk.contains("201"), true);
        eq("Empty IDs string gives empty set", Tubi.idsFromString("").size(), 0);

        // ---------- deep links ----------
        eq("Movie page URL", Tubi.movieUrl("550824"), "https://tubitv.com/movies/550824");
        eq("Movie intent URI",
            Tubi.movieIntentUri("550824"),
            "intent://tubitv.com/movies/550824#Intent;scheme=https;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;end");

        System.out.println("----");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
