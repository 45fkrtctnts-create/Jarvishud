package com.fikret.jarvishud;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class ScheduleReader {
    public static List<String> today(Context c) {
        List<String> out = new ArrayList<>();
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            out.add("Takvim izni gerekli");
            return out;
        }
        Calendar s = Calendar.getInstance();
        s.set(Calendar.HOUR_OF_DAY,0); s.set(Calendar.MINUTE,0); s.set(Calendar.SECOND,0); s.set(Calendar.MILLISECOND,0);
        Calendar e = (Calendar)s.clone(); e.add(Calendar.DAY_OF_YEAR,1);
        String[] proj = { CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE };
        String sel = CalendarContract.Instances.BEGIN + ">=? AND " + CalendarContract.Instances.BEGIN + "<?";
        String[] args = { String.valueOf(s.getTimeInMillis()), String.valueOf(e.getTimeInMillis()) };
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Instances.CONTENT_URI, proj, sel, args, CalendarContract.Instances.BEGIN+" ASC")) {
            if (cur != null) {
                SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
                while (cur.moveToNext() && out.size() < 4) {
                    long begin = cur.getLong(0);
                    String title = cur.getString(1);
                    if (title == null || title.trim().isEmpty()) title = "Etkinlik";
                    out.add(f.format(begin) + "  " + title);
                }
            }
        } catch (Exception ignored) {}
        if (out.isEmpty()) out.add("Bugün kayıtlı program yok");
        return out;
    }
}
