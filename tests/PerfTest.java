package com.bacem.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Performance + load tests for Logic (pure JVM, no Android). Run by
 * build_launcher.sh next to LogicTest; the build stops if a budget is blown.
 *
 * Budgets are smoke guards with wide headroom, not box benchmarks: they catch
 * accidental quadratic behaviour or a regex recompiled per call, not a slow
 * afternoon. Timings print on every run so a regression is visible.
 */
public class PerfTest {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name); }
    }

    static void time(String name, Runnable r, long budgetMs) {
        long t0 = System.nanoTime();
        r.run();
        long ms = (System.nanoTime() - t0) / 1000000;
        System.out.println("TIME  " + name + " = " + ms + " ms (budget " + budgetMs + ")");
        check(name + " within budget", ms <= budgetMs);
    }

    public static void main(String[] args) {
        final String yt = "com.google.android.youtube.tv";
        final String tubi = "com.tubi.tv";

        // Warmup so JIT settles before the timed runs.
        for (int i = 0; i < 10000; i++) {
            Logic.isMusicFull(null, yt, "Artist ft Singer - Song " + i, null);
            Logic.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=" + i + "s");
        }

        // 1. Music classification over a mixed feed (English + Arabic + ft + junk).
        final String[] titles = {
            "Artmasta ft Ahmed Sokne - Wila Wila", "Cheb Khaled - Aicha",
            "\u0627\u063a\u0627\u0646\u064a \u062a\u0648\u0646\u0633\u064a\u0629", "Al Nwaylati Episode 23",
            "Match Highlights: EST vs CA", "Top Hits 2026 playlist", "Choufli Hal",
            "Official audio - Fairuz Classics", "News bulletin October 30", "DJ Set Live",
        };
        time("isMusicFull x100k", new Runnable() {
            @Override public void run() {
                int n = 0;
                for (int i = 0; i < 100000; i++) if (Logic.isMusicFull(null, yt, titles[i % titles.length], null)) n++;
                if (n == 0) throw new AssertionError("no music found");
            }
        }, 5000);

        // 2. Dedupe keys + junk filter over a big store dump.
        time("dedupe keys x100k", new Runnable() {
            @Override public void run() {
                Set<String> seen = new HashSet<String>();
                for (int i = 0; i < 100000; i++) {
                    seen.add(Logic.watchDedupeKey("Show " + (i % 5000), (i % 2 == 0) ? yt : tubi, "poster" + (i % 5000)));
                    Logic.dedupeKey("Pick " + i, yt);
                    Logic.isJunkPick("com.yablio.sendfilestotv", "Transfer Service Active");
                }
                if (seen.size() != 5000) throw new AssertionError("dedupe size " + seen.size());
            }
        }, 5000);

        // 3. Video-ID extraction over mixed URL shapes.
        final String[] urls = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?t=42",
            "intent://x#Intent;component=vnd.youtube:dQw4w9WgXcQ;end",
            "https://example.com/pick?video_id=dQw4w9WgXcQ&x=1",
            "https://www.youtube.com/feed/history", "not a link at all",
        };
        time("extractVideoId x20k", new Runnable() {
            @Override public void run() {
                int found = 0, none = 0;
                for (int i = 0; i < 20000; i++) {
                    String id = Logic.extractVideoId(urls[i % urls.length]);
                    if (id != null) { found++; if (!"dQw4w9WgXcQ".equals(id)) throw new AssertionError("bad id " + id); }
                    else none++;
                }
                if (found == 0 || none == 0) throw new AssertionError("shape mix wrong");
            }
        }, 5000);

        // 4. App ordering: sort 2,000 packages against a 200-app saved order.
        // v3.91: the sort builds the order map ONCE and the comparator is two
        // HashMap lookups -- the old compareOrder rebuilt the whole order
        // string and scanned it twice per comparison.
        final StringBuilder order = new StringBuilder();
        for (int i = 0; i < 200; i++) { if (i > 0) order.append(','); order.append("com.app.saved").append(i); }
        final String orderCsv = order.toString();
        final java.util.Map<String, Integer> ordPos = Logic.orderMap(orderCsv);
        time("compareOrder sort x2000", new Runnable() {
            @Override public void run() {
                List<String> pkgs = new ArrayList<String>();
                for (int i = 0; i < 2000; i++) pkgs.add("com.app.saved" + (i % 400));
                Collections.sort(pkgs, new Comparator<String>() {
                    @Override public int compare(String a, String b) { return Logic.compareOrder(ordPos, a, b); }
                });
                if (pkgs.size() != 2000) throw new AssertionError("sort lost items");
                // Saved apps still come first, in saved order.
                if (!"com.app.saved0".equals(pkgs.get(0))) throw new AssertionError("order wrong");
            }
        }, 5000);

        // 4b. Kids classification (v4.17): no title keyword list any
        // more -- the test is structural now (genre / rating / app), so
        // 100k cards pay no per-call string scan at all.
        final String[] kidGenres = {
            "Kids", "Children", "Animation", "Family", "Drama", "Documentary",
            "Preschool", "Comedy",
        };
        final String[] kidRatings = {
            null, "TV-Y", "TV-Y7", "TV-G", "TV-PG", null, "TV-Y", null,
        };
        time("isKidsFull x100k", new Runnable() {
            @Override public void run() {
                int kids = 0;
                for (int i = 0; i < 100000; i++) {
                    int j = i % kidGenres.length;
                    if (Logic.isKidsFull(kidGenres[j], kidRatings[j], yt, "Anything", null)) kids++;
                }
                if (kids == 0) throw new AssertionError("no kids found");
            }
        }, 5000);

        // 5. Weather + news helpers.
        time("weather/news helpers x100k", new Runnable() {
            @Override public void run() {
                float f = 0;
                for (int i = 0; i < 100000; i++) {
                    f += Logic.wxRingFrac((i % 50) - 15);
                    Logic.wxRingColorFor((i % 50) - 15, 0xFFE8C874);
                    Logic.wxIconKey(i % 100, i % 2);
                    Logic.wxLabel(i % 100);
                    Logic.sunTimeFmt("2026-10-30T07:54");
                    Logic.dayLength("2026-10-30T07:54", "2026-10-30T18:15");
                    Logic.newsAge(1761760000000L, 1761760000000L + (i % 5000) * 60000L);
                }
                if (f == 0) throw new AssertionError("no ring fractions");
            }
        }, 5000);

        // 6. Load correctness: 10,000 Watch Next rows, 2,000 exact dupes -> 8,000 unique.
        Set<String> uniq = new HashSet<String>();
        for (int i = 0; i < 8000; i++) uniq.add(Logic.watchDedupeKey("Show " + i, (i % 2 == 0) ? yt : tubi, "poster" + i));
        for (int i = 0; i < 2000; i++) uniq.add(Logic.watchDedupeKey("Show " + i, (i % 2 == 0) ? yt : tubi, "poster" + i));
        check("10k rows dedupe to exactly 8000", uniq.size() == 8000);

        // 7. Classification load: 5,000 rows land in the right rows.
        int kids = 0, music = 0, films = 0;
        for (int i = 0; i < 5000; i++) {
            String row;
            if (i < 1000) row = Logic.rowFor("Animation", "TV-Y", yt);
            else if (i < 2000) row = Logic.rowFor("Music", null, yt);
            else row = Logic.rowFor("Drama", "TV-MA", yt);
            if ("kids".equals(row)) kids++;
            else if ("music".equals(row)) music++;
            else films++;
        }
        check("5k rows classify 1000/1000/3000", kids == 1000 && music == 1000 && films == 3000);

        System.out.println("PerfTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
