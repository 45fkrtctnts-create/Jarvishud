package com.fikret.jarvishud;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class WidgetRenderer {
    private static final int W = 1100, H = 520;

    public static Bitmap render(Context c) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));

        p.setColor(Color.rgb(5, 13, 22)); g.drawRoundRect(new RectF(6,6,W-6,H-6), 38,38,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.rgb(52, 172, 238)); g.drawRoundRect(new RectF(8,8,W-8,H-8), 38,38,p);

        p.setStrokeWidth(1); p.setColor(Color.argb(28, 90, 195, 255));
        for (int x=32;x<W;x+=42) g.drawLine(x,20,x,H-20,p);
        for (int y=30;y<H;y+=42) g.drawLine(20,y,W-20,y,p);

        Calendar now = Calendar.getInstance();
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.getTime());
        String date = new SimpleDateFormat("dd MMMM yyyy", new Locale("tr","TR")).format(now.getTime());
        String day = new SimpleDateFormat("EEEE", new Locale("tr","TR")).format(now.getTime());

        t.setColor(Color.rgb(231,248,255)); t.setTextSize(82); t.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)); g.drawText(time,62,108,t);
        t.setTextSize(30); t.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)); g.drawText(date,65,152,t);
        t.setTextSize(24); t.setColor(Color.rgb(145,170,190)); g.drawText(day,65,186,t);

        float cx=550, cy=242, r=104;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(70,35,145,255)); p.setShadowLayer(44,0,0,Color.rgb(45,165,255)); g.drawCircle(cx,cy,r*1.15f,p); p.clearShadowLayer();
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setColor(Color.rgb(200,238,255)); g.drawCircle(cx,cy,r*0.78f,p);
        p.setStrokeWidth(9); p.setColor(Color.rgb(70,180,255)); RectF rr=new RectF(cx-r,cy-r,cx+r,cy+r); g.drawArc(rr,-40,86,false,p); g.drawArc(rr,116,54,false,p); g.drawArc(rr,220,84,false,p);
        t.setTextAlign(Paint.Align.CENTER); t.setColor(Color.WHITE); t.setTextSize(35); t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD)); g.drawText("JARVIS",cx,cy+12,t); t.setTextAlign(Paint.Align.LEFT);

        panel(g,p,45,225,390,475); panel(g,p,700,42,1050,220); panel(g,p,700,240,1050,475);

        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD)); t.setTextSize(26); t.setColor(Color.WHITE); g.drawText("●  Son Dakika",66,263,t);
        List<String> news = NewsFetcher.cached(c);
        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL)); t.setTextSize(20); t.setColor(Color.rgb(185,220,240));
        int yy=305; for(String n:news){ g.drawText(ellipsize(n,31),70,yy,t); yy+=55; }

        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD)); t.setTextSize(26); t.setColor(Color.WHITE); g.drawText("Takvim",725,82,t);
        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL)); t.setTextSize(22); t.setColor(Color.rgb(180,215,235));
        String month = new SimpleDateFormat("MMMM yyyy", new Locale("tr","TR")).format(now.getTime()); g.drawText(month,725,120,t);
        t.setTextSize(18); t.setColor(Color.rgb(130,170,195)); g.drawText("Pzt  Sal  Çar  Per  Cum  Cmt  Paz",725,155,t);
        t.setTextSize(30); t.setColor(Color.rgb(85,195,255)); g.drawText("Bugün: " + now.get(Calendar.DAY_OF_MONTH),725,195,t);

        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD)); t.setTextSize(26); t.setColor(Color.WHITE); g.drawText("Günlük Program",725,282,t);
        t.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL)); t.setTextSize(19); t.setColor(Color.rgb(184,220,240));
        List<String> schedule = ScheduleReader.today(c); yy=320; for(String s:schedule){ g.drawText(ellipsize(s,27),725,yy,t); yy+=38; }

        t.setTextAlign(Paint.Align.CENTER); t.setTextSize(16); t.setColor(Color.rgb(90,175,220)); g.drawText("Dokun → JARVIS kontrol paneli",550,500,t); t.setTextAlign(Paint.Align.LEFT);
        return b;
    }

    private static void panel(Canvas g, Paint p, float l,float top,float r,float bottom){ p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(105,9,25,38)); g.drawRoundRect(new RectF(l,top,r,bottom),24,24,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.argb(150,76,185,245)); g.drawRoundRect(new RectF(l,top,r,bottom),24,24,p); }
    private static String ellipsize(String s,int n){ if(s==null)return ""; return s.length()<=n?s:s.substring(0,n-1)+"…"; }
}
