package com.bacem.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Tubi Top picks (v4.08): real movie cards from Tubi's web API, for boxes
 *  with no TV provider (N5 Max, X96mini) and for provider boxes when no
 *  movie cards were harvested. No API key, no user login: Tubi serves its
 *  free catalogue to an anonymous device token.
 *
 *  Handshake (verified live 2026-11-14, from Breezyslasher's
 *  plugin.video.tubitv tubi_auth.py -- the same calls the Tubi web client
 *  makes), all under https://account.production-public.tubi.io:
 *    1. POST /device/anonymous/signing_key
 *       body {"challenge","version":"1.0.0","platform":"web","device_id"}
 *       challenge = base64url(sha256(verifier)), verifier = 32 lowercase
 *       hex chars from 16 random bytes. Answers {"id","key"}.
 *    2. POST /device/anonymous/token?X-Tubi-Algorithm=...&X-Tubi-Date=...
 *       &X-Tubi-Expires=30&X-Tubi-SignedHeaders=content-type
 *       &X-Tubi-Signature=...
 *       body {"verifier","id","platform":"web","device_id"} (that key
 *       order), signed TUBI-HMAC-SHA256 (AWS SigV4 lookalike) with the
 *       key from step 1. Answers {"access_token","refresh_token",
 *       "expires_in":86400}.
 *  The token is cached (SharedPreferences via Store) and reused until
 *  5 minutes before expiry. Then one call:
 *    GET https://tensor-cdn.production-public.tubi.io/api/v8/homescreen
 *    (group_start=0, group_size=7, contents_limit=10, is_kids_mode=false,
 *    images[posterarts]=w408h583_poster,
 *    images[landscape_images]=w978h549_landscape, ...)
 *    with Authorization: Bearer <token> -- returns rows (containers) plus
 *    a contents map of every title they list, so one request is enough.
 *  A title carries: id, title, year, detailed_type ("movie"/"series"),
 *  type ("v"/"s"), tags, posterarts[0] (portrait) and
 *  landscape_images[0] (16:9, fits the 248x140 tile and the backdrop).
 *  Content objects carry no slug; the movie page is
 *  https://tubitv.com/movies/{id} (the site 301s to the slugged URL).
 *
 *  Pure logic, no Android imports (no org.json either -- Iptv.parseJson
 *  is reused), so the unit tests run on a plain JVM. The caller runs the
 *  network calls on a background thread. Any failure throws and the
 *  caller falls back to the app row -- never blank, never a crash.
 *  Tubi is undocumented and has changed before, so the fetch is expected
 *  to break one day; the fallback is the whole point. */
public class Tubi {

    public static final String ACCOUNT_API = "https://account.production-public.tubi.io";
    public static final String BROWSE_API = "https://tensor-cdn.production-public.tubi.io";
    public static final String WEB_API = "https://tubitv.com";

    /** Refresh the bearer token this many ms before the server expiry. */
    public static final long TOKEN_MARGIN_MS = 300L * 1000;
    /** Re-fetch the homescreen this often; between fetches the disk cache rules. */
    public static final long TITLES_TTL_MS = 6L * 3600 * 1000;
    /** Top picks shows at most this many Tubi cards. */
    public static final int MAX_TITLES = 30;
    /** At most this many movies per home row first (fair share, like the provider path). */
    static final int PER_ROW_FIRST = 3;

    static final int CONNECT_TIMEOUT_MS = 8000;
    static final int READ_TIMEOUT_MS = 20000;
    static final String SIGNING_KEY_VERSION = "1.0.0";
    static final String PLATFORM = "web";
    static final String ALGORITHM = "TUBI-HMAC-SHA256";

    // ---------- persistence (caller supplies SharedPreferences) ----------
    public interface Store {
        String getString(String key, String def);
        void putString(String key, String val);
        long getLong(String key, long def);
        void putLong(String key, long val);
    }

    // ---------- model ----------
    public static class Title {
        public String id = "", title = "", poster = "", landscape = "", genre = "";
        public int year = -1;
        public String detailedType = "", type = "";
        // v4.22: the homescreen row this title came from (e.g. "Kids
        // Movies"). Tubi's own row organisation is a kids signal that
        // does not depend on the per-title genre tags.
        public String row = "";
        public boolean isMovie() {
            if ("movie".equals(detailedType)) return true;
            if ("series".equals(detailedType) || "sports_event".equals(detailedType)) return false;
            return "v".equals(type);
        }
    }

    public static class Token {
        public String accessToken = "";
        public long expiresAtMs = 0;
        public boolean valid(long nowMs) {
            return accessToken.length() > 0 && expiresAtMs > nowMs + TOKEN_MARGIN_MS;
        }
    }

    // ---------- base64 (hand-rolled: java.util.Base64 needs API 26,
    // minSdk is 24, and android.util.Base64 would break the plain-JVM tests) ----------
    static final String B64_STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    static final String B64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    static String base64Encode(byte[] data, boolean urlSafe) {
        String alpha = urlSafe ? B64_URL : B64_STD;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xFF;
            int b1 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
            int b2 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;
            sb.append(alpha.charAt(b0 >> 2));
            sb.append(alpha.charAt(((b0 & 0x03) << 4) | (b1 >> 4)));
            sb.append(i + 1 < data.length ? alpha.charAt(((b1 & 0x0F) << 2) | (b2 >> 6)) : '=');
            sb.append(i + 2 < data.length ? alpha.charAt(b2 & 0x3F) : '=');
        }
        return sb.toString();
    }

    static int b64Val(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+' || c == '-') return 62;
        if (c == '/' || c == '_') return 63;
        return -1;
    }

    static byte[] base64Decode(String s) {
        if (s == null) return new byte[0];
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (b64Val(c) >= 0 || c == '=') clean.append(c);
        }
        String t = clean.toString();
        ArrayList<Byte> out = new ArrayList<Byte>();
        for (int i = 0; i + 4 <= t.length(); i += 4) {
            int v0 = b64Val(t.charAt(i));
            int v1 = b64Val(t.charAt(i + 1));
            int v2 = t.charAt(i + 2) == '=' ? 0 : b64Val(t.charAt(i + 2));
            int v3 = t.charAt(i + 3) == '=' ? 0 : b64Val(t.charAt(i + 3));
            if (v0 < 0 || v1 < 0) break;
            out.add((byte) ((v0 << 2) | (v1 >> 4)));
            if (t.charAt(i + 2) != '=') out.add((byte) (((v1 & 0x0F) << 4) | (v2 >> 2)));
            if (t.charAt(i + 3) != '=') out.add((byte) (((v2 & 0x03) << 6) | v3));
        }
        byte[] r = new byte[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    // ---------- crypto ----------
    static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    static String sha256Hex(String s) throws Exception {
        return hex(sha256(s.getBytes("UTF-8")));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            sb.append("0123456789abcdef".charAt(v >> 4));
            sb.append("0123456789abcdef".charAt(v & 0x0F));
        }
        return sb.toString();
    }

    static byte[] hmacSha256(byte[] key, byte[] msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg);
    }

    static String utcStamp(long nowMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(nowMs));
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return s == null ? "" : s; }
    }

    // ---------- PKCE + signing (pure, unit-tested) ----------
    /** 16 random bytes as 32 lowercase hex chars, exactly what the addon sends. */
    public static String newVerifier() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return hex(b);
    }

    /** base64url(sha256(verifier)), padding kept -- the addon sends it padded. */
    public static String codeChallenge(String verifier) throws Exception {
        return base64Encode(sha256(verifier.getBytes("UTF-8")), true);
    }

    /** The signed token request. Returns {body, queryString}. The body key
     *  order (verifier, id, platform, device_id) is part of the signature. */
    public static String[] signTokenRequest(String verifier, String signingId,
            String deviceId, String signingKeyB64, long nowMs) throws Exception {
        String body = "{\"verifier\":\"" + verifier + "\",\"id\":\"" + signingId
            + "\",\"platform\":\"" + PLATFORM + "\",\"device_id\":\"" + deviceId + "\"}";
        String canonical = "POST\n/device/anonymous/token\n\ncontent-type:application/json\n\n"
            + "content-type\n" + sha256Hex(body);
        String stamp = utcStamp(nowMs);
        String stringToSign = ALGORITHM + "\n" + stamp + "\n" + sha256Hex(canonical);
        byte[] k = concat("TUBI".getBytes("UTF-8"), base64Decode(signingKeyB64));
        k = hmacSha256(k, stamp.substring(0, 8).getBytes("UTF-8"));
        k = hmacSha256(k, "tubi_request".getBytes("UTF-8"));
        String sig = hex(hmacSha256(k, stringToSign.getBytes("UTF-8")));
        String qs = "X-Tubi-Algorithm=" + enc(ALGORITHM)
            + "&X-Tubi-Date=" + enc(stamp)
            + "&X-Tubi-Expires=30"
            + "&X-Tubi-SignedHeaders=content-type"
            + "&X-Tubi-Signature=" + enc(sig);
        return new String[] { body, qs };
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ---------- HTTP ----------
    static String readBody(HttpURLConnection c) throws Exception {
        BufferedReader r = new BufferedReader(
            new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    static String httpPost(String urlStr, String body, String query, int timeoutMs) throws Exception {
        if (query != null && query.length() > 0) urlStr += "?" + query;
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", WEB_API);
        c.setRequestProperty("Referer", WEB_API + "/login");
        byte[] bytes = body.getBytes("UTF-8");
        c.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        OutputStream os = c.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();
        int code = c.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code + " POST " + urlStr);
        return readBody(c);
    }

    static String httpGet(String urlStr, String query, String token, String deviceId,
            int timeoutMs) throws Exception {
        if (query != null && query.length() > 0) urlStr += "?" + query;
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", WEB_API);
        c.setRequestProperty("Referer", WEB_API + "/");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (deviceId != null) c.setRequestProperty("Cookie", "deviceId=" + deviceId + ";");
        int code = c.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code + " GET " + urlStr);
        return readBody(c);
    }

    // ---------- token ----------
    static String deviceId(Store store) {
        String id = store.getString("device_id", "");
        if (id.length() == 0) {
            id = UUID.randomUUID().toString();
            store.putString("device_id", id);
        }
        return id;
    }

    /** The anonymous device token, from cache when it is still fresh, else
     *  through the two-step handshake. Throws on any failure. */
    public static Token anonymousToken(Store store) throws Exception {
        long now = System.currentTimeMillis();
        Token t = new Token();
        t.accessToken = store.getString("access_token", "");
        t.expiresAtMs = store.getLong("expires_at", 0);
        if (t.valid(now)) return t;
        String did = deviceId(store);
        String verifier = newVerifier();
        String challenge = codeChallenge(verifier);
        String body1 = "{\"challenge\":\"" + challenge + "\",\"version\":\"" + SIGNING_KEY_VERSION
            + "\",\"platform\":\"" + PLATFORM + "\",\"device_id\":\"" + did + "\"}";
        String r1 = httpPost(ACCOUNT_API + "/device/anonymous/signing_key", body1, null, READ_TIMEOUT_MS);
        Object o1 = Iptv.parseJson(r1);
        if (!(o1 instanceof Map)) throw new Exception("bad signing_key response");
        Map<String, Object> m1 = (Map<String, Object>) o1;
        String signingId = Iptv.jstr(m1, "id");
        String signingKey = Iptv.jstr(m1, "key");
        if (signingId.length() == 0 || signingKey.length() == 0)
            throw new Exception("signing_key missing id/key");
        String[] signed = signTokenRequest(verifier, signingId, did, signingKey, now);
        String r2 = httpPost(ACCOUNT_API + "/device/anonymous/token", signed[0], signed[1], READ_TIMEOUT_MS);
        Object o2 = Iptv.parseJson(r2);
        if (!(o2 instanceof Map)) throw new Exception("bad token response");
        Map<String, Object> m2 = (Map<String, Object>) o2;
        String access = Iptv.jstr(m2, "access_token");
        int expiresIn = Iptv.jint(m2, "expires_in");
        if (access.length() == 0) throw new Exception("token missing access_token");
        Token out = new Token();
        out.accessToken = access;
        out.expiresAtMs = now + (expiresIn > 0 ? expiresIn : 86400) * 1000L;
        store.putString("access_token", out.accessToken);
        store.putLong("expires_at", out.expiresAtMs);
        return out;
    }

    // ---------- homescreen ----------
    // v4.17: kids mode returns only kids titles -- the authority on
    // which Tubi IDs are kids (no title words involved). The regular
    // homescreen (kidsMode=false) is the Top picks feed.
    static String homescreenQuery(boolean kidsMode) {
        // Same parameters (and sizes) the Tubi web client asks for.
        return "include_channels=true"
            + "&contents_limit=10"
            + "&include_empty_history=true"
            + "&include_empty_queue=true"
            + "&include_sponsorships=true"
            + "&include_ui_customization=true"
            + "&group_start=0"
            + "&group_size=7"
            + "&is_kids_mode=" + (kidsMode ? "true" : "false")
            + "&" + enc("images[posterarts]") + "=w408h583_poster"
            + "&" + enc("images[backgrounds]") + "=w1614h906_background"
            + "&" + enc("images[hero_16x9]") + "=w1280h720_hero"
            + "&" + enc("images[landscape_images]") + "=w978h549_landscape";
    }

    /** One homescreen call, parsed to movie titles (fair share per row, cap 30).
     *  Throws on any failure; the caller falls back to the app row. */
    public static List<Title> fetchHomescreenTitles(Store store) throws Exception {
        return fetchHomescreenTitles(store, false, true);
    }

    public static List<Title> fetchHomescreenTitles(Store store, boolean kidsMode) throws Exception {
        return fetchHomescreenTitles(store, kidsMode, true);
    }

    public static List<Title> fetchHomescreenTitles(Store store, boolean kidsMode, boolean moviesOnly) throws Exception {
        Token t = anonymousToken(store);
        String json = httpGet(BROWSE_API + "/api/v8/homescreen", homescreenQuery(kidsMode),
            t.accessToken, deviceId(store), READ_TIMEOUT_MS);
        return parseHomescreen(json, moviesOnly);
    }

    /** Kids-mode homescreen: every title it returns is kids by
     *  definition (Tubi's own kids catalog). The caller takes all
     *  their IDs as the kids set. v4.23: includes SERIES (moviesOnly
     *  false) -- the kids catalog is mostly series (Pokemon, Sonic,
     *  Masha, Larva, Milo and Chip), and the movies-only filter was
     *  excluding them from the kids sets entirely. */
    public static List<Title> fetchKidsTitles(Store store) throws Exception {
        return fetchHomescreenTitles(store, true, false);
    }

    /** IDs of the given titles that Tubi itself says are kids: either
     *  the title's own genre tags say kids, or it came from a kids row
     *  of the homescreen ("Kids Movies", "Kids & Family", ...). Pure,
     *  unit-tested. v4.22: the row title is the signal that does not
     *  depend on per-title genre tags (which are blank for several
     *  kids titles -- Pokemon, Larva, Milo and Chip stayed in the main
     *  Top picks row because neither their tags nor the kids-mode set
     *  caught them). */
    public static java.util.Set<String> kidsIds(List<Title> ts) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (ts == null) return out;
        for (Title t : ts) {
            if (t == null || t.id.length() == 0) continue;
            if (Logic.isKids(t.genre, null, null)) { out.add(t.id); continue; }
            String r = t.row == null ? "" : t.row.toLowerCase(java.util.Locale.US);
            if (r.contains("kid")) out.add(t.id);
        }
        return out;
    }

    /** Exact (lowercased) titles Tubi says are kids, from the same
     *  signals as kidsIds (genre tags or a kids row). Used as a second
     *  key for WatchNext items whose intent URI carries no parseable
     *  Tubi ID -- the exact title is matched against titles Tubi itself
     *  listed as kids, not against keywords. Pure, unit-tested. */
    public static java.util.Set<String> kidsTitles(List<Title> ts) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (ts == null) return out;
        for (Title t : ts) {
            if (t == null || t.id.length() == 0 || t.title.length() == 0) continue;
            boolean kid = Logic.isKids(t.genre, null, null);
            if (!kid) {
                String r = t.row == null ? "" : t.row.toLowerCase(java.util.Locale.US);
                kid = r.contains("kid");
            }
            if (kid) out.add(t.title.toLowerCase(java.util.Locale.US).trim());
        }
        return out;
    }

    /** A set of titles as one JSON array string for the disk cache. */
    public static String titlesSetToJson(java.util.Set<String> titles) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String t : titles) {
            if (t == null || t.length() == 0) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append(jsonStr(t));
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static java.util.Set<String> titlesSetFromJson(String json) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        Object v = Iptv.parseJson(json);
        if (!(v instanceof List)) return out;
        for (Object o : (List<Object>) v) {
            if (o instanceof String) {
                String t = ((String) o).trim();
                if (t.length() > 0) out.add(t);
            }
        }
        return out;
    }

    /** Kids IDs as one comma-separated string for the disk cache. */
    public static String idsToString(java.util.Set<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.length() == 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    public static java.util.Set<String> idsFromString(String v) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (v == null) return out;
        for (String part : v.split(",")) {
            String id = part.trim();
            if (id.length() > 0) out.add(id);
        }
        return out;
    }

    static String firstStr(List<Object> l) {
        if (l == null) return "";
        for (Object o : l) if (o instanceof String && ((String) o).length() > 0) return (String) o;
        return "";
    }

    @SuppressWarnings("unchecked")
    static String joinTags(Object v) {
        if (!(v instanceof List)) return "";
        StringBuilder sb = new StringBuilder();
        for (Object o : (List<Object>) v) {
            if (!(o instanceof String)) continue;
            String s = ((String) o).trim();
            if (s.length() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    static boolean skipRow(String title, String id) {
        String t = (title == null ? "" : title).toLowerCase(Locale.US);
        String i = (id == null ? "" : id).toLowerCase(Locale.US);
        if (t.contains("continue") || t.contains("my list") || t.contains("watch next")) return true;
        if (t.contains("live") || i.contains("live")) return true;
        return false;
    }

    /** Parse a homescreen response into movie titles. Pure, unit-tested.
     *  Rows are the containers; the contents map holds every title the
     *  rows list, so one response is enough -- no per-row calls. */
    @SuppressWarnings("unchecked")
    public static List<Title> parseHomescreen(String json) {
        return parseHomescreen(json, true);
    }

    // v4.23: moviesOnly=false keeps SERIES too. The Top picks cards stay
    // movies-only, but the KIDS sets must include series -- Pokemon the
    // Series, Sonic Boom, Masha, Larva, Milo and Chip are all series,
    // and the movies-only filter meant they were never in the kids sets
    // at all, so their WatchNext cards stayed in the Family row no matter
    // how the sets accumulated.
    public static List<Title> parseHomescreen(String json, boolean moviesOnly) {
        List<Title> out = new ArrayList<Title>();
        Object v = Iptv.parseJson(json);
        if (!(v instanceof Map)) return out;
        Map<String, Object> root = (Map<String, Object>) v;
        Object co = root.get("contents");
        Object ro = root.get("containers");
        if (!(co instanceof Map) || !(ro instanceof List)) return out;
        Map<String, Object> contents = (Map<String, Object>) co;
        List<Object> rows = (List<Object>) ro;
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        // First pass: at most PER_ROW_FIRST movies per row (variety).
        // Second pass: fill up to MAX_TITLES from the rows in order.
        for (int pass = 0; pass < 2 && out.size() < MAX_TITLES; pass++) {
            for (Object ro2 : rows) {
                if (out.size() >= MAX_TITLES) break;
                if (!(ro2 instanceof Map)) continue;
                Map<String, Object> row = (Map<String, Object>) ro2;
                Object ch = row.get("children");
                if (!(ch instanceof List)) continue;
                String rowTitle = Iptv.jstr(row, "title");
                if (skipRow(rowTitle, Iptv.jstr(row, "id"))) continue;
                int taken = 0;
                for (Object k : (List<Object>) ch) {
                    if (!(k instanceof String)) continue;
                    Object to = contents.get(k);
                    if (!(to instanceof Map)) continue;
                    Map<String, Object> c = (Map<String, Object>) to;
                    Title t = toTitle(c);
                    if (t == null) continue;
                    if (moviesOnly && !t.isMovie()) continue;
                    t.row = rowTitle;
                    if (seen.contains(t.id)) continue;
                    if (pass == 0 && taken >= PER_ROW_FIRST) break;
                    seen.add(t.id);
                    out.add(t);
                    taken++;
                    if (out.size() >= MAX_TITLES) break;
                }
            }
        }
        return out;
    }

    static String firstArt(Object v) {
        if (v instanceof String) return (String) v;
        if (v instanceof List) return firstStr((List<Object>) v);
        return "";
    }

    @SuppressWarnings("unchecked")
    static Title toTitle(Map<String, Object> c) {
        Title t = new Title();
        t.id = Iptv.jstr(c, "id");
        t.title = Iptv.jstr(c, "title");
        t.year = Iptv.jint(c, "year");
        t.detailedType = Iptv.jstr(c, "detailed_type");
        t.type = Iptv.jstr(c, "type");
        t.genre = joinTags(c.get("tags"));
        if (t.genre.length() == 0) t.genre = Iptv.jstr(c, "genre"); // disk cache stores it flat
        t.row = Iptv.jstr(c, "row");
        // Live responses carry posterarts / landscape_images as lists; the
        // disk cache stores them flat as poster / landscape strings.
        t.poster = firstArt(c.get("posterarts"));
        if (t.poster.length() == 0) t.poster = Iptv.jstr(c, "poster");
        t.landscape = firstArt(c.get("landscape_images"));
        if (t.landscape.length() == 0) t.landscape = Iptv.jstr(c, "landscape");
        if (t.id.length() == 0 || t.title.length() == 0) return null;
        return t;
    }

    /** The card art for a title: the 16:9 landscape fits the 248x140 tile
     *  and the backdrop; the portrait poster is the fallback. */
    public static String artFor(Title t) {
        if (t == null) return "";
        if (t.landscape != null && t.landscape.length() > 0) return t.landscape;
        return t.poster == null ? "" : t.poster;
    }

    /** The movie's page. The site 301s an id-only URL to the slugged one. */
    public static String movieUrl(String id) {
        return WEB_API + "/movies/" + id;
    }

    /** The intent a Top picks card stores, so pickTile's
     *  Intent.parseUri opens the Tubi app (or the browser as fallback)
     *  on the movie's page. */
    public static String movieIntentUri(String id) {
        return "intent://tubitv.com/movies/" + id
            + "#Intent;scheme=https;action=android.intent.action.VIEW;"
            + "category=android.intent.category.BROWSABLE;end";
    }

    // ---------- disk cache (titles as one compact JSON string) ----------
    public static String titlesToJson(List<Title> titles) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Title t : titles) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":").append(jsonStr(t.id));
            sb.append(",\"title\":").append(jsonStr(t.title));
            sb.append(",\"year\":").append(t.year);
            sb.append(",\"poster\":").append(jsonStr(t.poster));
            sb.append(",\"landscape\":").append(jsonStr(t.landscape));
            sb.append(",\"genre\":").append(jsonStr(t.genre));
            sb.append(",\"detailed_type\":").append(jsonStr(t.detailedType));
            sb.append(",\"type\":").append(jsonStr(t.type));
            sb.append(",\"row\":").append(jsonStr(t.row));
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    static String jsonStr(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        sb.append("\"");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<Title> titlesFromJson(String json) {
        List<Title> out = new ArrayList<Title>();
        Object v = Iptv.parseJson(json);
        if (!(v instanceof List)) return out;
        for (Object o : (List<Object>) v) {
            if (!(o instanceof Map)) continue;
            Title t = toTitle((Map<String, Object>) o);
            if (t != null) out.add(t);
        }
        return out;
    }
}
