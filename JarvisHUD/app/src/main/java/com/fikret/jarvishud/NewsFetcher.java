package com.fikret.jarvishud;

import android.content.Context;
import android.content.SharedPreferences;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class NewsFetcher {
    private static final String RSS = "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr";

    public interface Callback { void done(List<String> titles); }

    public static List<String> cached(Context c) {
        SharedPreferences p = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) out.add(p.getString("news"+i, i == 1 ? "Haberler yükleniyor..." : ""));
        return out;
    }

    public static void fetch(Context c, Callback cb) {
        new Thread(() -> {
            List<String> titles = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RSS).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "JarvisHUD/1.0");
                try (InputStream in = conn.getInputStream()) {
                    XmlPullParser xp = XmlPullParserFactory.newInstance().newPullParser();
                    xp.setInput(in, "UTF-8");
                    boolean inItem = false;
                    int event = xp.getEventType();
                    while (event != XmlPullParser.END_DOCUMENT && titles.size() < 3) {
                        if (event == XmlPullParser.START_TAG) {
                            String name = xp.getName();
                            if ("item".equalsIgnoreCase(name)) inItem = true;
                            else if (inItem && "title".equalsIgnoreCase(name)) {
                                String t = xp.nextText();
                                if (t != null && !t.trim().isEmpty()) titles.add(t.trim());
                            }
                        } else if (event == XmlPullParser.END_TAG && "item".equalsIgnoreCase(xp.getName())) inItem = false;
                        event = xp.next();
                    }
                }
                if (!titles.isEmpty()) {
                    SharedPreferences.Editor e = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit();
                    for (int i = 0; i < titles.size() && i < 3; i++) e.putString("news"+(i+1), titles.get(i));
                    e.apply();
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
                List<String> result = titles.isEmpty() ? cached(c) : titles;
                if (cb != null) cb.done(result);
            }
        }, "JarvisNews").start();
    }
}
