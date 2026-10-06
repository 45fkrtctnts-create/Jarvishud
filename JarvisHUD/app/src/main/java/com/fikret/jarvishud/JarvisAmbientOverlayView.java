package com.fikret.jarvishud;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class JarvisAmbientOverlayView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tx=new Paint(Paint.ANTI_ALIAS_FLAG);
    private JarvisCoreView.Mode mode=JarvisCoreView.Mode.LISTENING;
    private float amp=0f;
    private final long start=System.nanoTime();

    public JarvisAmbientOverlayView(Context c){
        super(c);
        setBackgroundColor(Color.TRANSPARENT);
        tx.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        tx.setTextAlign(Paint.Align.CENTER);
    }

    public void setState(JarvisCoreView.Mode m,String msg){ if(m!=null) mode=m; invalidate(); }
    public void setAmplitude(float a){ amp=Math.max(0f,Math.min(1f,a)); invalidate(); }

    private String state(){
        switch(mode){
            case LISTENING:return "DİNLİYORUM";
            case PROCESSING:return "İŞLENİYOR";
            case SPEAKING:return "YANITLIYOR";
            case ERROR:return "HATA";
            default:return "HAZIR";
        }
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(); if(w<=0||h<=0){postInvalidateOnAnimation();return;}
        float t=(System.nanoTime()-start)/1_000_000_000f,mn=Math.min(w,h);
        float cx=w*.5f,cy=h*.46f,base=mn*.22f;
        float pulse=.5f+.5f*(float)Math.sin(t*3.2f),a=Math.max(amp,.12f+.16f*pulse);
        if(mode==JarvisCoreView.Mode.PROCESSING)a=Math.max(a,.38f);
        if(mode==JarvisCoreView.Mode.SPEAKING)a=Math.max(a,.48f);

        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(42,0,14,27));c.drawRect(0,0,w,h,p);

        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(mn*.0022f);p.setColor(Color.argb(180,35,235,245));
        float e=mn*.03f,k=mn*.08f;Path f=new Path();
        f.moveTo(e,e+k);f.lineTo(e,e);f.lineTo(e+k,e);
        f.moveTo(w-e-k,e);f.lineTo(w-e,e);f.lineTo(w-e,e+k);
        f.moveTo(e,h-e-k);f.lineTo(e,h-e);f.lineTo(e+k,h-e);
        f.moveTo(w-e-k,h-e);f.lineTo(w-e,h-e);f.lineTo(w-e,h-e-k);c.drawPath(f,p);

        float ly=h*.085f,lr=mn*.05f;
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(220,3,25,39));c.drawCircle(cx,ly,lr,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(mn*.0025f);p.setColor(Color.rgb(65,241,250));c.drawCircle(cx,ly,lr,p);
        tx.setTypeface(Typeface.DEFAULT_BOLD);tx.setTextAlign(Paint.Align.CENTER);tx.setTextSize(lr*.9f);tx.setColor(Color.rgb(120,250,255));
        c.drawText("J+",cx,ly-(tx.ascent()+tx.descent())/2f,tx);

        tx.setTypeface(Typeface.DEFAULT);tx.setTextAlign(Paint.Align.LEFT);tx.setTextSize(mn*.018f);tx.setColor(Color.argb(220,115,245,255));
        float sy=h*.24f;
        c.drawText("●  SYSTEM ONLINE",w*.055f,sy,tx);
        c.drawText("●  VOICE ACTIVE",w*.055f,sy+mn*.045f,tx);
        c.drawText("●  AI CORE READY",w*.055f,sy+mn*.09f,tx);
        c.drawText(mode==JarvisCoreView.Mode.LISTENING?"◉  LISTENING":"○  LISTENING",w*.76f,sy,tx);
        c.drawText(mode==JarvisCoreView.Mode.PROCESSING?"◉  PROCESSING":"○  PROCESSING",w*.76f,sy+mn*.045f,tx);
        c.drawText(mode==JarvisCoreView.Mode.SPEAKING?"◉  SPEAKING":"○  SPEAKING",w*.76f,sy+mn*.09f,tx);

        RectF rr=new RectF(cx-base*1.38f,cy-base*1.38f,cx+base*1.38f,cy+base*1.38f);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(mn*.003f);p.setColor(Color.argb(180,40,238,247));
        float rot=(t*24f)%360f;c.drawArc(rr,rot,70,false,p);c.drawArc(rr,rot+125,45,false,p);c.drawArc(rr,rot+220,85,false,p);

        for(int ring=0;ring<3;ring++){
            Path q=new Path();int seg=110;float rad=base*(.94f+ring*.14f+a*.05f);
            for(int i=0;i<=seg;i++){
                float ang=(float)(Math.PI*2*i/seg);
                float noise=(float)Math.sin(ang*(6+ring)+t*(1.5f+ring*.2f))*base*.045f+(float)Math.sin(ang*13-t*.9f)*base*.016f;
                float r=rad+noise*(1f+a*1.7f),x=cx+(float)Math.cos(ang)*r,y=cy+(float)Math.sin(ang)*r*.86f;
                if(i==0)q.moveTo(x,y);else q.lineTo(x,y);
            }
            q.close();p.setStrokeWidth(mn*(.005f-ring*.0009f));p.setColor(Color.argb(210-ring*45,45,240,247));c.drawPath(q,p);
        }

        float cr=base*(.34f+a*.025f);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(55,40,235,246));c.drawCircle(cx,cy,cr*1.3f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(mn*.0034f);p.setColor(Color.rgb(135,250,255));c.drawCircle(cx,cy,cr,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<48;i++){
            double ang=Math.toRadians(i*137.5+t*12);float r=cr*(.12f+(i%9)/11f);
            float x=cx+(float)Math.cos(ang)*r,y=cy+(float)Math.sin(ang)*r*.72f;
            p.setColor(Color.argb(105+(i%4)*30,125,248,255));c.drawCircle(x,y,Math.max(1.4f,mn*.002f),p);
        }

        tx.setTextAlign(Paint.Align.CENTER);tx.setTypeface(Typeface.DEFAULT);tx.setTextSize(mn*.052f);tx.setColor(Color.rgb(230,253,255));
        c.drawText("J A R V I S",cx,cy+base*1.62f,tx);
        tx.setTextSize(mn*.028f);tx.setColor(Color.rgb(125,245,255));c.drawText(state(),cx,cy+base*1.84f,tx);

        float wy=cy+base*2.08f,ww=w*.48f;int bars=50;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Math.max(1.6f,mn*.0023f));p.setColor(Color.rgb(65,242,250));
        for(int i=0;i<bars;i++){
            float n=(i-bars/2f)/(bars/2f),env=1f-Math.min(1f,Math.abs(n)),mv=.3f+.7f*Math.abs((float)Math.sin(t*6+i*.47f));
            float level=mn*(.008f+.05f*env*mv*(.22f+a)),x=cx-ww/2+ww*i/(bars-1f);c.drawLine(x,wy-level,x,wy+level,p);
        }
        tx.setTextAlign(Paint.Align.RIGHT);tx.setTextSize(mn*.015f);tx.setColor(Color.argb(190,110,245,252));
        c.drawText("WAKE WORD ACTIVE",w-e*1.5f,e*2f,tx);
        postInvalidateOnAnimation();
    }
}
