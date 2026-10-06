package com.fikret.jarvishud;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class JarvisWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c,m,id);
        NewsFetcher.fetch(c, titles -> updateAll(c));
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        ComponentName cn = new ComponentName(c, JarvisWidgetProvider.class);
        int[] ids = m.getAppWidgetIds(cn);
        for (int id: ids) update(c,m,id);
    }

    private static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_layout);
        rv.setImageViewBitmap(R.id.widgetImage, WidgetRenderer.render(c));
        Intent i = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widgetImage, pi);
        m.updateAppWidget(id, rv);
    }
}
