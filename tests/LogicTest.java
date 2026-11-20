package com.bacem.launcher;

import java.util.HashSet;
import java.util.Set;

/**
 * Scenario tests for Logic (pure JVM, no Android). Run by build_launcher.sh
 * on every build; the build stops if any scenario fails.
 * Run manually: javac -d out Logic.java LogicTest.java && java -cp out com.bacem.launcher.LogicTest
 */
public class LogicTest {
    static int pass = 0, fail = 0;

    static void eq(String name, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name + "  expected=" + expected + "  actual=" + actual); }
    }

    public static void main(String[] args) {
        // ---------- Continue watching rows: which row does a card land in? ----------
        String yt = "com.google.android.youtube.tv";
        eq("YouTube talk show rated TV-G stays in Continue watching (regression)",
           Logic.rowFor("Entertainment", "TV-G", yt), "films");
        eq("Drama movie PG-13 is films", Logic.rowFor("Drama", "PG-13", "com.amazon.avod"), "films");
        eq("TV-MA series is films", Logic.rowFor("Drama", "TV-MA", yt), "films");
        eq("Football (Sports genre, Ostora) is films", Logic.rowFor("Sports", null, "com.ostora"), "films");
        eq("News is films", Logic.rowFor("News", null, yt), "films");
        eq("No genre, no rating, YouTube is films", Logic.rowFor(null, null, yt), "films");
        eq("Song pick with ft in title is music",
           Logic.isMusicFull(null, "org.smarttube", "Artmasta ft Ahmed Sokne - Wila Wila", null), true);
        eq("Arabic song title is music",
           Logic.isMusicFull(null, yt, "\u0627\u063a\u0627\u0646\u064a \u062a\u0648\u0646\u0633\u064a\u0629", null), true);
        eq("Drama episode is not music",
           Logic.isMusicFull("Drama", yt, "Al Nwaylati Episode 23", "Syrian drama series"), false);
        eq("Music genre is music (full test)",
           Logic.isMusicFull("Music", yt, "Anything", null), true);
        // v4.17: NO title keyword check any more -- a kids word in the
        // title alone does not make it kids (MASHA cooking show matched
        // "masha"). Only the rating, the genre, or the app count.
        eq("JJ & Mikey title alone is not kids",
           Logic.isKidsFull(null, null, yt, "JJ & Mikey Creator Essentials", null), false);
        eq("Diana Show title alone is not kids",
           Logic.isKidsFull(null, null, yt, "Diana Show - Kids Diana Show", null), false);
        eq("Kids genre is kids",
           Logic.isKidsFull("Kids", null, yt, "Anything", null), true);
        // v4.25: curated exact-title kids list (normalised, accents stripped).
        eq("Pokemon First Movie is a known kids title", Logic.isKnownKidsTitle("Pok\u00e9mon: The First Movie"), true);
        eq("Pokemon without accent is a known kids title", Logic.isKnownKidsTitle("Pokemon: The First Movie"), true);
        eq("Larva is a known kids title", Logic.isKnownKidsTitle("Larva"), true);
        eq("Milo and Chip is a known kids title", Logic.isKnownKidsTitle("Milo and Chip"), true);
        eq("Addams Family 2 is a known kids title", Logic.isKnownKidsTitle("The Addams Family 2"), true);
        eq("Sonic Boom is a known kids title", Logic.isKnownKidsTitle("Sonic Boom"), true);
        eq("Paw Patrol is a known kids title", Logic.isKnownKidsTitle("PAW Patrol"), true);
        eq("The Fish Guys is a known kids title", Logic.isKnownKidsTitle("The Fish Guys"), true);
        eq("Stay Wild is a known kids title", Logic.isKnownKidsTitle("Stay Wild"), true);
        eq("Air Buddies is a known kids title", Logic.isKnownKidsTitle("Air Buddies"), true);
        eq("Snow Buddies is a known kids title", Logic.isKnownKidsTitle("Snow Buddies"), true);
        eq("Uncle Buck is NOT a known kids title", Logic.isKnownKidsTitle("Uncle Buck"), false);
        eq("Random movie is NOT a known kids title", Logic.isKnownKidsTitle("Meet the Fockers"), false);
        eq("Bass Fishing title is not kids",
           Logic.isKidsFull(null, null, yt, "Bass Fishing Productions", null), false);
        eq("TV-Y rating is kids",
           Logic.isKidsFull(null, "TV-Y", yt, "Anything", null), true);
        // v3.87: Kids timer is time-based -- elapsed minutes come from the
        // clock, not from tick counts, so missed ticks while another app
        // (YouTube Kids) is in front do not lose minutes.
        eq("Kids elapsed 15 min in a session",
           Logic.kidsElapsedMin(1000000L, 1000000L + 15 * 60000L), 15L);
        eq("Kids elapsed 0 before session start",
           Logic.kidsElapsedMin(0L, 1000000L), 0L);
        eq("Kids used total adds stored + session elapsed",
           Logic.kidsUsedTotal(10L, 1000000L, 1000000L + 5 * 60000L), 15L);
        eq("Kids remaining is limit minus used",
           Logic.kidsRemainingMin(15L, 10L, 1000000L, 1000000L + 2 * 60000L), 3L);
        eq("Kids remaining never goes below 0",
           Logic.kidsRemainingMin(15L, 20L, 0L, 1000000L), 0L);
        // v3.87: bedtime alarm -- minutes until bedtime today, 0 when reached.
        eq("Bedtime 18:00 at 17:00 is 60 min away",
           Logic.bedtimeRemainingMin("18:00", 17 * 60), 60L);
        eq("Bedtime 18:00 at 18:00 is 0 (reached)",
           Logic.bedtimeRemainingMin("18:00", 18 * 60), 0L);
        eq("Bedtime 18:00 at 19:00 is 0 (already past)",
           Logic.bedtimeRemainingMin("18:00", 19 * 60), 0L);
        eq("No bedtime is -1",
           Logic.bedtimeRemainingMin("", 18 * 60), -1L);
        eq("Bad bedtime is -1",
           Logic.bedtimeRemainingMin("25:00", 18 * 60), -1L);
        // v4.29: timer-up lockdown -- the service and the launcher must
        // agree on "time is up". Mirrors MainActivity.kidsCheck.
        long t0 = 1000000L;
        eq("Kids mode off is never time up",
           Logic.kidsTimerUp(false, 15, 15, t0, t0 + 60 * 60000L, "", 12 * 60), false);
        eq("No limit and no bedtime is never time up",
           Logic.kidsTimerUp(true, 0, 0, t0, t0 + 60 * 60000L, "", 12 * 60), false);
        eq("Limit reached by stored minutes is time up",
           Logic.kidsTimerUp(true, 15, 15, 0L, t0, "", 12 * 60), true);
        eq("Limit reached by live session is time up",
           Logic.kidsTimerUp(true, 15, 10, t0, t0 + 5 * 60000L, "", 12 * 60), true);
        eq("Limit not reached is not time up",
           Logic.kidsTimerUp(true, 15, 10, t0, t0 + 4 * 60000L, "", 12 * 60), false);
        eq("Past bedtime is time up even with no limit",
           Logic.kidsTimerUp(true, 0, 0, 0L, t0, "20:00", 21 * 60), true);
        eq("Before 7 AM is time up (bedtime window)",
           Logic.kidsTimerUp(true, 0, 0, 0L, t0, "20:00", 2 * 60), true);
        eq("Before bedtime is not time up",
           Logic.kidsTimerUp(true, 0, 0, 0L, t0, "20:00", 19 * 60), false);
        eq("Ring color is gold at 12C", Logic.wxRingColor(12), 0xFFE8C874);
        eq("Ring color follows the accent when cold", Logic.wxRingColor(-3), 0xFFE8C874);
        eq("Ring fraction clamps at cold", Logic.wxRingFrac(-20), 0.05f);
        eq("Ring fraction full at 35C", Logic.wxRingFrac(35), 1f);
        eq("Themed ring uses the accent in the mild band", Logic.wxRingColorFor(12, 0xFF4DD0E1), 0xFF4DD0E1);
        eq("Themed ring follows the accent when cold", Logic.wxRingColorFor(-3, 0xFF4DD0E1), 0xFF4DD0E1);
        eq("Ring size is 64dp on Small", Logic.wxRingSize(0), 64);
        eq("Ring size is 96dp on Medium", Logic.wxRingSize(1), 96);
        eq("Ring size is 110dp on Large", Logic.wxRingSize(2), 110);
        eq("SmartTube stable is a Now Playing app", Logic.isNowPlayingApp("org.smarttube.stable"), true);
        eq("YouTube TV is a Now Playing app", Logic.isNowPlayingApp("com.google.android.youtube.tv"), true);
        eq("Spotify TV is a Now Playing app", Logic.isNowPlayingApp("com.spotify.tv.android"), true);
        eq("Cobalt is not a Now Playing app", Logic.isNowPlayingApp("io.gh.reisxd.tizentube.cobalt"), false);
        eq("Now Playing label is SmartTube for stable", Logic.nowPlayingAppLabel("org.smarttube.stable"), "YouTube (SmartTube)");
        eq("Now Playing title shows the video title", Logic.nowPlayingTitle("YouTube (SmartTube)", "Relaxing Piano Mix"), "YouTube (SmartTube) - Relaxing Piano Mix");
        eq("Now Playing falls back with no title", Logic.nowPlayingTitle("YouTube (SmartTube)", ""), "YouTube (SmartTube) - playing in background");
        eq("Eid accent is gold", Logic.themeAccent("eid"), 0xFFFFC107);
        eq("Ramadan accent is purple", Logic.themeAccent("ramadan"), 0xFF7C4DFF);
        eq("Mouled accent is green", Logic.themeAccent("mouled"), 0xFF2E7D32);
        eq("Christmas accent is white", Logic.themeAccent("christmas"), 0xFFFFFFFF);
        eq("Halloween accent is orange", Logic.themeAccent("halloween"), 0xFFFF6D00);
        eq("Eid has 4 rolling pictures", Logic.themePicCount("eid"), 4);
        eq("Ramadan has 4 rolling pictures", Logic.themePicCount("ramadan"), 4);
        eq("Christmas has 4 rolling pictures", Logic.themePicCount("christmas"), 4);
        eq("Eid al-Adha has 4 rolling pictures", Logic.themePicCount("eidadha"), 4);
        eq("Hijri New Year has 4 rolling pictures", Logic.themePicCount("hijri"), 4);
        eq("Ashura has 4 rolling pictures", Logic.themePicCount("ashura"), 4);
        eq("Hajj has 4 rolling pictures", Logic.themePicCount("hajj"), 4);
        eq("New Year has 4 rolling pictures", Logic.themePicCount("newyear"), 4);
        eq("Kids has 4 rolling pictures", Logic.themePicCount("kids"), 4);
        eq("Esperance has 1 picture", Logic.themePicCount("esperance"), 1);
        eq("Desert Mosque has 1 picture", Logic.themePicCount("mosquedesert"), 1);
        eq("Gold has 4 pictures now", Logic.themePicCount("gold"), 4);
        eq("Ocean has 4 pictures now", Logic.themePicCount("ocean"), 4);
        eq("Unknown theme has no pictures", Logic.themePicCount("nope"), 0);
        eq("Cartoon rated TV-Y is kids", Logic.rowFor("Entertainment", "TV-Y", yt), "kids");
        eq("TV-Y7 is kids", Logic.rowFor(null, "TV-Y7", yt), "kids");
        eq("Animation genre is kids again (v4.18)", Logic.rowFor("Animation", null, yt), "kids");
        eq("Family genre alone is not kids (v4.17)", Logic.rowFor("Family", null, yt), "films");
        eq("Kids & Family genre is kids", Logic.rowFor("Kids & Family", null, yt), "kids");
        eq("Children genre is kids", Logic.rowFor("Children", null, yt), "kids");
        eq("YouTube Kids app is kids", Logic.rowFor(null, null, "com.google.android.apps.youtube.kids"), "kids");
        eq("Spotify track is music", Logic.rowFor(null, null, "com.spotify.tv.android"), "music");
        eq("Music genre is music", Logic.rowFor("Music", "TV-G", yt), "music");
        eq("YouTube Music app is music", Logic.rowFor(null, null, "com.google.android.youtube.tvmusic"), "music");
        eq("Kids beats music when both match (partition order)",
           Logic.rowFor("Music", "TV-Y", yt), "kids");

        // ---------- Dedupe: same card twice or two different cards? ----------
        Set<String> seen = new HashSet<String>();
        seen.add(Logic.dedupeKey("Dune", "com.tubitv", "intent://x"));
        eq("Exact duplicate card dedupes", seen.add(Logic.dedupeKey("Dune", "com.tubitv", "intent://x")), false);
        eq("Same title, different app is a different card", seen.add(Logic.dedupeKey("Dune", "com.prime", "intent://x")), true);
        eq("Same title+app, different intent is a different card", seen.add(Logic.dedupeKey("Dune", "com.tubitv", "intent://y")), true);

        // ---------- YouTube video ID extraction ----------
        eq("watch?v= URL", Logic.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), "dQw4w9WgXcQ");
        eq("youtu.be short URL", Logic.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=4"), "dQw4w9WgXcQ");
        eq("vnd.youtube scheme", Logic.extractVideoId("vnd.youtube://dQw4w9WgXcQ"), "dQw4w9WgXcQ");
        eq("video_id parameter", Logic.extractVideoId("intent://play?video_id=dQw4w9WgXcQ#Intent"), "dQw4w9WgXcQ");
        eq("Non-YouTube URL has no ID", Logic.extractVideoId("https://example.com/movie/123"), null);
        eq("Null input has no ID", Logic.extractVideoId(null), null);

        // ---------- YouTube card targeting (v3.94) ----------
        eq("YouTube package column", Logic.isYouTubeTarget(null, "com.google.android.youtube.tv"), true);
        eq("youtube.com URL", Logic.isYouTubeTarget("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null), true);
        eq("youtu.be URL", Logic.isYouTubeTarget("https://youtu.be/dQw4w9WgXcQ", null), true);
        eq("Intent naming the YouTube package", Logic.isYouTubeTarget("intent:#Intent;action=android.intent.action.VIEW;package=com.google.android.youtube.tv;end", null), true);
        eq("vnd.youtube intent", Logic.isYouTubeTarget("vnd.youtube://dQw4w9WgXcQ", null), true);
        eq("Non-YouTube intent is not YouTube", Logic.isYouTubeTarget("intent://tubitv.com/movie/123#Intent;package=com.tubitv;end", "com.tubitv"), false);
        eq("Null intent and null package is not YouTube", Logic.isYouTubeTarget(null, null), false);

        // ---------- Weather scenes ----------
        eq("Code 0 day is clear", Logic.wxSceneKey(0, 1), "clear");
        eq("Code 0 night is night", Logic.wxSceneKey(0, 0), "night");
        eq("Code 1 night is night", Logic.wxSceneKey(1, 0), "night");
        eq("Code 2 is cloud", Logic.wxSceneKey(2, 1), "cloud");
        eq("Code 3 night is cloud", Logic.wxSceneKey(3, 0), "cloud");
        eq("Code 45 is fog", Logic.wxSceneKey(45, 1), "fog");
        eq("Code 48 is fog", Logic.wxSceneKey(48, 1), "fog");
        eq("Code 95 is storm", Logic.wxSceneKey(95, 1), "storm");
        eq("Code 99 is storm", Logic.wxSceneKey(99, 1), "storm");
        eq("Code 71 is snow", Logic.wxSceneKey(71, 1), "snow");
        eq("Code 77 is snow", Logic.wxSceneKey(77, 1), "snow");
        eq("Code 85 is snow", Logic.wxSceneKey(85, 1), "snow");
        eq("Code 61 is rain", Logic.wxSceneKey(61, 1), "rain");
        eq("Code 51 drizzle shows rain scene", Logic.wxSceneKey(51, 1), "rain");
        eq("Code 80 showers show rain scene", Logic.wxSceneKey(80, 1), "rain");

        // wxIconKey: the flat state icon on the weather card
        eq("icon clear day", Logic.wxIconKey(0, 1), "sun");
        eq("icon clear night", Logic.wxIconKey(0, 0), "moon");
        eq("icon mainly clear night", Logic.wxIconKey(1, 0), "moon");
        eq("icon partly cloudy day", Logic.wxIconKey(2, 1), "suncloud");
        eq("icon partly cloudy night", Logic.wxIconKey(2, 0), "mooncloud");
        eq("icon overcast", Logic.wxIconKey(3, 1), "cloud");
        eq("icon fog", Logic.wxIconKey(48, 1), "fog");
        eq("icon storm", Logic.wxIconKey(96, 1), "storm");
        eq("icon snow showers", Logic.wxIconKey(86, 1), "snow");
        eq("icon drizzle", Logic.wxIconKey(51, 1), "rain");
        eq("icon rain showers", Logic.wxIconKey(81, 1), "rain");
        eq("icon unknown code", Logic.wxIconKey(999, 1), "cloud");

        // sun times + news age
        eq("sunrise fmt", Logic.sunTimeFmt("2026-10-20T07:38"), "7:38 AM");
        eq("sunset fmt", Logic.sunTimeFmt("2026-10-20T18:24"), "6:24 PM");
        eq("midnight fmt", Logic.sunTimeFmt("2026-10-20T00:05"), "12:05 AM");
        eq("noon fmt", Logic.sunTimeFmt("2026-10-20T12:00"), "12:00 PM");
        eq("bad sun fmt", Logic.sunTimeFmt("x"), "");
        eq("day length", Logic.dayLength("2026-10-20T07:38", "2026-10-20T18:24"), "10 h 46 min");
        eq("age just now", Logic.newsAge(1000000L, 1000000L + 30000L), "just now");
        eq("age minutes", Logic.newsAge(0L, 12L * 60000L), "12 min ago");
        eq("age hours", Logic.newsAge(0L, 3L * 3600000L), "3 h ago");
        eq("age days", Logic.newsAge(0L, 2L * 86400000L), "2 d ago");

        // ---------- Weather labels ----------
        eq("Label 0", Logic.wxLabel(0), "Clear");
        eq("Label 3", Logic.wxLabel(3), "Overcast");
        eq("Label 45", Logic.wxLabel(45), "Fog");
        eq("Label 61", Logic.wxLabel(61), "Rain");
        eq("Label 71", Logic.wxLabel(71), "Snow");
        eq("Label 82", Logic.wxLabel(82), "Showers");
        eq("Label 95", Logic.wxLabel(95), "Thunderstorm");
        eq("Label for unknown code is empty", Logic.wxLabel(999), "");

        // ---------- App order (long-press Move) ----------
        String ord = "com.b,com.a";
        eq("Saved order: b before a", Logic.compareOrder(ord, "com.a", "com.b") > 0, true);
        eq("Saved order: a after b", Logic.compareOrder(ord, "com.b", "com.a") < 0, true);
        eq("Unlisted app goes after listed ones", Logic.compareOrder(ord, "com.new", "com.a") > 0, true);
        eq("Two unlisted apps are equal", Logic.compareOrder(ord, "com.x", "com.y"), 0);
        eq("Empty order: everything equal", Logic.compareOrder("", "com.a", "com.b"), 0);
        eq("Substring is not a match (com.tubi vs com.tubitv)",
           Logic.compareOrder("com.tubitv", "com.tubi", "com.tubitv") > 0, true);
        eq("Null order behaves as empty", Logic.compareOrder((String) null, "com.a", "com.b"), 0);

        // ---------- v3.28: Continue watching dedupe + junk picks ----------
        Set<String> seenW = new HashSet<String>();
        seenW.add(Logic.watchDedupeKey("Masha and the Bear", "com.tubitv", null));
        eq("Same Tubi show with blank art dedupes", seenW.add(Logic.watchDedupeKey("Masha and the Bear", "com.tubitv", null)), false);
        eq("Same show, different poster art survives", seenW.add(Logic.watchDedupeKey("Masha and the Bear", "com.tubitv", "http://x/ep2.jpg")), true);
        eq("Same title, different app survives", seenW.add(Logic.watchDedupeKey("Masha and the Bear", "com.google.android.youtube.tv", null)), true);
        eq("Title case does not split a duplicate", seenW.add(Logic.watchDedupeKey("MASHA AND THE BEAR", "com.tubitv", null)), false);
        eq("Send Files to TV card is junk", Logic.isJunkPick("com.yablio.sendfilestotv", "Transfer Service Active"), true);
        eq("Transfer Service Active title is junk from any app", Logic.isJunkPick("com.other", "Transfer Service Active"), true);
        eq("Real pick is not junk", Logic.isJunkPick("com.tubitv", "Masha and the Bear"), false);


        // ---------- v3.47: languages ----------
        eq("lang cycle en->fr", Logic.langNext("en"), "fr");
        eq("lang cycle fr->ar", Logic.langNext("fr"), "ar");
        eq("lang cycle ar->en", Logic.langNext("ar"), "en");
        eq("lang cycle unknown->en", Logic.langNext("xx"), "en");
        eq("Arabic is RTL", Logic.isRtl("ar"), true);
        eq("French is not RTL", Logic.isRtl("fr"), false);
        eq("tr home fr", Logic.tr("fr", "home"), "Accueil");
        eq("tr home ar", Logic.tr("ar", "home"), "\u0627\u0644\u0631\u0626\u064a\u0633\u064a\u0629");
        eq("tr home en", Logic.tr("en", "home"), "Home");
        eq("tr unknown key falls back to key", Logic.tr("fr", "nope"), "nope");
        eq("tr null lang is English", Logic.tr(null, "music"), "Music");
        eq("widget height small", Logic.widgetHeight(0), 130);
        eq("widget height medium", Logic.widgetHeight(1), 160);
        eq("widget height large", Logic.widgetHeight(2), 190);
        eq("widget size name small", Logic.widgetSizeName(0), "Small");
        eq("move widget right", Logic.moveWidget("weather,sun,care", "weather", 1), "sun,weather,care");
        eq("move widget left", Logic.moveWidget("weather,sun,care", "care", -1), "weather,care,sun");
        eq("move widget at end is a no-op", Logic.moveWidget("weather,sun,care", "care", 1), "weather,sun,care");
        eq("move unknown widget is a no-op", Logic.moveWidget("weather,sun,care", "nope", 1), "weather,sun,care");
        eq("beja accent", Logic.themeAccent("beja"), 0xFFD50000);
        eq("nightmosque accent", Logic.themeAccent("nightmosque"), 0xFF8AB4F8);
        eq("unknown theme accent is 0", Logic.themeAccent("nope"), 0);
        eq("beja is a hero theme", Logic.isHeroTheme("beja"), true);
        eq("nightmosque is not a hero theme", Logic.isHeroTheme("nightmosque"), false);
        eq("gold is not a hero theme", Logic.isHeroTheme("gold"), false);
        eq("YouTube Continue watching cap is 20", Logic.watchCapFor("com.google.android.youtube.tv"), 20);
        eq("Tubi Continue watching cap is 12", Logic.watchCapFor("com.tubitv"), 12);
        eq("Unknown app Continue watching cap is 12", Logic.watchCapFor(null), 12);
        eq("SmartTube candidates start with the current stable package", Logic.youtubeMusicPkgs()[0], "org.smarttube.stable");
        eq("SmartTube candidates still include the legacy package", Logic.youtubeMusicPkgs()[2], "com.teamsmart.videomanager.tv");
        eq("Watch intent gives a YouTube thumbnail", Logic.ytThumbUrl("intent://www.youtube.com/watch?v=dQw4w9WgXcQ#Intent;package=com.google.android.youtube.tv;end"), "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg");
        eq("Non-YouTube intent has no thumbnail", Logic.ytThumbUrl("intent://tubitv.com/watch/123#Intent;package=com.tubitv;end"), null);
        eq("OB hero subline", Logic.heroSubline("beja"), "Red and white \u00b7 Stade Boujemaa Kmiti");
        eq("Arsenal hero subline", Logic.heroSubline("arsenal"), "Victoria Concordia Crescit");
        eq("PSG hero subline", Logic.heroSubline("psg"), "Allez Paris Saint-Germain");
        eq("Real Madrid hero subline", Logic.heroSubline("realmadrid"), "Hala Madrid");
        eq("Barca hero subline", Logic.heroSubline("barca"), "M\u00e9s que un club");
        eq("Arsenal is a hero theme", Logic.isHeroTheme("arsenal"), true);
        eq("Ahly is gone from hero themes", Logic.isHeroTheme("ahly"), false);
        eq("Zamalek is gone from hero themes", Logic.isHeroTheme("zamalek"), false);
        eq("Arsenal accent is red", Logic.themeAccent("arsenal"), 0xFFEF0107);
        eq("Watching tab translates", Logic.tr("fr", "watching"), "Regarder");
        eq("Calendar tab translates", Logic.tr("fr", "calendar"), "Calendrier");
        // v4.53: Jannah's calendar -- dow: 1 Sun, 2 Mon ... 7 Sat.
        eq("Monday is Lundi", Logic.calendarDayName(2), "Lundi");
        eq("Sunday is Dimanche", Logic.calendarDayName(1), "Dimanche");
        eq("Monday is swimming at 17h", Logic.calendarActivity(2), "Piscine \u00e0 17h");
        eq("Tuesday is Madame Robin Jack", Logic.calendarActivity(3), "Madame Robin Jack");
        eq("Wednesday is Madame Robin Jack", Logic.calendarActivity(4), "Madame Robin Jack");
        eq("Thursday is art", Logic.calendarActivity(5), "Art");
        eq("Friday is pizza", Logic.calendarActivity(6), "Pizza !");
        eq("Saturday is family + popsicle", Logic.calendarActivity(7), "Famille + Popsicle");
        eq("Sunday is family + little sister + popsicle", Logic.calendarActivity(1), "Famille + Petite s\u0153ur + Popsicle");
        eq("2027 Jan 1 is New Year's Day", Logic.holiday2027(1, 1), "New Year's Day");
        eq("2027 Feb 15 is Family Day", Logic.holiday2027(2, 15), "Family Day");
        eq("2027 Mar 26 is Good Friday", Logic.holiday2027(3, 26), "Good Friday");
        eq("2027 May 24 is Victoria Day", Logic.holiday2027(5, 24), "Victoria Day");
        eq("2027 Jul 1 is Canada Day", Logic.holiday2027(7, 1), "Canada Day");
        eq("2027 Sep 6 is Labour Day", Logic.holiday2027(9, 6), "Labour Day");
        eq("2027 Oct 11 is Thanksgiving", Logic.holiday2027(10, 11), "Thanksgiving Day");
        eq("2027 Dec 25 is Christmas", Logic.holiday2027(12, 25), "Christmas Day");
        eq("2027 Dec 26 is Boxing Day", Logic.holiday2027(12, 26), "Boxing Day");
        eq("2027 Aug 2 Civic Holiday is NOT statutory", Logic.holiday2027(8, 2), null);
        eq("2027 Mar 29 Easter Monday is NOT statutory", Logic.holiday2027(3, 29), null);
        eq("2027 Jan 2 is not a holiday", Logic.isHoliday2027(1, 2), false);
        // v4.56: L'Harmonie PD days + school holidays, date math, events.
        eq("Nov 27 2026 is a PD day", Logic.isPdDay(2026, 11, 27), true);
        eq("Sep 2 2026 is a PD day", Logic.isPdDay(2026, 9, 2), true);
        eq("Oct 26 2026 is a PD day", Logic.isPdDay(2026, 10, 26), true);
        eq("Feb 5 2027 is a PD day", Logic.isPdDay(2027, 2, 5), true);
        eq("Jun 30 2027 is a PD day", Logic.isPdDay(2027, 6, 30), true);
        eq("Nov 28 2026 is NOT a PD day", Logic.isPdDay(2026, 11, 28), false);
        eq("Dec 25 2026 is Christmas Day", Logic.schoolHolidayName(2026, 12, 25), "Christmas Day");
        eq("Dec 22 2026 is Christmas break", Logic.schoolHolidayName(2026, 12, 22), "Christmas break");
        eq("Mar 17 2027 is March break", Logic.schoolHolidayName(2027, 3, 17), "March break");
        eq("Mar 29 2027 is Easter Monday", Logic.schoolHolidayName(2027, 3, 29), "Easter Monday");
        eq("Nov 27 2026 no-school reason is PD", Logic.noSchoolName(2026, 11, 27), "PD");
        eq("Dec 25 2026 no-school reason is Christmas Day", Logic.noSchoolName(2026, 12, 25), "Christmas Day");
        eq("Nov 28 2026 has school", Logic.noSchoolName(2026, 11, 28), null);
        eq("PD badge is French", Logic.noSchoolBadge(2026, 11, 27), "Pas d'\u00e9cole \u00b7 Journ\u00e9e p\u00e9dagogique");
        eq("Holiday badge is French", Logic.noSchoolBadge(2026, 12, 25), "Pas d'\u00e9cole \u00b7 Christmas Day");
        eq("Feb 2027 has 28 days", Logic.daysInMonth(2027, 2), 28);
        eq("Nov 2026 has 30 days", Logic.daysInMonth(2026, 11), 30);
        eq("Nov 19 2026 is a Thursday", Logic.dayOfWeek(2026, 11, 19), 5);
        eq("Nov 30 + 7 is Dec 7", java.util.Arrays.toString(Logic.addDays(2026, 11, 30, 7)), "[2026, 12, 7]");
        eq("Dec 28 + 7 is Jan 4", java.util.Arrays.toString(Logic.addDays(2026, 12, 28, 7)), "[2027, 1, 4]");
        eq("Nov + 1 is Dec", java.util.Arrays.toString(Logic.addMonths(2026, 11, 1)), "[2026, 12]");
        eq("Dec + 1 is Jan next year", java.util.Arrays.toString(Logic.addMonths(2026, 12, 1)), "[2027, 1]");
        eq("Monday of Nov 19 week is Nov 16", java.util.Arrays.toString(Logic.weekMonday(2026, 11, 19)), "[2026, 11, 16]");
        eq("Nov 2026 starts on a Sunday, offset 6", Logic.firstDayMondayOffset(2026, 11), 6);
        eq("Event date key", Logic.eventDateKey(2026, 11, 27), "2026-11-27");
        eq("Event text key", Logic.eventTextKey("2026-11-27"), "evt_2026-11-27");
        eq("Event pic key", Logic.eventPicKey("2026-11-27"), "evtpic_2026-11-27");
        eq("Event activity key", Logic.eventActivityKey("2026-11-27"), "evtact_2026-11-27");
        eq("Holiday date uses holiday picture", Logic.calendarPictureForDate(2026, 12, 25), "holiday");
        eq("PD day keeps Friday pizza picture", Logic.calendarPictureForDate(2026, 11, 27), "pizza");
        eq("Monday short activity is Piscine", Logic.calendarShortActivity(2), "Piscine");
        eq("November in French", Logic.monthNameFr(11), "Novembre");
        eq("Gold has no hero subline", Logic.heroSubline("gold"), "");
        System.out.println("----");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
