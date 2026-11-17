package com.bacem.launcher;

/** Live TV guide, Morph TV style (v3.66): categories in a left sidebar,
 *  channels as a grid of big cards, a red live dot on every card (like the
 *  Wi-Fi green dot). YouTube channels open in TizenTube Cobalt (direct
 *  /live link where verified, search fallback otherwise); the free HLS
 *  channels play in the built-in PlayerActivity from their direct stream
 *  (iptv-org public lists, verified live 2026-11-13). No programme data
 *  is invented: cards show the channel's own label, not a fake now/next.
 *  DPAD only: Left/Right switches column, Up/Down moves, OK selects/plays,
 *  Back exits. */
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class LiveTvActivity extends Activity {
    // kind 0 = YouTube, opens in Cobalt; kind 1 = free HLS, plays in
    // PlayerActivity; kind 2 = provider HLS; kind 3 = provider hint row.
    // kind 4 = series season, opens the Cemba episode list (v4.36).
    // ua = optional User-Agent override for the player (L'Equipe needs a
    // desktop Chrome UA); null means the default.
    static class Ch {
        String name, sub, url, init, ua; int color, logo, kind;
        Ch(String n, String s, String u, int c, int l, String i, int k) { this(n, s, u, c, l, i, k, null); }
        Ch(String n, String s, String u, int c, int l, String i, int k, String ua) {
            name = n; sub = s; url = u; color = c; logo = l; init = i; kind = k; this.ua = ua;
        }
    }
    static final String DESK_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    // Tunisian channels on YouTube (Watania 1/2 moved to direct HLS below).
    static final Ch[] YT = {
        new Ch("El Hiwar Ettounsi", "Talk & news - live", "https://www.youtube.com/channel/XzmMkXaHxMVlutDBD8goHA/live", 0xFF6A1B9A, R.drawable.lg_hiwar, "HE", 0),
        new Ch("Attessia TV", "General - live", "https://www.youtube.com/results?search_query=Attessia%20TV%20live", 0xFFAD1457, R.drawable.lg_attessia, "9", 0),
        new Ch("Nessma", "Maghreb - live", "https://www.youtube.com/@NessmaTV/live", 0xFFC62828, R.drawable.lg_nessma, "N", 0),
        new Ch("Nessma Sport", "Sport - live", "https://www.youtube.com/@NessmaSport/live", 0xFFEF6C00, R.drawable.lc_nessmasport, "NS", 0),
        new Ch("Hannibal TV", "General - live", "https://www.youtube.com/results?search_query=Hannibal%20TV%20Tunisie%20live", 0xFF1565C0, R.drawable.lg_hannibal, "H", 0),
        new Ch("Zitouna TV", "News & faith - live", "https://www.youtube.com/@zitouna_tv_officiel/live", 0xFF1B7A3D, 0, "Z", 0),
        new Ch("Telvza TV", "General - live", "https://www.youtube.com/results?search_query=Telvza%20TV%20live", 0xFF5E35B1, 0, "T", 0),
    };
    // Direct HLS streams from the iptv-org public lists (official broadcaster
    // CDNs where possible). Baked in; refresh in a later build if a link dies.
    static final Ch[] FREE = {
        new Ch("Al Jazeera", "News - Arabic - free", "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8", 0xFF0F2A44, 0, "AJ", 1),
        new Ch("Al Jazeera English", "News - English - free", "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8", 0xFF0F2A44, 0, "AJ", 1),
        new Ch("France 24 Arabic", "News - Arabic - free", "https://live.france24.com/hls/live/2037222-b/F24_AR_HI_HLS/master_5000.m3u8", 0xFF003DA5, 0, "24", 1),
        new Ch("Al Arabiya English", "News - English - free", "https://live.alarabiya.net/alarabiapublish/english/playlist_dvr.m3u8", 0xFFB71C1C, 0, "AA", 1),
        new Ch("Sky News Arabia", "News - Arabic - free", "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8", 0xFF6A1B9A, 0, "SN", 1),
        new Ch("Africanews English", "News - English - free", "https://cdn-euronews.akamaized.net/live/eds/africanews-en/25049/index.m3u8", 0xFF00695C, 0, "AN", 1),
        new Ch("ABC News", "News - English - free", "https://abc-news-dmd-streams-1.akamaized.net/out/v1/701126012d044971b3fa89406a440133/index.m3u8", 0xFF0D47A1, 0, "AB", 1),
        new Ch("CNN International", "News - English - free", "https://d3bp6dwmpbdajl.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-ury0meh5m4nzm/index.m3u8", 0xFFB71C1C, 0, "CN", 1),
        new Ch("DW English", "News - English - free", "https://amg01644-amg01644c1-amgplt0343.playout.now3.amagi.tv/ts-eu-w1-n2/playlist/amg01644-amg01644c1-amgplt0343/playlist.m3u8", 0xFF1565C0, 0, "DW", 1),
        new Ch("NBC News NOW", "News - English - free", "https://d1si3n1st4nkgb.cloudfront.net/10502/88896001/hls/master.m3u8?ads.xumo_channelId=88896001", 0xFF6A1B9A, 0, "NB", 1),
        new Ch("Bloomberg TV", "Business - English - free", "http://23.237.104.106:8080/USA_BLOOMBERG/index.m3u8", 0xFF00897B, 0, "BB", 1),
        new Ch("CNews", "News - French - free", "https://raw.githubusercontent.com/Paradise-91/ParaTV/main/streams/canalplus/cnews-dm.m3u8", 0xFF37474F, 0, "CN", 1),
        new Ch("Euronews French", "News - French - free", "https://2f6c5bf4.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmxheHhUVi1ldV9FdXJvbmV3c0ZyYW5jYWlzX0hMUw/playlist.m3u8", 0xFF5D4037, 0, "EF", 1),
    };
    // v3.66: free Arabic news (verified live 2026-11-13).
    static final Ch[] AR_NEWS = {
        new Ch("Al Arabiya", "News - Arabic - free", "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8", 0xFFB71C1C, 0, "AA", 1),
        new Ch("Al Hadath", "News - Arabic - free", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-hadath/2ff87ec4c2f3ede35295a20637d9f8fd/index.m3u8", 0xFF8E24AA, 0, "AH", 1),
        new Ch("DW Arabic", "News - Arabic - free", "https://dwamdstream103.akamaized.net/hls/live/2015526/dwstream103/master.m3u8", 0xFF1565C0, 0, "DW", 1),
        new Ch("TRT Arabi", "News - Arabic - free", "https://tv-trtarabi.medya.trt.com.tr/master.m3u8", 0xFFC62828, 0, "TA", 1),
        new Ch("Medi1TV", "News - Morocco - free", "https://cdn.live.easybroadcast.io/abr_corp/83_medi1tv-arabic_g90v4ec/playlist.m3u8", 0xFF2E7D32, 0, "M1", 1),
        new Ch("Asharq News", "News - Arabic - free", "https://live-news.asharq.com/asharq.m3u8", 0xFF37474F, 0, "AN", 1),
        new Ch("Al Jazeera 2", "News - Arabic - free", "https://live-hls-web-aja2-gcp.thehlive.com/AJA2/index.m3u8", 0xFF00897B, 0, "J2", 1),
        new Ch("Al Jazeera Mubasher", "News - Arabic - free", "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8", 0xFF0F2A44, 0, "JM", 1),
        new Ch("Al Mayadeen TV", "News - Arabic - free", "https://mdnlv.cdn.octivid.com/almdn/smil:mpegts.stream.smil/playlist.m3u8", 0xFFB71C1C, 0, "MY", 1),
        new Ch("Al Arabiya Business", "Business - Arabic - free", "https://live.alarabiya.net/alarabiapublish/aswaaq.smil/playlist.m3u8", 0xFF6A1B9A, 0, "AB", 1),
        new Ch("Al Arabiya Programs", "Documentary - Arabic - free", "https://live.alarabiya.net/alarabiapublish/aaprograms.smil/playlist.m3u8", 0xFF37474F, 0, "AP", 1),
    };
    // v3.66: free Arabic general (Watania 1/2 are direct HLS now, no YouTube).
    static final Ch[] AR_GEN = {
        new Ch("Watania 1", "Tunisia - general - free", "https://sw1.tanitweb.net/TunisiaTV/_definst_/watania1/chunklist.m3u8", 0xFF1B5E20, R.drawable.lg_watania1, "W1", 1),
        new Ch("Watania 2", "Tunisia - general - free", "https://sw1.tanitweb.net/TunisiaTV/_definst_/watania2/playlist.m3u8?DVR", 0xFF2E7D32, R.drawable.lg_watania2, "W2", 1),
        new Ch("Sharjah TV", "UAE - general - free", "https://live.kwikmotion.com/smc1live/smc1tv.smil/playlist.m3u8", 0xFF6A1B9A, 0, "SH", 1),
        new Ch("Al Saudiya", "Saudi - general - free", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-saudi-tv/2ad66056b51fd8c1b624854623112e43/index.m3u8", 0xFF00695C, 0, "SA", 1),
        new Ch("KTV 1", "Kuwait - general - free", "https://kwtktv1ta.cdn.mangomolo.com/ktv1/smil:ktv1.stream.smil/chunklist.m3u8", 0xFF5D4037, 0, "K1", 1),
        new Ch("MBC Masr", "Egypt - shows - free", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8", 0xFFAD1457, 0, "MM", 1),
        new Ch("Asharq Documentary", "Documentary - Arabic - free", "https://svs.itworkscdn.net/asharqdocumentarylive/asharqdocumentary.smil/playlist_dvr.m3u8", 0xFF5D4037, 0, "AD", 1),
        new Ch("Asharq Discovery", "Documentary - Arabic - free", "https://svs.itworkscdn.net/asharqdiscoverylive/asharqd.smil/playlist_dvr.m3u8", 0xFF6A1B9A, 0, "AS", 1),
        new Ch("MBC 4", "Entertainment - Arabic - free", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-4/24f134f1cd63db9346439e96b86ca6ed/index.m3u8", 0xFFAD1457, 0, "M4", 1),
        new Ch("MTV Lebanon", "General - Lebanon - free", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mtv-lebanon/b8ebb2a5affb812f1541712adde10e26/index.m3u8", 0xFF1565C0, 0, "ML", 1),
        new Ch("Al Jadeed", "General - Lebanon - free", "http://185.9.2.18/chid_391/mono.m3u8", 0xFF2E7D32, 0, "JD", 1),
    };
    // v3.66: free sport (verified live 2026-11-13). beIN Sports 1 added
    // 2026-11-17 at his explicit request -- he showed it playing in Morph TV
    // (Sports list #5) and asked for it in Cemba Home. It is a free restream,
    // not a licensed beIN feed; the stream below was verified live 2026-11-17.
    // No ESPN/Sky/DAZN -- those are paid channels; only free-to-air sport
    // is bundled here otherwise.
    static final Ch[] SPORT = {
        new Ch("KTV Sport", "Kuwait - sport - free", "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8", 0xFF1B5E20, 0, "KS", 1),
        new Ch("KTV Sport Plus", "Kuwait - sport - free", "https://kwtsplta.cdn.mangomolo.com/spl/smil:spl.stream.smil/chunklist.m3u8", 0xFF2E7D32, 0, "KP", 1),
        new Ch("Jordan Sport", "Jordan - sport - free", "https://jrtv-live.ercdn.net/jordansporthd/jordansporthd.m3u8", 0xFF1565C0, 0, "JS", 1),
        new Ch("Al Iraqia Sport", "Iraq - sport - free", "https://imn-live.esite-lab.com/hls/iraqia-sports-1.m3u8", 0xFF6A1B9A, 0, "IS", 1),
        new Ch("Bahrain Sports 1", "Bahrain - sport - free", "https://5c7b683162943.streamlock.net/live/ngrp:sportsone_all/playlist.m3u8", 0xFFEF6C00, 0, "B1", 1),
        new Ch("Bahrain Sports 2", "Bahrain - sport - free", "https://5c7b683162943.streamlock.net/live/ngrp:bahrainsportstwo_all/playlist.m3u8", 0xFF00897B, 0, "B2", 1),
        new Ch("beIN Sports 1", "Sport - beIN - free restream", "https://cp11.adabmedia.com/hls2/bein1.m3u8", 0xFF6A1B9A, 0, "b1", 1),
        new Ch("Red Bull TV", "Action sport - free", "https://3ea22335.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWdiX1JlZEJ1bGxUVl9ITFM/playlist.m3u8", 0xFFC62828, 0, "RB", 1),
        new Ch("FIFA+", "Football - 24/7 - free", "https://d2w9q46ikgrcwx.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-of5cbk3sav3w5/v1/sysdata_s_p_a_fifa_7/samsungheadend_us/latest/main/hls/playlist.m3u8", 0xFF0D47A1, 0, "F+", 1),
        new Ch("Sportitalia", "Italy - football - free", "https://edge-001.streamup.eu/sportitalia/sihd_abr/playlist.m3u8", 0xFF1B5E20, 0, "SI", 1),
        new Ch("L'Equipe", "France - sport - free", "https://dq37unyetkpcz.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-m04j89j7k5gtp/LEquipe_FR.m3u8", 0xFFB71C1C, 0, "LE", 1, DESK_UA),
    };
    // v4.32: free world channels (verified live 2026-11-17) -- French/English
    // general, not news, so they live here instead of the News row.
    static final Ch[] WORLD = {
        new Ch("RMC Decouverte", "Documentary - French - free", "https://d16zzycxcd0m0r.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-hixvx5kymecr9/RMC_Decouverte_FR.m3u8", 0xFF1B5E20, 0, "RD", 1),
        new Ch("RMC Story", "Entertainment - French - free", "https://d15aro46bnpfm8.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-fqkqiax1078up/RMC_Story_FR.m3u8", 0xFFEF6C00, 0, "RS", 1),
        new Ch("TV5Monde", "General - French - free", "https://n-5-2.eu.cdn-redge.media/livehls/oeu/mt/live/tv5_monde_fbs/live.livx/playlist.m3u8", 0xFF0D47A1, 0, "T5", 1),
        new Ch("PBS Kids", "Kids - English - free", "https://livestream.pbskids.org/out/v1/14507d931bbe48a69287e4850e53443c/est.m3u8", 0xFF8E24AA, 0, "PK", 1),
    };
    // v4.35: series on YouTube (VOD playlists, kind 0 = opens in Cobalt).
    // Choufli Hal: Tunisian sitcom, 6 seasons / 135 episodes, 2005-2009.
    // Season by season (his ask), using the season playlists the
    // Choufli Hal channel itself links to. Watania Replay also has one
    // official playlist for the whole sitcom, but he wants it by season.
    static final Ch[] SERIES = {
        new Ch("Choufli Hal 2005", "Season 1 - Tunisian sitcom", "https://www.youtube.com/playlist?list=PLJ42SUYk3_Q8dUWcl-f1M5kf_KhwQAhNY", 0xFFC62828, 0, "05", 4),
        new Ch("Choufli Hal 2006", "Season 2 - Tunisian sitcom", "https://www.youtube.com/playlist?list=PLJ42SUYk3_Q_2aTOKeMhkDhfR-il5S8Cn", 0xFFC62828, 0, "06", 4),
        new Ch("Choufli Hal 2007", "Season 3 - Tunisian sitcom", "https://www.youtube.com/playlist?list=PLJ42SUYk3_Q8RgjOKZB9t1KJHmRDio6u3", 0xFFC62828, 0, "07", 4),
        new Ch("Choufli Hal 2008", "Season 4 - Tunisian sitcom", "https://www.youtube.com/playlist?list=PLJ42SUYk3_Q_n8dmyc_2QOwiP5sN2ax6D", 0xFFC62828, 0, "08", 4),
        new Ch("Choufli Hal 2009", "Season 5 - Tunisian sitcom", "https://www.youtube.com/playlist?list=PLJ42SUYk3_Q-fUq2C2-_82RcQ1-I9UPd7", 0xFFC62828, 0, "09", 4),
    };
    static final String[] CATS = { "All channels", "Tunisia", "News \u2014 free", "Arabic", "Sport", "YouTube live", "World", "Series" };
    static final int GRID_COLS = 4;

    LinearLayout catCol, gridCol;
    ScrollView gridScroll, catScroll;
    TextView topTitle, topSub, footCount, searchPill;
    String searchQuery = null;
    ArrayList<Ch> shown = new ArrayList<Ch>();
    int cat = 0, chSel = 0, col = 1; // col 0 = categories, 1 = grid
    TextView[] catViews = new TextView[CATS.length];
    ArrayList<View> cardViews = new ArrayList<View>();
    // v3.54: the user's own provider channels (kind 2 = provider HLS).
    ArrayList<Ch> iptv = new ArrayList<Ch>();
    boolean iptvLoading = false;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    GradientDrawable redDotBg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFE62832);
        return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(20, 24, 48));
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        // ---- Left sidebar ----
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackground(rounded(Color.rgb(26, 31, 62), 16));
        side.setPadding(dp(24), dp(24), dp(24), dp(24));
        // v3.70: sidebar narrower and fonts smaller, like Morph (his ask:
        // "make the left bar smaller and the font smaller like the morphe").
        // Width 320 -> 260dp; CEMBA 34 -> 26sp; Live TV 24 -> 20sp.
        root.addView(side, new LinearLayout.LayoutParams(dp(260), -1));
        TextView brand = new TextView(this);
        brand.setText("CEMBA");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(26);
        brand.setTypeface(brand.getTypeface(), android.graphics.Typeface.BOLD);
        side.addView(brand);
        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lrl = new LinearLayout.LayoutParams(-2, -2);
        lrl.topMargin = dp(6);
        side.addView(liveRow, lrl);
        View dot = new View(this);
        dot.setBackground(redDotBg());
        liveRow.addView(dot, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView liveTv = new TextView(this);
        liveTv.setText("Live TV");
        liveTv.setTextColor(0xFFFFD200);
        liveTv.setTextSize(20);
        LinearLayout.LayoutParams ltl = new LinearLayout.LayoutParams(-2, -2);
        ltl.leftMargin = dp(10);
        liveRow.addView(liveTv, ltl);
        catCol = new LinearLayout(this);
        catCol.setOrientation(LinearLayout.VERTICAL);
        catScroll = new ScrollView(this);
        catScroll.setVerticalScrollBarEnabled(false);
        catScroll.addView(catCol);
        LinearLayout.LayoutParams ccl = new LinearLayout.LayoutParams(-1, 0, 1);
        ccl.topMargin = dp(28);
        side.addView(catScroll, ccl);
        footCount = new TextView(this);
        footCount.setTextColor(Color.rgb(175, 180, 200));
        footCount.setTextSize(18);
        side.addView(footCount);
        TextView footPage = new TextView(this);
        footPage.setText("Page 1 \u2014 Next >");
        footPage.setTextColor(Color.rgb(175, 180, 200));
        footPage.setTextSize(18);
        side.addView(footPage);
        // ---- Main area ----
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -1, 1);
        mlp.leftMargin = dp(32);
        root.addView(main, mlp);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        main.addView(top, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout topTx = new LinearLayout(this);
        topTx.setOrientation(LinearLayout.VERTICAL);
        top.addView(topTx, new LinearLayout.LayoutParams(0, -2, 1));
        topTitle = new TextView(this);
        topTitle.setTextColor(Color.WHITE);
        topTitle.setTextSize(34);
        topTitle.setTypeface(topTitle.getTypeface(), android.graphics.Typeface.BOLD);
        topTx.addView(topTitle);
        topSub = new TextView(this);
        topSub.setTextColor(Color.rgb(175, 180, 200));
        topSub.setTextSize(20);
        topTx.addView(topSub);
        // v3.69: the search pill WORKS now (Morph style) -- tap it to type a
        // channel name and the grid filters to matches. Tap it again with a
        // query set to clear the search.
        searchPill = new TextView(this);
        searchPill.setText("Search");
        searchPill.setTextColor(Color.rgb(175, 180, 200));
        searchPill.setTextSize(22);
        searchPill.setPadding(dp(28), dp(12), dp(28), dp(12));
        GradientDrawable sg = rounded(Color.rgb(38, 44, 78), 28);
        sg.setStroke(dp(2), Color.rgb(70, 80, 130));
        searchPill.setBackground(sg);
        searchPill.setFocusable(true);
        searchPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSearch(); }
        });
        top.addView(searchPill);
        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams gsl = new LinearLayout.LayoutParams(-1, -1);
        gsl.topMargin = dp(20);
        main.addView(gridScroll, gsl);
        gridCol = new LinearLayout(this);
        gridCol.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(gridCol);
        setContentView(root);
        int startCat = getIntent().getIntExtra("cat", 0);
        if (startCat >= 0 && startCat < CATS.length) cat = startCat;
        buildCats();
        buildChannels();
        refresh();
    }

    void buildCats() {
        // v3.69: categories made smaller so ALL six fit in the sidebar at
        // once, no scrolling -- the scroll made the selected name hide and
        // come back when he moved down ("make the list smaller and ui user
        // friendly like all the list shows").
        // v3.70: even smaller, like Morph -- text 20 -> 18sp, padding
        // 8 -> 6dp, margin 6 -> 4dp.
        catCol.removeAllViews();
        for (int i = 0; i < CATS.length; i++) {
            TextView t = new TextView(this);
            t.setText(CATS[i]);
            t.setTextSize(18);
            t.setPadding(dp(18), dp(6), dp(18), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            catCol.addView(t, lp);
            catViews[i] = t;
        }
    }

    void addAll(Ch[] a) { for (Ch c : a) shown.add(c); }

    void buildChannels() {
        shown.clear();
        if (cat == 0) {
            addAll(YT); addAll(FREE); addAll(AR_NEWS); addAll(AR_GEN); addAll(SPORT); addAll(WORLD); addAll(SERIES);
            if (iptvConfiguredHere()) {
                if (iptv.isEmpty()) {
                    shown.add(new Ch(iptvLoading ? "Loading provider channels..." : "No provider channels found", iptvLoading ? "Please wait" : "Test the connection in Settings > My IPTV", null, 0xFF3A4152, 0, "?", 3));
                    if (!iptvLoading) loadIptv();
                } else { for (Ch c : iptv) shown.add(c); }
            }
        }
        else if (cat == 1) { addAll(YT); shown.add(AR_GEN[0]); shown.add(AR_GEN[1]); }
        else if (cat == 2) { addAll(FREE); addAll(AR_NEWS); }
        else if (cat == 3) { addAll(AR_NEWS); addAll(AR_GEN); }
        else if (cat == 4) { addAll(SPORT); }
        else if (cat == 5) { addAll(YT); }
        else if (cat == 6) { addAll(WORLD); }
        else { addAll(SERIES); }
        // v3.69: search filter -- when a query is set, keep only channels
        // whose name contains it (case-insensitive), across the category.
        if (searchQuery != null && searchQuery.length() > 0) {
            String q = searchQuery.toLowerCase(java.util.Locale.US);
            java.util.List<Ch> kept = new java.util.ArrayList<Ch>();
            for (Ch c : shown) if (c.name != null && c.name.toLowerCase(java.util.Locale.US).contains(q)) kept.add(c);
            shown.clear(); shown.addAll(kept);
        }
        if (chSel >= shown.size()) chSel = 0;
        gridCol.removeAllViews();
        cardViews.clear();
        for (int r = 0; r * GRID_COLS < shown.size(); r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(20);
            gridCol.addView(row, rlp);
            for (int c = 0; c < GRID_COLS; c++) {
                int idx = r * GRID_COLS + c;
                if (idx >= shown.size()) {
                    View sp = new View(this);
                    row.addView(sp, new LinearLayout.LayoutParams(0, -2, 1));
                    continue;
                }
                Ch ch = shown.get(idx);
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
                if (c > 0) clp.leftMargin = dp(20);
                row.addView(card, clp);
                cardViews.add(card);
                // Logo area with initials + red live dot (top-right).
                FrameLayout logo = new FrameLayout(this);
                GradientDrawable lbg = rounded(ch.color, 12);
                logo.setBackground(lbg);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, dp(130));
                card.addView(logo, llp);
                if (ch.logo != 0) {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setImageResource(ch.logo);
                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    logo.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                } else {
                    TextView init = new TextView(this);
                    init.setText(ch.init);
                    init.setTextColor(Color.WHITE);
                    init.setTextSize(44);
                    init.setTypeface(init.getTypeface(), android.graphics.Typeface.BOLD);
                    init.setGravity(Gravity.CENTER);
                    logo.addView(init, new FrameLayout.LayoutParams(-1, -1));
                }
                View rdot = new View(this);
                rdot.setBackground(redDotBg());
                FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(20), dp(20));
                dlp.gravity = Gravity.TOP | Gravity.END;
                dlp.topMargin = dp(10);
                dlp.rightMargin = dp(10);
                logo.addView(rdot, dlp);
                TextView n = new TextView(this);
                n.setText(ch.name);
                n.setTextColor(Color.WHITE);
                n.setTextSize(24);
                n.setTypeface(n.getTypeface(), android.graphics.Typeface.BOLD);
                n.setMaxLines(1);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
                nlp.topMargin = dp(12);
                card.addView(n, nlp);
                TextView s = new TextView(this);
                s.setText(ch.sub);
                s.setTextColor(Color.rgb(175, 180, 200));
                s.setTextSize(18);
                s.setMaxLines(1);
                card.addView(s);
            }
        }
    }

    void refresh() {
        for (int i = 0; i < CATS.length; i++) {
            TextView t = catViews[i];
            GradientDrawable g = rounded(Color.TRANSPARENT, 12);
            if (i == cat) {
                g.setColor(col == 0 ? 0xFFFFD200 : Color.rgb(58, 68, 120));
                t.setTextColor(col == 0 ? Color.rgb(20, 20, 20) : Color.WHITE);
                t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                t.setTextColor(Color.rgb(225, 230, 240));
                t.setTypeface(t.getTypeface(), android.graphics.Typeface.NORMAL);
            }
            t.setBackground(g);
        }
        for (int i = 0; i < cardViews.size(); i++) {
            View card = cardViews.get(i);
            GradientDrawable g = rounded(Color.rgb(38, 44, 78), 16);
            if (i == chSel && col == 1) g.setStroke(dp(3), 0xFFFFD200);
            else g.setStroke(dp(2), Color.rgb(70, 80, 130));
            card.setBackground(g);
        }
        topTitle.setText(CATS[cat]);
        topSub.setText(shown.size() + " titles \u2014 choose a channel to play"
            + ((searchQuery != null && searchQuery.length() > 0) ? ("  \u2022  searching: " + searchQuery) : ""));
        footCount.setText(shown.size() + " channels \u2022 free");
        if (searchPill != null) searchPill.setText((searchQuery != null && searchQuery.length() > 0) ? ("X  " + searchQuery) : "Search");
        final int row = chSel / GRID_COLS;
        gridScroll.post(new Runnable() {
            @Override public void run() { gridScroll.scrollTo(0, row * dp(270)); }
        });
        // v3.69: no sidebar auto-scroll -- all six categories now fit, so
        // the selected one never hides. (The v3.67 auto-scroll made the
        // name hide and come back when he moved down.)
    }

    // v3.69: functional search (Morph style). Tapping the search pill opens
    // a text box; typing a name filters the grid to matches in this
    // category. Tapping it again while a search is active clears it.
    void openSearch() {
        if (searchQuery != null && searchQuery.length() > 0) {
            searchQuery = null; chSel = 0; buildChannels(); refresh(); return;
        }
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Channel name...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(150, 155, 175));
        new android.app.AlertDialog.Builder(this)
            .setTitle("Search channels")
            .setView(input)
            .setPositiveButton("Search", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    String q = String.valueOf(input.getText()).trim();
                    if (q.length() > 0) { searchQuery = q; chSel = 0; buildChannels(); refresh(); }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
        input.requestFocus();
    }

    @Override public void onBackPressed() {
        // v3.67: Back from the grid goes to the sidebar first; Back from
        // the sidebar exits the guide. (Before, Back exited straight away.)
        if (col == 1) { col = 0; refresh(); return; }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            // v3.68: back to the NORMAL way (his correction) -- Left in the
            // grid moves left to the previous channel; only at the leftmost
            // column does it go to the sidebar. v3.67 made Left jump to the
            // sidebar from any column, so he could not move left inside the
            // grid at all -- "it should work normal way".
            if (col == 1) {
                if (chSel % GRID_COLS == 0) col = 0;
                else chSel--;
                refresh();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (col == 0) { col = 1; refresh(); }
            else if (chSel % GRID_COLS < GRID_COLS - 1 && chSel + 1 < shown.size()) { chSel++; refresh(); }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (col == 0) { cat = (cat + CATS.length - 1) % CATS.length; chSel = 0; buildChannels(); }
            else if (chSel >= GRID_COLS) chSel -= GRID_COLS;
            refresh(); return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (col == 0) { cat = (cat + 1) % CATS.length; chSel = 0; buildChannels(); }
            else if (chSel + GRID_COLS < shown.size()) chSel += GRID_COLS;
            refresh(); return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (col == 0) { col = 1; refresh(); return true; }
            playSelected(); return true;
        }
        return super.onKeyDown(code, e);
    }

    boolean iptvConfiguredHere() {
        android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        String t = p.getString("iptv_type", "");
        if ("xtream".equals(t)) return p.getString("iptv_server", "").length() > 0
            && p.getString("iptv_user", "").length() > 0 && p.getString("iptv_pass", "").length() > 0;
        if ("m3u".equals(t)) return p.getString("iptv_m3u", "").length() > 0;
        return false;
    }

    // v3.54: cached channels first (guide opens instantly), then a fresh
    // fetch in the background; the raw reply is cached on the box.
    void loadIptv() {
        if (iptvLoading) return;
        iptvLoading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
                final String type = p.getString("iptv_type", "");
                final String server = p.getString("iptv_server", "");
                final String user = p.getString("iptv_user", "");
                final String pass = p.getString("iptv_pass", "");
                final String m3u = p.getString("iptv_m3u", "");
                final ArrayList<Ch> cached = readIptvCache(type, server, user, pass);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!cached.isEmpty()) { iptv = cached; if (cat == 0) { buildChannels(); refresh(); } }
                    }
                });
                ArrayList<Ch> fresh = new ArrayList<Ch>();
                try {
                    if ("xtream".equals(type)) {
                        String catsJson = Iptv.fetchText(Iptv.xtreamApiUrl(server, user, pass, "get_live_categories"), 10000);
                        java.util.Map<String, String> cats = Iptv.parseLiveCategories(catsJson);
                        String streamsJson = Iptv.fetchText(Iptv.xtreamApiUrl(server, user, pass, "get_live_streams"), 20000);
                        writeFile("iptv_cache.txt", streamsJson);
                        writeFile("iptv_cats.txt", catsJson);
                        for (Iptv.Chan c : Iptv.parseLiveStreams(streamsJson, cats, server, user, pass))
                            fresh.add(new Ch(c.name, c.sub, c.url, 0xFF1E3A5F, 0, Iptv.initials(c.name), 2));
                    } else {
                        String text = Iptv.fetchText(m3u, 20000);
                        writeFile("iptv_cache.txt", text);
                        for (Iptv.Chan c : Iptv.parseM3u(text))
                            fresh.add(new Ch(c.name, c.sub, c.url, 0xFF1E3A5F, 0, Iptv.initials(c.name), 2));
                    }
                } catch (Exception e) { /* keep the cached list */ }
                if (fresh.isEmpty()) fresh = cached;
                final ArrayList<Ch> done = fresh;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        iptvLoading = false; iptv = done;
                        if (cat == 0) { buildChannels(); refresh(); }
                    }
                });
            }
        }).start();
    }

    void writeFile(String name, String text) {
        try {
            java.io.FileWriter w = new java.io.FileWriter(new java.io.File(getFilesDir(), name));
            w.write(text); w.close();
        } catch (Exception e) { /* cache is best-effort */ }
    }

    String readFile(String name) {
        try {
            java.io.File f = new java.io.File(getFilesDir(), name);
            if (!f.exists()) return "";
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    ArrayList<Ch> readIptvCache(String type, String server, String user, String pass) {
        ArrayList<Ch> out = new ArrayList<Ch>();
        String raw = readFile("iptv_cache.txt");
        if (raw.length() == 0) return out;
        if ("xtream".equals(type)) {
            java.util.Map<String, String> cats = Iptv.parseLiveCategories(readFile("iptv_cats.txt"));
            for (Iptv.Chan c : Iptv.parseLiveStreams(raw, cats, server, user, pass))
                out.add(new Ch(c.name, c.sub, c.url, 0xFF1E3A5F, 0, Iptv.initials(c.name), 2));
        } else {
            for (Iptv.Chan c : Iptv.parseM3u(raw))
                out.add(new Ch(c.name, c.sub, c.url, 0xFF1E3A5F, 0, Iptv.initials(c.name), 2));
        }
        return out;
    }

    void playSelected() {
        if (shown.isEmpty()) return;
        Ch c = shown.get(chSel);
        if (c.kind == 3) { Toast.makeText(this, "Add your provider in Settings > My IPTV", Toast.LENGTH_SHORT).show(); return; }
        if (c.kind == 4) {
            // v4.36: a series season opens the Cemba episode list; the
            // season year is the last 4 chars of the card name.
            String season = c.name.length() >= 4 ? c.name.substring(c.name.length() - 4) : "";
            Intent i = new Intent(this, EpisodeListActivity.class);
            i.putExtra("season", season);
            i.putExtra("title", c.name);
            startActivity(i);
        } else if (c.kind == 0) {
            if (!tryCobalt(c.url)) Toast.makeText(this, "TizenTube Cobalt not installed", Toast.LENGTH_SHORT).show();
        } else {
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra("url", c.url);
            i.putExtra("name", c.name);
            if (c.ua != null) i.putExtra("ua", c.ua);
            startActivity(i);
        }
    }

    boolean tryCobalt(String url) {
        String[] pkgs = { "io.gh.reisxd.tizentube.cobalt", "io.gh.reisxd.tizentube.cobalt.ab" };
        for (String pk : pkgs) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.setComponent(new ComponentName(pk, "dev.cobalt.app.MainActivity"));
                i.putExtra("url", url);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) { /* try next */ }
        }
        return false;
    }
}
