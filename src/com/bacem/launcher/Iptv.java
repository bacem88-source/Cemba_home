package com.bacem.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Built-in IPTV provider support (v3.54): Xtream Codes API + m3u playlists.
 *  Pure logic, no new dependency -- the JSON reader below is hand-rolled for
 *  the flat Xtream responses (no org.json, so the unit tests run on plain
 *  Java). The provider login is the user's own subscription: the app only
 *  talks to the provider he configured, and his channels stay on his box. */
public class Iptv {
    public static class Chan {
        public String name, sub, url, icon; public int id;
        public Chan(String n, String s, String u, String i, int d) {
            name = n; sub = s; url = u; icon = i; id = d;
        }
    }

    // ---------- server / URLs ----------
    public static String normalizeServer(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() == 0) return "";
        int cut = s.indexOf("/player_api.php");
        if (cut >= 0) s = s.substring(0, cut);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.length() == 0) return "";
        if (s.indexOf("://") < 0) s = "http://" + s;
        return s;
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return s == null ? "" : s; }
    }

    public static String xtreamApiUrl(String server, String user, String pass, String action) {
        return normalizeServer(server) + "/player_api.php?username=" + enc(user)
            + "&password=" + enc(pass) + "&action=" + action;
    }

    public static String xtreamStreamUrl(String server, String user, String pass, int streamId) {
        return normalizeServer(server) + "/live/" + enc(user) + "/" + enc(pass)
            + "/" + streamId + ".m3u8";
    }

    // ---------- minimal JSON reader (flat arrays of objects) ----------
    // Recursive descent; returns Map/List/String/Double/Boolean/null, or null
    // (with bad=true) on malformed input. Handles string escapes incl. backslash-u.
    static class J {
        final String s; int i; boolean bad;
        J(String s) { this.s = s == null ? "" : s; i = 0; bad = false; }
        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++; else break;
            }
        }
        Object val() {
            ws();
            if (bad || i >= s.length()) { bad = true; return null; }
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (c == 't') { if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; } bad = true; return null; }
            if (c == 'f') { if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; } bad = true; return null; }
            if (c == 'n') { if (s.startsWith("null", i)) { i += 4; return null; } bad = true; return null; }
            return num();
        }
        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (i < s.length() && !bad) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') { bad = true; return null; }
                String k = str();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') { bad = true; return null; }
                i++;
                Object v = val();
                if (bad) return null;
                m.put(k, v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
                bad = true; return null;
            }
            bad = true; return null;
        }
        List<Object> arr() {
            List<Object> l = new ArrayList<Object>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (i < s.length() && !bad) {
                Object v = val();
                if (bad) return null;
                l.add(v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
                bad = true; return null;
            }
            bad = true; return null;
        }
        String str() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) { bad = true; return null; }
                    char e = s.charAt(i++);
                    if (e == '"') sb.append('"');
                    else if (e == '\\') sb.append('\\');
                    else if (e == '/') sb.append('/');
                    else if (e == 'b') sb.append('\b');
                    else if (e == 'f') sb.append('\f');
                    else if (e == 'n') sb.append('\n');
                    else if (e == 'r') sb.append('\r');
                    else if (e == 't') sb.append('\t');
                    else if (e == 'u') {
                        if (i + 4 > s.length()) { bad = true; return null; }
                        try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        catch (Exception x) { bad = true; return null; }
                    } else { bad = true; return null; }
                } else sb.append(c);
            }
            bad = true; return null;
        }
        Double num() {
            int st = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
                else break;
            }
            if (i == st) { bad = true; return null; }
            try { return Double.valueOf(s.substring(st, i)); }
            catch (Exception e) { bad = true; return null; }
        }
    }

    public static Object parseJson(String s) {
        if (s == null) return null;
        J j = new J(s.trim());
        Object v = j.val();
        if (j.bad) return null;
        j.ws();
        if (j.i != j.s.length()) return null;
        return v;
    }

    @SuppressWarnings("unchecked")
    static String jstr(Map<String, Object> m, String k) {
        if (m == null) return "";
        Object v = m.get(k);
        if (v == null) return "";
        if (v instanceof String) return (String) v;
        if (v instanceof Double) {
            double d = (Double) v;
            return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return "";
    }

    static int jint(Map<String, Object> m, String k) {
        if (m == null) return -1;
        Object v = m.get(k);
        if (v instanceof Double) return (int) Math.round((Double) v);
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); }
            catch (Exception e) {
                try { return (int) Math.round(Double.parseDouble(((String) v).trim())); }
                catch (Exception e2) { return -1; }
            }
        }
        return -1;
    }

    // ---------- Xtream ----------
    @SuppressWarnings("unchecked")
    public static Map<String, String> parseLiveCategories(String json) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Object v = parseJson(json);
        if (!(v instanceof List)) return out;
        for (Object o : (List<Object>) v) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            String id = jstr(m, "category_id"), name = jstr(m, "category_name");
            if (name.length() > 0) out.put(id, name);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Chan> parseLiveStreams(String json, Map<String, String> cats,
            String server, String user, String pass) {
        List<Chan> out = new ArrayList<Chan>();
        Object v = parseJson(json);
        if (!(v instanceof List)) return out;
        for (Object o : (List<Object>) v) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            int id = jint(m, "stream_id");
            String name = jstr(m, "name");
            if (id < 0 || name.length() == 0) continue;
            String cat = cats == null ? "" : cats.get(jstr(m, "category_id"));
            if (cat == null) cat = "";
            out.add(new Chan(name, cat, xtreamStreamUrl(server, user, pass, id),
                jstr(m, "stream_icon"), id));
        }
        return out;
    }

    // ---------- m3u ----------
    static String m3uAttr(String line, String key) {
        String pat = key + "=\"";
        int p = line.indexOf(pat);
        if (p < 0) return "";
        int st = p + pat.length();
        int e = line.indexOf('"', st);
        return e < 0 ? "" : line.substring(st, e);
    }

    public static List<Chan> parseM3u(String text) {
        List<Chan> out = new ArrayList<Chan>();
        if (text == null) return out;
        String pendingName = null, pendingGroup = null, pendingLogo = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.indexOf(',');
                pendingName = comma >= 0 && comma + 1 < line.length()
                    ? line.substring(comma + 1).trim() : "";
                pendingGroup = m3uAttr(line, "group-title");
                pendingLogo = m3uAttr(line, "tvg-logo");
                if (pendingName.length() == 0) pendingName = m3uAttr(line, "tvg-name");
            } else if (line.startsWith("#")) {
                continue;
            } else if (pendingName != null) {
                if (pendingName.length() > 0) {
                    out.add(new Chan(pendingName,
                        pendingGroup == null ? "" : pendingGroup, line,
                        pendingLogo == null ? "" : pendingLogo, -1));
                }
                pendingName = null; pendingGroup = null; pendingLogo = null;
            }
        }
        return out;
    }

    // ---------- HTTP ----------
    public static String fetchText(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "CembaHome/3.54");
        int code = c.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        BufferedReader r = new BufferedReader(
            new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    // ---------- guide badge initials ----------
    public static String initials(String name) {
        if (name == null) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.length() == 0) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
